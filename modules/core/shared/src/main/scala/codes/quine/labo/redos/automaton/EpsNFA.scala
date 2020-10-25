package codes.quine.labo.redos
package automaton

import scala.collection.mutable

import EpsNFA._
import data.IChar
import data.ICharSet
import util.GraphvizUtil.escape

/** EpsNFA is an ordered ε-NFA on unicode code points. */
final case class EpsNFA[Q](alphabet: ICharSet, stateSet: Set[Q], init: Q, accept: Q, tau: Map[Q, Transition[Q]]) {

  /** Converts to Graphviz format text. */
  def toGraphviz: String = {
    val sb = new mutable.StringBuilder

    sb.append("digraph {\n")
    sb.append(s"  ${escape("")} [shape=point];\n")
    sb.append(s"  ${escape("")} -> ${escape(init)};\n")
    for (q0 <- stateSet) {
      tau.get(q0) match {
        case Some(Eps(Seq(q1))) =>
          sb.append(s"  ${escape(q0)} [shape=circle];\n")
          sb.append(s"  ${escape(q0)} -> ${escape(q1)};\n")
        case Some(Eps(qs)) =>
          sb.append(s"  ${escape(q0)} [shape=diamond];\n")
          for ((q1, i) <- qs.zipWithIndex)
            sb.append(s"  ${escape(q0)} -> ${escape(q1)} [label=$i];\n")
        case Some(Assert(k, q1)) =>
          sb.append(s"  ${escape(q0)} [shape=circle];\n")
          sb.append(s"  ${escape(q0)} -> ${escape(q1)} [label=${escape(k)}];\n")
        case Some(Consume(chs, q1)) =>
          sb.append(s"  ${escape(q0)} [shape=circle];\n")
          sb.append(s"  ${escape(q0)} -> ${escape(q1)} [label=${escape(chs.mkString("{", ", ", "}"))}];\n")
        case None =>
          sb.append(s"  ${escape(q0)} [shape=doublecircle];\n")
      }
    }
    sb.append("}")

    sb.result()
  }

  /** Converts this ε-NFA to ordered NFA. */
  def toOrderedNFA: OrderedNFA[IChar, (CharInfo, Seq[Q])] = {
    // Skips ε-transition without context infotmation.
    def buildClosure0(q: Q, path: Seq[Q]): Seq[Q] =
      // Exits this loop if a cyclic path is found.
      if (path.lastOption.exists(p => path.containsSlice(Seq(p, q)))) Seq.empty
      else
        tau.get(q) match {
          case Some(Eps(qs))       => qs.flatMap(buildClosure0(_, path :+ q))
          case Some(Assert(_, _))  => Seq(q)
          case Some(Consume(_, _)) => Seq(q)
          case None                => Seq(q)
        }
    val closure0Cache = mutable.Map.empty[Q, Seq[Q]]
    def closure0(q: Q): Seq[Q] = closure0Cache.getOrElseUpdate(q, buildClosure0(q, Seq.empty))

    // Skips ε-transition with context information.
    def buildClosure(c0: CharInfo, c1: CharInfo, q: Q, path: Seq[Q]): Seq[Q] =
      // Exits this loop if a cyclic path is found.
      if (path.lastOption.exists(p => path.containsSlice(Seq(p, q)))) Seq.empty
      else
        tau.get(q) match {
          case Some(Eps(qs)) => qs.flatMap(buildClosure(c0, c1, _, path :+ q))
          case Some(Assert(k, q1)) =>
            if (AssertKind.accepts(k, c0, c1)) buildClosure(c0, c1, q1, path :+ q) else Seq.empty
          case Some(Consume(_, _)) => Seq(q)
          case None                => Seq(q)
        }
    val closureCache = mutable.Map.empty[(CharInfo, CharInfo, Q), Seq[Q]]
    def closure(c0: CharInfo, c1: CharInfo, q: Q): Seq[Q] =
      closureCache.getOrElseUpdate((c0, c1, q), buildClosure(c0, c1, q, Seq.empty))

    val queue = mutable.Queue.empty[(CharInfo, Seq[Q])]
    val newStateSet = mutable.Set.empty[(CharInfo, Seq[Q])]
    val newInits = Seq((CharInfo(true, false), closure0(init)))
    val newAcceptSet = Set.newBuilder[(CharInfo, Seq[Q])]
    val newDelta = Map.newBuilder[((CharInfo, Seq[Q]), IChar), Seq[(CharInfo, Seq[Q])]]

    queue.enqueueAll(newInits)
    newStateSet.addAll(newInits)

    while (queue.nonEmpty) {
      val (c0, qs) = queue.dequeue()
      if (qs.exists(closure(c0, CharInfo(true, false), _).exists(_ == accept))) {
        newAcceptSet.addOne((c0, qs))
      }
      for (ch <- alphabet.chars) {
        val c1 = CharInfo.from(ch)
        val d = Seq.newBuilder[(CharInfo, Seq[Q])]
        for (q0 <- qs; q1 <- closure(c0, c1, q0)) {
          tau.get(q1) match {
            case Some(Consume(chs, q2)) if chs.contains(ch) =>
              val qs1 = closure0(q2)
              d.addOne((c1, qs1))
              if (!newStateSet.contains((c1, qs1))) {
                queue.enqueue((c1, qs1))
                newStateSet.addOne((c1, qs1))
              }
            case Some(Consume(_, _)) | None =>
              () // Nothing to do here because of terminal state or non-match consuming state.
            // $COVERAGE-OFF$
            case _ => throw new IllegalStateException
            // $COVERAFE-ON$
          }
        }
        newDelta.addOne(((c0, qs), ch) -> d.result())
      }
    }

    OrderedNFA(alphabet.chars.toSet, newStateSet.toSet, newInits, newAcceptSet.result(), newDelta.result())
  }
}

