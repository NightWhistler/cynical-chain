package net.nightwhistler.nwcsc.blockchain

import java.lang.Integer.parseInt
import java.math.BigInteger
import java.util.Date

import com.roundeights.hasher.Implicits._
import com.typesafe.scalalogging.Logger

import scala.annotation.tailrec
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Created by alex on 13-6-17.
  */
case class Block(index: Long, previousHash: String, timestamp: Long, data: String, nonse: Long, hash: String) {
  def difficulty = BigInt(hash, 16)
}

object GenesisBlock extends Block(0, "0", 1497359352, "Genesis block", 0, "ccce7d8349cf9f5d9a9c8f9293756f584d02dfdb953361c5ee36809aa0f560b4")

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

  def calculateHashForBlock( block: Block ) = calculateHash(block.index, block.previousHash, block.timestamp, block.data, block.nonse)

  def calculateHash(index: Long, previousHash: String, timestamp: Long, data: String, nonse: Long) =
    s"$index:$previousHash:$timestamp:$data:$nonse".sha256.hex


}

class BlockChain private( val blocks: Seq[Block] ) {

  import BlockChain._

  val logger = Logger("BlockChain")

  def addBlock( data: String ): BlockChain = new BlockChain(generateNextBlock(data) +: blocks)

  def addBlock( block: Block ): Try[ BlockChain ] =
    if ( validBlock(block) ) Success( new BlockChain(block +: blocks ))
    else Failure( new IllegalArgumentException("Invalid block added"))

  def firstBlock: Block = blocks(blocks.length -1)
  def latestBlock: Block = blocks.head

  def generateNextBlock( blockData: String ): Block = {
    val timeBefore = new Date().getTime
    val block = generateNextBlock(blockData, 0)
    logger.debug(s"Found block in ${new Date().getTime - timeBefore} ms after ${block.nonse +1} tries.")
    block
  }

  @tailrec
  private def generateNextBlock( blockData: String, nonse: Long ): Block = {
     val previousBlock = latestBlock
    val nextIndex = previousBlock.index + 1
    val nextTimestamp = new Date().getTime() / 1000
    val nextHash = calculateHash(nextIndex, previousBlock.hash, nextTimestamp, blockData, nonse)

    val block = Block(nextIndex, previousBlock.hash, nextTimestamp, blockData, nonse, nextHash )
    if (! validBlock(block) ) {
      generateNextBlock(blockData, nonse +1)
    } else block
  }

  def validBlock( newBlock: Block ): Boolean = BlockChain.validBlock(newBlock, latestBlock)

  override def toString: String = s"$blocks"

}



