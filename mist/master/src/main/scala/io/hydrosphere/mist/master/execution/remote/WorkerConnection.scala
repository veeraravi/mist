package io.hydrosphere.mist.master.execution.remote

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated, Timers}
import io.hydrosphere.mist.core.CommonData.{WorkerInitInfo, WorkerReady}

import scala.concurrent.Promise
import scala.concurrent.duration.{Duration, FiniteDuration}

/**
  * Direct connection to remote worker
  */
class WorkerConnection(
  id: String,
  initInfo: WorkerInitInfo,
  remote: ActorRef,
  ready: Promise[Unit],
  readyTimeout: FiniteDuration
) extends Actor with ActorLogging with Timers{

  override def preStart(): Unit = {
    context watch remote
    remote ! initInfo
    timers.startSingleTimer(s"worker-conn-$id", WorkerConnection.InitTimeout, readyTimeout)
  }

  override def receive: Receive = initialiazing

  private def initialiazing: Receive = {
    case WorkerReady(wId) if wId == id =>
      ready.success(())
      context become process

    case WorkerConnection.InitTimeout =>
      val msg = s"Worker $id was terminated during initialization"
      log.warning(msg)
      ready.failure(new RuntimeException(msg))
      context stop self
      println("STOPPED")

    case Terminated(_) =>
      val msg = s"Worker $id was terminated during initialization"
      log.warning(msg)
      ready.failure(new RuntimeException(msg))
      context stop self
  }

  private def process: Receive = {
    case Terminated(_) =>
      context stop self

    case other => remote forward other
  }

}

object WorkerConnection {
  case object InitTimeout

  def props(
    id: String,
    initInfo: WorkerInitInfo,
    remote: ActorRef,
    ready: Promise[Unit],
    readyTimeout: FiniteDuration
  ): Props = Props(classOf[WorkerConnection], id, initInfo, remote, ready, readyTimeout)

}