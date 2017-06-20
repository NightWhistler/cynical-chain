package net.nightwhistler.nwcsc.blockchain

import akka.actor.{ActorRef, Terminated}
import net.nightwhistler.nwcsc.actor.{CompositeActor, MiningActor}
import net.nightwhistler.nwcsc.blockchain.Mining.MineBlock
import net.nightwhistler.nwcsc.p2p.PeerToPeer

/**
  * Created by alex on 20-6-17.
  */
object Mining {
  case class MineBlock( blockMessage: BlockMessage )
}

trait Mining {
  this: BlockChainCommunication with PeerToPeer with CompositeActor =>

  var miners: Map[BlockMessage, ActorRef] = Map.empty

  receiver {
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


  }
}