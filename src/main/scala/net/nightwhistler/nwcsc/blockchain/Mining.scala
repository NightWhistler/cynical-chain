package net.nightwhistler.nwcsc.blockchain

import akka.actor.{ActorRef, ActorRefFactory, Props, Terminated}
import net.nightwhistler.nwcsc.actor.MiningWorker.{MineResult, StopMining}
import net.nightwhistler.nwcsc.actor.{CompositeActor, MiningWorker}
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

  def createWorker( factory: ActorRefFactory ): ActorRef = factory.actorOf(MiningWorker.props(self))

  receiver {
    case BlockChainInvalidated =>
      logger.debug("The blockchain has changed, stopping all miners.")
      miners.foreach( _ ! StopMining )

      messages = messages.filterNot( blockChain.contains(_))

      if ( ! messages.isEmpty ) {
        self ! MineBlock(messages.toSeq)
      }

    case MineBlock(requestMessages) =>

      logger.debug(s"Got mining request for ${requestMessages.size} messages with current blockchain index at ${blockChain.latestBlock.index}")
      val filtered = requestMessages.filterNot( blockChain.contains(_))

      //We only need to start mining if any new messages are in the message.
      if ( ! (filtered.toSet -- messages).isEmpty ) {
        messages ++= filtered

        //Tell all peers to start mining
        peers.foreach( p => p ! MineBlock(messages.toSeq))

        //Spin up a new actor to do the mining
        val miningActor = createWorker(context)
        context.watch(miningActor)
        miners += miningActor

        miningActor ! MiningWorker.MineBlock(blockChain, messages.toSeq)
      } else logger.debug("Request contained no new messages, so not doing anything.")

    case MineResult(block) =>
      if ( blockChain.validBlock(block) ) {
        logger.debug(s"Received a valid block from the miner for index ${block.index}, adding it to the chain.")
        messages = messages -- block.messages
        handleBlockChainResponse(Seq(block))
      } else logger.debug(s"Received an outdated block from the miner for index ${block.index}.")

    case Terminated(deadActor) =>
      miners -= deadActor
      logger.debug(s"Still running ${miners.size} miners for ${messages.size} messages")

      if ( miners.size == 0  && ! messages.isEmpty ) {
        val request = MineBlock(messages.toSeq)
        messages = Set.empty
        self ! request
      }
  }


}
