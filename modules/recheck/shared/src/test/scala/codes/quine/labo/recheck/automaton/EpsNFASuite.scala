package codes.quine.labo.recheck
package automaton

import scala.collection.immutable

import codes.quine.labo.recheck.automaton.EpsNFA._
import codes.quine.labo.recheck.common.Context
import codes.quine.labo.recheck.common.UnsupportedException
import codes.quine.labo.recheck.data.IChar
import codes.quine.labo.recheck.data.ICharSet
import codes.quine.labo.recheck.data.ICharSet.CharKind

class EpsNFASuite extends munit.FunSuite {

  /** A default context. */
  implicit def ctx: Context = Context()

  test("EpsNFA.AssertKind#toCharKindSet") {
    val Normal: CharKind = CharKind.Normal
    val LineTerminator: CharKind = CharKind.LineTerminator
    val Word: CharKind = CharKind.Word
    assertEquals(AssertKind.LineBegin.toCharKindSet(Normal), Set.empty[CharKind])
    assertEquals(AssertKind.LineBegin.toCharKindSet(LineTerminator), Set(Normal, LineTerminator, Word))
    assertEquals(AssertKind.LineBegin.toCharKindSet(Word), Set.empty[CharKind])
    assertEquals(AssertKind.LineEnd.toCharKindSet(Normal), Set(LineTerminator))
    assertEquals(AssertKind.LineEnd.toCharKindSet(LineTerminator), Set(LineTerminator))
    assertEquals(AssertKind.LineEnd.toCharKindSet(Word), Set(LineTerminator))
    assertEquals(AssertKind.WordBoundary.toCharKindSet(Normal), Set(Word))
    assertEquals(AssertKind.WordBoundary.toCharKindSet(LineTerminator), Set(Word))
    assertEquals(AssertKind.WordBoundary.toCharKindSet(Word), Set(Normal, LineTerminator))
    assertEquals(AssertKind.WordBoundaryNot.toCharKindSet(Normal), Set(Normal, LineTerminator))
    assertEquals(AssertKind.WordBoundaryNot.toCharKindSet(LineTerminator), Set(Normal, LineTerminator))
    assertEquals(AssertKind.WordBoundaryNot.toCharKindSet(Word), Set(Word))
  }

  test("EpsNFA#toGraphviz") {
    val nfa = EpsNFA(
      ICharSet.any(false, false).add(IChar('a')),
      immutable.SortedSet(0, 1, 2, 3, 4, 5, 6),
      0,
      6,
      Map(
        0 -> Eps(Seq(1)),
        1 -> Eps(Seq(2, 3)),
        2 -> Consume(Set((IChar('a'), CharKind.Normal)), 4),
        3 -> Assert(AssertKind.LineBegin, 4),
        4 -> LoopEnter(0, 5),
        5 -> LoopExit(0, 6)
      )
    )
    assertEquals(
      nfa.toGraphviz,
      """|digraph {
         |  "" [shape=point];
         |  "" -> "0";
         |  "0" [shape=circle];
         |  "0" -> "1";
         |  "1" [shape=diamond];
         |  "1" -> "2" [label=0];
         |  "1" -> "3" [label=1];
         |  "2" [shape=circle];
         |  "2" -> "4" [label="{([a],Normal)}"];
         |  "3" [shape=circle];
         |  "3" -> "4" [label="LineBegin"];
         |  "4" [shape=circle];
         |  "4" -> "5" [label="Enter(0)"];
         |  "5" [shape=circle];
         |  "5" -> "6" [label="Exit(0)"];
         |  "6" [shape=doublecircle];
         |}""".stripMargin
    )
  }

  test("EpsNFA#toOrderedNFA") {
    val nfa1 = EpsNFA(
      ICharSet.any(false, false).add(IChar('\n'), CharKind.LineTerminator),
      Set(0, 1, 2, 3, 4, 5, 6),
      0,
      6,
      Map(
        0 -> Eps(Seq(1, 5)),
        1 -> LoopEnter(0, 2),
        2 -> Eps(Seq(3, 4)),
        3 -> Consume(Set((IChar('\n'), CharKind.LineTerminator)), 0),
        4 -> Assert(AssertKind.LineEnd, 0),
        5 -> LoopExit(0, 6)
      )
    )
    assertEquals(
      nfa1.toOrderedNFA,
      OrderedNFA[IChar, (CharKind, Seq[Int])](
        Set(IChar('\n'), IChar('\n').complement(false)),
        Set((CharKind.LineTerminator, Seq(3, 6))),
        Vector((CharKind.LineTerminator, Seq(3, 6))),
        Set((CharKind.LineTerminator, Seq(3, 6))),
        Map(
          ((CharKind.LineTerminator, Seq(3, 6)), IChar('\n')) -> Vector(
            (CharKind.LineTerminator, Seq(3, 6))
          )
        )
      )
    )
    val nfa2 = EpsNFA(
      ICharSet.any(false, false).add(IChar('\n'), CharKind.LineTerminator).add(IChar('a'), CharKind.Word),
      Set(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
      0,
      10,
      Map(
        0 -> Assert(AssertKind.LineEnd, 1),
        1 -> Eps(Seq(2, 8)),
        2 -> LoopEnter(0, 3),
        3 -> Eps(Seq(4, 5)),
        4 -> Consume(Set((IChar('\n'), CharKind.LineTerminator)), 1),
        5 -> Consume(Set((IChar('a'), CharKind.Word)), 6),
        6 -> Eps(Seq(7, 1)),
        7 -> Assert(AssertKind.WordBoundaryNot, 1),
        8 -> LoopExit(0, 9),
        9 -> Assert(AssertKind.WordBoundary, 10)
      )
    )
    assertEquals(
      nfa2.toOrderedNFA,
      OrderedNFA(
        Set(IChar('\n'), IChar('a'), IChar('a').union(IChar('\n')).complement(false)),
        Set(
          (CharKind.LineTerminator, Seq(4)),
          (CharKind.LineTerminator, Seq(4, 5)),
          (CharKind.Word, Seq(5, 4, 5, 10))
        ),
        Vector((CharKind.LineTerminator, Seq(4))),
        Set((CharKind.Word: CharKind, Seq(5, 4, 5, 10))),
        Map(
          ((CharKind.LineTerminator, Seq(4)), IChar('\n')) -> Vector(
            (CharKind.LineTerminator, Seq(4, 5))
          ),
          ((CharKind.LineTerminator, Seq(4, 5)), IChar('a')) -> Vector(
            (CharKind.Word, Seq(5, 4, 5, 10))
          ),
          ((CharKind.LineTerminator, Seq(4, 5)), IChar('\n')) -> Vector(
            (CharKind.LineTerminator, Seq(4, 5))
          ),
          ((CharKind.Word, Seq(5, 4, 5, 10)), IChar('\n')) -> Vector(
            (CharKind.LineTerminator, Seq(4, 5))
          ),
          ((CharKind.Word, Seq(5, 4, 5, 10)), IChar('a')) -> Vector(
            (CharKind.Word, Seq(5, 4, 5, 10)),
            (CharKind.Word, Seq(5, 4, 5, 10))
          )
        )
      )
    )
    interceptMessage[UnsupportedException]("OrderedNFA size is too large") {
      nfa1.toOrderedNFA(maxNFASize = 1)
    }
  }
}
