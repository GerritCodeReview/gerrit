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

package com.google.gerrit.server.git.receive;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Change;
import java.util.Arrays;
import java.util.EnumMap;

/** Keeps track of the change IDs thus far updated by {@link ReceiveCommits}. */
@AutoValue
public abstract class ReceiveCommitsResult {
  /** Status of a change. Used to aggregate metrics. */
  public enum ChangeStatus {
    CREATED,
    REPLACED,
    AUTOCLOSED,
  }

  /**
   * Returns change IDs of the given type for which the BatchUpdate succeeded, or empty list if
   * there are none.
   */
  public abstract ImmutableMap<ChangeStatus, ImmutableSet<Change.Id>> changes();

  /** Indicate that the ReceiveCommits call involved a magic branch, such as {@code refs/for/}. */
  public abstract boolean magicPush();

  public static Builder builder() {
    return new AutoValue_ReceiveCommitsResult.Builder().magicPush(false);
  }

  public static ReceiveCommitsResult empty() {
    return builder().build();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    private EnumMap<ChangeStatus, ImmutableSet.Builder<Change.Id>> changes;

    Builder() {
      changes = Maps.newEnumMap(ChangeStatus.class);
      Arrays.stream(ChangeStatus.values()).forEach(k -> changes.put(k, ImmutableSet.builder()));
    }

    /** Record a change ID update as having completed. */
    @CanIgnoreReturnValue
    public Builder addChange(ChangeStatus key, Change.Id id) {
      changes.get(key).add(id);
      return this;
    }

    @CanIgnoreReturnValue
    public abstract Builder magicPush(boolean isMagicPush);

    public ReceiveCommitsResult build() {
      ImmutableMap.Builder<ChangeStatus, ImmutableSet<Change.Id>> changesBuilder =
          ImmutableMap.builder();
      changes.entrySet().forEach(e -> changesBuilder.put(e.getKey(), e.getValue().build()));
      changes(changesBuilder.build());
      return autoBuild();
    }

    protected abstract Builder changes(ImmutableMap<ChangeStatus, ImmutableSet<Change.Id>> changes);

    protected abstract ReceiveCommitsResult autoBuild();
  }
}
