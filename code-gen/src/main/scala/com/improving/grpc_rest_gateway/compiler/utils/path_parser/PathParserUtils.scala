package com.improving
package grpc_rest_gateway
package compiler
package utils
package path_parser

import scala.collection.mutable

object PathParserUtils {
  def buildTree[Source](rawValues: Seq[RawValue[Source]]): Map[String, TreeNode[Source]] = {
    val rawPaths = toRawPaths(rawValues)
    val sourceTree = mutable.Map.empty[String, TreeNode[Source]]
    rawPaths.foreach { rawPath =>
      val node = rawPathToTreeNode(rawPath)
      sourceTree.get(node.path) match {
        case Some(rootNode) => rootNode.updateTree(rawPath)
        case None           => sourceTree += (node.path -> node)
      }
    }
    sourceTree.toMap
  }
}
