package net.nightwhistler.nwcsc.rest

import akka.actor.ActorRef
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.{TestActor, TestKitBase, TestProbe}
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.actor.BlockChainActor._
import net.nightwhistler.nwcsc.actor.PeerToPeer.{AddPeer, GetPeers, Peers}
import net.nightwhistler.nwcsc.blockchain.{Block, BlockChain, BlockMessage, GenesisBlock}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext

class RestInterfaceTest extends FlatSpec with ScalatestRouteTest with TestKitBase
  with Matchers {

  trait RestInterfaceFixture extends RestInterface {
    val peerToPeerProbe = TestProbe()

    override val peerToPeerActor: ActorRef = peerToPeerProbe.ref

    override val logger = Logger("TestLogger")
    override implicit val executionContext: ExecutionContext = ExecutionContext.global
  }

  "A route " should "get the blockchain from the peertopeer actor for /blocks" in new RestInterfaceFixture {

    peerToPeerProbe.setAutoPilot { (sender: ActorRef, msg: Any) => msg match {
      case GetBlockChain =>
        sender ! CurrentBlockChain(BlockChain())
        TestActor.NoAutoPilot
      }
    }

    Get("/blocks") ~> routes ~> check {
      responseAs[Seq[Block]] shouldEqual Seq(GenesisBlock)
    }
  }

  it should "return the latest block for /latestBlock" in new RestInterfaceFixture {
    peerToPeerProbe.setAutoPilot { (sender: ActorRef, msg: Any) => msg match {
      case QueryLatest =>
        sender ! NewBlock(GenesisBlock)
        TestActor.NoAutoPilot
      }
    }
    Get("/latestBlock") ~> routes ~> check {
      responseAs[Block] shouldEqual GenesisBlock
    }
  }

  it should "retrieve all peers for /peers" in new RestInterfaceFixture {
    peerToPeerProbe.setAutoPilot { (sender: ActorRef, msg: Any) => msg match {

      case GetPeers =>
        sender ! Peers(Seq("PeerOne"))
        TestActor.NoAutoPilot
      }
    }

    Get("/peers") ~> routes ~> check {
      responseAs[Peers] shouldEqual Peers(Seq("PeerOne"))
    }
  }

  it should "add a new peer for /addPeer" in new RestInterfaceFixture {
    Post("/addPeer", HttpEntity(ContentTypes.`text/html(UTF-8)`, "TestPeer")) ~> routes ~> check {
      peerToPeerProbe.expectMsg(AddPeer("TestPeer"))
    }
  }

  it should "add a new block for /mineBlock" in new RestInterfaceFixture {
    peerToPeerProbe.setAutoPilot { (sender: ActorRef, msg: Any) => msg match {
      case AddMessages(data) =>
        sender ! NewBlock(Block(0, 0, 0, data, 0, 1))
        TestActor.NoAutoPilot
      }
    }

    Post("/mineBlock", HttpEntity(ContentTypes.`text/html(UTF-8)`, "MyBlock")) ~> routes ~> check {
      val resp = responseAs[BlockMessage]
      assert( resp.data == "MyBlock")
    }
  }

}


