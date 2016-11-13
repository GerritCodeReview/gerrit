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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * A {@code TreeModification} which restores a file. The file is added again if it was present
 * before the specified commit or deleted if it was absent.
 */
public class RestoreFileModification implements TreeModification {

  private final String filePath;

  public RestoreFileModification(String filePath) {
    this.filePath = filePath;
  }

  @Override
  public List<DirCacheEditor.PathEdit> getPathEdits(Repository repository, RevCommit baseCommit)
      throws IOException {
    if (baseCommit.getParentCount() == 0) {
      DirCacheEditor.DeletePath deletePath = new DirCacheEditor.DeletePath(filePath);
      return Collections.singletonList(deletePath);
    }

    RevCommit base = baseCommit.getParent(0);
    try (RevWalk revWalk = new RevWalk(repository)) {
      revWalk.parseHeaders(base);
      try (TreeWalk treeWalk =
          TreeWalk.forPath(revWalk.getObjectReader(), filePath, base.getTree())) {
        if (treeWalk == null) {
          DirCacheEditor.DeletePath deletePath = new DirCacheEditor.DeletePath(filePath);
          return Collections.singletonList(deletePath);
        }

        AddPath addPath = new AddPath(filePath, treeWalk.getFileMode(0), treeWalk.getObjectId(0));
        return Collections.singletonList(addPath);
      }
    }
  }
}
