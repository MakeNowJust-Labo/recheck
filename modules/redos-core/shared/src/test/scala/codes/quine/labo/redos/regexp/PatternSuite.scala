package codes.quine.labo.redos
package regexp

import scala.util.Success

import Pattern._
import data.IChar
import data.ICharSet
import data.UChar

class PatternSuite extends munit.FunSuite {
  test("Pattern.AtomNode#toIChar") {
    assertEquals(Character(UChar('x')).toIChar(false, false), Success(IChar('x')))
    assertEquals(SimpleEscapeClass(false, EscapeClassKind.Word).toIChar(false, false), Success(IChar.Word))
    assertEquals(
      SimpleEscapeClass(true, EscapeClassKind.Word).toIChar(false, false),
      Success(IChar.Word.complement(false))
    )
    assertEquals(
      SimpleEscapeClass(false, EscapeClassKind.Word).toIChar(true, true),
      Success(IChar.canonicalize(IChar.Word, true))
    )
    assertEquals(
      SimpleEscapeClass(true, EscapeClassKind.Word).toIChar(true, true),
      Success(IChar.canonicalize(IChar.Word, true).complement(true))
    )
    assertEquals(SimpleEscapeClass(false, EscapeClassKind.Digit).toIChar(false, false), Success(IChar.Digit))
    assertEquals(
      SimpleEscapeClass(true, EscapeClassKind.Digit).toIChar(false, false),
      Success(IChar.Digit.complement(false))
    )
    assertEquals(SimpleEscapeClass(false, EscapeClassKind.Space).toIChar(false, false), Success(IChar.Space))
    assertEquals(
      SimpleEscapeClass(true, EscapeClassKind.Space).toIChar(false, false),
      Success(IChar.Space.complement(false))
    )
    assertEquals(UnicodeProperty(false, "ASCII").toIChar(false, false), Success(IChar.UnicodeProperty("ASCII").get))
    assertEquals(
      UnicodeProperty(true, "ASCII").toIChar(false, true),
      Success(IChar.UnicodeProperty("ASCII").get.complement(true))
    )
    assertEquals(UnicodeProperty(false, "L").toIChar(false, false), Success(IChar.UnicodeProperty("L").get))
    assertEquals(
      UnicodeProperty(true, "L").toIChar(false, true),
      Success(IChar.UnicodeProperty("L").get.complement(true))
    )
    interceptMessage[InvalidRegExpException]("unknown Unicode property: invalid") {
      UnicodeProperty(false, "invalid").toIChar(false, false).get
    }
    val Hira = IChar.UnicodePropertyValue("sc", "Hira").get
    assertEquals(UnicodePropertyValue(false, "sc", "Hira").toIChar(false, false), Success(Hira))
    assertEquals(UnicodePropertyValue(true, "sc", "Hira").toIChar(false, true), Success(Hira.complement(true)))
    interceptMessage[InvalidRegExpException]("unknown Unicode property-value: sc=invalid") {
      UnicodePropertyValue(false, "sc", "invalid").toIChar(false, false).get
    }
    assertEquals(
      CharacterClass(false, Seq(Character(UChar('a')), Character(UChar('A')))).toIChar(false, false),
      Success(IChar('a').union(IChar('A')))
    )
    assertEquals(
      CharacterClass(true, Seq(Character(UChar('a')), Character(UChar('A')))).toIChar(false, false),
      Success(IChar('a').union(IChar('A'))) // Not complemented is intentionally.
    )
    interceptMessage[InvalidRegExpException]("an empty range") {
      CharacterClass(true, Seq(ClassRange(UChar('z'), UChar('a')))).toIChar(false, false).get
    }
    assertEquals(ClassRange(UChar('a'), UChar('a')).toIChar(false, false), Success(IChar('a')))
    assertEquals(ClassRange(UChar('a'), UChar('z')).toIChar(false, false), Success(IChar.range(UChar('a'), UChar('z'))))
    interceptMessage[InvalidRegExpException]("an empty range") {
      ClassRange(UChar('z'), UChar('a')).toIChar(false, false).get
    }
  }

