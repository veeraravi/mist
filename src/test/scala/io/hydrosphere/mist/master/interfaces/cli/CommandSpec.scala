package io.hydrosphere.mist.master.interfaces.cli

import io.hydrosphere.mist.Constants
import org.scalatest.{Matchers, FunSpec}
import org.scalatest.prop.TableDrivenPropertyChecks._

class CommandSpec extends FunSpec with Matchers {

  import Constants.CLI.Commands

  val expected = Table(
    ("input", "cmd"),
    (Commands.listJobs, ActiveJobsCmd),
    (s"${Commands.stopWorker} name", StopWorkerCmd("name")),
    (Constants.CLI.Commands.listWorkers, ListWorkersCmd),
    (Constants.CLI.Commands.listRouters, ListRoutesCmd),
    (Constants.CLI.Commands.stopAllWorkers, StopAllWorkersCmd),
    (s"${Constants.CLI.Commands.stopJob} namespace job", StopJobCmd("namespace", "job")),
    (Constants.CLI.Commands.exit, Exit)
  )

  it("should parse input") {
    forAll(expected) { (input: String, cmd: Command) =>
      Command.parse(input) shouldBe Some(cmd)
    }
  }
}
