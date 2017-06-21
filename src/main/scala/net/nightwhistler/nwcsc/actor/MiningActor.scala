package net.nightwhistler.nwcsc.actor

import java.util.Date

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.actor.MiningActor.{MineBlock, MineResult, StopMining}
import net.nightwhistler.nwcsc.blockchain.{Block, BlockChain, BlockMessage}

/**
  * Created by alex on 19-6-17.
  */
object MiningActor {
  case class MineBlock(blockChain: BlockChain, messages: Seq[BlockMessage], startNonse: Long = 0, timeStamp: Long = new java.util.Date().getTime )

  case class MineResult(block: Block)

  case object StopMining

  def props: Props = Props[MiningActor]
}

class MiningActor extends Actor {

  val logger = Logger("MiningActor")

  var keepMining = true

  override def receive: Receive = {

    case StopMining => keepMining = false

    case MineBlock(blockChain, messages, startNonse, timeStamp) =>
      if ( startNonse == 0 ) {
        logger.debug(s"Starting mining attempt for ${messages.size} messages with index ${blockChain.latestBlock.index +1}")
      }

      val newBlockOption = startNonse.until(startNonse+100).map { nonse =>
        blockChain.attemptBlock(messages, nonse)
      }.flatten.headOption match {
        case Some(block) =>
          logger.debug(s"Found a block with index ${block.index} after ${new java.util.Date().getTime - timeStamp} ms and ${block.nonse} attempts.")
          context.parent ! MineResult(block)
          context.stop(self)

        case None if keepMining => self ! MineBlock(blockChain, messages, startNonse + 100, timeStamp)

        case None =>
          logger.debug(s"Aborting mining for messages block ${blockChain.latestBlock.index +1}")
          context.stop(self)

      }
  }
}
