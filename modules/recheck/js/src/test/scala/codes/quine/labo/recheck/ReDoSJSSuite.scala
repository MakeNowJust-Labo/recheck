package codes.quine.labo.recheck

class ReDoSJSSuite extends munit.FunSuite {
  test("ReDoSJS.check") {
    val result = ReDoSJS.check("^foo$", "", ())
    assertEquals(result.status, "safe")
  }
}