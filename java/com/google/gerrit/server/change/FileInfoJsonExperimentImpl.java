// Copyright (C) 2021 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import java.util.Map;
import javax.inject.Inject;
import org.eclipse.jgit.lib.ObjectId;

/**
 * An experimental implementation of FileInfoJson that uses {@link FileInfoJsonNewImpl} if the
 * experiment flag "GerritBackendRequestFeature__use_new_diff_cache" is enabled, or {@link
 * FileInfoJsonOldImpl} otherwise. This would enable a gradual rollout of {@link
 * FileInfoJsonNewImpl}.
 */
public class FileInfoJsonExperimentImpl implements FileInfoJson {
  @VisibleForTesting
  public static final String NEW_DIFF_CACHE_FEATURE =
      "GerritBackendRequestFeature__use_new_diff_cache";

  private final FileInfoJsonOldImpl oldImpl;
  private final FileInfoJsonNewImpl newImpl;
  private final ExperimentFeatures experimentFeatures;

  @Inject
  public FileInfoJsonExperimentImpl(
      FileInfoJsonOldImpl oldImpl,
      FileInfoJsonNewImpl newImpl,
      ExperimentFeatures experimentFeatures) {
    this.oldImpl = oldImpl;
    this.newImpl = newImpl;
    this.experimentFeatures = experimentFeatures;
  }

  @Override
  public Map<String, FileInfo> getFileInfoMap(
      Change change, ObjectId objectId, @Nullable PatchSet base)
      throws ResourceConflictException, PatchListNotAvailableException {
    return experimentFeatures.isFeatureEnabled(NEW_DIFF_CACHE_FEATURE)
        ? newImpl.getFileInfoMap(change, objectId, base)
        : oldImpl.getFileInfoMap(change, objectId, base);
  }

  @Override
  public Map<String, FileInfo> getFileInfoMap(
      Project.NameKey project, ObjectId objectId, int parentNum)
      throws ResourceConflictException, PatchListNotAvailableException {
    return experimentFeatures.isFeatureEnabled(NEW_DIFF_CACHE_FEATURE)
        ? newImpl.getFileInfoMap(project, objectId, parentNum)
        : oldImpl.getFileInfoMap(project, objectId, parentNum);
  }
}
