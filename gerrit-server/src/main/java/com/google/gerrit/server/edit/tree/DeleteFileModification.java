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

import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/** A {@code TreeModification} which deletes a file. */
public class DeleteFileModification implements TreeModification {

  private final String filePath;

  public DeleteFileModification(String filePath) {
    this.filePath = filePath;
  }

  @Override
  public List<DirCacheEditor.PathEdit> getPathEdits(Repository repository, RevCommit baseCommit) {
    DirCacheEditor.DeletePath deletePathEdit = new DirCacheEditor.DeletePath(filePath);
    return Collections.singletonList(deletePathEdit);
  }
}
