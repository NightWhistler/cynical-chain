package net.nightwhistler.nwcsc.actor

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem, Props, Terminated}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import net.nightwhistler.nwcsc.actor.MiningWorker.MineResult
import net.nightwhistler.nwcsc.blockchain._
import net.nightwhistler.nwcsc.p2p.PeerToPeer
import org.scalatest._

/**
  * Created by alex on 22-6-17.
  */

class MiningWorkerTest extends TestKit(ActorSystem("BlockChain")) with FlatSpecLike
  with ImplicitSender with GivenWhenThen with BeforeAndAfterAll with Matchers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  trait WithMiningWorker {
    val miningWorker = system.actorOf(MiningWorker.props(testActor))
  }

  "A MiningWorker" should "notify its parent when a block has been found" in new WithMiningWorker {
    val blockChain = BlockChain(NoDifficulty)
    val message = BlockMessage("bla")
    miningWorker ! MiningWorker.MineBlock(blockChain, Seq(message))

    expectMsgPF() {
      case MineResult(Block(_, _, _, Seq(msg), _, _)) => msg.data shouldEqual "bla"
    }
  }

  it should "stop trying to find a block when it has been asked to stop" in new WithMiningWorker {
    val blockChain = BlockChain(ImpossibleDifficulty)
    val message = BlockMessage("bla")
    miningWorker ! MiningWorker.MineBlock(blockChain, Seq(message))

    watch(miningWorker)
    miningWorker ! MiningWorker.StopMining

    expectMsgPF() {
      case Terminated(ref) => ref shouldBe miningWorker
    }
  }

}
