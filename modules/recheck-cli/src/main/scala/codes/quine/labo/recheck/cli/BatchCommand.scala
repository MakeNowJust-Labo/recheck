package codes.quine.labo.recheck.cli

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import scala.collection.mutable

import io.circe.Decoder
import io.circe.generic.semiauto._

import codes.quine.labo.recheck.ReDoS
import codes.quine.labo.recheck.cli.BatchCommand._
import codes.quine.labo.recheck.codec._
import codes.quine.labo.recheck.diagnostics.Diagnostics

/** `recheck batch` method types. */
object BatchCommand {

  /** `"check"` method parameter. */
  final case class CheckParams(source: String, flags: String, config: ConfigData)

  object CheckParams {
    implicit def decode: Decoder[CheckParams] = deriveDecoder
  }

  /** `"cancel"` method parameter. */
  final case class CancelParams(id: RPC.ID)

  object CancelParams {
    implicit def decode: Decoder[CancelParams] = deriveDecoder
  }

  /** A running execution token to cancel. */
  private[cli] final case class Token(source: String, flags: String, send: RPC.Send[Diagnostics], cancel: () => Unit)
}

/** `recheck batch` command implementation. */
class BatchCommand(threadSize: Int, io: RPC.IO = RPC.IO.stdio) {

  /** A thread pool used by checking. */
  val executor: ExecutorService = Executors.newFixedThreadPool(threadSize)

  /** A map from current running request IDs to their tokens. */
  val tokens: mutable.Map[RPC.ID, Token] = mutable.Map.empty

  /** `"check"` method implementation. */
  def handleCheck(id: RPC.ID, params: CheckParams, send: RPC.Send[Diagnostics]): Unit = {
    val config = synchronized {
      val (config, cancel) = params.config.instantiate()
      tokens.remove(id).foreach(doCancel)
      tokens.update(id, Token(params.source, params.flags, send, cancel))
      config
    }
    executor.execute(() => {
      val diagnostics = ReDoS.check(params.source, params.flags, config)
      synchronized {
        send(Right(diagnostics))
        tokens.remove(id)
        // When there is no running execution, it enforces GC.
        if (tokens.isEmpty) gc()
      }
    })
  }

  /** `"cancel"` method implementation. */
  def handleCancel(params: CancelParams): Unit = synchronized {
    tokens.remove(params.id).foreach(doCancel)
    // When there is no running execution, it enforces GC.
    if (tokens.isEmpty) gc()
  }

  /** Cancels the given token execution. */
  def doCancel(token: Token): Unit = {
    token.cancel()
    token.send(Right(Diagnostics.Unknown(token.source, token.flags, Diagnostics.ErrorKind.Cancel, None)))
  }

  /** Enforces GC. */
  def gc(): Unit = {
    System.gc()
    System.runFinalization()
  }

  def run(): Unit =
    try {
      RPC.run(io)(
        "check" -> RPC.RequestHandler(handleCheck),
        "cancel" -> RPC.NotificationHandler(handleCancel)
      )
    } finally {
      tokens.values.foreach(doCancel)
      tokens.clear()
      executor.shutdown()
      executor.awaitTermination(Long.MaxValue, TimeUnit.NANOSECONDS)
    }
}
