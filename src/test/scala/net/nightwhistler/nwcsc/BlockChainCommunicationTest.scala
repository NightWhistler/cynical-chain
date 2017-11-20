package net.nightwhistler.nwcsc

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import net.nightwhistler.nwcsc.actor.CompositeActor
import net.nightwhistler.nwcsc.blockchain.BlockChainCommunication.{QueryAll, QueryLatest, ResponseBlock, ResponseBlocks}
import net.nightwhistler.nwcsc.blockchain._
import net.nightwhistler.nwcsc.p2p.PeerToPeer
import net.nightwhistler.nwcsc.p2p.PeerToPeer.{AddPeer, GetPeers, HandShake}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, GivenWhenThen, Matchers}

/**
  * Created by alex on 15-6-17.
  */

class BlockChainCommunicationActor(var blockChain: BlockChain) extends
  CompositeActor with BlockChainCommunication with PeerToPeer

class BlockChainCommunicationTest extends TestKit(ActorSystem("BlockChain")) with FlatSpecLike
  with ImplicitSender with GivenWhenThen with BeforeAndAfterAll with Matchers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  trait WithTestActor {
    val blockChain = BlockChain(NoDifficulty).addMessage("My test data")
    val blockChainCommunicationActor: ActorRef = system.actorOf(Props(classOf[BlockChainCommunicationActor], blockChain))
  }

  "A BlockChainCommunication actor" should "send the blockchain to anybody that requests it" in new WithTestActor {
    blockChainCommunicationActor ! QueryAll
    expectMsg(ResponseBlocks(blockChain.blocks))
  }

  it should "send the latest block, and nothing more for a QueryLatest request" in new WithTestActor {
    When("we query for a single block")
    blockChainCommunicationActor ! QueryLatest

    Then("we only expect the latest block")
    expectMsg(ResponseBlock(blockChain.latestBlock))
  }

  it should "attach a block to the current chain when it receives a new BlockChain which has exactly 1 new block" in new WithTestActor {

    Given("and a new chain containing an extra block")
    val nextBlock = blockChain.generateNextBlock(Seq(BlockMessage("Some more data")))
    val oldBlocks = blockChain.blocks

    When("we receive a message with the longer chain")
    blockChainCommunicationActor ! ResponseBlock(nextBlock)
    blockChainCommunicationActor ! QueryAll

    Then("the new block should be added to the chain, and a broadcast should be sent")
    expectMsgPF() {
      case ResponseBlocks(blocks) => blocks shouldEqual( nextBlock +: oldBlocks )
    }
  }

  it should "replace the chain if more than 1 new block is received" in new WithTestActor {
    Given("A blockchain with 3 new blocks")
    val blockData = Seq("aap", "noot", "mies")
    val longerChain = blockData.foldLeft(blockChain) { case (chain, data) =>
      chain.addMessage(data)
    }

    blockChainCommunicationActor ! AddPeer(testActor.path.toStringWithoutAddress)
    expectMsg(HandShake)
    expectMsg(GetPeers)

    When("we receive this longer chain")
    blockChainCommunicationActor ! ResponseBlocks(longerChain.blocks)

    Then("The chain should be replaced, and a broadcast should be sent")
    expectMsg(ResponseBlocks(longerChain.blocks))

  }


  it should "do nothing if the received chain is valid but shorter than the current one" in new WithTestActor  {
    Given("the old blockchain and a current blockchain which is longer")
    val oldBlockChain = blockChain
    val newBlockChain = oldBlockChain .addMessage("Some new data") .addMessage("And more")

    blockChainCommunicationActor ! ResponseBlocks(newBlockChain.blocks)

    When("we receive the old blockchain")
    blockChainCommunicationActor ! ResponseBlocks(oldBlockChain.blocks)

    Then("We expect the message to be discarded")
    blockChainCommunicationActor ! QueryAll
    expectMsg(ResponseBlocks(newBlockChain.blocks))
  }

  it should "query for the full chain when we receive a single block that is further ahead in the chain" in new WithTestActor {
    Given("a later version of the blockchain which is 2 blocks ahead")
    val oldBlockChain = blockChain
    val newBlockChain = blockChain
      .addMessage("Some new data") .addMessage("And more")

    blockChainCommunicationActor ! HandShake

    When("we receive the head of this blockchain")
    blockChainCommunicationActor ! ResponseBlock(newBlockChain.latestBlock)

    Then("expect a query for the full blockchain")
    expectMsg(QueryAll)

    Then("we expect the blockchain to be unchanged")
    blockChainCommunicationActor ! QueryAll
    expectMsg(ResponseBlocks(oldBlockChain.blocks))

  }

}
