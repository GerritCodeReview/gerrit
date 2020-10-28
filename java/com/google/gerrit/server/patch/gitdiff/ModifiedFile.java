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

package com.google.gerrit.server.patch.gitdiff;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.ModifiedFileProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.protobuf.Descriptors.FieldDescriptor;
import java.util.Optional;

/**
 * An entity representing a Modified file due to a diff between 2 git trees. This entity contains
 * the change type and the old & new paths, but does not include any actual content diff of the
 * file.
 */
@AutoValue
public abstract class ModifiedFile {
  /**
   * Returns the change type (i.e. add, delete, modify, rename, etc...) associated with this
   * modified file.
   */
  public abstract ChangeType changeType();

  /**
   * Returns the old name associated with this file. An empty optional is returned if {@link
   * #changeType()} is equal to {@link ChangeType#ADDED}.
   */
  public abstract Optional<String> oldPath();

  /**
   * Returns the new name associated with this file. An empty optional is returned if {@link
   * #changeType()} is equal to {@link ChangeType#DELETED}
   */
  public abstract Optional<String> newPath();

  public static Builder builder() {
    return new AutoValue_ModifiedFile.Builder();
  }

  /** Computes this object's weight, which is its size in bytes. */
  public int weight() {
    int weight = 1; // the changeType field
    if (oldPath().isPresent()) {
      weight += oldPath().get().length();
    }
    if (newPath().isPresent()) {
      weight += newPath().get().length();
    }
    return weight;
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder changeType(ChangeType value);

    public abstract Builder oldPath(Optional<String> value);

    public abstract Builder newPath(Optional<String> value);

    public abstract ModifiedFile build();
  }

  enum Serializer implements CacheSerializer<ModifiedFile> {
    INSTANCE;

    private static final FieldDescriptor oldPathDescriptor =
        ModifiedFileProto.getDescriptor().findFieldByName("old_path");

    private static final FieldDescriptor newPathDescriptor =
        ModifiedFileProto.getDescriptor().findFieldByName("new_path");

    @Override
    public byte[] serialize(ModifiedFile modifiedFile) {
      return Protos.toByteArray(toProto(modifiedFile));
    }

    public ModifiedFileProto toProto(ModifiedFile modifiedFile) {
      ModifiedFileProto.Builder builder = ModifiedFileProto.newBuilder();
      builder.setChangeType(modifiedFile.changeType().toString());
      if (modifiedFile.oldPath().isPresent()) {
        builder.setOldPath(modifiedFile.oldPath().get());
      }
      if (modifiedFile.newPath().isPresent()) {
        builder.setNewPath(modifiedFile.newPath().get());
      }
      return builder.build();
    }

    @Override
    public ModifiedFile deserialize(byte[] in) {
      ModifiedFileProto modifiedFileProto = Protos.parseUnchecked(ModifiedFileProto.parser(), in);
      return fromProto(modifiedFileProto);
    }

    public ModifiedFile fromProto(ModifiedFileProto modifiedFileProto) {
      ModifiedFile.Builder builder = ModifiedFile.builder();
      builder.changeType(ChangeType.valueOf(modifiedFileProto.getChangeType()));

      if (modifiedFileProto.hasField(oldPathDescriptor)) {
        builder.oldPath(Optional.of(modifiedFileProto.getOldPath()));
      }
      if (modifiedFileProto.hasField(newPathDescriptor)) {
        builder.newPath(Optional.of(modifiedFileProto.getNewPath()));
      }
      return builder.build();
    }
  }
}
