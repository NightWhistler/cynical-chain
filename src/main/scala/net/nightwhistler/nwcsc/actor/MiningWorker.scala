package net.nightwhistler.nwcsc.actor

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.actor.MiningWorker.{MineBlock, MineResult, StopMining}
import net.nightwhistler.nwcsc.blockchain.{Block, BlockChain, BlockMessage}

/**
  * Created by alex on 19-6-17.
  */
object MiningWorker {
  case class MineBlock(blockChain: BlockChain, messages: Seq[BlockMessage], startNonse: Long = 0, timeStamp: Long = new java.util.Date().getTime )

  case class MineResult(block: Block)

  case object StopMining

  def props( reportBackTo: ActorRef ): Props = Props(classOf[MiningWorker], reportBackTo)
}

class MiningWorker(reportBackTo: ActorRef) extends Actor {

  val logger = Logger("MiningActor")

  var keepMining = true

  override def receive: Receive = {

    case StopMining => keepMining = false

    case MineBlock(blockChain, messages, startNonse, timeStamp) =>
      if ( startNonse == 0 ) {
        logger.debug(s"Starting mining attempt for ${messages.size} messages with index ${blockChain.latestBlock.index +1}")
      }

      startNonse.until(startNonse+100).flatMap { nonse =>
        blockChain.attemptBlock(messages, nonse)
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
