package io.hydrosphere.mist.master.execution

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import io.hydrosphere.mist.core.CommonData.{Action, JobParams, RunJobRequest}
import io.hydrosphere.mist.master.TestUtils
import io.hydrosphere.mist.master.execution.status.StatusReporter
import io.hydrosphere.mist.utils.akka.ActorF
import mist.api.data.JsLikeNumber
import org.scalatest._

import scala.concurrent._
import scala.concurrent.duration._

class ContextFrontendSpec extends TestKit(ActorSystem("ctx-frontend-spec"))
  with FunSpecLike
  with Matchers
  with TestUtils {

  it("should execute jobs") {
    val executor = TestProbe()
    val job = TestProbe()

    val props = ContextFrontend.props(
      name = "name",
      status = StatusReporter.NOOP,
      executorStarter = (_, _) => Future.successful(executor.ref),
      jobFactory = ActorF.static(job.ref)
    )
    val frontend = TestActorRef[ContextFrontend](props)
    frontend ! ContextFrontend.Event.UpdateContext(TestUtils.FooContext)

    val probe = TestProbe()
    probe.send(frontend, RunJobRequest("id", JobParams("path", "MyClass", Map.empty, Action.Execute)))

    probe.expectMsgPF(){
      case info: ExecutionInfo =>
        info.request.id shouldBe "id"
        info.promise.future
    }

    job.expectMsgType[JobActor.Event.Perform]
  }

//  it("should queue jobs") {
//    val executor = TestProbe()
//
//    val props = ContextFrontend.props(
//      name = "name",
//      status = StatusReporter.NOOP,
//      executorStarter = (_, _) => Future.successful(executor.ref)
//    )
//  }

}