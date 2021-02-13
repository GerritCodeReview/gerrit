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

package com.google.gerrit.server.patch.diff;

import static com.google.gerrit.server.patch.DiffUtil.stringSize;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Project;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.ModifiedFilesKeyProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import org.eclipse.jgit.lib.ObjectId;

/** Cache key for the {@link com.google.gerrit.server.patch.diff.ModifiedFilesCache} */
@AutoValue
public abstract class ModifiedFilesCacheKey {

  /** A specific git project / repository. */
  public abstract Project.NameKey project();

  /** @return the old commit ID used in the git tree diff */
  public abstract ObjectId aCommit();

  /** @return the new commit ID used in the git tree diff */
  public abstract ObjectId bCommit();

  /**
   * Percentage score used to identify a file as a "rename". A special value of -1 means that the
   * computation will ignore renames and rename detection will be disabled.
   */
  public abstract int renameScore();

  public boolean renameDetectionEnabled() {
    return renameScore() != -1;
  }

  /** Returns the size of the object in bytes */
  public int weight() {
    return stringSize(project().get()) // project
        + 20 * 2 // aCommit and bCommit
        + 4; // renameScore
  }

  public static ModifiedFilesCacheKey.Builder builder() {
    return new AutoValue_ModifiedFilesCacheKey.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract ModifiedFilesCacheKey.Builder project(Project.NameKey value);

    public abstract ModifiedFilesCacheKey.Builder aCommit(ObjectId value);

    public abstract ModifiedFilesCacheKey.Builder bCommit(ObjectId value);

    public ModifiedFilesCacheKey.Builder disableRenameDetection() {
      renameScore(-1);
      return this;
    }

    public abstract ModifiedFilesCacheKey.Builder renameScore(int value);

    public abstract ModifiedFilesCacheKey build();
  }

  public enum Serializer implements CacheSerializer<ModifiedFilesCacheKey> {
    INSTANCE;

    @Override
    public byte[] serialize(ModifiedFilesCacheKey key) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      return Protos.toByteArray(
          ModifiedFilesKeyProto.newBuilder()
              .setProject(key.project().get())
              .setACommit(idConverter.toByteString(key.aCommit()))
              .setBCommit(idConverter.toByteString(key.bCommit()))
              .setRenameScore(key.renameScore())
              .build());
    }

    @Override
    public ModifiedFilesCacheKey deserialize(byte[] in) {
      ModifiedFilesKeyProto proto = Protos.parseUnchecked(ModifiedFilesKeyProto.parser(), in);
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      return ModifiedFilesCacheKey.builder()
          .project(Project.NameKey.parse(proto.getProject()))
          .aCommit(idConverter.fromByteString(proto.getACommit()))
          .bCommit(idConverter.fromByteString(proto.getBCommit()))
          .renameScore(proto.getRenameScore())
          .build();
    }
  }
}
