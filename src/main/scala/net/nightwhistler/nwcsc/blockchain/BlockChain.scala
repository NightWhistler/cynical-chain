package net.nightwhistler.nwcsc.blockchain

import java.lang.Integer.parseInt
import java.math.BigInteger
import java.util.{Date, UUID}

import com.roundeights.hasher.Implicits._
import com.typesafe.scalalogging.Logger

import scala.annotation.tailrec
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Created by alex on 13-6-17.
  */

case class BlockMessage( data: String, id: String = UUID.randomUUID().toString)

object GenesisBlock extends Block(0, "0", 1497359352, Seq(BlockMessage("74dd70aa-2ddb-4aa2-8f95-ffc3b5cebad1","Genesis block")), 0, "e9f158815e681216371253df8af80db834248bed90f2dda82ad65ef99cf777f6")

case class Block(index: Long, previousHash: String, timestamp: Long, messages: Seq[BlockMessage], nonse: Long, hash: String) {
  def difficulty = BigInt(hash, 16)
}

object BlockChain {

  val BASE_DIFFICULTY = BigInt("f" * 64, 16)
  val EVERY_X_BLOCKS = 5
  val POW_CURVE = 5
  val TWO_HOURS = 2 * 60 * 60 * 1000

  def apply(): BlockChain = new BlockChain(Seq(GenesisBlock))

  def apply(blocks: Seq[Block]): Try[BlockChain] = {
    if ( validChain(blocks) ) Success(new BlockChain(blocks))
    else Failure(new IllegalArgumentException("Invalid chain specified."))
  }

  def validChain( chain: Seq[Block] ): Boolean = validChain(chain, Set.empty)

  @tailrec
  private def validChain( chain: Seq[Block], messages: Set[BlockMessage]): Boolean = chain match {
    case singleBlock :: Nil if singleBlock == GenesisBlock => true
    case head :: beforeHead :: tail if validBlock(head, beforeHead, messages) => validChain(beforeHead :: tail, messages ++ head.messages)
    case _ => false
  }

  private def validBlock(newBlock: Block, previousBlock: Block, messages: Set[BlockMessage]) =
    previousBlock.index + 1 == newBlock.index &&
    previousBlock.hash == newBlock.previousHash &&
    (newBlock.timestamp - new Date().getTime) < TWO_HOURS &&
    previousBlock.timestamp < newBlock.timestamp &&
    calculateHashForBlock(newBlock) == newBlock.hash &&
    newBlock.difficulty < calculateDifficulty(newBlock.index) &&
    ! newBlock.messages.exists( messages.contains(_))

  def calculateDifficulty(index: Long): BigInt = {
    val blockSeriesNumber = ((BigInt(index) + 1) / EVERY_X_BLOCKS ) + 1
    val pow = blockSeriesNumber.pow(POW_CURVE)

    BASE_DIFFICULTY / pow
  }

  def calculateHashForBlock( block: Block ) = calculateHash(block.index, block.previousHash, block.timestamp, block.messages, block.nonse)

  def calculateHash(index: Long, previousHash: String, timestamp: Long, messages: Seq[BlockMessage], nonse: Long) =
    s"$index:$previousHash:$timestamp:${contentsAsString(messages)}:$nonse".sha256.hex

  private def contentsAsString( messages: Seq[BlockMessage]) = messages.map{ case BlockMessage(data, id) => s"${data}:${id}" }.mkString(":")

}

case class BlockChain private( val blocks: Seq[Block] ) {

  import BlockChain._

  val logger = Logger("BlockChain")

  def addMessage(data: String ): BlockChain = addBlock(Seq(BlockMessage(data)))

  def addBlock(messages: Seq[BlockMessage] ) = new BlockChain(generateNextBlock(messages) +: blocks)

  def contains( blockMessage: BlockMessage ) = blocks.find( b => b.messages.contains(blockMessage) ).isDefined

  def containsAll( messages: Seq[BlockMessage] ) = messages.forall( contains(_) )

  def addBlock( block: Block ): Try[ BlockChain ] =
    if ( validBlock(block) ) Success( new BlockChain(block +: blocks ))
    else Failure( new IllegalArgumentException("Invalid block added"))

  def firstBlock: Block = blocks.last
  def latestBlock: Block = blocks.head

  def generateNextBlock(messages: Seq[BlockMessage] ): Block = {
    val timeBefore = new Date().getTime
    val block = generateNextBlock(messages, 0)
    logger.debug(s"Found block in ${new Date().getTime - timeBefore} ms after ${block.nonse +1} tries.")
    block
  }

  @tailrec
  private def generateNextBlock( messages: Seq[BlockMessage], nonse: Long ): Block = {
    attemptBlock(messages, nonse) match {
      case Some(block) => block
      case None => generateNextBlock(messages, nonse +1)
    }
  }

  def attemptBlock(messages: Seq[BlockMessage], nonse: Long ): Option[Block] = {
    val previousBlock = latestBlock
    val nextIndex = previousBlock.index + 1
    val nextTimestamp = new Date().getTime() / 1000
    val nextHash = calculateHash(nextIndex, previousBlock.hash, nextTimestamp, messages, nonse)

    val block = Block(nextIndex, previousBlock.hash, nextTimestamp, messages, nonse, nextHash )
    if ( validBlock(block) ) {
      Some(block)
    } else {
      None
    }
  }

  def validBlock(newBlock: Block): Boolean = validChain( newBlock +: blocks )
}