/** EpsNFA types and utilities. */
object EpsNFA {

  /** Transition is a transition of ε-NFA. */
  sealed abstract class Transition[Q] extends Serializable with Product

  /** Eps is an ε-NFA transition without consuming a character. */
  final case class Eps[Q](to: Seq[Q]) extends Transition[Q]

  /** Assert is an ε-NFA transition with consuming no character and assertion. */
  final case class Assert[Q](kind: AssertKind, to: Q) extends Transition[Q]

  /** Eps is an ε-NFA transition with consuming a character. */
  final case class Consume[Q](set: Set[IChar], to: Q) extends Transition[Q]

  /** AssertKind is assertion kind of this ε-NFA transition. */
  sealed abstract class AssertKind extends Serializable with Product

  /** AssertKind values and utilities. */
  object AssertKind {

    /** LineBegin is `^` assertion. */
    case object LineBegin extends AssertKind

    /** LineBegin is `$` assertion. */
    case object LineEnd extends AssertKind

    /** LineBegin is `\b` assertion. */
    case object WordBoundary extends AssertKind

    /** LineBegin is `\B` assertion. */
    case object NotWordBoundary extends AssertKind

    /** Tests the assertion on around character informations. */
    def accepts(kind: AssertKind, prev: CharInfo, next: CharInfo): Boolean = kind match {
      case LineBegin       => prev.isLineTerminator
      case LineEnd         => next.isLineTerminator
      case WordBoundary    => prev.isWord != next.isWord
      case NotWordBoundary => prev.isWord == next.isWord
    }
  }

  /** CharInfo is a minimum character information for assertion check. */
  final case class CharInfo(isLineTerminator: Boolean, isWord: Boolean)

  /** CharInfo utilities. */
  object CharInfo {

    /** Extracts a character information from the interval set. */
    def from(ch: IChar): CharInfo = CharInfo(ch.isLineTerminator, ch.isWord)
  }
}