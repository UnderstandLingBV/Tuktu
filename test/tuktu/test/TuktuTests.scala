package tuktu.test

import org.scalatest.DoNotDiscover
import org.scalatest.SequentialNestedSuiteExecution
import org.scalatest.Suites
import org.scalatestplus.play.OneAppPerSuite
import org.scalatestplus.play.PlaySpec
import scala.collection.JavaConversions._
import com.typesafe.config.ConfigFactory

class TuktuTests extends Suites(
    new NoTests +:
    new NoTests +:
    ({
        // Get classes that contain the tests, ugly, but hey
        val testClasses = ConfigFactory.load("application.conf").getStringList("tuktu.tests").toSeq
        testClasses.map(tc => {
            val clazz = Class.forName(tc)
            clazz.getConstructor().newInstance().asInstanceOf[PlaySpec]
        })
    }): _*
) with SequentialNestedSuiteExecution with OneAppPerSuite

@DoNotDiscover
class NoTests extends PlaySpec {}