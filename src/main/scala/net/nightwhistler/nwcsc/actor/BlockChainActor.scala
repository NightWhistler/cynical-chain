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
    case QueryLatest => sender() ! NewBlock(blockChain.latestBlock)
    case QueryAll => sender() ! NewBlockChain(blockChain.blocks)

    case GetBlockChain => sender() ! CurrentBlockChain(blockChain)

    case NewBlock(block) => handleNewBlock(block, sender())
    case NewBlockChain(blocks) => handleBlockChainResponse(blocks)

  }

  def handleNewBlock( block: Block, peer: ActorRef ): Unit = {

    val localLatestBlock = blockChain.latestBlock
    logger.info(s"New block with index ${block.index} received, our latest index is ${localLatestBlock.index}")

    if ( block.index == localLatestBlock.index + 1) {
      blockChain.addBlock(block) match {
        case Success(newChain) =>
          logger.info("We can append the received block to our chain.")
          blockChain = newChain
          peerToPeer ! BroadcastRequest(NewBlock(blockChain.latestBlock))
          peerToPeer ! BlockChainUpdated(blockChain)

        case Failure(_) =>
          logger.info("Could not add the block to our chain, querying for full chain")
          peer ! QueryAll
      }
    } else if ( block.index <= localLatestBlock.index ) {
      logger.debug("Block was not newer than our current latest, ignoring.")
    } else {
      logger.info("Block is more than 1 ahead, we have to query the chain from our peer")
      peer ! QueryAll
    }
  }

  def handleBlockChainResponse( receivedBlocks: Seq[Block] ): Unit = {
    val localLatestBlock = blockChain.latestBlock
    logger.info(s"${receivedBlocks.length} blocks received.")

    receivedBlocks match {
      case Nil => logger.warn("Received an empty block list, discarding")

      case latestReceivedBlock :: _ if latestReceivedBlock.index <= localLatestBlock.index =>
        logger.debug("received blockchain is not longer than current blockchain. Do nothing")

      case _ =>
        logger.info("Received blockchain is longer than the current blockchain")
        blockChain.withBlocks(receivedBlocks) match {
          case Success(newChain) =>
            blockChain = newChain
            peerToPeer ! BroadcastRequest(NewBlock(blockChain.latestBlock))
            peerToPeer ! BlockChainUpdated(blockChain)

          case Failure(s) => logger.error("Rejecting received chain.", s)
        }
    }
  }

}


