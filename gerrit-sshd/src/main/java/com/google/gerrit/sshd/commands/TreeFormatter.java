// Copyright (C) 2011 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.sshd.commands;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;

/**
 * This class formats a graph of objects as a tree. The formatted tree is
 * printed to the given {@link PrintWriter}. The object graph that should be
 * formatted and printed as a tree must be provided as a graph of
 * {@link TreeNode}'s. The tree formatter is able to detect cycles in the graph
 * of TreeNode's. TreeNode's already printed but reached again via a cycle in
 * the graph will be specially formatted and not further expanded. If the graph
 * of TreeNode's can contain different instances of TreeNode's that represent
 * the same object, the implementor of the TreeNode must take care to implement
 * the {@link Object#equals(Object)} and {@link Object#hashCode()} methods.
 * Otherwise the TreeFormatter is not able to detect the cycles.
 */
public class TreeFormatter {

  /**
   * This interface represents a node in a tree that is formatted by the
   * {@link TreeFormatter}. Each TreeNode can have other TreeNode's as children so
   * that a graph of TreeNode's is generated. It is allowed to have cycles within
   * the TreeNode graph since the TreeFormatter can handle cycles. However if
   * different TreeNode instances are used to represent the same object the
   * implementor of the TreeNode interface has to take care to implement the
   * {@link Object#equals(Object)} and {@link Object#hashCode()} methods.
   * Otherwise the TreeFormatter is not able to detect cycles.
   */
  public static interface TreeNode {
    public String getDisplayName();
    public String getDescription();
    public boolean isVisible();
    public SortedSet<? extends TreeNode> getChildren();
  }

  public static final String NOT_VISIBLE_NODE = "(x)";

  private static final String NODE_PREFIX = "|-- ";
  private static final String LAST_NODE_PREFIX = "`-- ";
  private static final String DEFAULT_TAB_SEPARATOR = "|";
  private static final String RECURSIVE_NODE_SEPARATOR = "...";

  private final PrintWriter stdout;
  private String currentTabSeparator = " ";

  public TreeFormatter(final PrintWriter stdout) {
    this.stdout = stdout;
  }

  public void printTree(final SortedSet<? extends TreeNode> rootNodes) {
    if (rootNodes.isEmpty()) {
      return;
    }
    if (rootNodes.size() == 1) {
      printTree(rootNodes.first());
    } else {
      currentTabSeparator = DEFAULT_TAB_SEPARATOR;
      int i = 0;
      final int size = rootNodes.size();
      for (final TreeNode rootNode : rootNodes) {
        final boolean isLastRoot = ++i == size;
        if (isLastRoot) {
          currentTabSeparator = " ";
        }
        printTree(rootNode);
      }
    }
  }

  public void printTree(final TreeNode rootNode) {
    printTree(rootNode, 0, true, new HashSet<TreeNode>());
  }

  private void printTree(final TreeNode node, final int level,
      final boolean isLast, final Set<TreeNode> seen) {
    seen.add(node);
    printNode(node, level, isLast, false);
    final SortedSet<? extends TreeNode> childNodes = node.getChildren();
    int i = 0;
    final int size = childNodes.size();
    for (final TreeNode childNode : childNodes) {
      final boolean isLastChild = ++i == size;
      if (!seen.contains(childNode)) {
        printTree(childNode, level + 1, isLastChild, seen);
      } else {
        printNode(childNode, level + 1, isLast, true);
      }
    }
  }

  private void printIndention(final int level) {
    if (level > 0) {
      stdout.print(String.format("%-" + 4 * level + "s", currentTabSeparator));
    }
  }

  private void printNode(final TreeNode node, final int level,
      final boolean isLast, final boolean recursive) {
    printIndention(level);
    stdout.print(isLast ? LAST_NODE_PREFIX : NODE_PREFIX);
    if (node.isVisible()) {
      stdout.print(node.getDisplayName());
    } else {
      stdout.print(NOT_VISIBLE_NODE);
    }
    stdout.print("\n");
    if (node.getDescription() != null) {
      printIndention(level + 1);
      stdout.print(node.getDescription());
      stdout.print("\n");
    }
    if (recursive) {
      printIndention(level + 1);
      stdout.print(RECURSIVE_NODE_SEPARATOR);
      stdout.print("\n");
    }
  }
}
