// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.edit.tree;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

/** A specific modification of a Git tree. */
public interface TreeModification {

  /**
   * Returns a list of {@code PathEdit}s which are necessary in order to achieve the desired
   * modification of the Git tree. The order of the {@code PathEdit}s can be crucial and hence
   * shouldn't be changed.
   *
   * @param repository the affected Git repository
   * @param treeId tree to which the modification is applied. A value of {@code ObjectId.zero()}
   *     indicates an empty tree.
   * @param parents parent commits of the commit to whose tree this modification is applied
   * @return an ordered list of necessary {@code PathEdit}s
   * @throws IOException if problems arise when accessing the repository
   */
  List<DirCacheEditor.PathEdit> getPathEdits(
      Repository repository, ObjectId treeId, ImmutableList<? extends ObjectId> parents)
      throws IOException;

  /**
   * Indicates a file path which is affected by this {@code TreeModification}. If the modification
   * refers to several file paths (e.g. renaming a file), returning either of them is appropriate as
   * long as the returned value is deterministic.
   *
   * @return an affected file path
   */
  @VisibleForTesting
  String getFilePath();
}