  test("Pattern.showNode") {
    val x = Character(UChar('x'))
    assertEquals(showNode(Disjunction(Seq(Disjunction(Seq(x, x)), x))), "(?:x|x)|x")
    assertEquals(showNode(Disjunction(Seq(x, x, x))), "x|x|x")
    assertEquals(showNode(Sequence(Seq(Disjunction(Seq(x, x)), x))), "(?:x|x)x")
    assertEquals(showNode(Sequence(Seq(Sequence(Seq(x, x)), x))), "(?:xx)x")
    assertEquals(showNode(Sequence(Seq(x, x, x))), "xxx")
    assertEquals(showNode(Capture(x)), "(x)")
    assertEquals(showNode(NamedCapture("foo", x)), "(?<foo>x)")
    assertEquals(showNode(Group(x)), "(?:x)")
    assertEquals(showNode(Star(false, x)), "x*")
    assertEquals(showNode(Star(true, x)), "x*?")
    assertEquals(showNode(Star(false, Disjunction(Seq(x, x)))), "(?:x|x)*")
    assertEquals(showNode(Star(false, Sequence(Seq(x, x)))), "(?:xx)*")
    assertEquals(showNode(Star(false, Star(false, x))), "(?:x*)*")
    assertEquals(showNode(Star(false, LookAhead(false, x))), "(?:(?=x))*")
    assertEquals(showNode(Star(false, LookBehind(false, x))), "(?:(?<=x))*")
    assertEquals(showNode(Plus(false, x)), "x+")
    assertEquals(showNode(Plus(true, x)), "x+?")
    assertEquals(showNode(Question(false, x)), "x?")
    assertEquals(showNode(Question(true, x)), "x??")
    assertEquals(showNode(Repeat(false, 3, None, x)), "x{3}")
    assertEquals(showNode(Repeat(true, 3, None, x)), "x{3}?")
    assertEquals(showNode(Repeat(false, 3, Some(None), x)), "x{3,}")
    assertEquals(showNode(Repeat(true, 3, Some(None), x)), "x{3,}?")
    assertEquals(showNode(Repeat(false, 3, Some(Some(5)), x)), "x{3,5}")
    assertEquals(showNode(Repeat(true, 3, Some(Some(5)), x)), "x{3,5}?")
    assertEquals(showNode(WordBoundary(false)), "\\b")
    assertEquals(showNode(WordBoundary(true)), "\\B")
    assertEquals(showNode(LineBegin), "^")
    assertEquals(showNode(LineEnd), "$")
    assertEquals(showNode(LookAhead(false, x)), "(?=x)")
    assertEquals(showNode(LookAhead(true, x)), "(?!x)")
    assertEquals(showNode(LookBehind(false, x)), "(?<=x)")
    assertEquals(showNode(LookBehind(true, x)), "(?<!x)")
    assertEquals(showNode(Character(UChar('/'))), "\\/")
    assertEquals(showNode(Character(UChar(1))), "\\cA")
    assertEquals(showNode(Character(UChar('\n'))), "\\n")
    assertEquals(showNode(Character(UChar(' '))), " ")
    assertEquals(showNode(Character(UChar('A'))), "A")
    assertEquals(showNode(CharacterClass(false, Seq(x))), "[x]")
    assertEquals(showNode(CharacterClass(false, Seq(ClassRange(UChar('a'), UChar('z'))))), "[a-z]")
    assertEquals(showNode(CharacterClass(false, Seq(SimpleEscapeClass(false, EscapeClassKind.Word)))), "[\\w]")
    assertEquals(showNode(CharacterClass(false, Seq(Character(UChar(1))))), "[\\cA]")
    assertEquals(showNode(CharacterClass(false, Seq(Character(UChar('-'))))), "[\\-]")
    assertEquals(showNode(CharacterClass(true, Seq(x))), "[^x]")
    assertEquals(showNode(SimpleEscapeClass(false, EscapeClassKind.Digit)), "\\d")
    assertEquals(showNode(SimpleEscapeClass(true, EscapeClassKind.Digit)), "\\D")
    assertEquals(showNode(SimpleEscapeClass(false, EscapeClassKind.Word)), "\\w")
    assertEquals(showNode(SimpleEscapeClass(true, EscapeClassKind.Word)), "\\W")
    assertEquals(showNode(SimpleEscapeClass(false, EscapeClassKind.Space)), "\\s")
    assertEquals(showNode(SimpleEscapeClass(true, EscapeClassKind.Space)), "\\S")
    assertEquals(showNode(UnicodeProperty(false, "ASCII")), "\\p{ASCII}")
    assertEquals(showNode(UnicodeProperty(true, "ASCII")), "\\P{ASCII}")
    assertEquals(showNode(UnicodePropertyValue(false, "sc", "Hira")), "\\p{sc=Hira}")
    assertEquals(showNode(UnicodePropertyValue(true, "sc", "Hira")), "\\P{sc=Hira}")
    assertEquals(showNode(Dot), ".")
    assertEquals(showNode(BackReference(1)), "\\1")
    assertEquals(showNode(NamedBackReference("foo")), "\\k<foo>")
  }

