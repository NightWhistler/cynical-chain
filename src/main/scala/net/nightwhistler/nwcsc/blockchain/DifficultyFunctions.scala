package net.nightwhistler.nwcsc.blockchain

import net.nightwhistler.nwcsc.blockchain.BlockChain.DifficultyFunction

/**
  * Created by alex on 22-6-17.
  */

/**
  * This is the difficulty function used by naivecoin,
  * which raises the difficulty based on the block-index.
  *
  * The difficulty goes up every X blocks.
  */
object NaiveCoinDifficulty extends DifficultyFunction {
  val BASE_DIFFICULTY: BigInt = BigInt("f" * 64, 16)
  val EVERY_X_BLOCKS = 5
  val POW_CURVE = 5

  override def apply(block: Block): BigInt = {
    val blockSeriesNumber = ((BigInt(block.index) + 1) / EVERY_X_BLOCKS ) + 1
    val pow = blockSeriesNumber.pow(POW_CURVE)

    BASE_DIFFICULTY / pow
  }
}

/**
  * A difficultyfunction that will never succeed
  */
object ImpossibleDifficulty extends DifficultyFunction {
  override def apply(block: Block): BigInt = 0
}

/**
  * A difficulty function that will always succeed
  */
object DummyDifficulty extends DifficultyFunction {
  override def apply(b: Block): BigInt = NaiveCoinDifficulty.BASE_DIFFICULTY
}
