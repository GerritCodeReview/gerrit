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

import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;

/**
 * A {@code PathEdit} which adds a file path to the index. This operation is the counterpart to
 * {@link org.eclipse.jgit.dircache.DirCacheEditor.DeletePath}.
 */
class AddPath extends DirCacheEditor.PathEdit {

  private final FileMode fileMode;
  private final ObjectId objectId;

  AddPath(String filePath, FileMode fileMode, ObjectId objectId) {
    super(filePath);
    this.fileMode = fileMode;
    this.objectId = objectId;
  }

  @Override
  public void apply(DirCacheEntry dirCacheEntry) {
    dirCacheEntry.setFileMode(fileMode);
    dirCacheEntry.setObjectId(objectId);
  }
}
