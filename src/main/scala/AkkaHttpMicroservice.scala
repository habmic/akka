import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.model.StatusCodes.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}

import java.io.IOException
import scala.concurrent.{ExecutionContext, Future}
import scala.math.*
//import io.opentelemetry.sdk.autoconfigure.*
//import io.opentelemetry.sdk.*
//import io.opentelemetry.exporter.otlp.trace.*
//import io.opentelemetry.sdk.trace.*
//import io.opentelemetry.api.trace.Tracer
//import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
//import io.opentelemetry.context.propagation.ContextPropagators
//import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
//import io.opentelemetry.sdk.trace.`export`.{SimpleSpanProcessor, SpanExporter}
//import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter
//import io.opentelemetry.sdk.resources.Resource
import java.util.function.BiFunction


enum IpApiResponseStatus {
  case Success, Fail
}


case class IpApiResponse(status: IpApiResponseStatus, message: Option[String], query: String, country: Option[String], city: Option[String], lat: Option[Double], lon: Option[Double])

case class IpInfo(query: String, country: Option[String], city: Option[String], lat: Option[Double], lon: Option[Double])

case class IpPairSummaryRequest(ip1: String, ip2: String)

case class IpPairSummary(distance: Option[Double], ip1Info: IpInfo, ip2Info: IpInfo)

object IpPairSummary {
  def apply(ip1Info: IpInfo, ip2Info: IpInfo): IpPairSummary = IpPairSummary(calculateDistance(ip1Info, ip2Info), ip1Info, ip2Info)

  private def calculateDistance(ip1Info: IpInfo, ip2Info: IpInfo): Option[Double] = {
    (ip1Info.lat, ip1Info.lon, ip2Info.lat, ip2Info.lon) match {
      case (Some(lat1), Some(lon1), Some(lat2), Some(lon2)) =>
        // see http://www.movable-type.co.uk/scripts/latlong.html
        val ??1 = toRadians(lat1)
        val ??2 = toRadians(lat2)
        val ???? = toRadians(lat2 - lat1)
        val ???? = toRadians(lon2 - lon1)
        val a = pow(sin(???? / 2), 2) + cos(??1) * cos(??2) * pow(sin(???? / 2), 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        Option(EarthRadius * c)
      case _ => None
    }
  }

  private val EarthRadius = 6371.0
}

trait Protocols extends ErrorAccumulatingCirceSupport {

  import io.circe.generic.semiauto._

  implicit val ipApiResponseStatusDecoder: Decoder[IpApiResponseStatus] = Decoder.decodeString.map(s => IpApiResponseStatus.valueOf(s.capitalize))
  implicit val ipApiResponseDecoder: Decoder[IpApiResponse] = deriveDecoder
  implicit val ipInfoDecoder: Decoder[IpInfo] = deriveDecoder
  implicit val ipInfoEncoder: Encoder[IpInfo] = deriveEncoder
  implicit val ipPairSummaryRequestDecoder: Decoder[IpPairSummaryRequest] = deriveDecoder
  implicit val ipPairSummaryRequestEncoder: Encoder[IpPairSummaryRequest] = deriveEncoder
  implicit val ipPairSummaryEncoder: Encoder[IpPairSummary] = deriveEncoder
  implicit val ipPairSummaryDecoder: Decoder[IpPairSummary] = deriveDecoder
}

//trait OpenTelemetryProvider {

  // Create Azure Monitor exporter and configure OpenTelemetry tracer to use this exporter
  // This should be done just once when application starts up
  //  var exporter: OtlpGrpcSpanExporter =
  //  OtlpGrpcSpanExporter.builder()
  //      .setEndpoint("http://localhost:14250")
  //      .build();
  //
  //val tracerProvider: SdkTracerProvider = SdkTracerProvider.builder()
  //    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
  //    .build();
  //
  //val openTelemetrySdk: OpenTelemetrySdk = OpenTelemetrySdk.builder()
  //    .setTracerProvider(tracerProvider)
  //    .buildAndRegisterGlobal();

  ////val tracer:Tracer = openTelemetrySdk.getTracer("Sample");
  //  // Set to the name of the service
  //  OTEL_SERVICE_NAME
  //
  //  // Set Authorization header - Authorization=c82fe3d7-8ca5-402a-a05a-9b71906cd579
  //  OTEL_EXPORTER_OTLP_HEADERS
  //
  //  // Set to https://otelcol.aspecto.io:4317
  //  OTEL_EXPORTER_OTLP_TRACES_ENDPOINT


//}
// otelBuilder.addSpanExporterCustomizer()

// private val sdkTracerProvider: SdkTracerProvider = SdkTracerProvider
//   .builder()
//   .addSpanProcessor(
//     SimpleSpanProcessor
//       .builder(OtlpGrpcSpanExporter.builder().build())
//       .build()
//   )
//   .build()

// val openTelemetry: OpenTelemetry = OpenTelemetrySdk
//   .builder()
//   .setTracerProvider(sdkTracerProvider)
//   .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
//   .buildAndRegisterGlobal()

// Runtime.getRuntime.addShutdownHook(new Thread(() => sdkTracerProvider.close()))


trait Service extends Protocols {
  implicit val system: ActorSystem

  implicit def executor: ExecutionContext

  def config: Config

  val logger
  : LoggingAdapter

