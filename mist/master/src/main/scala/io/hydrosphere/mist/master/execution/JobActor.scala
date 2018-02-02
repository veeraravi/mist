package io.hydrosphere.mist.master.execution

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated, Timers}
import io.hydrosphere.mist.core.CommonData._
import io.hydrosphere.mist.master.Messages.StatusMessages._
import io.hydrosphere.mist.master.execution.status.StatusReporter
import mist.api.data.JsLikeData

import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration

class JobActor(
  callback: ActorRef,
  req: RunJobRequest,
  promise: Promise[JsLikeData],
  report: StatusReporter
) extends Actor with ActorLogging with Timers {

  import JobActor._

  override def preStart(): Unit = {
    req.timeout match {
      case f: FiniteDuration =>
        timers.startSingleTimer(s"job-${req.id}", Event.Timeout, f)
      case _ =>
    }
  }

  override def receive: Receive = initial

  private def initial: Receive = {
    case Event.Cancel => cancelFinally("user request")
    case Event.Timeout => cancelFinally("timeout")
    case Event.GetStatus => sender() ! ExecStatus.Queued

    case Event.Perform(executor) =>
      executor ! req
      context watch executor
      context become starting(executor)
  }

  def cancelRemotely(cancelRespond: ActorRef, executor: ActorRef, reason: String): Unit = {
    log.info(s"Start cancelling ${req.id} remotely: $reason")
    executor ! CancelJobRequest(req.id)
    context become cancellingOnExecutor(Seq(cancelRespond), executor, reason)
  }

  private def starting(executor: ActorRef, workerRespond: Boolean = false): Receive = {
    case Event.GetStatus => sender() ! ExecStatus.Queued
    case Event.Cancel => cancelRemotely(sender(), executor, "user request")
    case Event.Timeout => cancelRemotely(sender(), executor, "timeout")

    case Terminated(_) =>
      if (workerRespond) onExecutorTermination()
      else {
        log.info(s"Returning job: {} back to initial state, worker didn't respond on start request", req.id)
        context become initial
      }

    case JobFileDownloading(id, time) =>
      report.report(JobFileDownloadingEvent(id, time))
      callback ! Event.Started(id)
      context become starting(executor, true)

    case JobStarted(id, time) =>
      report.report(StartedEvent(id, time))
      if (!workerRespond) {
        callback ! Event.Started(id)
      }
      context become completion(callback, executor)
  }

  private def completion(callback: ActorRef, executor: ActorRef): Receive = {
    case Event.GetStatus => sender() ! ExecStatus.Started
    case Event.Cancel => cancelRemotely(sender(), executor, "user request")
    case Event.Timeout => cancelRemotely(sender(), executor, "timeout")

    case Terminated(_) => onExecutorTermination()

    case JobSuccess(_, data) => completeSuccess(data)
    case JobFailure(_, err) => completeFailure(err)
  }

  private def cancellingOnExecutor(cancelRespond: Seq[ActorRef], executor: ActorRef, reason: String): Receive = {
    case Event.GetStatus => sender() ! ExecStatus.Cancelling
    case Event.Cancel => context become cancellingOnExecutor(cancelRespond :+ sender(), executor, reason)
    case Event.Timeout => log.info(s"Timeout exceeded for ${req.id} that is in cancelling process")

    case Terminated(_) =>
      val msg = akka.actor.Status.Failure(new IllegalStateException(s"Executor was terminated"))
      cancelRespond.foreach(_ ! msg)
      onExecutorTermination()

    case ev @ JobIsCancelled(_, _) =>
      cancelRespond.foreach(_ ! ev)
      cancelFinally(reason)

    case JobSuccess(_, data) =>
      val msg = akka.actor.Status.Failure(new IllegalStateException(s"Job ${req.id} was completed"))
      cancelRespond.foreach(_ ! msg)
      completeSuccess(data)

    case JobFailure(_, err) =>
      val msg = akka.actor.Status.Failure(new IllegalStateException(s"Job ${req.id} was completed"))
      cancelRespond.foreach(_ ! msg)
      completeFailure(err)
  }

  private def cancelFinally(
    reason: String,
    time: Long = System.currentTimeMillis()
  ): Unit = {
    promise.failure(new RuntimeException(s"Job was cancelled: $reason"))
    report.report(CanceledEvent(req.id, time))
    log.info(s"Job ${req.id} was cancelled: $reason")
    callback ! Event.Completed(req.id)
    self ! PoisonPill
  }

  private def onExecutorTermination(): Unit = completeFailure("Executor was terminated")

  private def completeSuccess(data: JsLikeData): Unit = {
    promise.success(data)
    report.report(FinishedEvent(req.id, System.currentTimeMillis(), data))
    log.info(s"Job ${req.id} completed successfully")
    callback ! Event.Completed(req.id)
    self ! PoisonPill
  }

  private def completeFailure(err: String): Unit = {
    promise.failure(new RuntimeException(err))
    report.report(FailedEvent(req.id, System.currentTimeMillis(), err))
    log.info(s"Job ${req.id} completed with error")
    callback ! Event.Completed(req.id)
    self ! PoisonPill
  }

}

object JobActor {

  sealed trait Event
  object Event {
    case object Cancel extends Event
    case object Timeout extends Event
    final case class Perform(ref: ActorRef) extends Event
    final case class Started(id: String) extends Event
    final case class Completed(id: String) extends Event
    case object GetStatus extends Event
  }

  def props(
    callback: ActorRef,
    req: RunJobRequest,
    promise: Promise[JsLikeData],
    reporter: StatusReporter
  ): Props = Props(classOf[JobActor], callback, req, promise, reporter)

}
