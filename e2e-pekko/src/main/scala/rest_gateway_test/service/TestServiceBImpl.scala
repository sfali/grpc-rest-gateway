package rest_gateway_test
package service

import com.google.protobuf.empty.Empty
import org.slf4j.LoggerFactory
import rest_gateway_test.api.model.{TestRequestB, TestResponseB}
import rest_gateway_test.api.scala_api.TestServiceB

import scala.concurrent.Future

class TestServiceBImpl extends TestServiceB {

  private val logger = LoggerFactory.getLogger(classOf[TestServiceB])

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

  override def update(request: TestRequestB): Future[Empty] = {
    val requestId = request.requestId
    logger.info("Serving update request: {}", requestId)
    Future.successful(Empty())
  }
}