  lazy val ipApiConnectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("services.ip-api.host"), config.getInt("services.ip-api.port"))

  // Please note that using `Source.single(request).via(pool).runWith(Sink.head)` is considered anti-pattern. It's here only for the simplicity.
  // See why and how to improve it here: https://github.com/theiterators/akka-http-microservice/issues/32
  def ipApiRequest(request: HttpRequest): Future[HttpResponse] = Source.single(request).via(ipApiConnectionFlow).runWith(Sink.head)

  def fetchIpInfo(ip: String): Future[String | IpInfo] = {
//    a.end();
    ipApiRequest(RequestBuilding.Get(s"/json/$ip")).flatMap { response =>
      response.status match {
        case OK =>
          Unmarshal(response.entity).to[IpApiResponse].map { ipApiResponse =>
            ipApiResponse.status match {
              case IpApiResponseStatus.Success => IpInfo(ipApiResponse.query, ipApiResponse.country, ipApiResponse.city, ipApiResponse.lat, ipApiResponse.lon)
              case IpApiResponseStatus.Fail => s"""ip-api request failed with message: ${ipApiResponse.message.getOrElse("")}"""
            }
          }
        case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"ip-api request failed with status code ${response.status} and entity $entity"
          logger.error(error)
          Future.failed(new IOException(error))
        }
      }
    }
  }

  val routes: Route = {
    logRequestResult("akka-http-microservice") {
      pathPrefix("ip") {
        (get & path(Segment)) { ip =>
          complete {
            fetchIpInfo(ip).map[ToResponseMarshallable] {
              case ipInfo: IpInfo => ipInfo
              case errorMessage: String => BadRequest -> errorMessage
            }
          }
        } ~
          (post & entity(as[IpPairSummaryRequest])) { ipPairSummaryRequest =>
            complete {
              val ip1InfoFuture = fetchIpInfo(ipPairSummaryRequest.ip1)
              val ip2InfoFuture = fetchIpInfo(ipPairSummaryRequest.ip2)
              ip1InfoFuture.zip(ip2InfoFuture).map[ToResponseMarshallable] {
                case (info1: IpInfo, info2: IpInfo) => IpPairSummary(info1, info2)
                case (errorMessage: String, _) => BadRequest -> errorMessage
                case (_, errorMessage: String) => BadRequest -> errorMessage
              }
            }
          }
      }
    }
  }
}

object AkkaHttpMicroservice extends App with Service {



  //  // Set to the name of the service
  //  OTEL_SERVICE_NAME
  //
  //  // Set Authorization header - Authorization=c82fe3d7-8ca5-402a-a05a-9b71906cd579
  //  OTEL_EXPORTER_OTLP_HEADERS
  //
  //  // Set to https://otelcol.aspecto.io:4317
  //  OTEL_EXPORTER_OTLP_TRACES_ENDPOINT

  //
  //  .addResourceCustomizer(new BiFunction[Resource,ConfigProperties, Resource] {
  //    override def apply(t: Resource, u: ConfigProperties): Resource = {
  //      Resource.create(new {
  //        ""
  //      })
  //    }
//  //  }
//  val otelBuilder: AutoConfiguredOpenTelemetrySdk = AutoConfiguredOpenTelemetrySdk
//    .builder().addSpanExporterCustomizer(new BiFunction[SpanExporter, ConfigProperties, SpanExporter] {
//    def apply(a: SpanExporter, b: ConfigProperties): SpanExporter = {
//      println(s"12345")
//      return JaegerGrpcSpanExporter.builder()
//        //        .setEndpoint("https://otelcol.aspecto.io:4317")//
//        .setEndpoint("http://localhost:14250")
//        .build();
//    }
//  })
//    .setResultAsGlobal(true)
//    .build()
//  val span = otelBuilder.getOpenTelemetrySdk().getTracer("test").spanBuilder("my span").startSpan();
//  span.end();



  //  val exporter = JaegerGrpcSpanExporter.builder()
  //    //        //        .setEndpoint("https://otelcol.aspecto.io:4317")//
  //    .setEndpoint("http://localhost:14250")
  //    .build();
  //  val sdkTracerProvider = SdkTracerProvider.builder.addSpanProcessor(SimpleSpanProcessor.create(exporter)).build
  //
  //  val sdk = OpenTelemetrySdk.builder.setTracerProvider(sdkTracerProvider).setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance)).build

  //  Runtime.getRuntime.addShutdownHook(new Thread(sdkTracerProvider.close))
  //  OpenTelemetrySdkAutoConfiguration.initialize(true)

  override implicit val system: ActorSystem = ActorSystem()
  override implicit val executor: ExecutionContext = system.dispatcher

  override val config = ConfigFactory.load()
  override val logger = Logging(system, "AkkaHttpMicroservice")

  Http().newServerAt(config.getString("http.interface"), config.getInt("http.port")).bindFlow(routes)


//  import io.opentelemetry.api._
//  val a = GlobalOpenTelemetry.getTracer("s").spanBuilder("good span").startSpan();
//  a.end();
//  import scala.collection.JavaConverters._
//  import scala.language.implicitConversions
//  val environmentVars = System.getenv().asScala
//  for ((k,v) <- environmentVars) println(s"key: $k, value: $v")
//
//  val properties = System.getProperties().asScala
//  for ((k,v) <- properties) println(s"key: $k, value: $v")
}
