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

package com.google.gerrit.server.util;

import java.io.PrintWriter;
import java.util.SortedSet;

public class TreeFormatter {

  public interface TreeNode {
    String getDisplayName();

    boolean isVisible();

    SortedSet<? extends TreeNode> getChildren();
  }

  public static final String NOT_VISIBLE_NODE = "(x)";

  private static final String NODE_PREFIX = "|-- ";
  private static final String LAST_NODE_PREFIX = "`-- ";
  private static final String DEFAULT_TAB_SEPARATOR = "|";

  private final PrintWriter stdout;
  private String currentTabSeparator = " ";

  public TreeFormatter(PrintWriter stdout) {
    this.stdout = stdout;
  }

  public void printTree(SortedSet<? extends TreeNode> rootNodes) {
    if (rootNodes.isEmpty()) {
      return;
    }
    if (rootNodes.size() == 1) {
      printTree(rootNodes.first());
    } else {
      currentTabSeparator = DEFAULT_TAB_SEPARATOR;
      int i = 0;
      final int size = rootNodes.size();
      for (TreeNode rootNode : rootNodes) {
        final boolean isLastRoot = ++i == size;
        if (isLastRoot) {
          currentTabSeparator = " ";
        }
        printTree(rootNode);
      }
    }
  }

  public void printTree(TreeNode rootNode) {
    printTree(rootNode, 0, true);
  }

  private void printTree(TreeNode node, int level, boolean isLast) {
    printNode(node, level, isLast);
    final SortedSet<? extends TreeNode> childNodes = node.getChildren();
    int i = 0;
    final int size = childNodes.size();
    for (TreeNode childNode : childNodes) {
      final boolean isLastChild = ++i == size;
      printTree(childNode, level + 1, isLastChild);
    }
  }

  private void printIndention(int level) {
    if (level > 0) {
      stdout.print(String.format("%-" + 4 * level + "s", currentTabSeparator));
    }
  }

  private void printNode(TreeNode node, int level, boolean isLast) {
    printIndention(level);
    stdout.print(isLast ? LAST_NODE_PREFIX : NODE_PREFIX);
    if (node.isVisible()) {
      stdout.print(node.getDisplayName());
    } else {
      stdout.print(NOT_VISIBLE_NODE);
    }
    stdout.print("\n");
  }
}
