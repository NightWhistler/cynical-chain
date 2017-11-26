package net.nightwhistler.nwcsc.blockchain

import java.util.{Date, UUID}

import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.blockchain.BlockChain.{DifficultyFunction, HashFunction}

import scala.annotation.tailrec
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

case class BlockMessage( data: String, id: String = UUID.randomUUID().toString)

object GenesisBlock extends Block(0, BigInt(0), 1497359352, "Genesis", Seq(BlockMessage("Genesis block", "74dd70aa-2ddb-4aa2-8f95-ffc3b5cebad1")), 0,
  BigInt("2b33684ac1ce0a93a54410de84d4114a3882362bcec35a2a9b588630811ae92b", 16))

case class Block(index: Long, previousHash: BigInt, timestamp: Long, foundBy: String, messages: Seq[BlockMessage], nonse: Long, hash: BigInt)

object BlockChain {

  type DifficultyFunction = Block => BigInt
  type HashFunction = Block => BigInt

  val defaultDifficultyFunction = NaiveCoinDifficulty
  val defaultHashFunction = SimpleSHA256Hash

  def apply( difficultyFunction: DifficultyFunction = defaultDifficultyFunction, hashFunction: HashFunction = defaultHashFunction )
    = new BlockChain(Seq(GenesisBlock), difficultyFunction, hashFunction)

}

case class BlockChain private(val blocks: Seq[Block], difficultyFunction: DifficultyFunction, hashFunction: HashFunction) {

  val TWO_HOURS = 2 * 60 * 60

  val logger = Logger("BlockChain")

  def addMessage(data: String ): BlockChain = addBlock(Seq(BlockMessage(data)))

  def addBlock(messages: Seq[BlockMessage]) = new BlockChain(generateNextBlock(messages) +: blocks, difficultyFunction, hashFunction)

  def contains( blockMessage: BlockMessage ) = blocks.find( b => b.messages.contains(blockMessage) ).isDefined

  def containsAll( messages: Seq[BlockMessage] ) = messages.forall( contains(_) )

  def replaceBlocks( newBlocks: Seq[Block] ): Try[BlockChain] = {
    newBlocks match {
      case GenesisBlock :: tail => BlockChain(difficultyFunction, hashFunction).appendBlocks(tail)
      case blocks :+ GenesisBlock => BlockChain(difficultyFunction, hashFunction).appendBlocks(blocks.reverse)
      case _ => Failure(new IllegalArgumentException("New chain does not start or end with the GenesisBlock"))
    }
  }

  def appendBlocks( newBlocks: Seq[Block] ): Try[BlockChain] = {
    newBlocks.foldLeft(Try(this)) { (blockChain, block) =>
      blockChain.flatMap( chain => chain.addBlock(block))
    }
  }

  def addBlock( block: Block ): Try[ BlockChain ] =
    if ( validBlock(block) ) Success( new BlockChain(block +: blocks, difficultyFunction, hashFunction ))
    else Failure( new IllegalArgumentException("Invalid block added"))

  def firstBlock: Block = blocks.last
  def latestBlock: Block = blocks.head

  def generateNextBlock(messages: Seq[BlockMessage], nodeName: String = ""): Block = {
    val timeBefore = new Date().getTime
    val block = generateNextBlock(messages, 0, nodeName)
    logger.debug(s"Found block in ${new Date().getTime - timeBefore} ms after ${block.nonse +1} tries.")
    block
  }

  @tailrec
  private def generateNextBlock( messages: Seq[BlockMessage], nonse: Long, nodeName: String ): Block = {
    attemptBlock(messages, nonse, nodeName) match {
      case Some(block) => block
      case None => generateNextBlock(messages, nonse +1, nodeName)
    }
  }

  private def validBlock(newBlock: Block, previousBlock: Block, messages: Set[BlockMessage]) =
    previousBlock.index + 1 == newBlock.index &&
    previousBlock.hash == newBlock.previousHash &&
    (newBlock.timestamp - (new Date().getTime / 1000)) < TWO_HOURS &&
    previousBlock.timestamp <= newBlock.timestamp &&
    hashFunction(newBlock) == newBlock.hash &&
    newBlock.hash < difficultyFunction(newBlock) &&
    ! newBlock.messages.exists( messages.contains(_))



  def attemptBlock(messages: Seq[BlockMessage], nonse: Long, nodeName: String ): Option[Block] = {
    val previousBlock = latestBlock
    val nextIndex = previousBlock.index + 1
    val nextTimestamp = new Date().getTime() / 1000

    val tempBlock = Block(nextIndex, previousBlock.hash, nextTimestamp, nodeName, messages, nonse, 0)
    val block = tempBlock.copy( hash = hashFunction(tempBlock) )

    Some(block).filter(validBlock)
  }

  def validBlock(newBlock: Block): Boolean = validChain( newBlock +: blocks )

  def validChain( chain: Seq[Block] ): Boolean = validChain(chain, Set.empty)

  @tailrec
  private def validChain( chain: Seq[Block], messages: Set[BlockMessage]): Boolean = chain match {
    case singleBlock :: Nil if singleBlock == GenesisBlock => true
    case head :: beforeHead :: tail if validBlock(head, beforeHead, messages) => validChain(beforeHead :: tail, messages ++ head.messages)
    case _ => false
  }

}