  test("Pattern.showFlagSet") {
    assertEquals(showFlagSet(FlagSet(false, false, false, false, false, false)), "")
    assertEquals(showFlagSet(FlagSet(true, true, true, true, true, true)), "gimsuy")
  }

  test("Pattern#toString") {
    assertEquals(
      Pattern(Character(UChar('x')), FlagSet(true, true, false, false, false, false)).toString,
      "/x/gi"
    )
  }

  test("Pattern#hasLineBeginAtBegin") {
    val flagSet0 = FlagSet(false, false, false, false, false, false)
    val flagSet1 = FlagSet(false, false, true, false, false, false)
    assertEquals(Pattern(Disjunction(Seq(LineBegin, LineBegin)), flagSet0).hasLineBeginAtBegin, true)
    assertEquals(Pattern(Disjunction(Seq(Dot, LineBegin)), flagSet0).hasLineBeginAtBegin, false)
    assertEquals(Pattern(Disjunction(Seq(LineBegin, Dot)), flagSet0).hasLineBeginAtBegin, false)
    assertEquals(Pattern(Disjunction(Seq(Dot, Dot)), flagSet0).hasLineBeginAtBegin, false)
    assertEquals(Pattern(Sequence(Seq(Dot, LineBegin)), flagSet0).hasLineBeginAtBegin, false)
    assertEquals(Pattern(Sequence(Seq(LineBegin, Dot)), flagSet0).hasLineBeginAtBegin, true)
    assertEquals(Pattern(Sequence(Seq(Dot, Dot)), flagSet0).hasLineBeginAtBegin, false)
    assertEquals(Pattern(Capture(LineBegin), flagSet0).hasLineBeginAtBegin, true)
    assertEquals(Pattern(Capture(Dot), flagSet0).hasLineBeginAtBegin, false)
    assertEquals(Pattern(NamedCapture("foo", LineBegin), flagSet0).hasLineBeginAtBegin, true)
    assertEquals(Pattern(NamedCapture("foo", Dot), flagSet0).hasLineBeginAtBegin, false)
    assertEquals(Pattern(Group(LineBegin), flagSet0).hasLineBeginAtBegin, true)
    assertEquals(Pattern(Group(Dot), flagSet0).hasLineBeginAtBegin, false)
    assertEquals(Pattern(LineBegin, flagSet0).hasLineBeginAtBegin, true)
    assertEquals(Pattern(LineEnd, flagSet0).hasLineBeginAtBegin, false)
    assertEquals(Pattern(Dot, flagSet0).hasLineBeginAtBegin, false)
    assertEquals(Pattern(LineBegin, flagSet1).hasLineBeginAtBegin, false)
  }

