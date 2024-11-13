package rest_gateway_test.service

import org.slf4j.LoggerFactory
import rest_gateway_test.api.model.{TestRequestB, TestResponseB}
import rest_gateway_test.api.scala_api.TestServiceBGrpc

import scala.concurrent.Future

class TestServiceBImpl extends TestServiceBGrpc.TestServiceB {

  private val logger = LoggerFactory.getLogger(classOf[TestServiceBGrpc.TestServiceB])

  override def getRequest(request: TestRequestB): Future[TestResponseB] = {
    val requestId = request.requestId
    logger.info("Serving get request: {}", requestId)
    Future.successful(TestResponseB(success = true, requestId = requestId, result = s"RequestId: $requestId"))
  }

  override def process(request: TestRequestB): Future[TestResponseB] = {
    val requestId = request.requestId
    logger.info("Serving process request: {}", requestId)
    Future.successful(TestResponseB(success = true, requestId = requestId, result = s"RequestId: $requestId"))
  }
}
