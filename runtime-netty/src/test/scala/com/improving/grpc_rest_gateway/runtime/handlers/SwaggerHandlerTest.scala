package com.improving
package grpc_rest_gateway
package runtime
package handlers

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.prop.Tables.Table

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SwaggerHandlerTest extends AnyFlatSpec with Matchers with TableDrivenPropertyChecks {

  // Simple mock implementation for testing
  class MockGatewayHandler(val specName: String) extends GrpcGatewayHandler(null) {
    override val specificationName: String = specName
    override protected def dispatchCall(
      method: io.netty.handler.codec.http.HttpMethod,
      uri: String,
      body: String
    ): scala.concurrent.Future[(Int, scalapb.GeneratedMessage)] = Future.successful((200, null))
    override val serviceName: String = specName
    override protected val httpMethodsToUrisMap: Map[String, Seq[String]] = Map.empty
  }

  // Parameterized test data for different specs directory configurations
  private val specsDirectoryConfigs = Table(
    "specsDirectory",
    "specs",
    "api-specs",
    "openapi/v1",
    "",
    "specs_v2",
    "docs/api/specs"
  )

  "SwaggerHandler" should "be created with different specs directory configurations" in {
    forAll(specsDirectoryConfigs) { specsDirectory =>
      val services = Seq(new MockGatewayHandler("test-service"))
      val swaggerHandler = new SwaggerHandler(specsDirectory, services)

      swaggerHandler should not be null
    }
  }

  it should "handle empty services list with different specs directory configurations" in {
    forAll(specsDirectoryConfigs) { specsDirectory =>
      val services = Seq.empty[MockGatewayHandler]
      val swaggerHandler = new SwaggerHandler(specsDirectory, services)

      swaggerHandler should not be null
    }
  }

  it should "handle multiple services with duplicate names with different specs directory configurations" in {
    forAll(specsDirectoryConfigs) { specsDirectory =>
      val services = Seq(
        new MockGatewayHandler("duplicate-service"),
        new MockGatewayHandler("unique-service"),
        new MockGatewayHandler("duplicate-service") // Duplicate
      )
      val swaggerHandler = new SwaggerHandler(specsDirectory, services)

      swaggerHandler should not be null
    }
  }

  it should "handle services with special characters in names with different specs directory configurations" in {
    forAll(specsDirectoryConfigs) { specsDirectory =>
      val services = Seq(
        new MockGatewayHandler("service-with-dashes"),
        new MockGatewayHandler("service_with_underscores"),
        new MockGatewayHandler("service.with.dots")
      )
      val swaggerHandler = new SwaggerHandler(specsDirectory, services)

      swaggerHandler should not be null
    }
  }

  "SwaggerHandler custom specs directory verification" should "generate correct URLs with different specs directory configurations" in {
    forAll(specsDirectoryConfigs) { specsDirectory =>
      val services = Seq(new MockGatewayHandler("test-service"))
      val swaggerHandler = new SwaggerHandler(specsDirectory, services)

      // Use reflection to access the private indexPage field
      val indexPageField = swaggerHandler.getClass.getDeclaredField("indexPage")
      indexPageField.setAccessible(true)
      val indexPage = indexPageField.get(swaggerHandler).asInstanceOf[String]

      // Should contain the correct URL with the specs directory
      val expectedPath = if (specsDirectory.isEmpty) "" else s"/$specsDirectory"
      indexPage should include(s"{url: '$expectedPath/test-service.yml', name: 'test-service'}")
    }
  }
}
