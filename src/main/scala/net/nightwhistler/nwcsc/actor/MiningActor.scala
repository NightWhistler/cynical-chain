package net.nightwhistler.nwcsc.actor

import akka.actor.{Actor, Props}
import net.nightwhistler.nwcsc.actor.MiningActor.MineBlock
import net.nightwhistler.nwcsc.blockchain.{BlockChain, BlockMessage}
import net.nightwhistler.nwcsc.p2p.PeerToPeerCommunication.{MessageType, PeerMessage}

/**
  * Created by alex on 19-6-17.
  */
object MiningActor {
  case class MineBlock(blockChain: BlockChain, blockMessage: BlockMessage)

  def props: Props = Props[MiningActor]
}

class MiningActor extends Actor {

  override def receive: Receive = {

    case MineBlock(blockChain, blockMessage) =>
      if ( ! blockChain.contains(blockMessage) ) {
        val newChain = blockChain.addBlock(blockMessage)
        sender() ! PeerMessage(MessageType.ResponseBlockChain, Seq(newChain.latestBlock))
        context.stop(self)
      }
  }
}
