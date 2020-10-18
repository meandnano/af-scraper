package providers

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model._
import akka.stream.testkit.scaladsl.TestSink
import akka.util.ByteString
import com.fasterxml.jackson.databind.node.ObjectNode
import config.NetworkDef
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.BeforeAndAfter
import org.scalatest.wordspec.AnyWordSpec
import persistence.{Deal, PointLocation, Store}

import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._

class DealsProviderTest extends AnyWordSpec with BeforeAndAfter {

  val store = new Store(
    internalId = 100500L,
    networkKey = "pennys",
    location = Option(PointLocation(72.932646, -41.9906897)),
    address = Option("Greenland Ice, Greenland")
  )

  val networkDef: NetworkDef = NetworkDef(
    title = "Penny store Inc,",
    storesLink = "http://pennys.one/stores",
    dealsLink = "http://pennys.one/deals?some=thing"
  )

  val theDeal = new Deal(
    store = store,
    title = "Fake deal",
    imgUrl = None,
    priceUnit = None,
    priceOriginal = None,
    priceDisc = None,
    dateStart = None,
    dateEnd = None,
  )

  val makeADeal: ObjectNode => Deal = (_: ObjectNode) => theDeal
  val fetchDelayer: () => FiniteDuration = () => 0.seconds

  def responseWithBody(body: String): HttpResponse = HttpResponse(entity =
    Strict(
      contentType = ContentType(MediaType.applicationWithOpenCharset("json"), HttpCharsets.`UTF-8`),
      data = ByteString(body)
    )
  )

  def mockRequestHandler(pages: Int, itemsPerPage: Int): RequestHandler = {
    val body =
      s"""{
         |"results": [${Seq.fill(itemsPerPage)("{}").mkString(",")}]
         |}""".stripMargin

    val handler = Mockito.mock(classOf[RequestHandler])

    when(handler.handle(any())).thenAnswer(new Answer[Future[HttpResponse]] {
      private var returned = 0

      override def answer(invocation: InvocationOnMock): Future[HttpResponse] = {
        returned += 1
        if (returned <= pages) {
          Future.successful(responseWithBody(body))
        } else {
          Future.successful(responseWithBody(""))
        }
      }
    })

    handler
  }

  "stream()" should {
    implicit val system: ActorSystem = ActorSystem()

    "make paged requests" when {
      "response is not empty" in {
        val handler: RequestHandler = mockRequestHandler(pages = 2, itemsPerPage = 3)
        new DealsProvider(store, networkDef, handler, fetchDelayer, makeADeal)
          .stream()
          .runWith(TestSink.probe)
          .request(7)
          .expectNextN(6)

        val reqMatcher = ArgumentCaptor.forClass(classOf[HttpRequest])

        verify(handler, times(3)).handle(reqMatcher.capture())

        val requests = reqMatcher.getAllValues.asScala
        assertResult(3)(requests.size)
        verifyPageRequest(requests(0), pageSize = DealsProvider.PAGE_SIZE, offset = 0)
        verifyPageRequest(requests(1), pageSize = DealsProvider.PAGE_SIZE, offset = 3)
        verifyPageRequest(requests(2), pageSize = DealsProvider.PAGE_SIZE, offset = 6)
      }
    }

    "parse all items in paged requests" when {
      "response in not empty" in {
        new DealsProvider(store, networkDef, mockRequestHandler(pages = 2, itemsPerPage = 3), fetchDelayer, makeADeal)
          .stream()
          .runWith(TestSink.probe)
          .request(7)
          .expectNextN(Seq.fill(6)(theDeal))
          .expectComplete()
      }
    }
  }

  def verifyPageRequest(request: HttpRequest, storeId: Long = 100500L, pageSize: Int, offset: Int): Unit = {
    def queryValue(key: String): Option[String] = request.uri.query().get(key)

    assertResult(Some(storeId.toString))(queryValue("q"))
    assertResult(Some(pageSize.toString))(queryValue("size"))
    assertResult(Some(offset.toString))(queryValue("offlineSize"))
    assertResult(Some("thing"))(queryValue("some"))
  }

}
