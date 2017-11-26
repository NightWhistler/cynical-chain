package net.nightwhistler.nwcsc.actor

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.BlockChainConfig
import net.nightwhistler.nwcsc.actor.Mining.MineResult
import net.nightwhistler.nwcsc.actor.MiningWorker.{MineBlock, StopMining}
import net.nightwhistler.nwcsc.blockchain.{BlockChain, BlockMessage}

object MiningWorker {
  case class MineBlock(blockChain: BlockChain, messages: Seq[BlockMessage],
                       startNonse: Long = 0, timeStamp: Long = new java.util.Date().getTime )

  case object StopMining

  def props( reportBackTo: ActorRef): Props = Props(new MiningWorker(reportBackTo))
}

/*
Better strategy:

Send a message per none, and use become() when we get a request to stop.
 */
class MiningWorker(reportBackTo: ActorRef) extends Actor {

  val logger = Logger(classOf[MiningWorker])
  val nodeName = BlockChainConfig.nodeName

  var keepMining = true

  override def receive: Receive = LoggingReceive {

    case StopMining => keepMining = false

    case MineBlock(blockChain, messages, startNonse, timeStamp) =>
      if ( startNonse == 0 ) {
        logger.debug(s"Starting mining attempt for ${messages.size} messages with index ${blockChain.latestBlock.index +1}")
      }

      startNonse.until(startNonse+100).flatMap { nonse =>
        blockChain.attemptBlock(messages, nonse, nodeName)
      }.headOption match {
        case Some(block) =>
          logger.debug(s"Found a block with index ${block.index} after ${new java.util.Date().getTime - timeStamp} ms and ${block.nonse +1} attempts.")
          reportBackTo ! MineResult(block)
          context.stop(self)

        case None if keepMining => self ! MineBlock(blockChain, messages, startNonse + 100, timeStamp)

        case None =>
          logger.debug(s"Aborting mining for messages block ${blockChain.latestBlock.index +1}")
          context.stop(self)

      }
  }
}
