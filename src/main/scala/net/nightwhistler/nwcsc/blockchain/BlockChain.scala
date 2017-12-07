package net.nightwhistler.nwcsc.blockchain

import java.util.{Date, UUID}

import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.blockchain.BlockChain.{DifficultyFunction, HashFunction}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import scala.collection.immutable.Seq

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
    = new BlockChain(GenesisBlock, None, difficultyFunction, hashFunction)

}

case class BlockChain private(head: Block, tail: Option[BlockChain], difficultyFunction: DifficultyFunction,
                              hashFunction: HashFunction) {

  val logger = Logger("BlockChain")

  /** Conceptually:
  def blocks: List[Block] = latestBlock :: prevChain.map( _.blocks ).getOrElse( Nil )
  **/
  def blocks: List[Block] = foldLeft[List[Block]](Nil) {
    case (list, block) => list :+ block
  }

  def blocksReverse: List[Block] = foldLeft[List[Block]](Nil) {
    case (list, block) => block :: list
  }

  def firstBlock: Block = foldLeft[Option[Block]](None) {
    case (_, block) => Some(block)
  }.get

  def contains(blockMessage: BlockMessage) = foldLeft(false) {
    case (b, block) => b || block.messages.contains(blockMessage)
  }

  @tailrec
  final def foldLeft[A]( startValue: A )( op: (A, Block) => A ): A = {
    val currentResult = op(startValue, head)
    tail match {
      case None => currentResult
      case Some(chain) => chain.foldLeft(currentResult)(op)
    }
  }

  def addMessage(data: String, foundBy: String = "", nonse: Long = 0 ): Try[BlockChain]
    = addMessages(Seq(BlockMessage(data)), foundBy, nonse)

  def addMessages(blockMessages: Seq[BlockMessage], foundBy: String, nonse: Long): Try[BlockChain] =
    addBlock( generateNextBlock(blockMessages, foundBy, nonse) )

  def generateNextBlock(blockMessages: Seq[BlockMessage], foundBy: String, nonse: Long): Block = {
    val previousBlock = head
    val nextIndex = previousBlock.index + 1
    val nextTimestamp = currentTime

    val tempBlock = Block(nextIndex, previousBlock.hash, nextTimestamp, foundBy, blockMessages, nonse, 0)
    tempBlock.copy( hash = hashFunction(tempBlock) )
  }

  private def currentTime = System.currentTimeMillis() / 1000

  def withBlocks(newBlocks: Seq[Block] ): Try[BlockChain] = newBlocks match {
    case GenesisBlock :: tail => BlockChain(difficultyFunction, hashFunction).appendBlocks(tail)
    case blocks :+ GenesisBlock => BlockChain(difficultyFunction, hashFunction).appendBlocks(blocks.reverse)
    case _ => Failure(new IllegalArgumentException("New chain does not start or end with the GenesisBlock"))
  }

  def appendBlocks( newBlocks: Seq[Block] ): Try[BlockChain] = newBlocks.foldLeft(Try(this)) {
    (blockChain, block) => blockChain.flatMap(chain => chain.addBlock(block))
  }

  def addBlock( block: Block ): Try[ BlockChain ] =
    if (validBlock(block) ) Success( new BlockChain(block, Some(this), difficultyFunction, hashFunction ))
    else Failure(new IllegalArgumentException("Invalid block added"))

  def validBlock(newBlock: Block): Boolean = validBlock(newBlock, head)

  private val fiveMinutes = 300

  private def validBlock(newBlock: Block, previousBlock: Block) =
    previousBlock.index + 1 == newBlock.index &&
    previousBlock.hash == newBlock.previousHash &&
    previousBlock.timestamp <= newBlock.timestamp &&
    (newBlock.timestamp - currentTime) < fiveMinutes &&
    hashFunction(newBlock) == newBlock.hash &&
    newBlock.hash < difficultyFunction(newBlock) &&
    ! newBlock.messages.exists( contains(_) )

  def valid: Boolean = validChain

  @tailrec
  private def validChain: Boolean = tail match {
    case None => head == GenesisBlock
    case Some(chain) => validBlock(head, chain.head) && chain.validChain
  }

  override def toString: String = {
    val stringBuilder = new StringBuilder("BlockChain(")
    buildToString(stringBuilder)
    stringBuilder.append(")")
    stringBuilder.toString
  }

  @tailrec
  private def buildToString(builder: StringBuilder): Unit = {
    builder.append(s", $head")
    tail match {
      case None => //End
      case Some(chain) => chain.buildToString(builder)
    }
  }
}



