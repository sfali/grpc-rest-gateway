package com.improving
package grpc_rest_gateway
package runtime
package handlers

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.prop.Tables.Table
import org.apache.pekko
import pekko.http.scaladsl.model.*
import pekko.http.scaladsl.testkit.ScalatestRouteTest
import pekko.http.scaladsl.server.*
import pekko.http.scaladsl.server.Directives.*

import scala.concurrent.ExecutionContext.Implicits.global

class SwaggerHandlerTest extends AnyFlatSpec with Matchers with ScalatestRouteTest with TableDrivenPropertyChecks {

  // Simple mock implementation for testing
  class MockGatewayHandler(val specName: String) extends GrpcGatewayHandler {
    override val specificationName: String = specName
    override val route: Route = complete("mock response")
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
      val swaggerHandler = SwaggerHandler(specsDirectory, services)

      swaggerHandler should not be null
    }
  }

  it should "handle empty services list with different specs directory configurations" in {
    forAll(specsDirectoryConfigs) { specsDirectory =>
      val services = Seq.empty[MockGatewayHandler]
      val swaggerHandler = SwaggerHandler(specsDirectory, services)

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
      val swaggerHandler = SwaggerHandler(specsDirectory, services)

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
      val swaggerHandler = SwaggerHandler(specsDirectory, services)

      swaggerHandler should not be null
    }
  }

  "SwaggerHandler routes" should "redirect root to docs landing page with different specs directory configurations" in {
    forAll(specsDirectoryConfigs) { specsDirectory =>
      val services = Seq(new MockGatewayHandler("test-service"))
      val swaggerHandler = SwaggerHandler(specsDirectory, services)

      Get("/") ~> swaggerHandler.route ~> check {
        status shouldBe StatusCodes.PermanentRedirect
        header("location").get.value() shouldBe "/docs/index.html"
      }
    }
  }

  it should "redirect docs prefix to docs landing page with different specs directory configurations" in {
    forAll(specsDirectoryConfigs) { specsDirectory =>
      val services = Seq(new MockGatewayHandler("test-service"))
      val swaggerHandler = SwaggerHandler(specsDirectory, services)

      Get("/docs") ~> swaggerHandler.route ~> check {
        status shouldBe StatusCodes.PermanentRedirect
        header("location").get.value() shouldBe "/docs/index.html"
      }
    }
  }

  it should "return swagger index page for docs landing page with different specs directory configurations" in {
    forAll(specsDirectoryConfigs) { specsDirectory =>
      val services = Seq(new MockGatewayHandler("test-service"), new MockGatewayHandler("another-service"))
      val swaggerHandler = SwaggerHandler(specsDirectory, services)

      Get("/docs/index.html") ~> swaggerHandler.route ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`text/html(UTF-8)`

        val content = responseAs[String]
        content should include("test-service")
        content should include("another-service")
        content should include("Swagger UI")
      }
    }
  }

  it should "return 404 for non-existent swagger resources with different specs directory configurations" in {
    forAll(specsDirectoryConfigs) { specsDirectory =>
      val services = Seq(new MockGatewayHandler("test-service"))
      val swaggerHandler = SwaggerHandler(specsDirectory, services)

      Get("/docs/non-existent.css") ~> swaggerHandler.route ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  it should "return 404 for non-existent spec files with different specs directory configurations" in {
    forAll(specsDirectoryConfigs) { specsDirectory =>
      val services = Seq(new MockGatewayHandler("test-service"))
      val swaggerHandler = SwaggerHandler(specsDirectory, services)

      val specPath = if (specsDirectory.isEmpty) "/non-existent.yml" else s"/$specsDirectory/non-existent.yml"
      Get(specPath) ~> swaggerHandler.route ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  it should "pass through unsupported paths with different specs directory configurations" in {
    forAll(specsDirectoryConfigs) { specsDirectory =>
      val services = Seq(new MockGatewayHandler("test-service"))
      val swaggerHandler = SwaggerHandler(specsDirectory, services)

      Get("/unsupported/path") ~> swaggerHandler.route ~> check {
        handled shouldBe false
      }
    }
  }

  "SwaggerHandler custom specs directory verification" should "return swagger index page with correct service URLs for different specs directory configurations" in {
    forAll(specsDirectoryConfigs) { specsDirectory =>
      val services = Seq(new MockGatewayHandler("test-service"), new MockGatewayHandler("another-service"))
      val swaggerHandler = SwaggerHandler(specsDirectory, services)

      Get("/docs/index.html") ~> swaggerHandler.route ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`text/html(UTF-8)`

        val content = responseAs[String]
        content should include("test-service")
        content should include("another-service")
        content should include("Swagger UI")

        // Verify that service URLs use the correct specs directory
        val expectedPath = if (specsDirectory.isEmpty) "" else s"/$specsDirectory"
        content should include(s"url: '$expectedPath/test-service.yml'")
        content should include(s"url: '$expectedPath/another-service.yml'")
      }
    }
  }

  it should "serve spec files from different specs directory configurations" in {
    forAll(specsDirectoryConfigs) { specsDirectory =>
      val services = Seq(new MockGatewayHandler("test-service"))
      val swaggerHandler = SwaggerHandler(specsDirectory, services)

      // This should route through the specs directory
      val specPath = if (specsDirectory.isEmpty) "/test-service.yml" else s"/$specsDirectory/test-service.yml"
      Get(specPath) ~> swaggerHandler.route ~> check {
        // Should return 404 since the file doesn't exist, but the route should be handled
        status shouldBe StatusCodes.NotFound
        handled shouldBe true
      }
    }
  }

  it should "return 404 for non-existent spec files with different specs directory configurations" in {
    forAll(specsDirectoryConfigs) { specsDirectory =>
      val services = Seq(new MockGatewayHandler("test-service"))
      val swaggerHandler = SwaggerHandler(specsDirectory, services)

      val specPath = if (specsDirectory.isEmpty) "/non-existent.yml" else s"/$specsDirectory/non-existent.yml"
      Get(specPath) ~> swaggerHandler.route ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }
}
