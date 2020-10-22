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

package com.google.gerrit.server.patch.gitfilediff;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheImpl.DiffAlgorithm;
import org.eclipse.jgit.lib.ObjectId;

// TODO(ghareeb): Implement a key protobuf serializer
@AutoValue
public abstract class GitFileDiffCacheKey {

  /** A specific git project / repository. */
  public abstract Project.NameKey project();

  /** The old 20 bytes SHA-1 git tree ID used in the git tree diff */
  public abstract ObjectId oldTree();

  /** The new 20 bytes SHA-1 git tree ID used in the git tree diff */
  public abstract ObjectId newTree();

  /** File name in the tree identified by {@link #newTree()} */
  public abstract String newFilePath();

  /**
   * Percentage score used to identify a file as a "rename". A special value of -1 means that the
   * computation will ignore renames and rename detection will be disabled.
   */
  public abstract Integer renameScore();

  public abstract DiffAlgorithm diffAlgorithm();

  public abstract DiffPreferencesInfo.Whitespace whitespace();

  public int weight() {
    return stringSize(project().get())
        + 20 * 2 // oldTree and newTree
        + stringSize(newFilePath())
        + 4 // renameScore
        + 4 // diffAlgorithm
        + 4; // whitespace
  }

  private static int stringSize(String str) {
    if (str != null) {
      // each character in the string occupies 2 bytes. Ignoring the fixed overhead for the string
      // (length, offset and hash code) since they are negligible and do not
      // affect the comparison of 2 strings
      return str.length() * 2;
    }
    return 0;
  }

  public static Builder builder() {
    return new AutoValue_GitFileDiffCacheKey.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder project(NameKey value);

    public abstract Builder oldTree(ObjectId value);

    public abstract Builder newTree(ObjectId value);

    public abstract Builder newFilePath(String value);

    public abstract Builder renameScore(Integer value);

    public Builder disableRenameDetection() {
      renameScore(-1);
      return this;
    }

    public abstract Builder diffAlgorithm(DiffAlgorithm value);

    public abstract Builder whitespace(Whitespace value);

    public abstract GitFileDiffCacheKey build();
  }
}
