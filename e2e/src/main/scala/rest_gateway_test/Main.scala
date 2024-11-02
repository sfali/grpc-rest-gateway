package rest_gateway_test

import com.google.common.util.concurrent.ThreadFactoryBuilder
import rest_gateway_test.server.GrpcServer

import java.util.concurrent.{ExecutorService, Executors}

object Main {

  def main(args: Array[String]): Unit = {
    GrpcServer.startGateWayServer(gatewayServerExecutorSvc)
    GrpcServer.startGrpcServer(grpcServerExecutorSvc)
  }

  private def grpcServerExecutorSvc: ExecutorService = executorSvc("grpc-server-%d")

  private def gatewayServerExecutorSvc: ExecutorService = executorSvc("grpc-rest-gateway-%d")

  private def executorSvc(format: String): ExecutorService =
    Executors.newFixedThreadPool(32, new ThreadFactoryBuilder().setNameFormat(format).build)
}
