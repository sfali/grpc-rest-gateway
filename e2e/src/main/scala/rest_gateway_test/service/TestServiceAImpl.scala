package rest_gateway_test.service

import com.google.rpc.{Code, Status}
import io.grpc.protobuf.StatusProto
import org.slf4j.LoggerFactory
import rest_gateway_test.api.model.common.{TestRequestA, TestResponseA}
import rest_gateway_test.api.scala_api.TestServiceA.TestServiceAGrpc

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class TestServiceAImpl extends TestServiceAGrpc.TestServiceA {

  private val logger = LoggerFactory.getLogger(classOf[TestServiceAGrpc.TestServiceA])
  private val cache = mutable.Map.empty[Long, TestResponseA]

  override def getRequest(request: TestRequestA): Future[TestResponseA] = {
    val requestId = request.requestId
    Try(validateRequestId(requestId)) match {
      case Failure(ex)              => Future.failed(ex)
      case Success(value) if !value =>
        // this should never happen
        Future.failed(
          StatusProto.toStatusRuntimeException(
            Status
              .newBuilder()
              .setCode(Code.INTERNAL_VALUE)
              .setMessage("Unknown state")
              .build()
          )
        )
      case Success(_) =>
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
  }

  override def process(request: TestRequestA): Future[TestResponseA] = {
    val requestId = request.requestId
    Try(validateRequestId(requestId)) match {
      case Failure(ex)              => Future.failed(ex)
      case Success(value) if !value =>
        // this should never happen
        Future.failed(
          StatusProto.toStatusRuntimeException(
            Status
              .newBuilder()
              .setCode(Code.INTERNAL_VALUE)
              .setMessage("Unknown state")
              .build()
          )
        )
      case Success(_) =>
        logger.info("Serving process request: {}", requestId)
        val response =
          cache.get(requestId) match {
            case Some(response) => response
            case None =>
              TestResponseA(requestId = requestId, color = requestId.getColor, transactionId = requestId.toProtoUUID)
          }
        Future.successful(response)
    }
  }

  private def validateRequestId(requestId: Long) =
    if (requestId <= 0) {
      throw StatusProto.toStatusRuntimeException(
        Status
          .newBuilder()
          .setCode(Code.INVALID_ARGUMENT_VALUE)
          .setMessage(s"RequestId {$requestId} is not valid")
          .build()
      )
    } else true
}
