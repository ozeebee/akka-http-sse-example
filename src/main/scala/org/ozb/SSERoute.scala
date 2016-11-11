package org.ozb

import akka.NotUsed
import akka.pattern.ask
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.Directives._
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import akka.stream.scaladsl.Source
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.Timeout
import de.heikoseeberger.akkasse.ServerSentEvent
import de.heikoseeberger.akkasse.EventStreamMarshalling._
import org.ozb.SSEPublisher.{Event, GetClientsCount, RegisterClient}

import scala.collection.mutable
import scala.concurrent.duration._

class SSEClient[T] extends Actor with ActorPublisher[T] with ActorLogging {
	val queue = mutable.Queue[T]()

	def receive: Receive = {
		case p: Event[T] =>
			log.info("got Publish : " + p)
			queue.enqueue(p.data)
			publish()
		case Request(n) => publish()
		case Cancel =>
			log.info("Cancel received")
			context.stop(self)
	}

	private def publish(): Unit = {
		log.info(s"publish totalDemand=$totalDemand queue.size=${queue.size}")
		while (totalDemand > 0 && queue.nonEmpty) {
			log.info("publishing")
			onNext(queue.dequeue())
		}
	}
}

object SSEPublisher {
	case class Event[T](data: T)
	case class RegisterClient(client: ActorRef)
	case object GetClientsCount
}

class SSEPublisher[T] extends Actor with ActorLogging {
	val clients = mutable.Set.empty[ActorRef]

	def receive: Receive = {
		case RegisterClient(client) =>
			log.info("registering new SSEClient: " + client)
			context.watch(client)
			clients += client
		case evt: Event[T] =>
			log.info(s"dispatching Event [$evt] to ${clients.size} clients")
			clients.foreach(_ ! evt)
		case GetClientsCount =>
			sender() ! clients.size
		case Terminated(client) =>
			log.info("removing client " + client)
			clients -= client
	}
}

class SSERoute(system: ActorSystem) {
	import system.dispatcher // ExecutionContext for Futures
	val publisher = system.actorOf(Props[SSEPublisher[String]], name = "SSEPublisher")

	implicit val timeout = Timeout(30.seconds)

	def route = {
		def eventStream(): Source[ServerSentEvent, Any] = {
			Source.queue(100, OverflowStrategy.backpressure)
		}

		// in V1, we explicitly create the actor
		def sseClientRequestV1(): Source[ServerSentEvent, Any] = {
			val client = system.actorOf(Props[SSEClient[String]])
			publisher ! RegisterClient(client)
			Source.fromPublisher(ActorPublisher[String](client))
				.map(ServerSentEvent(_))
		}

		// in V2, we let the Source create the client actor for us
		def sseClientRequestV2(): Source[ServerSentEvent, Any] = {
			Source.actorPublisher[String](Props[SSEClient[String]])
				.map(ServerSentEvent(_))
				.mapMaterializedValue { client =>
					publisher ! RegisterClient(client)
				}
		}

		pathPrefix("sse") {
			path("tick") {
				get {
					complete {
						ToResponseMarshallable(
							Source.tick(2.seconds, 2.seconds, NotUsed)
								.map(_ => new java.util.Date().toString)
								.map(ServerSentEvent(_))
						)
					}
				}
			} ~
			path("queue") {
				get {
					complete {
						ToResponseMarshallable(
							eventStream()
						)
					}
				}
			} ~
			path("subscribe") {
				get {
					complete {
						ToResponseMarshallable(
							sseClientRequestV2()
								.keepAlive(5.second, () => ServerSentEvent.Heartbeat)
						)
					}
				}
			} ~
			path("publish" / Segment) { str =>
				get {
					publisher ! Event(str)
					complete("OK published: " + str)
				}
			} ~
			path("clients") {
				get {
					complete {
						(publisher ? GetClientsCount).map(_.toString)
					}
				}
			}
		}
	}
}
