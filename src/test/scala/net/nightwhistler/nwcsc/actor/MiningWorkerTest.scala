package net.nightwhistler.nwcsc.actor

import org.scalatest.{FlatSpecLike, FunSuite, Ignore}

/**
  * Created by alex on 22-6-17.
  */
class MiningWorkerTest extends FlatSpecLike {

  "A MiningWorker" should "notify its parent when a block has been found" ignore {
    fail("not yet written")
  }

  ignore should "schedule the next series of nonses to be checked as long as no block has been found " +
    "and it hasn't been asked to stop" in {
    fail("not yet written")
  }

  ignore should "stop trying to find a block when it has been asked to stop" in {
    fail("not yet written")
  }

}
