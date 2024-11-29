package com.improving
package grpc_rest_gateway
package compiler
package utils

import com.google.api.HttpRule.PatternCase

package object path_parser {

  type RawValue[Source] = (String, PatternCase, String, Source)

  private[path_parser] def toRawPaths[Source](rawValues: Seq[RawValue[Source]]): Seq[RawPath[Source]] =
    rawValues
      .groupBy(_._1)
      .map { case (path, seq) =>
        val methodInfos =
          seq.map { case (path, patternCase, body, source) =>
            MethodInfo(patternCase, path, body, source)
          }
        RawPath(path, methodInfos.toList.sorted)
      }
      .toSeq
      .sorted

  private[path_parser] def rawPathToTreeNode[Source](rawPath: RawPath[Source]): TreeNode[Source] = {
    val pathElements = rawPath.path.split("/").filterNot(_.isBlank)
    pathElements.foldRight(TreeNode.empty[Source]) { case (pathElement, treeNode) =>
      if (treeNode.path == "") {
        treeNode.path = pathElement
        treeNode.methodInfos = rawPath.methodInfos
        treeNode
      } else TreeNode(path = pathElement, children = Map(treeNode.path -> treeNode))
    }
  }
}
