package rest_gateway_test.service

import org.slf4j.LoggerFactory
import rest_gateway_test.api.model.common.{TestRequestA, TestResponseA}
import rest_gateway_test.api.scala_api.TestServiceA.TestServiceAGrpc

import scala.collection.mutable
import scala.concurrent.Future

class TestServiceAImpl extends TestServiceAGrpc.TestServiceA {

  private val logger = LoggerFactory.getLogger(classOf[TestServiceAGrpc.TestServiceA])
  private val cache = mutable.Map.empty[Long, TestResponseA]

  override def getRequest(request: TestRequestA): Future[TestResponseA] = {
    val requestId = request.requestId
    logger.info("Serving get request: {}", requestId)
    cache.get(requestId) match {
      case Some(response) => Future.successful(response)
      case None           => Future.failed(new IllegalArgumentException(s"Invalid requestId: $requestId"))
    }
  }

  override def process(request: TestRequestA): Future[TestResponseA] = {
    val requestId = request.requestId
    logger.info("Serving process request: {}", requestId)
    val response =
      cache.get(requestId) match {
        case Some(response) => response
        case None =>
          TestResponseA(requestId = requestId, color = getRandomColor, transactionId = requestId.toProtoUUID)
      }
    Future.successful(response)
  }
}
