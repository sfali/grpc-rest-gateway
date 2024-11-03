package rest_gateway_test.api.scala_api.TestServiceB

import _root_.scalapb.GeneratedMessage
import _root_.scalapb.json4s.JsonFormat
import _root_.com.improving.grpc_rest_gateway.runtime.handlers._
import _root_.io.grpc._
import _root_.io.netty.handler.codec.http.{HttpMethod, QueryStringDecoder}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scalapb.json4s.JsonFormatException
import scala.util._

class TestServiceBGatewayHandler(channel: ManagedChannel)(implicit ec: ExecutionContext)
  extends GrpcGatewayHandler(channel)(ec) {
  override val name: String = "TestServiceB"
  private val stub = TestServiceBGrpc.stub(channel)
  
  override def supportsCall(method: HttpMethod, uri: String): Boolean = {
    val queryString = new QueryStringDecoder(uri)
    (method.name, queryString.path) match {
      case ("GET", "/restgateway/test/testserviceb") => true
      case ("POST", "/restgateway/test/testserviceb") => true
      case _ => false
    }
  }
  
  override def unaryCall(method: HttpMethod, uri: String, body: String): Future[GeneratedMessage] = {
    val queryString = new QueryStringDecoder(uri)
    (method.name, queryString.path) match {
      case ("GET", "/restgateway/test/testserviceb") => 
        val input = Try {
          val requestId = 
            queryString.parameters().get("requestId").asScala.head.toLong
          rest_gateway_test.api.model.common.TestRequestB(requestId = requestId)
        }
        Future.fromTry(input).flatMap(stub.getRequest)
      case ("POST", "/restgateway/test/testserviceb") => 
      for {
        msg <- Future.fromTry(Try(JsonFormat.fromJsonString[rest_gateway_test.api.model.common.TestRequestB](body)).recoverWith(jsonException2GatewayExceptionPF))
        res <- stub.process(msg)
      } yield res
      case (methodName, path) => 
        Future.failed(InvalidArgument(s"No route defined for $methodName($path)"))
    }
  }
}
