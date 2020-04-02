// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.git.receive;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.gerrit.entities.Change;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

/** Keeps track of the change IDs thus far updated by ReceiveCommit. */
@AutoValue
public abstract class ReceiveCommitsResult {
  public enum Key {
    CREATED,
    REPLACED,
    AUTOCLOSED,
  }

  /**
   * Returns change IDs of the given type for which the BatchUpdate succeeded, or empty list if
   * there are none.
   */
  public abstract ImmutableMap<Key, ImmutableSet<Change.Id>> changes();

  /** Indicate that the ReceiveCommits call involved a magic branch, such as {@code refs/for/}. */
  public abstract boolean magicPush();

  public static Builder builder() {
    return new AutoValue_ReceiveCommitsResult.Builder().magicPush(false);
  }

  public static ReceiveCommitsResult empty() {
    return builder().build();
  }

  abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    private EnumMap<Key, Set<Change.Id>> changes;

    Builder() {
      changes = Maps.newEnumMap(Key.class);
      Arrays.stream(Key.values()).forEach(k -> changes.put(k, new HashSet<>()));
    }

    /** Record a change ID update as having completed. */
    public Builder addChange(Key key, Change.Id id) {
      changes.get(key).add(id);
      return this;
    }

    public abstract Builder magicPush(boolean isMagicPush);

    public ReceiveCommitsResult build() {
      ImmutableMap.Builder<Key, ImmutableSet<Change.Id>> c = ImmutableMap.builder();
      changes.entrySet().forEach(e -> c.put(e.getKey(), ImmutableSet.copyOf(e.getValue())));
      changes(c.build());
      return autoBuild();
    }

    protected abstract Builder changes(ImmutableMap<Key, ImmutableSet<Change.Id>> changes);

    protected abstract ReceiveCommitsResult autoBuild();
  }
}
