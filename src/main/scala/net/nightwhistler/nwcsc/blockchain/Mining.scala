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
  case class MineBlock(messages: Seq[BlockMessage] )

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

      self ! MineBlock( oldMessages.filterNot( blockChain.contains(_)).toSeq )

    case MineBlock(requestMessages) =>

      logger.debug(s"Got mining request: ${requestMessages}")

      if ( ! requestMessages.forall( messages.contains(_) )) {
        messages ++= requestMessages

        //Tell all peers to start mining
        peers.foreach( p => p ! MineBlock(messages.toSeq))

        //Spin up a new actor to do the mining
        val miningActor = context.actorOf(MiningActor.props)
        context.watch(miningActor)
        miners += miningActor

        miningActor ! MiningActor.MineBlock(blockChain, messages.toSeq)
      }

    case MineResult(block) =>
      val minedMessages = block.messages
      messages = messages -- minedMessages.toSet

      if ( blockChain.validBlock(block) ) {
        logger.debug(s"Received a valid block from the miner for message ${minedMessages}, adding it to the chain.")
        handleBlockChainResponse(Seq(block))
      } else if ( ! blockChain.containsAll(minedMessages)) {
        logger.debug(s"Received an outdated block from the miner for messages ${minedMessages}, but not all messages are in the blockchain yet. Queueing the remainder again.")
        self ! MineBlock(minedMessages)
      } else {
        logger.debug(s"Miner finished for message ${minedMessages}, but the block is already in the chain.")
      }

    case Terminated(deadActor) =>
      miners -= deadActor
      logger.debug(s"Still running ${miners.size} miners for ${messages.size} blocks")
  }


}
