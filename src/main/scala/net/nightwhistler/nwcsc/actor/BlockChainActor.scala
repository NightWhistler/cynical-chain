package net.nightwhistler.nwcsc.actor

import akka.actor.{Actor, ActorRef, ActorSelection, Props, Terminated}
import net.nightwhistler.nwcsc.actor.BlockChainActor._
import net.nightwhistler.nwcsc.blockchain.{BlockChain, BlockMessage}
import net.nightwhistler.nwcsc.p2p.PeerToPeerCommunication
import net.nightwhistler.nwcsc.p2p.PeerToPeerCommunication.PeerMessage

/**
  * Actor-based implementation of PeerToPeerCommunication
  */
object BlockChainActor {
  case class MineBlock(blockMessage: BlockMessage )

  case class AddPeer( address: String )

  case class Peers( peers: Seq[String] )

  case object GetPeers

  case object HandShake

  def props: Props = Props[BlockChainActor]
}

class BlockChainActor extends Actor with PeerToPeerCommunication {

  override var blockChain: BlockChain = BlockChain()

  var peers: Set[ActorSelection] = Set.empty

  var miners: Map[BlockMessage, ActorRef] = Map.empty

  override def receive: Receive = {
    case AddPeer(peerAddress) =>
      val selection = context.actorSelection(peerAddress)
      logger.debug(s"Got request to add peer ${peerAddress}")

      if ( ! peers.contains(selection) ) {
        //Introduce ourselves
        selection ! HandShake

        //Ask for its friends
        selection ! GetPeers

        //Tell our existing peers
        peers.foreach( peer => peer ! AddPeer(peerAddress))

        //Add to the current list of peers
        peers += context.actorSelection(peerAddress)
      } else logger.debug("We already know this peer, discarding")

    case Peers(peers) => peers.foreach( self ! AddPeer(_))

    case HandShake =>
      logger.debug(s"Received a handshake from ${sender().path.toStringWithoutAddress}")
      peers += context.actorSelection(sender().path)

    case GetPeers => sender() ! Peers(peers.toSeq.map(_.toSerializationFormat))

    case m@MineBlock(blockMessage) =>
      if ( ! miners.contains(blockMessage) ) {
        logger.debug(s"Got mining request: ${blockMessage}")
        //Tell all peers to start mining
        peers.foreach( p => p ! m)

        //Spin up a new actor to do the mining
        val miningActor = context.actorOf(MiningActor.props)
        context.watch(miningActor)
        miners += blockMessage -> miningActor
        miningActor ! MiningActor.MineBlock(blockChain, blockMessage)
      }

    case Terminated =>
      val deadActor = sender()
      miners.find{ case (_, ref) => ref == deadActor }
        .map(_._1).foreach( blockMessage => miners -= blockMessage )

    case p@PeerMessage(_,_) =>
      val replyTo = sender()
      handleMessage(p) { peerReply => replyTo ! peerReply }
  }

  override def broadcast(peerMessage: PeerMessage): Unit = {
    peers.foreach( peer => peer ! peerMessage)
  }
}
