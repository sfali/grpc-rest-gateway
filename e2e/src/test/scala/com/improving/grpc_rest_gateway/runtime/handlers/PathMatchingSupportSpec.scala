package com.improving
package grpc_rest_gateway
package runtime
package handlers

import io.netty.handler.codec.http.HttpMethod
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PathMatchingSupportSpec extends AnyWordSpec with Matchers with PathMatchingSupport {
  override protected val httpMethodsToUrisMap: Map[String, Seq[String]] =
    Map(
      "GET" -> Seq(
        "/v1/messages",
        "/v1/messages/{message_id}",
        "/v1/users/{user_id}",
        "/v1/messages/{message_id}/sub/{sub.subfield}",
        "/v1/messages/{message_id}/users/{user_id}"
      ),
      "POST" -> Seq("/v1/messages")
    )

  "PathMatchingSupport" should {

    "ignore when http method is not configured" in {
      supportsCall(new HttpMethod("custom"), "/v1/messages") shouldBe false
    }

    "ignore when path is not configured" in {
      supportsCall(HttpMethod.POST, "/v1/messages/1") shouldBe false
    }

    "ignore when path has parameters but not configured" in {
      supportsCall(HttpMethod.GET, "/v1/messages/1/users") shouldBe false
    }

    "match simple path" in {
      supportsCall(HttpMethod.POST, "/v1/messages") shouldBe true
      supportsCall(HttpMethod.GET, "/v1/messages") shouldBe true
    }

    "match path with parameters" in {
      supportsCall(HttpMethod.GET, "/v1/messages/1") shouldBe true
      supportsCall(HttpMethod.GET, "/v1/users/1") shouldBe true
    }

    "replace path parameters" in {
      replacePathParameters(
        "/v1/messages/{message_id}/users/{user_id}",
        "/v1/messages/1/users/2"
      ) shouldBe "/v1/messages/1/users/2"
    }
  }
}
