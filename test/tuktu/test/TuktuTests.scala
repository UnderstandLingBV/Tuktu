package tuktu.test

import org.scalatest.Suites
import org.scalatest.SequentialNestedSuiteExecution
import tuktu.test.processor.tests.BaseProcessorTestSuite
import tuktu.test.processor.tests.BufferProcessorTestSuite
import tuktu.test.flow.tests.FlowTests
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.OneAppPerSuite
import play.api.libs.concurrent.Akka

class TuktuTests extends Suites(
    new BaseProcessorTestSuite,
    new BufferProcessorTestSuite,
    new FlowTests
) with SequentialNestedSuiteExecution with OneAppPerSuite