package codes.quine.labo.redos

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import automaton.AutomatonChecker
import automaton.Complexity
import automaton.EpsNFACompiler
import common.ReDoSException
import common.UnsupportedException
import common.Checker
import data.UChar
import data.UString
import fuzz.FuzzChecker
import fuzz.FuzzIR
import regexp.Parser
import regexp.Pattern
import util.Timeout

/** ReDoS is a ReDoS checker frontend. */
object ReDoS {

  /** Tests the given RegExp pattern causes ReDoS. */
  def check(source: String, flags: String, config: Config = Config()): Diagnostics = {
    import config._
    val result = for {
      _ <- Try(()) // Ensures `Try` context.
      pattern <- Parser.parse(source, flags)
      diagnostics <- checker match {
        case Checker.Automaton => checkAutomaton(pattern, config)
        case Checker.Fuzz      => checkFuzz(pattern, config)
        case Checker.Hybrid    => checkHybrid(pattern, config)
      }
    } yield diagnostics
    result.recover { case ex: ReDoSException => Diagnostics.Unknown.from(ex) }.get
  }

  private[redos] def checkAutomaton(pattern: Pattern, config: Config): Try[Diagnostics] = {
    import config._
    val maxNFASize = if (checker == Checker.Hybrid) config.maxNFASize else Int.MaxValue

    val result = for {
      _ <- Try(()) // Ensures `Try` context.
      _ <-
        if (checker == Checker.Hybrid && repeatCount(pattern) >= maxRepeatCount)
          Failure(new UnsupportedException("The pattern contains too many repeat"))
        else Success(())
      complexity <-
        // When the pattern has no infinite repetition, then it is safe.
        if (pattern.isConstant) Success(None)
        else
          for {
            _ <-
              if (checker == Checker.Hybrid && pattern.size >= maxPatternSize)
                Failure(new UnsupportedException("The pattern is too large"))
              else Success(())
            epsNFA <- EpsNFACompiler.compile(pattern)
            orderedNFA <- Try(epsNFA.toOrderedNFA(maxNFASize).rename.mapAlphabet(_.head))
          } yield Some(AutomatonChecker.check(orderedNFA, maxNFASize))
    } yield complexity

    result
      .map {
        case Some(vuln: Complexity.Vulnerable[UChar]) =>
          val attack = UString(vuln.buildAttack(attackLimit, maxAttackSize).toIndexedSeq)
          Diagnostics.Vulnerable(attack, Some(vuln), Some(Checker.Automaton))
        case Some(safe: Complexity.Safe) => Diagnostics.Safe(Some(safe), Some(Checker.Automaton))
        case None                        => Diagnostics.Safe(None, Some(Checker.Automaton))
      }
      .recoverWith { case ex: ReDoSException =>
        ex.used = Some(Checker.Automaton)
        Failure(ex)
      }
  }

  private[redos] def checkFuzz(pattern: Pattern, config: Config): Try[Diagnostics] = {
    import config._

    val result = FuzzIR.from(pattern).map { fuzz =>
      FuzzChecker.check(
        fuzz,
        random,
        seedLimit,
        populationLimit,
        attackLimit,
        crossSize,
        mutateSize,
        maxAttackSize,
        maxSeedSize,
        maxGenerationSize,
        maxIteration
      )
    }

    result
      .map {
        case Some(attack) => Diagnostics.Vulnerable(attack.toUString, None, Some(Checker.Fuzz))
        case None         => Diagnostics.Safe(None, Some(Checker.Fuzz))
      }
      .recoverWith { case ex: ReDoSException =>
        ex.used = Some(Checker.Fuzz)
        Failure(ex)
      }
  }

  private[redos] def checkHybrid(pattern: Pattern, config: Config): Try[Diagnostics] =
    checkAutomaton(pattern, config).recoverWith { case _: UnsupportedException => checkFuzz(pattern, config) }

  /** Gets a sum of repeat specifier counts. */
  private[redos] def repeatCount(pattern: Pattern)(implicit timeout: Timeout = Timeout.NoTimeout): Int =
    timeout.checkTimeout("Checker.repeatCount") {
      import Pattern._

      def loop(node: Node): Int =
        timeout.checkTimeout("Checker.repeatCount:loop")(node match {
          case Disjunction(ns)        => ns.map(loop).sum
          case Sequence(ns)           => ns.map(loop).sum
          case Capture(_, n)          => loop(n)
          case NamedCapture(_, _, n)  => loop(n)
          case Group(n)               => loop(n)
          case Star(_, n)             => loop(n)
          case Plus(_, n)             => loop(n)
          case Question(_, n)         => loop(n)
          case Repeat(_, min, max, n) => max.flatten.getOrElse(min) + loop(n)
          case LookAhead(_, n)        => loop(n)
          case LookBehind(_, n)       => loop(n)
          case _                      => 0
        })

      loop(pattern.node)
    }
}