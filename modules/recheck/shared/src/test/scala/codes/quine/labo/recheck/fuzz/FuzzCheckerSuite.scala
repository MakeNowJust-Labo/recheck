package codes.quine.labo.recheck
package fuzz

import scala.util.Random

import codes.quine.labo.recheck.common.Context
import codes.quine.labo.recheck.regexp.Parser

class FuzzCheckerSuite extends munit.FunSuite {

  /** A default context. */
  implicit def ctx: Context = Context()

  /** A fixed seed random instance for deterministic test. */
  def random0: Random = new Random(0)

  /** Tests the pattern is vulnerable or not. */
  def check(source: String, flags: String, quick: Boolean = false): Boolean = {
    val result = for {
      pattern <- Parser.parse(source, flags)
      fuzz <- FuzzIR.from(pattern)
    } yield FuzzChecker.check(
      fuzz,
      random0,
      maxAttackSize = if (quick) 400 else 4000,
      seedLimit = if (quick) 1_00 else 1_000,
      populationLimit = if (quick) 1_000 else 10_000,
      attackLimit = if (quick) 10_000 else 100_000
    )
    result.get.isDefined
  }

  test("FuzzChecker.check: constant") {
    assert(!check("^foo$", ""))
    assert(!check("^(foo|bar)$", ""))
    assert(!check("^(fiz{2}|buz{2){1,2}$", ""))
  }

  test("FuzzChecker.check: linear") {
    assert(!check("(a|a)*", ""))
    assert(!check("(a*)*", ""))
  }

  test("FuzzChecker.check: polynomial") {
    assert(check("\\s*$", "", quick = true))
    assert(check("^a*aa*$", "", quick = true))
  }

  test("FuzzChecker.check: exponential") {
    assert(check("^(a|a)*$", "", quick = true))
    assert(check("^(a*)*$", "", quick = true))
    assert(check("^(a|b|ab)*$", "", quick = true))
    assert(check("^(a|B|Ab)*$", "i", quick = true))
    assert(check("^(aa|b|aab)*$", "", quick = true))

    assert(check("^(a?){50}a{50}$", "", quick = true))

    // The checker can find an attack string on seeding phase.
    assert {
      val result = for {
        pattern <- Parser.parse("^(a?){50}a{50}$", "")
        fuzz <- FuzzIR.from(pattern)
      } yield FuzzChecker.check(fuzz, random0, seedLimit = 1000, populationLimit = 1000, attackLimit = 10000)
      result.get.isDefined
    }

    // The checker cannot find too small attack string.
    assert {
      val result = for {
        pattern <- Parser.parse("^(a|a)*$", "")
        fuzz <- FuzzIR.from(pattern)
      } yield FuzzChecker.check(fuzz, random0, populationLimit = 100, maxAttackSize = 5)
      result.get.isEmpty
    }
  }
}