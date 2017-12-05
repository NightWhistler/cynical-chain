package net.nightwhistler.nwcsc

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import net.nightwhistler.nwcsc.actor.BlockChainActor
import net.nightwhistler.nwcsc.actor.BlockChainActor.{NewBlock, NewBlockChain, QueryAll, QueryLatest}
import net.nightwhistler.nwcsc.actor.PeerToPeer.{BlockChainUpdated, BroadcastRequest}
import net.nightwhistler.nwcsc.blockchain._
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, GivenWhenThen, Matchers}

import scala.collection.immutable.Seq
import scala.util.Try

class BlockChainActorTest extends TestKit(ActorSystem("BlockChain")) with FlatSpecLike
  with ImplicitSender with GivenWhenThen with BeforeAndAfterAll with Matchers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  trait WithTestActor {
    val blockChain = BlockChain(NoDifficulty).addMessage("My test data").get
    val peerToPeer = TestProbe()
    val blockChainActor: ActorRef = system.actorOf(BlockChainActor.props(blockChain, peerToPeer.ref))
  }

  "A BlockChainActor actor" should "send the blockchain to anybody that requests it" in new WithTestActor {
    blockChainActor ! QueryAll
    expectMsg(NewBlockChain(blockChain.blocks))
  }

  it should "send the latest block, and nothing more for a QueryLatest request" in new WithTestActor {
    When("we query for a single block")
    blockChainActor ! QueryLatest

    Then("we only expect the latest block")
    expectMsg(NewBlock(blockChain.latestBlock))
  }

  it should "attach a block to the current chain when it receives a new BlockChain which has exactly 1 new block" in new WithTestActor {

    Given("and a new chain containing an extra block")
    val nextBlock = blockChain.generateNextBlock(Seq(BlockMessage("Some more data")), "", 0)
    val oldBlocks = blockChain.blocks

    When("we receive a message with the longer chain")
    blockChainActor ! NewBlock(nextBlock)
    blockChainActor ! QueryAll

    Then("the new block should be added to the chain, and a broadcast should be sent")
    expectMsgPF() {
      case NewBlockChain(blocks) => blocks shouldEqual( nextBlock +: oldBlocks )
    }
  }

  it should "replace the chain if more than 1 new block is received" in new WithTestActor {
    Given("A blockchain with 3 new blocks")
    val blockData = Seq("aap", "noot", "mies")
    val longerChain = blockData.foldLeft(Try(blockChain)) { case (chain, data) =>
      chain.flatMap(_.addMessage(data))
    }.get

    When("we receive this longer chain")
    blockChainActor ! NewBlockChain(longerChain.blocks)

    Then("The chain should be replaced, and the peer to peer actor should be notified")
    peerToPeer.expectMsg(BroadcastRequest(NewBlock(longerChain.latestBlock)))
    peerToPeer.expectMsg(BlockChainUpdated(longerChain))

  }


  it should "do nothing if the received chain is valid but shorter than the current one" in new WithTestActor  {
    Given("the old blockchain and a current blockchain which is longer")
    val oldBlockChain = blockChain
    val newBlockChain = Seq("Some new data", "And more").foldLeft(Try(oldBlockChain)) {
      case (chain, msg) => chain.flatMap(_.addMessage(msg))
    }.get

    blockChainActor ! NewBlockChain(newBlockChain.blocks)

    When("we receive the old blockchain")
    blockChainActor ! NewBlockChain(oldBlockChain.blocks)

    Then("We expect the message to be discarded")
    blockChainActor ! QueryAll
    expectMsg(NewBlockChain(newBlockChain.blocks))
  }

  it should "query for the full chain when we receive a single block that is further ahead in the chain" in new WithTestActor {
    Given("a later version of the blockchain which is 2 blocks ahead")
    val oldBlockChain = blockChain
    val newBlockChain = Seq("Some new data", "And more").foldLeft(Try(oldBlockChain)) {
      case (chain, msg) => chain.flatMap(_.addMessage(msg))
    }.get


    When("we receive the head of this blockchain")
    blockChainActor ! NewBlock(newBlockChain.latestBlock)

    Then("expect a query for the full blockchain")
    peerToPeer.expectMsg(BroadcastRequest(QueryAll))
    peerToPeer.reply(NewBlockChain(newBlockChain.blocks))

    Then("we expect the blockchain to be changed")
    blockChainActor ! QueryAll
    expectMsg(NewBlockChain(newBlockChain.blocks))

  }

}
