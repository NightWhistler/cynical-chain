package net.nightwhistler.nwcsc.actor

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.BlockChainConfig
import net.nightwhistler.nwcsc.actor.Mining.MineResult
import net.nightwhistler.nwcsc.actor.MiningWorker.MineBlock
import net.nightwhistler.nwcsc.blockchain.{BlockChain, BlockMessage}

object MiningWorker {
  case class MineBlock(blockChain: BlockChain, messages: Seq[BlockMessage],
                       startNonse: Long = 0, timeStamp: Long = new java.util.Date().getTime )

  def props( reportBackTo: ActorRef): Props = Props(new MiningWorker(reportBackTo))
}

class MiningWorker(reportBackTo: ActorRef) extends Actor {

  val logger = Logger(classOf[MiningWorker])
  val nodeName = BlockChainConfig.nodeName

  override def receive: Receive = LoggingReceive {

    case MineBlock(blockChain, messages, nonse, timeStamp) =>
      if ( nonse == 0 ) {
        logger.debug(s"Starting mining attempt for ${messages.size} messages with index ${blockChain.latestBlock.index +1}")
      }

      blockChain.attemptBlock(messages, nonse, nodeName) match {
        case Some(block) =>
          logger.debug(s"Found a block with index ${block.index} after ${new java.util.Date().getTime - timeStamp} ms and ${block.nonse +1} attempts.")
          reportBackTo ! MineResult(block)
          context.stop(self)

        case None => self ! MineBlock(blockChain, messages, nonse + 1, timeStamp)
      }
  }

  override def postStop(): Unit = logger.debug("Aborted mining.")
}
