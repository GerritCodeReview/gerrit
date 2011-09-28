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

import java.util.SortedSet;

/**
 * This interface represents a node in a tree that is formatted by the
 * {@link TreeFormatter}. Each TreeNode can have other TreeNode's as children so
 * that a graph of TreeNode's is generated. It is allowed to have recursions
 * within the TreeNode graph since the TreeFormatter can handle recursions.
 * However if different TreeNode instances are used to represent the same object
 * the implementor of the TreeNode interface has to take care to implement the
 * {@link Object#equals(Object)} and {@link Object#hashCode()} methods.
 * Otherwise the TreeFormatter is not able to detect the recursion.
 */
public interface TreeNode {
  public String getDisplayName();
  public String getDescription();
  public boolean isVisible();
  public SortedSet<? extends TreeNode> getChildren();
}
