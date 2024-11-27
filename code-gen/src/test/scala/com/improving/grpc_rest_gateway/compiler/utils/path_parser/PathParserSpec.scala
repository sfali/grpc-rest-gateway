package com.improving
package grpc_rest_gateway
package compiler
package utils
package path_parser

import com.google.api.HttpRule.PatternCase.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PathParserSpec extends AnyWordSpec with Matchers {

  private lazy val rawValues = Seq(
    ("/restgateway/test/testservicea", GET, "", "GetRequest"),
    (
      "/restgateway/test/testservicea/{request_id}",
      GET,
      "",
      "GetRequestWithParam"
    ),
    ("/restgateway/test/testservicea", POST, "*", "Process"),
    ("/v1/test/messages/{message_id}", GET, "", "GetMessageV1"),
    ("/v1/test/users/{user_id}", GET, "", "GetMessageV2"),
    ("/v1/test/users/{user_id}/messages/{message_id}", GET, "", "GetMessageV2"),
    (
      "/v1/test/messages/{message_id}/users/{user_id}",
      GET,
      "",
      "GetMessageV3"
    ),
    (
      "/v1/test/users/{user_id}/messages/{message_id}",
      POST,
      "sub",
      "PostMessage"
    ),
    (
      "/v1/test/users/{user_id}/messages/{message_id}",
      PUT,
      "sub",
      "PutMessage"
    ),
    ("/v1/test/messages", GET, "", "GetMessageV4"),
    ("/v1/test/array", GET, "", "GetMessageV5")
  )

  private lazy val expectedTree = {
    /*
       Covers following paths
        RawPath(/restgateway/test/testservicea,List(MethodInfo(GET,,GetRequest), MethodInfo(POST,*,Process)))
        RawPath(/restgateway/test/testservicea/{request_id},List(MethodInfo(GET,,GetRequestWithParam)))
     */
    val node1 =
      TreeNode(path = "{request_id}", methodInfos = MethodInfo(GET, "", "GetRequestWithParam") :: Nil)
    val node2 =
      TreeNode(
        path = "testservicea",
        methodInfos = MethodInfo(GET, "", "GetRequest") :: MethodInfo(POST, "*", "Process") :: Nil,
        children = Map(node1.path -> node1)
      )

    val restgateway_test_Node = TreeNode(path = "test", children = Map(node2.path -> node2))
    val restgatewayNode =
      TreeNode(path = "restgateway", children = Map(restgateway_test_Node.path -> restgateway_test_Node))

    // Covers array in /v1/test/array
    val node3 = TreeNode(path = "array", methodInfos = MethodInfo(GET, "", "GetMessageV5") :: Nil)

    // Covers {user_id} in /v1/test/messages/{message_id}/users/{user_id}
    val node4 = TreeNode(path = "{user_id}", methodInfos = MethodInfo(GET, "", "GetMessageV3") :: Nil)

    // Covers users in /v1/test/messages/{message_id}/users/{user_id}
    val node5 = TreeNode(path = "users", children = Map(node4.path -> node4))

    /*
       Covers {message_id} in RawPath(/v1/test/messages/{message_id},List(MethodInfo(GET,,GetMessageV1))) and
                           RawPath(/v1/test/messages/{message_id}/users/{user_id},List(MethodInfo(GET,,GetMessageV3)))
     */
    val node6 = TreeNode(
      path = "{message_id}",
      methodInfos = MethodInfo(GET, "", "GetMessageV1") :: Nil,
      children = Map(node5.path -> node5)
    )

    /*
       Covers messages in RawPath(/v1/test/messages,List(MethodInfo(GET,,GetMessageV4))) and
                        RawPath(/v1/test/messages/{message_id},List(MethodInfo(GET,,GetMessageV1))) and
                        RawPath(/v1/test/messages/{message_id}/users/{user_id},List(MethodInfo(GET,,GetMessageV3)))
     */
    val node7 = TreeNode(
      path = "messages",
      methodInfos = MethodInfo(GET, "", "GetMessageV4") :: Nil,
      children = Map(node6.path -> node6)
    )

    /*
       Covers {message_id} and messages in RawPath(/v1/test/users/{user_id}/messages/{message_id},
                List(MethodInfo(GET,,GetMessageV2), MethodInfo(POST,sub,PostMessage), MethodInfo(PUT,sub,PutMessage)))
     */
    val node8 = TreeNode(
      path = "{message_id}",
      methodInfos = MethodInfo(GET, "", "GetMessageV2") :: MethodInfo(POST, "sub", "PostMessage") :: MethodInfo(
        PUT,
        "sub",
        "PutMessage"
      ) :: Nil
    )
    val node9 = TreeNode(path = "messages", children = Map(node8.path -> node8))

    /* Covers {user_id} and users in RawPath(/v1/test/users/{user_id},List(MethodInfo(GET,,GetMessageV2)))
        RawPath(/v1/test/users/{user_id}/messages/{message_id},List(MethodInfo(GET,,GetMessageV2),
              MethodInfo(POST,sub,PostMessage), MethodInfo(PUT,sub,PutMessage)))
     */
    val node10 = TreeNode(
      path = "{user_id}",
      methodInfos = MethodInfo(GET, "", "GetMessageV2") :: Nil,
      children = Map(node9.path -> node9)
    )
    val node11 = TreeNode(path = "users", children = Map(node10.path -> node10))

    val v1_testNode =
      TreeNode(path = "test", children = Map(node3.path -> node3, node7.path -> node7, node11.path -> node11))
    val v1Node = TreeNode(path = "v1", children = Map(v1_testNode.path -> v1_testNode))

    Map(restgatewayNode.path -> restgatewayNode, v1Node.path -> v1Node)
  }

  "TreeNode" should {

    "create empty tree node" in {
      TreeNode.empty[String] shouldBe empty
    }

    "create from RawPath" in {
      val rawPath = RawPath(
        path = "/v1/test/users/{user_id}/messages/{message_id}",
        methodInfos = MethodInfo(patternCase = GET, body = "", source = "SomeThing") :: Nil
      )

      val leaf = TreeNode("{message_id}", methodInfos = rawPath.methodInfos)
      val parent1 = TreeNode("messages", children = Map(leaf.path -> leaf))
      val parent2 = TreeNode("{user_id}", children = Map(parent1.path -> parent1))
      val parent3 = TreeNode("users", children = Map(parent2.path -> parent2))
      val parent4 = TreeNode("test", children = Map(parent3.path -> parent3))
      val expected = TreeNode("v1", children = Map(parent4.path -> parent4))

      TreeNode(rawPath) shouldBe expected
    }

    "create from raw path with multiple methods" in {
      val rawPath = RawPath(
        path = "/v1/test/users/{user_id}/messages/{message_id}",
        methodInfos = MethodInfo(patternCase = GET, body = "", source = "SomeThing") :: MethodInfo(
          patternCase = POST,
          body = "*",
          source = "SomeOtherThing"
        ) :: Nil
      )

      val leaf = TreeNode("{message_id}", methodInfos = rawPath.methodInfos)
      val parent1 = TreeNode("messages", children = Map(leaf.path -> leaf))
      val parent2 = TreeNode("{user_id}", children = Map(parent1.path -> parent1))
      val parent3 = TreeNode("users", children = Map(parent2.path -> parent2))
      val parent4 = TreeNode("test", children = Map(parent3.path -> parent3))
      val expected = TreeNode("v1", children = Map(parent4.path -> parent4))

      TreeNode(rawPath) shouldBe expected
    }

    "create from raw path with single path element" in {
      val rawPath = RawPath(
        path = "/v1",
        methodInfos = MethodInfo(patternCase = POST, body = "*", source = "SomeThing") :: Nil
      )
      val expected = TreeNode("v1", methodInfos = rawPath.methodInfos)
      TreeNode(rawPath) shouldBe expected
    }

    "update current tree" in {
      val rawPath = RawPath("/v1/test/messages/{message_id}", List(MethodInfo(GET, "", "GetMessageV1")))
      val treeNode = TreeNode(rawPath)

      val pathToAdd =
        RawPath("/v1/test/messages/{message_id}/users/{user_id}", List(MethodInfo(GET, "", "GetMessageV3")))
      treeNode.updateTree(pathToAdd)

      val leaf = TreeNode("{user_id}", methodInfos = pathToAdd.methodInfos)
      val parent1 = TreeNode("users", children = Map(leaf.path -> leaf))
      val parent2 = TreeNode(
        "{message_id}",
        methodInfos = List(MethodInfo(GET, "", "GetMessageV1")),
        children = Map(parent1.path -> parent1)
      )
      val parent3 = TreeNode("messages", children = Map(parent2.path -> parent2))
      val parent4 = TreeNode("test", children = Map(parent3.path -> parent3))
      val expected = TreeNode("v1", children = Map(parent4.path -> parent4))

      treeNode shouldBe expected
    }
  }

  "ParserUtils" should {

    "build tree with single node" in {
      val sourceTree = PathParserUtils.buildTree(Seq(("/v1/test/messages/{message_id}", GET, "", "GetMessageV1")))

      val leaf = TreeNode("{message_id}", methodInfos = List(MethodInfo(GET, "", "GetMessageV1")))
      val parent1 = TreeNode("messages", children = Map(leaf.path -> leaf))
      val parent2 = TreeNode("test", children = Map(parent1.path -> parent1))
      val expected = TreeNode("v1", children = Map(parent2.path -> parent2))

      sourceTree shouldBe Map(expected.path -> expected)
    }

    "build entire tree" in {
      PathParserUtils.buildTree(rawValues) shouldBe expectedTree
    }
  }

}
