//  Copyright (C) 2020 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

package com.google.gerrit.server.patch.entities;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;

@AutoValue
public abstract class FileHeader {
  public static FileHeader create(org.eclipse.jgit.patch.FileHeader jgitHeader) {
    return new AutoValue_FileHeader(jgitHeader);
  }

  public abstract org.eclipse.jgit.patch.FileHeader jgitHeader();

  public String getOldPath() {
    org.eclipse.jgit.patch.FileHeader hdr = jgitHeader();
    Patch.ChangeType changeType = getChangeType();
    switch (changeType) {
      case DELETED:
      case ADDED:
      case MODIFIED:
      case REWRITE:
        return null;

      case COPIED:
      case RENAMED:
        return hdr.getOldPath();
    }
    return null;
  }

  public String getNewPath() {
    org.eclipse.jgit.patch.FileHeader hdr = jgitHeader();
    Patch.ChangeType changeType = getChangeType();
    switch (changeType) {
      case DELETED:
        return hdr.getOldPath();

      case ADDED:
      case MODIFIED:
      case REWRITE:
      case COPIED:
      case RENAMED:
        return hdr.getNewPath();
    }
    return null;
  }

  public ChangeType getChangeType() {
    org.eclipse.jgit.patch.FileHeader hdr = jgitHeader();
    switch (hdr.getChangeType()) {
      case ADD:
        return Patch.ChangeType.ADDED;
      case MODIFY:
        return Patch.ChangeType.MODIFIED;
      case DELETE:
        return Patch.ChangeType.DELETED;
      case RENAME:
        return Patch.ChangeType.RENAMED;
      case COPY:
        return Patch.ChangeType.COPIED;
      default:
        throw new IllegalArgumentException("Unsupported type " + hdr.getChangeType());
    }
  }
}
