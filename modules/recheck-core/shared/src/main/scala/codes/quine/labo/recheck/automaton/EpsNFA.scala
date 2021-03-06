package codes.quine.labo.recheck
package automaton

import scala.collection.mutable

import codes.quine.labo.recheck.automaton.EpsNFA._
import codes.quine.labo.recheck.common.Context
import codes.quine.labo.recheck.common.UnsupportedException
import codes.quine.labo.recheck.unicode.IChar
import codes.quine.labo.recheck.unicode.ICharSet
import codes.quine.labo.recheck.unicode.ICharSet.CharKind
import codes.quine.labo.recheck.util.GraphvizUtil.escape

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
        case Some(Consume(chs, q1, _)) =>
          sb.append(s"  ${escape(q0)} [shape=circle];\n")
          sb.append(s"  ${escape(q0)} -> ${escape(q1)} [label=${escape(chs.mkString("{", ", ", "}"))}];\n")
        case Some(LoopEnter(loop, q1)) =>
          sb.append(s"  ${escape(q0)} [shape=circle];\n")
          sb.append(s"  ${escape(q0)} -> ${escape(q1)} [label=${escape(s"Enter($loop)")}];\n")
        case Some(LoopExit(loop, q1)) =>
          sb.append(s"  ${escape(q0)} [shape=circle];\n")
          sb.append(s"  ${escape(q0)} -> ${escape(q1)} [label=${escape(s"Exit($loop)")}];\n")
        case None =>
          sb.append(s"  ${escape(q0)} [shape=doublecircle];\n")
      }
    }
    sb.append("}")

    sb.result()
  }

  /** Converts this ε-NFA to ordered NFA with ε-elimination. */
  def toOrderedNFA(implicit ctx: Context): OrderedNFA[IChar, (CharKind, Seq[Q])] = toOrderedNFA(Int.MaxValue)

  /** Converts this ε-NFA to ordered NFA with ε-elimination. */
  def toOrderedNFA(maxNFASize: Int)(implicit ctx: Context): OrderedNFA[IChar, (CharKind, Seq[Q])] =
    ctx.interrupt {
      // Obtains a set of possible character information.
      // The last `CharKind.LineTerminator` means a beginning or ending marker.
      val charInfoSet = alphabet.pairs.iterator.map(_._2).toSet ++ Set(CharKind.LineTerminator)

      /** Skips ε-transitions from the state and collects not ε-transition or terminal state. */
      def buildClosure(k0: CharKind, ks: Set[CharKind], q: Q, loops: Set[Int]): Seq[(Q, Option[Consume[Q]])] =
        ctx.interrupt {
          if (ks.isEmpty) Vector.empty
          else
            tau.get(q) match {
              case Some(Eps(qs))       => qs.flatMap(buildClosure(k0, ks, _, loops))
              case Some(Assert(k, q1)) => buildClosure(k0, ks & k.toCharKindSet(k0), q1, loops)
              case Some(LoopEnter(loop, q1)) =>
                if (loops.contains(loop)) Vector.empty // An ε-loop is detected.
                else buildClosure(k0, ks, q1, loops ++ Set(loop))
              case Some(LoopExit(loop, q1)) =>
                if (loops.contains(loop)) Vector.empty // An ε-loop is detected.
                else buildClosure(k0, ks, q1, loops)
              case Some(Consume(chs, q1, pos)) =>
                val newChs = chs.filter { case (_, k) => ks.contains(k) }
                if (newChs.nonEmpty) Vector((q, Some(Consume(newChs, q1, pos))))
                else Vector.empty
              case None =>
                if (ks.contains(CharKind.LineTerminator)) Vector((q, None))
                else Vector.empty
            }
        }
      val closureCache = mutable.Map.empty[(CharKind, Q), Seq[(Q, Option[Consume[Q]])]]
      def closure(k0: CharKind, q: Q): Seq[(Q, Option[Consume[Q]])] =
        closureCache.getOrElseUpdate((k0, q), buildClosure(k0, charInfoSet, q, Set.empty))

      val closureInit = closure(CharKind.LineTerminator, init)

      val queue = mutable.Queue.empty[(CharKind, Seq[(Q, Option[Consume[Q]])])]
      val newStateSet = mutable.Set.empty[(CharKind, Seq[Q])]
      val newInits = Vector((CharKind.LineTerminator, closureInit.map(_._1)))
      val newAcceptSet = Set.newBuilder[(CharKind, Seq[Q])]
      val newDelta = Map.newBuilder[((CharKind, Seq[Q]), IChar), Seq[(CharKind, Seq[Q])]]
      val newSourcemap = mutable.Map
        .empty[((CharKind, Seq[Q]), IChar, (CharKind, Seq[Q])), Seq[(Int, Int)]]
        .withDefaultValue(Vector.empty)
      var deltaSize = 0

      queue.enqueue((CharKind.LineTerminator, closureInit))
      newStateSet.addAll(newInits)

      while (queue.nonEmpty) ctx.interrupt {
        val (c0, qps) = queue.dequeue()
        val qs0 = qps.map(_._1)
        if (qs0.contains(accept)) newAcceptSet.addOne((c0, qs0))
        val d = mutable.Map.empty[IChar, Seq[(CharKind, Seq[Q])]].withDefaultValue(Vector.empty)
        for ((_, p) <- qps) {
          p match {
            case Some(Consume(chs, q1, loc)) =>
              for ((ch, k1) <- chs) {
                val qps1 = closure(k1, q1)
                val qs1 = qps1.map(_._1)
                d(ch) = d(ch) :+ (k1, qs1)
                newSourcemap(((c0, qs0), ch, (k1, qs1))) ++= loc
                if (!newStateSet.contains((k1, qs1))) {
                  queue.enqueue((k1, qps1))
                  newStateSet.addOne((k1, qs1))
                }
              }
            case None =>
              () // Nothing to do here because of terminal state.
          }
        }
        for ((ch, to) <- d) newDelta.addOne(((c0, qs0), ch) -> to)
        deltaSize += d.size
        if (deltaSize >= maxNFASize) throw new UnsupportedException("OrderedNFA size is too large")
      }

      OrderedNFA(
        alphabet.any.map(_._1),
        newStateSet.toSet,
        newInits,
        newAcceptSet.result(),
        newDelta.result(),
        newSourcemap.toMap.filter(_._2.nonEmpty)
      )
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

  /** Consume is an ε-NFA transition with consuming a character. */
  final case class Consume[Q](set: Set[(IChar, CharKind)], to: Q, loc: Option[(Int, Int)] = None) extends Transition[Q]

  /** LoopEnter is an ε-NFA transition with consuming no character and marking a entering of the loop. */
  final case class LoopEnter[Q](loop: Int, to: Q) extends Transition[Q]

  /** LoopExit is an ε-NFA transition with consuming no character and marking a exiting of the loop. */
  final case class LoopExit[Q](loop: Int, to: Q) extends Transition[Q]

  /** AssertKind is assertion kind of this ε-NFA transition. */
  sealed abstract class AssertKind extends Serializable with Product {

    /** Returns a set of next possible character information from the previous character information. */
    def toCharKindSet(prev: CharKind): Set[CharKind]
  }

  /** AssertKind values and utilities. */
  object AssertKind {

    /** LineBegin is `^` assertion. */
    case object LineBegin extends AssertKind {
      def toCharKindSet(prev: CharKind): Set[CharKind] =
        if (prev == CharKind.LineTerminator) Set(CharKind.Normal, CharKind.LineTerminator, CharKind.Word)
        else Set.empty
    }

    /** LineEnd is `$` assertion. */
    case object LineEnd extends AssertKind {
      def toCharKindSet(prev: CharKind): Set[CharKind] = Set(CharKind.LineTerminator)
    }

    /** WordBoundary is `\b` assertion. */
    case object WordBoundary extends AssertKind {
      def toCharKindSet(prev: CharKind): Set[CharKind] =
        if (prev == CharKind.Word) Set(CharKind.Normal, CharKind.LineTerminator)
        else Set(CharKind.Word)
    }

    /** WordBoundaryNot is `\B` assertion. */
    case object WordBoundaryNot extends AssertKind {
      def toCharKindSet(prev: CharKind): Set[CharKind] =
        if (prev == CharKind.Word) Set(CharKind.Word)
        else Set(CharKind.Normal, CharKind.LineTerminator)
    }
  }
}
