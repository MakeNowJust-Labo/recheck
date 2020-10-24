package codes.quine.labo.redos.util

import java.util.concurrent.TimeoutException

import scala.concurrent.duration._

import Timeout._

class TimeoutSuite extends munit.FunSuite {
  test("Timeout.from") {
    assertEquals(Timeout.from(Duration.Inf), NoTimeout)
    assert(Timeout.from(1.second).isInstanceOf[DeadlineTimeout])
    intercept[IllegalArgumentException](Timeout.from(Duration.MinusInf))
  }

  test("Timeout#checkTimeout") {
    interceptMessage[TimeoutException]("foo") {
      DeadlineTimeout(-1.second.fromNow).checkTimeout("foo")
    }
    assertEquals(DeadlineTimeout(1.second.fromNow).checkTimeout("foo"), ())
    assertEquals(NoTimeout.checkTimeout("foo"), ())
  }

  test("Timeout#checkTimeoutWith") {
    interceptMessage[TimeoutException]("foo") {
      DeadlineTimeout(-1.second.fromNow).checkTimeoutWith("foo")(1)
    }
    assertEquals(DeadlineTimeout(1.second.fromNow).checkTimeoutWith("foo")(1), 1)
    assertEquals(NoTimeout.checkTimeoutWith("foo")(1), 1)
  }
}
