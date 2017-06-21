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
  case class MineBlock(blockChain: BlockChain, blockMessage: BlockMessage, startNonse: Long = 0, timeStamp: Long = new java.util.Date().getTime )

  case class MineResult(block: Block)

  case object StopMining

  def props: Props = Props[MiningActor]
}

class MiningActor extends Actor {

  val logger = Logger("MiningActor")

  var keepMining = true

  override def receive: Receive = {

    case StopMining => keepMining = false

    case MineBlock(blockChain, blockMessage, startNonse, timeStamp) =>
      if ( ! blockChain.contains(blockMessage) ) {
        if ( startNonse == 0 ) {
          logger.debug(s"Starting mining attempt for blockMessage ${blockMessage}")
        }

        val newBlockOption = startNonse.until(startNonse+100).map { nonse =>
          blockChain.attemptBlock(blockMessage, nonse)
        }.flatten.headOption match {
          case Some(block) =>
            logger.debug(s"Found a block for message ${blockMessage} after ${new java.util.Date().getTime - timeStamp} ms and ${block.nonse} attempts.")
            context.parent ! MineResult(block)
            context.stop(self)

          case None if keepMining => self ! MineBlock(blockChain, blockMessage, startNonse + 100, timeStamp)

          case None =>
            logger.debug(s"Aborting mining for message ${blockMessage}")
            context.stop(self)

        }

      } else logger.debug("The message is already in the chain.")
  }
}
