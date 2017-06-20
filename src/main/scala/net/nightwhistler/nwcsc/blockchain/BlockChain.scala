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

object GenesisBlock extends Block(0, "0", 1497359352, BlockMessage("74dd70aa-2ddb-4aa2-8f95-ffc3b5cebad1","Genesis block"), 0, "e9f158815e681216371253df8af80db834248bed90f2dda82ad65ef99cf777f6")

case class Block(index: Long, previousHash: String, timestamp: Long, message: BlockMessage, nonse: Long, hash: String) {
  def difficulty = BigInt(hash, 16)
}

object BlockChain {

  val BASE_DIFFICULTY = BigInt("f" * 64, 16)
  val EVERY_X_BLOCKS = 5
  val POW_CURVE = 5

  def apply(): BlockChain = new BlockChain(Seq(GenesisBlock))

  def apply(blocks: Seq[Block]): Try[BlockChain] = {
    if ( validChain(blocks) ) Success(new BlockChain(blocks))
    else Failure(new IllegalArgumentException("Invalid chain specified."))
  }

  @tailrec
  def validChain( chain: Seq[Block] ): Boolean = chain match {
    case singleBlock :: Nil if singleBlock == GenesisBlock => true
    case head :: beforeHead :: tail if validBlock(head, beforeHead) => validChain(beforeHead :: tail)
    case _ => false
  }

  def validBlock(newBlock: Block, previousBlock: Block) =
    previousBlock.index + 1 == newBlock.index &&
    previousBlock.hash == newBlock.previousHash &&
    calculateHashForBlock(newBlock) == newBlock.hash &&
    newBlock.difficulty < calculateDifficulty(newBlock.index)

  def calculateDifficulty(index: Long): BigInt = {
    val blockSeriesNumber = ((BigInt(index) + 1) / EVERY_X_BLOCKS ) + 1
    val pow = blockSeriesNumber.pow(POW_CURVE)

    BASE_DIFFICULTY / pow
  }

  def calculateHashForBlock( block: Block ) = calculateHash(block.index, block.previousHash, block.timestamp, block.message, block.nonse)

  def calculateHash(index: Long, previousHash: String, timestamp: Long, blockMessage: BlockMessage, nonse: Long) =
    s"$index:$previousHash:$timestamp:${blockMessage.id}:${blockMessage.data}:$nonse".sha256.hex


}

case class BlockChain private( val blocks: Seq[Block] ) {

  import BlockChain._

  val logger = Logger("BlockChain")

  def addBlock( data: String ): BlockChain = addBlock(BlockMessage(data))

  def addBlock( blockMessage: BlockMessage ) = new BlockChain(generateNextBlock(blockMessage) +: blocks)

  def contains( blockMessage: BlockMessage ) = blocks.find( b => b.message == blockMessage ).isDefined

  def addBlock( block: Block ): Try[ BlockChain ] =
    if ( validBlock(block) ) Success( new BlockChain(block +: blocks ))
    else Failure( new IllegalArgumentException("Invalid block added"))

  def firstBlock: Block = blocks.last
  def latestBlock: Block = blocks.head

  def generateNextBlock(blockMessage: BlockMessage ): Block = {
    val timeBefore = new Date().getTime
    val block = generateNextBlock(blockMessage, 0)
    logger.debug(s"Found block in ${new Date().getTime - timeBefore} ms after ${block.nonse +1} tries.")
    block
  }

  @tailrec
  private def generateNextBlock( blockMessage: BlockMessage, nonse: Long ): Block = {
    val previousBlock = latestBlock
    val nextIndex = previousBlock.index + 1
    val nextTimestamp = new Date().getTime() / 1000
    val nextHash = calculateHash(nextIndex, previousBlock.hash, nextTimestamp, blockMessage, nonse)

    val block = Block(nextIndex, previousBlock.hash, nextTimestamp, blockMessage, nonse, nextHash )
    if (! validBlock(block) ) {
      generateNextBlock(blockMessage, nonse +1)
    } else block
  }

  def validBlock( newBlock: Block ): Boolean = BlockChain.validBlock(newBlock, latestBlock)

}



