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

import static com.google.gerrit.server.patch.DiffUtil.stringSize;

import com.google.auto.value.AutoValue;
import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.GitFileDiffKeyProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheImpl.DiffAlgorithm;
import org.eclipse.jgit.lib.ObjectId;

@AutoValue
public abstract class GitFileDiffCacheKey {

  /** A specific git project / repository. */
  public abstract Project.NameKey project();

  /**
   * The old 20 bytes SHA-1 git tree ID used in the git tree diff. If equals to {@link
   * ObjectId#zeroId()}, a null tree is used for the diff scan, and {@link #newTree()} ()} is
   * treated as an added tree.
   */
  public abstract ObjectId oldTree();

  /** The new 20 bytes SHA-1 git tree ID used in the git tree diff */
  public abstract ObjectId newTree();

  /** File name in the tree identified by {@link #newTree()} */
  public abstract String newFilePath();

  /**
   * Percentage score used to identify a file as a "rename". A special value of -1 means that the
   * computation will ignore renames and rename detection will be disabled.
   */
  public abstract int renameScore();

  public abstract DiffAlgorithm diffAlgorithm();

  public abstract DiffPreferencesInfo.Whitespace whitespace();

  /** Employ a timeout on the git computation while formatting the file header. */
  public abstract boolean useTimeout();

  public int weight() {
    return stringSize(project().get())
        + 20 * 2 // oldTree and newTree
        + stringSize(newFilePath())
        + 4 // renameScore
        + 4 // diffAlgorithm
        + 4 // whitespace
        + 1; // useTimeout
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

    public abstract Builder renameScore(int value);

    public Builder disableRenameDetection() {
      renameScore(-1);
      return this;
    }

    public abstract Builder diffAlgorithm(DiffAlgorithm value);

    public abstract Builder whitespace(Whitespace value);

    public abstract Builder useTimeout(boolean value);

    public abstract GitFileDiffCacheKey build();
  }

  public enum Serializer implements CacheSerializer<GitFileDiffCacheKey> {
    INSTANCE;

    private static final Converter<String, DiffAlgorithm> DIFF_ALGORITHM_CONVERTER =
        Enums.stringConverter(DiffAlgorithm.class);

    private static final Converter<String, Whitespace> WHITESPACE_CONVERTER =
        Enums.stringConverter(Whitespace.class);

    @Override
    public byte[] serialize(GitFileDiffCacheKey key) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      return Protos.toByteArray(
          GitFileDiffKeyProto.newBuilder()
              .setProject(key.project().get())
              .setATree(idConverter.toByteString(key.oldTree()))
              .setBTree(idConverter.toByteString(key.newTree()))
              .setFilePath(key.newFilePath())
              .setRenameScore(key.renameScore())
              .setDiffAlgorithm(DIFF_ALGORITHM_CONVERTER.reverse().convert(key.diffAlgorithm()))
              .setWhitepsace(WHITESPACE_CONVERTER.reverse().convert(key.whitespace()))
              .setUseTimeout(key.useTimeout())
              .build());
    }

    @Override
    public GitFileDiffCacheKey deserialize(byte[] in) {
      GitFileDiffKeyProto proto = Protos.parseUnchecked(GitFileDiffKeyProto.parser(), in);
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      return GitFileDiffCacheKey.builder()
          .project(Project.nameKey(proto.getProject()))
          .oldTree(idConverter.fromByteString(proto.getATree()))
          .newTree(idConverter.fromByteString(proto.getBTree()))
          .newFilePath(proto.getFilePath())
          .renameScore(proto.getRenameScore())
          .diffAlgorithm(DIFF_ALGORITHM_CONVERTER.convert(proto.getDiffAlgorithm()))
          .whitespace(WHITESPACE_CONVERTER.convert(proto.getWhitepsace()))
          .useTimeout(proto.getUseTimeout())
          .build();
    }
  }
}
