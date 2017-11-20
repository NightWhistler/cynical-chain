package net.nightwhistler.nwcsc.blockchain

import akka.actor.Actor
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.actor.CompositeActor
import net.nightwhistler.nwcsc.p2p.PeerToPeer

import scala.util.{Failure, Success}

/**
  * Created by alex on 14-6-17.
  */

object BlockChainCommunication {

  case object QueryLatest
  case object QueryAll

  case class ResponseBlocks(blocks: Seq[Block])
  case class ResponseBlock(block: Block)

}

trait BlockChainCommunication {
  this: PeerToPeer with CompositeActor =>

  import BlockChainCommunication._

  val logger = Logger("PeerToPeerCommunication")

  var blockChain: BlockChain

  receiver {
    case QueryLatest => sender() ! responseLatest
    case QueryAll => sender() ! responseBlockChain

    case ResponseBlock(block) => handleNewBlock(block)
    case ResponseBlocks(blocks) => handleBlockChainResponse(blocks)
  }

  def handleNewBlock( block: Block ): Unit = {

    val localLatestBlock = blockChain.latestBlock

    if (block.previousHash == localLatestBlock.hash) {
      logger.info("We can append the received block to our chain.")
      blockChain.addBlock(block) match {
        case Success(newChain) =>
          blockChain = newChain
          broadcast(responseLatest)
        case Failure(e) => logger.error("Refusing to add new block", e)
      }
    } else {
      logger.info("We have to query the chain from our peer")
      broadcast(QueryAll)
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
            broadcast(responseBlockChain)
          case Failure(s) => logger.error("Rejecting received chain.", s)
        }
    }
  }

  def responseLatest = ResponseBlock(blockChain.latestBlock)

  def responseBlockChain = ResponseBlocks(blockChain.blocks)

}