  test("Pattern#hasLineEndAtEnd") {
    val flagSet0 = FlagSet(false, false, false, false, false, false)
    val flagSet1 = FlagSet(false, false, true, false, false, false)
    assertEquals(Pattern(Disjunction(Seq(LineEnd, LineEnd)), flagSet0).hasLineEndAtEnd, true)
    assertEquals(Pattern(Disjunction(Seq(Dot, LineEnd)), flagSet0).hasLineEndAtEnd, false)
    assertEquals(Pattern(Disjunction(Seq(LineEnd, Dot)), flagSet0).hasLineEndAtEnd, false)
    assertEquals(Pattern(Disjunction(Seq(Dot, Dot)), flagSet0).hasLineEndAtEnd, false)
    assertEquals(Pattern(Sequence(Seq(Dot, LineEnd)), flagSet0).hasLineEndAtEnd, true)
    assertEquals(Pattern(Sequence(Seq(LineEnd, Dot)), flagSet0).hasLineEndAtEnd, false)
    assertEquals(Pattern(Sequence(Seq(Dot, Dot)), flagSet0).hasLineEndAtEnd, false)
    assertEquals(Pattern(Capture(LineEnd), flagSet0).hasLineEndAtEnd, true)
    assertEquals(Pattern(Capture(Dot), flagSet0).hasLineEndAtEnd, false)
    assertEquals(Pattern(NamedCapture("foo", LineEnd), flagSet0).hasLineEndAtEnd, true)
    assertEquals(Pattern(NamedCapture("foo", Dot), flagSet0).hasLineEndAtEnd, false)
    assertEquals(Pattern(Group(LineEnd), flagSet0).hasLineEndAtEnd, true)
    assertEquals(Pattern(Group(Dot), flagSet0).hasLineEndAtEnd, false)
    assertEquals(Pattern(LineBegin, flagSet0).hasLineEndAtEnd, false)
    assertEquals(Pattern(LineEnd, flagSet0).hasLineEndAtEnd, true)
    assertEquals(Pattern(Dot, flagSet0).hasLineEndAtEnd, false)
    assertEquals(Pattern(LineEnd, flagSet1).hasLineEndAtEnd, false)
  }

  test("Pattern#alphabet") {
    val flagSet0 = FlagSet(false, false, false, false, false, false)
    val flagSet1 = FlagSet(false, false, true, false, false, false)
    val flagSet2 = FlagSet(false, false, false, true, false, false)
    val flagSet3 = FlagSet(false, true, false, false, false, false)
    val flagSet4 = FlagSet(false, true, false, false, true, false)
    val word = IChar.Word.withWord
    val lineTerminator = IChar.LineTerminator.withLineTerminator
    val dot16 = IChar.Any16.diff(IChar.LineTerminator)
    val dot = IChar.Any.diff(IChar.LineTerminator)
    assertEquals(Pattern(Sequence(Seq.empty), flagSet0).alphabet, Success(ICharSet.any(false, false)))
    assertEquals(Pattern(Dot, flagSet0).alphabet, Success(ICharSet.any(false, false).add(dot16)))
    assertEquals(
      Pattern(Disjunction(Seq(Character(UChar('A')), Character(UChar('Z')))), flagSet0).alphabet,
      Success(ICharSet.any(false, false).add(IChar('A')).add(IChar('Z')))
    )
    assertEquals(
      Pattern(WordBoundary(false), flagSet0).alphabet,
      Success(ICharSet.any(false, false).add(word))
    )
    assertEquals(
      Pattern(LineBegin, flagSet1).alphabet,
      Success(ICharSet.any(false, false).add(lineTerminator))
    )
    assertEquals(
      Pattern(Sequence(Seq(LineBegin, WordBoundary(false))), flagSet1).alphabet,
      Success(ICharSet.any(false, false).add(lineTerminator).add(word))
    )
    assertEquals(Pattern(Capture(Dot), flagSet2).alphabet, Success(ICharSet.any(false, false)))
    assertEquals(Pattern(NamedCapture("foo", Dot), flagSet2).alphabet, Success(ICharSet.any(false, false)))
    assertEquals(Pattern(Group(Dot), flagSet2).alphabet, Success(ICharSet.any(false, false)))
    assertEquals(Pattern(Star(false, Dot), flagSet2).alphabet, Success(ICharSet.any(false, false)))
    assertEquals(Pattern(Plus(false, Dot), flagSet2).alphabet, Success(ICharSet.any(false, false)))
    assertEquals(Pattern(Question(false, Dot), flagSet2).alphabet, Success(ICharSet.any(false, false)))
    assertEquals(Pattern(Repeat(false, 2, None, Dot), flagSet2).alphabet, Success(ICharSet.any(false, false)))
    assertEquals(Pattern(LookAhead(false, Dot), flagSet2).alphabet, Success(ICharSet.any(false, false)))
    assertEquals(Pattern(LookBehind(false, Dot), flagSet2).alphabet, Success(ICharSet.any(false, false)))
    assertEquals(Pattern(Dot, flagSet2).alphabet, Success(ICharSet.any(false, false)))
    assertEquals(Pattern(Sequence(Seq.empty), flagSet3).alphabet, Success(ICharSet.any(true, false)))
    assertEquals(
      Pattern(Dot, flagSet3).alphabet,
      Success(ICharSet.any(true, false).add(IChar.canonicalize(dot16, false)))
    )
    assertEquals(
      Pattern(Disjunction(Seq(Character(UChar('A')), Character(UChar('Z')))), flagSet3).alphabet,
      Success(ICharSet.any(true, false).add(IChar('A')).add(IChar('Z')))
    )
    assertEquals(Pattern(Sequence(Seq.empty), flagSet4).alphabet, Success(ICharSet.any(true, true)))
    assertEquals(
      Pattern(Dot, flagSet4).alphabet,
      Success(ICharSet.any(true, true).add(IChar.canonicalize(dot, true)))
    )
  }

