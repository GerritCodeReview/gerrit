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
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.entities.Weighable;
import java.util.Optional;
import org.apache.commons.lang.NotImplementedException;

/**
 * An entity representing a Modified file due to a diff between 2 git trees. This entity contains
 * the change type and the old & new paths, but does not include any actual content diff of the
 * file.
 */
@AutoValue
public abstract class ModifiedFile implements Weighable {
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
  @Override
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

  // TODO(ghareeb): Implement protobuf serialization
  enum Serializer implements CacheSerializer<ModifiedFile> {
    INSTANCE;

    @Override
    public byte[] serialize(ModifiedFile object) {
      throw new NotImplementedException("This method is not yet implemented");
    }

    @Override
    public ModifiedFile deserialize(byte[] in) {
      throw new NotImplementedException("This method is not yet implemented");
    }
  }
}
