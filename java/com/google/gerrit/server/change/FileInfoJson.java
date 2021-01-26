// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Computes the list of modified files between two patchset commits.
 *
 * <p>This class uses a Gerrit config to either use the old or the new diff cache implementation. We
 * are temporarily adding both implementation in parallel. The old implementation should be
 * deprecated soon.
 *
 * <p>TODO(ghareeb): Also get rid of the old {@link PatchListNotAvailableException}.
 */
@Singleton
public class FileInfoJson {
  private final boolean useNewDiffCache;
  private final FileInfoJsonOldImpl oldImpl;
  private final FileInfoJsonNewImpl newImpl;

  @Inject
  FileInfoJson(
      @GerritServerConfig Config config, FileInfoJsonOldImpl oldImpl, FileInfoJsonNewImpl newImpl) {
    this.oldImpl = oldImpl;
    this.newImpl = newImpl;
    this.useNewDiffCache = config.getBoolean("cache", "diff_cache", "useNewDiffCache", false);
  }

  public Map<String, FileInfo> toFileInfoMap(Change change, PatchSet patchSet)
      throws ResourceConflictException, PatchListNotAvailableException {
    return this.useNewDiffCache
        ? newImpl.toFileInfoMap(change, patchSet)
        : oldImpl.toFileInfoMap(change, patchSet);
  }

  public Map<String, FileInfo> toFileInfoMap(
      Change change, ObjectId objectId, @Nullable PatchSet base)
      throws ResourceConflictException, PatchListNotAvailableException {
    return this.useNewDiffCache
        ? newImpl.toFileInfoMap(change, objectId, base)
        : oldImpl.toFileInfoMap(change, objectId, base);
  }

  public Map<String, FileInfo> toFileInfoMap(Change change, ObjectId objectId, int parent)
      throws ResourceConflictException, PatchListNotAvailableException {
    return this.useNewDiffCache
        ? newImpl.toFileInfoMap(change, objectId, parent)
        : oldImpl.toFileInfoMap(change, objectId, parent);
  }

  public Map<String, FileInfo> toFileInfoMap(
      Project.NameKey project, ObjectId objectId, int parentNum)
      throws ResourceConflictException, PatchListNotAvailableException {
    if (this.useNewDiffCache) {
      return newImpl.toFileInfoMap(project, objectId, parentNum);
    }
    PatchListKey key;
    if (parentNum > 0) {
      key =
          PatchListKey.againstParentNum(
              parentNum, objectId, DiffPreferencesInfo.Whitespace.IGNORE_NONE);
    } else {
      key = PatchListKey.againstCommit(null, objectId, DiffPreferencesInfo.Whitespace.IGNORE_NONE);
    }
    return oldImpl.toFileInfoMap(project, key);
  }
}
