package net.nightwhistler.nwcsc.actor

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.actor.PeerToPeer.{BlockChainUpdated, BroadcastRequest}
import net.nightwhistler.nwcsc.blockchain.{Block, BlockChain, BlockMessage}

import scala.collection.immutable.Seq

import scala.util.{Failure, Success}

object BlockChainActor {

  case object QueryLatest
  case class NewBlock(block: Block)

  case object QueryAll
  case class NewBlockChain(blocks: Seq[Block])

  case object GetBlockChain
  case class CurrentBlockChain( blockChain: BlockChain )

  case class AddMessages(messages: Seq[BlockMessage] )

  def props( peerToPeer: ActorRef ) =
    Props(new BlockChainActor(BlockChain(), peerToPeer))

  def props( blockChain: BlockChain, peerToPeer: ActorRef ) =
    Props(new BlockChainActor(blockChain, peerToPeer))
}

class BlockChainActor( var blockChain: BlockChain, peerToPeer: ActorRef) extends Actor {

  import BlockChainActor._

  val logger = Logger(classOf[BlockChainActor])

  override def receive = LoggingReceive {
    case QueryLatest => sender() ! NewBlock(blockChain.head)
    case QueryAll => sender() ! NewBlockChain(blockChain.blocks)
    case GetBlockChain => sender() ! CurrentBlockChain(blockChain)

    case NewBlock(block) => handleNewBlock(block)
    case NewBlockChain(blocks) => handleBlockChainResponse(blocks)
  }

  def handleNewBlock( block: Block ): Unit = {

    val localLatestBlock = blockChain.head

    if (block.previousHash == localLatestBlock.hash) {
      logger.info("We can append the received block to our chain.")
      blockChain.addBlock(block) match {
        case Success(newChain) =>
          blockChain = newChain
          peerToPeer ! BroadcastRequest(NewBlock(blockChain.head))
          peerToPeer ! BlockChainUpdated(blockChain)

        case Failure(e) => logger.error("Refusing to add new block", e)
      }
    } else {
      logger.info("We have to query the chain from our peer")
      peerToPeer ! BroadcastRequest(QueryAll)
    }
  }

  def handleBlockChainResponse( receivedBlocks: Seq[Block] ): Unit = {
    val localLatestBlock = blockChain.head
    logger.info(s"${receivedBlocks.length} blocks received.")

    receivedBlocks match {
      case Nil => logger.warn("Received an empty block list, discarding")

      case latestReceivedBlock :: _ if latestReceivedBlock.index <= localLatestBlock.index =>
        logger.debug("received blockchain is not longer than received blockchain. Do nothing")

      case _ =>
        logger.info("Received blockchain is longer than the current blockchain")
        blockChain.withBlocks(receivedBlocks) match {
          case Success(newChain) =>
            blockChain = newChain
            //We never broadcast a whole chain, just the latest block.
            peerToPeer ! BroadcastRequest(NewBlock(blockChain.head))
            peerToPeer ! BlockChainUpdated(blockChain)

          case Failure(s) => logger.error("Rejecting received chain.", s)
        }
    }
  }

}


