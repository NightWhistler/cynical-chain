package net.nightwhistler.nwcsc.actor

import akka.actor.Props
import net.nightwhistler.nwcsc.blockchain.{BlockChain, BlockChainCommunication, Mining}
import net.nightwhistler.nwcsc.p2p.PeerToPeer

object BlockChainActor {
  def props( blockChain: BlockChain ): Props = Props(classOf[BlockChainActor], blockChain)
}

class BlockChainActor( var blockChain: BlockChain ) extends CompositeActor with PeerToPeer
  with BlockChainCommunication with Mining





