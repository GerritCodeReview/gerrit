// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.patch.filediff;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheImpl.DiffAlgorithm;
import org.eclipse.jgit.lib.ObjectId;

@AutoValue
public abstract class FileDiffCacheKey {

  public abstract Project.NameKey project();

  public abstract ObjectId oldCommit();

  public abstract ObjectId newCommit();

  public abstract String newFilePath();

  public abstract Integer renameScore();

  public abstract DiffAlgorithm diffAlgorithm();

  public abstract DiffPreferencesInfo.Whitespace whitespace();

  public int weight() {
    // TODO(ghareeb): implement a proper weigher
    return 1;
  }

  public static FileDiffCacheKey.Builder builder() {
    return new AutoValue_FileDiffCacheKey.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract FileDiffCacheKey.Builder project(NameKey value);

    public abstract FileDiffCacheKey.Builder oldCommit(ObjectId value);

    public abstract FileDiffCacheKey.Builder newCommit(ObjectId value);

    public abstract FileDiffCacheKey.Builder newFilePath(String value);

    public abstract FileDiffCacheKey.Builder renameScore(Integer value);

    public FileDiffCacheKey.Builder disableRenameDetection() {
      renameScore(-1);
      return this;
    }

    public abstract FileDiffCacheKey.Builder diffAlgorithm(DiffAlgorithm value);

    public abstract FileDiffCacheKey.Builder whitespace(Whitespace value);

    public abstract FileDiffCacheKey build();
  }
}
