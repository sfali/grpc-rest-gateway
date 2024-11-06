package rest_gateway_test.service

import com.google.rpc.{Code, Status}
import io.grpc.protobuf.StatusProto
import org.slf4j.LoggerFactory
import rest_gateway_test.api.model.common.{GetMessageRequest, GetMessageResponse, TestRequestA, TestResponseA}
import rest_gateway_test.api.scala_api.TestServiceA.TestServiceAGrpc

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class TestServiceAImpl extends TestServiceAGrpc.TestServiceA {

  private val logger = LoggerFactory.getLogger(classOf[TestServiceAGrpc.TestServiceA])
  private val cache = mutable.Map.empty[Long, TestResponseA]

  override def getRequest(request: TestRequestA): Future[TestResponseA] =
    Try(validateRequestId(request.requestId)) match {
      case Failure(ex) => Future.failed(ex)
      case Success(requestId) =>
        logger.info("Serving get request: {}", requestId)
        cache.get(requestId) match {
          case Some(response) => Future.successful(response)
          case None =>
            Future.failed(
              StatusProto.toStatusRuntimeException(
                Status
                  .newBuilder()
                  .setCode(Code.NOT_FOUND_VALUE)
                  .setMessage(s"RequestId {$requestId} not found")
                  .build()
              )
            )
        }
    }

  override def getRequestWithParam(request: TestRequestA): Future[TestResponseA] = getRequest(request)

  override def process(request: TestRequestA): Future[TestResponseA] =
    Try(validateRequestId(request.requestId)) match {
      case Failure(ex) => Future.failed(ex)
      case Success(requestId) =>
        logger.info("Serving process request: {}", requestId)
        val response =
          cache.get(requestId) match {
            case Some(response) => response
            case None =>
              val r =
                TestResponseA(requestId = requestId, color = requestId.getColor, transactionId = requestId.toProtoUUID)
              cache.addOne(requestId -> r)
              r
          }
        Future.successful(response)
    }

  override def getRequestWithoutRest(request: TestRequestA): Future[TestResponseA] = getRequest(request)

  override def getMessageV1(request: GetMessageRequest): Future[GetMessageResponse] =
    Future.successful(request.toGetMessageResponse)

  override def getMessageV2(request: GetMessageRequest): Future[GetMessageResponse] =
    Future.successful(request.toGetMessageResponse)

  override def getMessageV3(request: GetMessageRequest): Future[GetMessageResponse] =
    Future.successful(request.toGetMessageResponse)

  private def validateRequestId(requestId: Long) =
    if (requestId <= 0) {
      throw StatusProto.toStatusRuntimeException(
        Status
          .newBuilder()
          .setCode(Code.INVALID_ARGUMENT_VALUE)
          .setMessage(s"RequestId {$requestId} is not valid")
          .build()
      )
    } else requestId
}