  test("Pattern#needsLineTerminatorDistinction") {
    val flagSet0 = FlagSet(false, false, true, false, false, false)
    val flagSet1 = FlagSet(false, false, false, false, false, false)
    assertEquals(Pattern(Disjunction(Seq(Dot, LineBegin)), flagSet0).needsLineTerminatorDistinction, true)
    assertEquals(Pattern(Disjunction(Seq(LineBegin, Dot)), flagSet0).needsLineTerminatorDistinction, true)
    assertEquals(Pattern(Disjunction(Seq(Dot, Dot)), flagSet0).needsLineTerminatorDistinction, false)
    assertEquals(Pattern(Sequence(Seq(Dot, LineBegin)), flagSet0).needsLineTerminatorDistinction, true)
    assertEquals(Pattern(Sequence(Seq(LineBegin, Dot)), flagSet0).needsLineTerminatorDistinction, true)
    assertEquals(Pattern(Sequence(Seq(Dot, Dot)), flagSet0).needsLineTerminatorDistinction, false)
    assertEquals(Pattern(Capture(LineBegin), flagSet0).needsLineTerminatorDistinction, true)
    assertEquals(Pattern(Capture(Dot), flagSet0).needsLineTerminatorDistinction, false)
    assertEquals(Pattern(NamedCapture("foo", LineBegin), flagSet0).needsLineTerminatorDistinction, true)
    assertEquals(Pattern(NamedCapture("foo", Dot), flagSet0).needsLineTerminatorDistinction, false)
    assertEquals(Pattern(Group(LineBegin), flagSet0).needsLineTerminatorDistinction, true)
    assertEquals(Pattern(Group(Dot), flagSet0).needsLineTerminatorDistinction, false)
    assertEquals(Pattern(Star(false, LineBegin), flagSet0).needsLineTerminatorDistinction, true)
    assertEquals(Pattern(Star(false, Dot), flagSet0).needsLineTerminatorDistinction, false)
    assertEquals(Pattern(Plus(false, LineBegin), flagSet0).needsLineTerminatorDistinction, true)
    assertEquals(Pattern(Plus(false, Dot), flagSet0).needsLineTerminatorDistinction, false)
    assertEquals(Pattern(Question(false, LineBegin), flagSet0).needsLineTerminatorDistinction, true)
    assertEquals(Pattern(Question(false, Dot), flagSet0).needsLineTerminatorDistinction, false)
    assertEquals(Pattern(Repeat(false, 2, None, LineBegin), flagSet0).needsLineTerminatorDistinction, true)
    assertEquals(Pattern(Repeat(false, 2, None, Dot), flagSet0).needsLineTerminatorDistinction, false)
    assertEquals(Pattern(LookAhead(false, LineBegin), flagSet0).needsLineTerminatorDistinction, true)
    assertEquals(Pattern(LookAhead(false, Dot), flagSet0).needsLineTerminatorDistinction, false)
    assertEquals(Pattern(LookBehind(false, LineBegin), flagSet0).needsLineTerminatorDistinction, true)
    assertEquals(Pattern(LookBehind(false, Dot), flagSet0).needsLineTerminatorDistinction, false)
    assertEquals(Pattern(LineBegin, flagSet0).needsLineTerminatorDistinction, true)
    assertEquals(Pattern(LineEnd, flagSet0).needsLineTerminatorDistinction, true)
    assertEquals(Pattern(WordBoundary(true), flagSet0).needsLineTerminatorDistinction, false)
    assertEquals(Pattern(WordBoundary(false), flagSet0).needsLineTerminatorDistinction, false)
    assertEquals(Pattern(Dot, flagSet0).needsLineTerminatorDistinction, false)
    assertEquals(Pattern(LineBegin, flagSet1).needsLineTerminatorDistinction, false)
  }

