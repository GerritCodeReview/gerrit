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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/** A {@code TreeModification} which renames a file or moves it to a different path. */
public class RenameFileModification implements TreeModification {

  private final String currentFilePath;
  private final String newFilePath;

  public RenameFileModification(String currentFilePath, String newFilePath) {
    this.currentFilePath = currentFilePath;
    this.newFilePath = newFilePath;
  }

  @Override
  public List<DirCacheEditor.PathEdit> getPathEdits(Repository repository, RevCommit baseCommit)
      throws IOException {
    try (RevWalk revWalk = new RevWalk(repository)) {
      revWalk.parseHeaders(baseCommit);
      try (TreeWalk treeWalk =
          TreeWalk.forPath(revWalk.getObjectReader(), currentFilePath, baseCommit.getTree())) {
        if (treeWalk == null) {
          return Collections.emptyList();
        }
        DirCacheEditor.DeletePath deletePathEdit = new DirCacheEditor.DeletePath(currentFilePath);
        AddPath addPathEdit =
            new AddPath(newFilePath, treeWalk.getFileMode(0), treeWalk.getObjectId(0));
        return Arrays.asList(deletePathEdit, addPathEdit);
      }
    }
  }
}
