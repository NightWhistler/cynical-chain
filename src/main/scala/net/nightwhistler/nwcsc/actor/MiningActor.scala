package net.nightwhistler.nwcsc.actor

import akka.actor.{Actor, PoisonPill, Props}
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.actor.MiningActor.{MineBlock, MineResult}
import net.nightwhistler.nwcsc.blockchain.BlockChainCommunication.ResponseBlock
import net.nightwhistler.nwcsc.blockchain.{Block, BlockChain, BlockMessage}

/**
  * Created by alex on 19-6-17.
  */
object MiningActor {
  case class MineBlock(blockChain: BlockChain, blockMessage: BlockMessage)

  case class MineResult(blockMessage: BlockMessage, block: Block)

  def props: Props = Props[MiningActor]
}

class MiningActor extends Actor {

  val logger = Logger("MiningActor")

  override def receive: Receive = {

    case MineBlock(blockChain, blockMessage) =>
      if ( ! blockChain.contains(blockMessage) ) {
        logger.debug(s"Starting mining attempt for blockMessage ${blockMessage}")
        val newChain = blockChain.addBlock(blockMessage)
        sender() ! MineResult(blockMessage, newChain.latestBlock)
      } else logger.debug("The message is already in the chain.")

      logger.debug("Nothing more to do, shutting down.")
      context.stop(self)
  }
}
