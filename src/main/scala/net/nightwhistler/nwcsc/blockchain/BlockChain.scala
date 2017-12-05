package net.nightwhistler.nwcsc.blockchain

import java.util.{Date, UUID}

import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.blockchain.BlockChain.{DifficultyFunction, HashFunction}

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

case class BlockMessage( data: String, id: String = UUID.randomUUID().toString)

object GenesisBlock extends Block(0, BigInt(0), 1497359352, "Genesis",
  Seq(BlockMessage("Genesis block", "74dd70aa-2ddb-4aa2-8f95-ffc3b5cebad1")), 0,
  BigInt("8bf633d2c8025c2fedea4ecdf68378584d6c0e545736fa95a0f2fa094182912a", 16))

case class Block(index: Long, previousHash: BigInt, timestamp: Long, foundBy: String,
                 messages: Seq[BlockMessage], nonse: Long, hash: BigInt)

object BlockChain {

  type DifficultyFunction = Block => BigInt
  type HashFunction = Block => BigInt

  val defaultDifficultyFunction = NaiveCoinDifficulty
  val defaultHashFunction = SimpleSHA256Hash

  def apply( difficultyFunction: DifficultyFunction = defaultDifficultyFunction, hashFunction: HashFunction = defaultHashFunction )
    = new BlockChain(Seq(GenesisBlock), difficultyFunction, hashFunction)

}

case class BlockChain private(val blocks: Seq[Block], difficultyFunction: DifficultyFunction, hashFunction: HashFunction) {

  val logger = Logger("BlockChain")

  lazy val messages: Set[BlockMessage] = blocks.foldLeft(Set[BlockMessage]()) {
    case (set, block) => set ++ block.messages
  }

  def addMessage(data: String, foundBy: String = "", nonse: Long = 0 ): Try[BlockChain]
    = addMessages(Seq(BlockMessage(data)), foundBy, nonse)

  def addMessages(blockMessages: Seq[BlockMessage], foundBy: String, nonse: Long): Try[BlockChain] =
    addBlock( generateNextBlock(blockMessages, foundBy, nonse) )

  def contains( blockMessage: BlockMessage ) = messages.contains( blockMessage )

  def withBlocks(newBlocks: Seq[Block] ): Try[BlockChain] = {
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

  def generateNextBlock(blockMessages: Seq[BlockMessage], foundBy: String, nonse: Long): Block = {
    val previousBlock = latestBlock
    val nextIndex = previousBlock.index + 1
    val nextTimestamp = new Date().getTime() / 1000

    val tempBlock = Block(nextIndex, previousBlock.hash, nextTimestamp, foundBy, blockMessages, nonse, 0)
    tempBlock.copy( hash = hashFunction(tempBlock) )
  }

  def validBlock(newBlock: Block): Boolean = validBlock(newBlock, latestBlock)

  private def validBlock(newBlock: Block, previousBlock: Block) =
    previousBlock.index + 1 == newBlock.index &&
    previousBlock.hash == newBlock.previousHash &&
    previousBlock.timestamp <= newBlock.timestamp &&
    hashFunction(newBlock) == newBlock.hash &&
    newBlock.hash < difficultyFunction(newBlock) &&
    ! newBlock.messages.exists( messages.contains(_))

}



