package net.nightwhistler.nwcsc.actor

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, Terminated}
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.actor.BlockChainCommunication.ResponseBlock
import net.nightwhistler.nwcsc.actor.Mining.{BlockChainChanged, MineBlock}
import net.nightwhistler.nwcsc.actor.MiningWorker.{MineResult, StopMining}
import net.nightwhistler.nwcsc.actor.PeerToPeer.BroadcastRequest
import net.nightwhistler.nwcsc.blockchain.{BlockChain, BlockMessage}

/**
  * Created by alex on 20-6-17.
  */
object Mining {
  case class MineBlock(messages: Seq[BlockMessage] )

  case class BlockChainChanged( blockchain: BlockChain )

  def props( blockChain: BlockChain, blockChainCommunication: ActorRef, peerToPeer: ActorRef ) =
    Props( new Mining(blockChain, blockChainCommunication, peerToPeer))
}

class Mining( var blockChain: BlockChain, blockChainCommunication: ActorRef, peerToPeer: ActorRef ) extends Actor {

  var miners: Set[ActorRef] = Set.empty
  var messages: Set[BlockMessage] = Set.empty

  def createWorker( factory: ActorRefFactory ): ActorRef = factory.actorOf(MiningWorker.props(self))

  val logger = Logger(classOf[Mining])

  override def receive = {

    case BlockChainChanged(newBlockChain) =>
      logger.debug("The blockchain has changed, stopping all miners.")
      miners.foreach( _ ! StopMining )

      blockChain = newBlockChain

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
        peerToPeer! BroadcastRequest(MineBlock(messages.toSeq))

        //Spin up a new actor to do the mining
        val miningActor = createWorker(context)
        context.watch(miningActor)
        miners += miningActor

        miningActor ! MiningWorker.MineBlock(blockChain, messages.toSeq)
      } else logger.debug("Request contained no new messages, so not doing anything.")

    case MineResult(block) =>
      if ( blockChain.validBlock(block) ) {
        logger.debug(s"Received a valid block from the miner for index ${block.index}, adding it to the chain.")
        //We don't remove the messages yet, not until they have been confirmed to be in the blockchain.
        //The main blockchain may still reject the block!
        blockChainCommunication ! ResponseBlock(block)
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
