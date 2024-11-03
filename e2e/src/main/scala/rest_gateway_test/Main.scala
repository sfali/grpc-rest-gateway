package rest_gateway_test

import com.google.common.util.concurrent.ThreadFactoryBuilder
import rest_gateway_test.server.GrpcServer

import java.util.concurrent.{ExecutorService, Executors}

object Main {

  def main(args: Array[String]): Unit = {
    val gatewayServer = GrpcServer.startGateWayServer(gatewayServerExecutorSvc)
    val grpcServer = GrpcServer.startGrpcServer(grpcServerExecutorSvc)

    sys.addShutdownHook {
      println("Stopping gateway server!")
      gatewayServer.stop()
      println("Gateway server stopped!")

      println("Stopping gRPC server!")
      grpcServer.stop()
      println("gRPC server stopped!")
    }
  }

  private def grpcServerExecutorSvc: ExecutorService = executorSvc("grpc-server-%d")

  private def gatewayServerExecutorSvc: ExecutorService = executorSvc("grpc-rest-gateway-%d")

  private def executorSvc(format: String): ExecutorService =
    Executors.newFixedThreadPool(32, new ThreadFactoryBuilder().setNameFormat(format).build)
}
