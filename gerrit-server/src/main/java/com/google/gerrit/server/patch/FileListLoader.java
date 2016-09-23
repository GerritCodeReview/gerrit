// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

public class FileListLoader implements Callable<FileList> {
  static final Logger log = LoggerFactory.getLogger(FileListLoader.class);

  public interface Factory {
    FileListLoader create(PatchListKey key, Project.NameKey project);
  }

  private final PatchListCache patchListCache;
  private final PatchListKey key;
  private final Project.NameKey project;

  @AssistedInject
  FileListLoader(PatchListCache plc,
      @Assisted PatchListKey k,
      @Assisted Project.NameKey p) {
    patchListCache = plc;
    key = k;
    project = p;
  }

  @Override
  public FileList call() throws Exception {
    PatchList patchList = patchListCache.get(key, project);
    return toFileList(patchList);
  }

  static FileList toFileList(PatchList patchList) {
    List<String> r = new ArrayList<>(patchList.getPatches().size());
    for (PatchListEntry e : patchList.getPatches()) {
      if (Patch.isMagic(e.getNewName())) {
        continue;
      }
      switch (e.getChangeType()) {
        case ADDED:
        case MODIFIED:
        case DELETED:
        case COPIED:
        case REWRITE:
          r.add(e.getNewName());
          break;

        case RENAMED:
          r.add(e.getOldName());
          r.add(e.getNewName());
          break;
      }
    }
    Collections.sort(r);
    return new FileList(r.toArray(new String[r.size()]));
  }
}
