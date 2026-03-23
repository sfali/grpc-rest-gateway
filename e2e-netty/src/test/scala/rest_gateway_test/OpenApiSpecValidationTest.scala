package rest_gateway_test

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.yaml.snakeyaml.Yaml

import java.io.InputStream
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

class OpenApiSpecValidationTest extends AnyWordSpec with Matchers {

  "OpenAPI YAML specifications" should {
    "be generated and accessible from classpath" in {
      val specFiles = Seq("TestServiceA.yml", "TestServiceB.yml", "test/MultipleServices.yml")
      
      specFiles.foreach { fileName =>
        val resourcePath = s"specs/$fileName"
        val inputStream = getClass.getClassLoader.getResourceAsStream(resourcePath)
        
        withClue(s"File $resourcePath should exist in classpath: ") {
          inputStream should not be null
        }
        
        if (inputStream != null) {
          inputStream.close()
        }
      }
    }

    "have valid YAML structure" in {
      val yaml = new Yaml()
      val specFiles = Seq("TestServiceA.yml", "TestServiceB.yml", "test/MultipleServices.yml")
      
      specFiles.foreach { fileName =>
        val resourcePath = s"specs/$fileName"
        Using(getClass.getClassLoader.getResourceAsStream(resourcePath)) { inputStream =>
          withClue(s"$fileName should be parseable as YAML: ") {
            val result = Try(yaml.load[java.util.Map[String, Any]](inputStream))
            result.isSuccess shouldBe true
            result.get should not be null
          }
        }
      }
    }

    "contain required OpenAPI 3.1.0 fields" in {
      val yaml = new Yaml()
      val specFiles = Seq("TestServiceA.yml", "TestServiceB.yml")
      
      specFiles.foreach { fileName =>
        val resourcePath = s"specs/$fileName"
        Using(getClass.getClassLoader.getResourceAsStream(resourcePath)) { inputStream =>
          val spec = yaml.load[java.util.Map[String, Any]](inputStream)
          
          withClue(s"$fileName should have 'openapi' field: ") {
            spec.containsKey("openapi") shouldBe true
            spec.get("openapi") shouldBe "3.1.0"
          }
          
          withClue(s"$fileName should have 'info' section: ") {
            spec.containsKey("info") shouldBe true
            val info = spec.get("info").asInstanceOf[java.util.Map[String, Any]]
            info should not be null
            info.containsKey("version") shouldBe true
            info.containsKey("title") shouldBe true
            info.containsKey("description") shouldBe true
          }
          
          withClue(s"$fileName should have 'paths' section: ") {
            spec.containsKey("paths") shouldBe true
            val paths = spec.get("paths").asInstanceOf[java.util.Map[String, Any]]
            paths should not be null
            paths.isEmpty shouldBe false
          }
          
          withClue(s"$fileName should have 'components' section: ") {
            spec.containsKey("components") shouldBe true
            val components = spec.get("components").asInstanceOf[java.util.Map[String, Any]]
            components should not be null
            components.containsKey("schemas") shouldBe true
          }
        }
      }
    }

    "have correct version from proto-level configuration for TestServiceB" in {
      val yaml = new Yaml()
      val resourcePath = "specs/TestServiceB.yml"
      
      Using(getClass.getClassLoader.getResourceAsStream(resourcePath)) { inputStream =>
        val spec = yaml.load[java.util.Map[String, Any]](inputStream)
        val info = spec.get("info").asInstanceOf[java.util.Map[String, Any]]
        
        withClue("TestServiceB should have version 1.0.0 from proto file openapi_info option: ") {
          info.get("version") shouldBe "1.0.0"
        }
      }
    }

    "contain valid HTTP methods in paths" in {
      val yaml = new Yaml()
      val validMethods = Set("get", "post", "put", "delete", "patch", "options", "head")
      
      val resourcePath = "specs/TestServiceB.yml"
      Using(getClass.getClassLoader.getResourceAsStream(resourcePath)) { inputStream =>
        val spec = yaml.load[java.util.Map[String, Any]](inputStream)
        val paths = spec.get("paths").asInstanceOf[java.util.Map[String, Any]]
        
        paths.asScala.foreach { case (path, pathItem) =>
          val pathItemMap = pathItem.asInstanceOf[java.util.Map[String, Any]]
          val methods = pathItemMap.keySet().asScala.toSet
          
          withClue(s"Path $path should only contain valid HTTP methods: ") {
            methods.foreach { method =>
              validMethods should contain(method.toLowerCase)
            }
          }
        }
      }
    }

    "have responses section for each operation" in {
      val yaml = new Yaml()
      val resourcePath = "specs/TestServiceB.yml"
      
      Using(getClass.getClassLoader.getResourceAsStream(resourcePath)) { inputStream =>
        val spec = yaml.load[java.util.Map[String, Any]](inputStream)
        val paths = spec.get("paths").asInstanceOf[java.util.Map[String, Any]]
        
        paths.asScala.foreach { case (path, pathItem) =>
          val pathItemMap = pathItem.asInstanceOf[java.util.Map[String, Any]]
          
          pathItemMap.asScala.foreach { case (method, operation) =>
            if (Set("get", "post", "put", "delete", "patch").contains(method.toLowerCase)) {
              val operationMap = operation.asInstanceOf[java.util.Map[String, Any]]
              
              withClue(s"Operation $method $path should have 'responses' section: ") {
                operationMap.containsKey("responses") shouldBe true
                val responses = operationMap.get("responses").asInstanceOf[java.util.Map[String, Any]]
                responses should not be null
                responses.isEmpty shouldBe false
              }
            }
          }
        }
      }
    }

    "have correct status codes for TestServiceB operations" in {
      val yaml = new Yaml()
      val resourcePath = "specs/TestServiceB.yml"
      
      Using(getClass.getClassLoader.getResourceAsStream(resourcePath)) { inputStream =>
        val spec = yaml.load[java.util.Map[String, Any]](inputStream)
        val paths = spec.get("paths").asInstanceOf[java.util.Map[String, Any]]
        
        // Check GET /restgateway/test/testserviceb
        val getPath = paths.get("/restgateway/test/testserviceb").asInstanceOf[java.util.Map[String, Any]]
        val getOp = getPath.get("get").asInstanceOf[java.util.Map[String, Any]]
        val getResponses = getOp.get("responses").asInstanceOf[java.util.Map[String, Any]]
        
        withClue("GET operation should have 200 response: ") {
          getResponses.containsKey("200") shouldBe true
        }
        
        // Check PUT /restgateway/test/testserviceb/update
        val updatePath = paths.get("/restgateway/test/testserviceb/update").asInstanceOf[java.util.Map[String, Any]]
        val putOp = updatePath.get("put").asInstanceOf[java.util.Map[String, Any]]
        val putResponses = putOp.get("responses").asInstanceOf[java.util.Map[String, Any]]
        
        withClue("PUT update operation should have 204 response (from custom status annotation): ") {
          putResponses.containsKey("204") shouldBe true
        }
        
        withClue("PUT update operation should have 400 response: ") {
          putResponses.containsKey("400") shouldBe true
        }
        
        withClue("PUT update operation should have 404 response: ") {
          putResponses.containsKey("404") shouldBe true
        }
      }
    }

    "have schema components referenced in paths" in {
      val yaml = new Yaml()
      val resourcePath = "specs/TestServiceB.yml"
      
      Using(getClass.getClassLoader.getResourceAsStream(resourcePath)) { inputStream =>
        val spec = yaml.load[java.util.Map[String, Any]](inputStream)
        val components = spec.get("components").asInstanceOf[java.util.Map[String, Any]]
        val schemas = components.get("schemas").asInstanceOf[java.util.Map[String, Any]]
        
        withClue("TestServiceB should have TestRequestB schema: ") {
          schemas.containsKey("TestRequestB") shouldBe true
        }
        
        withClue("TestServiceB should have TestResponseB schema: ") {
          schemas.containsKey("TestResponseB") shouldBe true
        }
        
        // Verify schema structure
        val requestSchema = schemas.get("TestRequestB").asInstanceOf[java.util.Map[String, Any]]
        requestSchema.get("type") shouldBe "object"
        requestSchema.containsKey("properties") shouldBe true
        
        val responseSchema = schemas.get("TestResponseB").asInstanceOf[java.util.Map[String, Any]]
        responseSchema.get("type") shouldBe "object"
        responseSchema.containsKey("properties") shouldBe true
      }
    }

    "have tags section with service names" in {
      val yaml = new Yaml()
      val resourcePath = "specs/TestServiceB.yml"
      
      Using(getClass.getClassLoader.getResourceAsStream(resourcePath)) { inputStream =>
        val spec = yaml.load[java.util.Map[String, Any]](inputStream)
        
        withClue("Spec should have 'tags' section: ") {
          spec.containsKey("tags") shouldBe true
          val tags = spec.get("tags").asInstanceOf[java.util.List[Any]]
          tags should not be null
          tags.isEmpty shouldBe false
          
          val firstTag = tags.get(0).asInstanceOf[java.util.Map[String, Any]]
          firstTag.get("name") shouldBe "TestServiceB"
          firstTag.containsKey("description") shouldBe true
        }
      }
    }
  }
}
