package io

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future

trait RequestHandler {

  def handle(request: HttpRequest): Future[HttpResponse]

}

class RequestHandlerImpl()(implicit val actorSystem: ActorSystem) extends RequestHandler {
  private lazy val http = Http()

  override def handle(request: HttpRequest): Future[HttpResponse] = http.singleRequest(request)
}
