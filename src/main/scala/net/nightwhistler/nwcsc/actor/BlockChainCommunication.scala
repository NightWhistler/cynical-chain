package net.nightwhistler.nwcsc.actor

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.actor.Mining.{BlockChainChanged, MineBlock}
import net.nightwhistler.nwcsc.actor.PeerToPeer.BroadcastRequest
import net.nightwhistler.nwcsc.blockchain.{Block, BlockChain}

import scala.util.{Failure, Success}

/**
  * Created by alex on 14-6-17.
  */

object BlockChainCommunication {

  case object QueryLatest
  case object QueryAll

  case class ResponseBlocks(blocks: Seq[Block])
  case class ResponseBlock(block: Block)

  def props( blockChain: BlockChain, peerToPeer: ActorRef ) =
    Props(new BlockChainCommunication(blockChain, peerToPeer))

}

class BlockChainCommunication( var blockChain: BlockChain, peerToPeer: ActorRef) extends Actor {

  import BlockChainCommunication._

  val miningActor = context.actorOf(Mining.props(blockChain, self, peerToPeer ))

  val logger = Logger("PeerToPeerCommunication")

  override def receive = {
    case QueryLatest => sender() ! responseLatest
    case QueryAll => sender() ! responseBlockChain

    case ResponseBlock(block) => handleNewBlock(block)
    case ResponseBlocks(blocks) => handleBlockChainResponse(blocks)

    case mineRequest: MineBlock => miningActor ! mineRequest
  }

  def handleNewBlock( block: Block ): Unit = {

    val localLatestBlock = blockChain.latestBlock

    if (block.previousHash == localLatestBlock.hash) {
      logger.info("We can append the received block to our chain.")
      blockChain.addBlock(block) match {
        case Success(newChain) =>
          blockChain = newChain
          peerToPeer ! BroadcastRequest(responseLatest)
          miningActor ! BlockChainChanged(blockChain)
        case Failure(e) => logger.error("Refusing to add new block", e)
      }
    } else {
      logger.info("We have to query the chain from our peer")
      peerToPeer ! BroadcastRequest(QueryAll)
    }
  }

  def handleBlockChainResponse( receivedBlocks: Seq[Block] ): Unit = {
    val localLatestBlock = blockChain.latestBlock
    logger.info(s"${receivedBlocks.length} blocks received.")

    receivedBlocks match {
      case Nil => logger.warn("Received an empty block list, discarding")

      case latestReceivedBlock :: _ if latestReceivedBlock.index <= localLatestBlock.index =>
        logger.debug("received blockchain is not longer than received blockchain. Do nothing")

      case _ =>
        logger.info("Received blockchain is longer than the current blockchain")
        blockChain.replaceBlocks(receivedBlocks) match {
          case Success(newChain) =>
            blockChain = newChain
            peerToPeer ! BroadcastRequest(responseBlockChain)
            miningActor ! BlockChainChanged(blockChain)
          case Failure(s) => logger.error("Rejecting received chain.", s)
        }
    }
  }

  def responseLatest = ResponseBlock(blockChain.latestBlock)

  def responseBlockChain = ResponseBlocks(blockChain.blocks)

}


