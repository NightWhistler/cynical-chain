package net.nightwhistler.nwcsc.blockchain

import akka.actor.{ActorRef, Terminated}
import net.nightwhistler.nwcsc.actor.MiningActor.{MineResult, StopMining}
import net.nightwhistler.nwcsc.actor.{CompositeActor, MiningActor}
import net.nightwhistler.nwcsc.blockchain.Mining.{BlockChainInvalidated, MineBlock}
import net.nightwhistler.nwcsc.p2p.PeerToPeer

/**
  * Created by alex on 20-6-17.
  */
object Mining {
  case class MineBlock( blockMessage: BlockMessage )

  case object BlockChainInvalidated
}

trait Mining {
  this: BlockChainCommunication with PeerToPeer with CompositeActor =>

  var miners: Set[ActorRef] = Set.empty
  var messages: Set[BlockMessage] = Set.empty

  receiver {
    case BlockChainInvalidated =>
      logger.debug("The blockchain has changed, stopping all miners.")
      miners.foreach( _ ! StopMining )
      val oldMessages = messages
      messages = Set.empty
      oldMessages.foreach { msg =>
        if ( ! blockChain.contains(msg) ) {
          self ! MineBlock(msg)
        } else logger.debug(s"Message ${msg} is already in the new chain, no longer need to mine it.")
      }

    case m@MineBlock(blockMessage) =>
      if ( ! blockChain.contains(blockMessage) && ! messages.contains(blockMessage) ) {
        logger.debug(s"Got mining request: ${blockMessage}")
        messages += blockMessage

        //Tell all peers to start mining
        peers.foreach( p => p ! m)

        //Spin up a new actor to do the mining
        val miningActor = context.actorOf(MiningActor.props)
        context.watch(miningActor)
        miners += miningActor

        miningActor ! MiningActor.MineBlock(blockChain, blockMessage)
      }

    case MineResult(block) =>
      val blockMessage = block.message
      messages -= blockMessage

      if ( blockChain.validBlock(block) ) {
        logger.debug(s"Received a valid block from the miner for message ${blockMessage}, adding it to the chain.")
        handleBlockChainResponse(Seq(block))
      } else if ( ! blockChain.contains(blockMessage)) {
        logger.debug(s"Received an outdated block from the miner for message ${blockMessage}, but the message isn't in the blockchain yet. Queueing it again.")
        self ! MineBlock(blockMessage)
      } else {
        logger.debug(s"Miner finished for message ${blockMessage}, but the block is already in the chain.")
      }

    case Terminated(deadActor) =>
      miners -= deadActor
      logger.debug(s"Still running ${miners.size} miners for ${messages.size} blocks")
  }


}