  test("Pattern#needsWordDistinction") {
    val flagSet = FlagSet(false, false, false, false, false, false)
    assertEquals(Pattern(Disjunction(Seq(Dot, WordBoundary(false))), flagSet).needsWordDistinction, true)
    assertEquals(Pattern(Disjunction(Seq(WordBoundary(false), Dot)), flagSet).needsWordDistinction, true)
    assertEquals(Pattern(Disjunction(Seq(Dot, Dot)), flagSet).needsWordDistinction, false)
    assertEquals(Pattern(Sequence(Seq(Dot, WordBoundary(false))), flagSet).needsWordDistinction, true)
    assertEquals(Pattern(Sequence(Seq(WordBoundary(false), Dot)), flagSet).needsWordDistinction, true)
    assertEquals(Pattern(Sequence(Seq(Dot, Dot)), flagSet).needsWordDistinction, false)
    assertEquals(Pattern(Capture(WordBoundary(false)), flagSet).needsWordDistinction, true)
    assertEquals(Pattern(Capture(Dot), flagSet).needsWordDistinction, false)
    assertEquals(Pattern(NamedCapture("foo", WordBoundary(false)), flagSet).needsWordDistinction, true)
    assertEquals(Pattern(NamedCapture("foo", Dot), flagSet).needsWordDistinction, false)
    assertEquals(Pattern(Group(WordBoundary(false)), flagSet).needsWordDistinction, true)
    assertEquals(Pattern(Group(Dot), flagSet).needsWordDistinction, false)
    assertEquals(Pattern(Star(false, WordBoundary(false)), flagSet).needsWordDistinction, true)
    assertEquals(Pattern(Star(false, Dot), flagSet).needsWordDistinction, false)
    assertEquals(Pattern(Plus(false, WordBoundary(false)), flagSet).needsWordDistinction, true)
    assertEquals(Pattern(Plus(false, Dot), flagSet).needsWordDistinction, false)
    assertEquals(Pattern(Question(false, WordBoundary(false)), flagSet).needsWordDistinction, true)
    assertEquals(Pattern(Question(false, Dot), flagSet).needsWordDistinction, false)
    assertEquals(Pattern(Repeat(false, 2, None, WordBoundary(false)), flagSet).needsWordDistinction, true)
    assertEquals(Pattern(Repeat(false, 2, None, Dot), flagSet).needsWordDistinction, false)
    assertEquals(Pattern(LookAhead(false, WordBoundary(false)), flagSet).needsWordDistinction, true)
    assertEquals(Pattern(LookAhead(false, Dot), flagSet).needsWordDistinction, false)
    assertEquals(Pattern(LookBehind(false, WordBoundary(false)), flagSet).needsWordDistinction, true)
    assertEquals(Pattern(LookBehind(false, Dot), flagSet).needsWordDistinction, false)
    assertEquals(Pattern(WordBoundary(true), flagSet).needsWordDistinction, true)
    assertEquals(Pattern(WordBoundary(false), flagSet).needsWordDistinction, true)
    assertEquals(Pattern(LineBegin, flagSet).needsWordDistinction, false)
    assertEquals(Pattern(LineEnd, flagSet).needsWordDistinction, false)
    assertEquals(Pattern(Dot, flagSet).needsWordDistinction, false)
  }
}
