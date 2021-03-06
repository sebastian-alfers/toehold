/*    _             _           _     _                            *\
**   | |_ ___   ___| |__   ___ | | __| |   License: MIT  (2014)    **
**   | __/ _ \ / _ \ '_ \ / _ \| |/ _` |                           **
**   | || (_) |  __/ | | | (_) | | (_| |                           **
\*    \__\___/ \___|_| |_|\___/|_|\__,_|                           */

package scenario

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import bootstrap._
import io.gatling.core.scenario.AtOnceInjection

class GenDataSimulation extends Simulation {
  val scn = scenario("Generate data").repeat(100) {
    exec(
      http("Generate 100 lines")
        .get("http://localhost:9000/gendata.php?lines=100")
        .check(
          GenDataHttpCheck.check
        ))
  }

  setUp(scn.inject(AtOnceInjection(40)))

}
