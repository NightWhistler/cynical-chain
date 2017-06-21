package net.nightwhistler.nwcsc.blockchain

import akka.actor.{ActorRef, Terminated}
import net.nightwhistler.nwcsc.actor.MiningActor.MineResult
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
      if ( ! blockChain.contains(blockMessage) && ! miners.contains(blockMessage) ) {
        logger.debug(s"Got mining request: ${blockMessage}")
        //Tell all peers to start mining
        peers.foreach( p => p ! m)

        //Spin up a new actor to do the mining
        val miningActor = context.actorOf(MiningActor.props)
        context.watch(miningActor)

        miners += blockMessage -> miningActor
        miningActor ! MiningActor.MineBlock(blockChain, blockMessage)
      }

    case MineResult(block) =>
      val blockMessage = block.message
      miners -= blockMessage

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
      val key = miners.find{ case (_, ref) => ref == deadActor }
        .map(_._1)

      key.foreach( blockMessage => miners -= blockMessage )
      logger.debug( s"Terminated miner for ${key}")
      logger.debug(s"Still mining ${miners.size} blocks.")
  }


}
