package org.ozb

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import org.slf4j.LoggerFactory

object SSEServer extends App {
	lazy val log = LoggerFactory.getLogger(this.getClass)

	implicit val system = ActorSystem("wafe-server")
	implicit val materializer = ActorMaterializer()
	implicit val ec = system.dispatcher

	val httpInterface = "0.0.0.0"
	val httpPort = 9000

	Http().bindAndHandle(new SSERoute(system).route, interface = httpInterface, port = httpPort)
	println(s"Server listening at [$httpInterface:$httpPort]...")
}