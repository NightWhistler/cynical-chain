package net.nightwhistler.nwcsc.actor

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.event.LoggingReceive
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.actor.PeerToPeer._
import akka.pattern.pipe
import net.nightwhistler.nwcsc.actor.BlockChainActor._
import net.nightwhistler.nwcsc.actor.MiningActor.{BlockChainChanged, MineBlock, MineResult}
import net.nightwhistler.nwcsc.blockchain.{Block, BlockChain}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import akka.pattern.ask
import net.nightwhistler.nwcsc.BlockChainConfig

object PeerToPeer {

  case class AddPeer( address: String )

  case class ResolvedPeer( actorRef: ActorRef )

  case class Peers( peers: Seq[String] )

  case object GetPeers

  case class HandShake(nodeName: String)

  case class BroadcastRequest(message: Any)

  case class BlockChainUpdated( blockChain: BlockChain )

  def props(implicit ec: ExecutionContext) = Props(new PeerToPeer)
}

class PeerToPeer(implicit ec: ExecutionContext) extends Actor {

  implicit val timeout = Timeout(Duration(5, TimeUnit.SECONDS))
  implicit val executionContext = context.system.dispatcher

  val blockChainActor = context.actorOf(BlockChainActor.props(self))
  val miningActor = context.actorOf(MiningActor.props(self))

  val logger: Logger = Logger(classOf[PeerToPeer])
  var peers: Set[ActorRef] = Set.empty

  val myNodeName = BlockChainConfig.nodeName

  def broadcast( message: Any ) = peers.foreach( _ ! message )

  override def receive = LoggingReceive {

    //P2P messages
    case AddPeer(peerAddress) =>
      logger.debug(s"Got request to add peer ${peerAddress}")
      context.actorSelection(peerAddress).resolveOne().map( ResolvedPeer(_) ).pipeTo(self)

    case ResolvedPeer(newPeerRef: ActorRef) =>

      if ( ! peers.contains(newPeerRef) ) {
        context.watch(newPeerRef)

        //Introduce ourselves
        newPeerRef ! HandShake(myNodeName)

        //Ask for its friends
        newPeerRef ! GetPeers

        //Tell our existing peers
        broadcast(AddPeer(newPeerRef.path.toSerializationFormat))

        //Add to the current list of peers
        peers += newPeerRef

        //And ask it for it's view on the world
        newPeerRef ! QueryLatest

        logger.debug(s"Peer list grew to size ${peers.size}")
      } else logger.debug("We already know this peer, discarding")


    case HandShake(fromNode) =>
      logger.debug(s"Received a handshake from $fromNode at ${sender().path.toStringWithoutAddress}")
      peers += sender()

    case BroadcastRequest(message) => broadcast(message)

    case Peers(peers) => peers.foreach( self ! AddPeer(_))

    case GetPeers => sender() ! Peers(peers.toSeq.map(_.path.toSerializationFormat))

    case Terminated(actorRef) =>
      logger.debug(s"Peer ${actorRef} has terminated. Removing it from the list.")
      peers -= actorRef

    //Mining messages
    case AddMessages(messages) =>
      (blockChainActor ? GetBlockChain).mapTo[CurrentBlockChain]
        .map( chain => MineBlock(chain.blockChain, messages) ) pipeTo miningActor

    case MineResult(block) =>
      logger.debug(s"Received a valid block from the miner for index ${block.index}, adding it to the chain.")
      //We don't remove the messages yet, not until they have been confirmed to be in the blockchain.
      //The main blockchain may still reject the block!
      blockChainActor ! NewBlock(block)

    case BlockChainUpdated(blockChain) => miningActor ! BlockChainChanged(blockChain)

    //Blockchain messages
    case blockchainMessage @ (GetBlockChain | QueryAll | QueryLatest | NewBlock(_) | NewBlockChain(_) ) =>
      blockChainActor forward blockchainMessage

  }


}
