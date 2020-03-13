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

package com.google.gerrit.server.edit;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.edit.tree.TreeModification;
import java.util.Optional;

@AutoValue
public abstract class CommitModification {

  public abstract ImmutableList<TreeModification> treeModifications();

  public abstract Optional<String> newCommitMessage();

  public static Builder builder() {
    return new AutoValue_CommitModification.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public Builder addTreeModification(TreeModification treeModification) {
      treeModificationsBuilder().add(treeModification);
      return this;
    }

    abstract ImmutableList.Builder<TreeModification> treeModificationsBuilder();

    public abstract Builder treeModifications(ImmutableList<TreeModification> treeModifications);

    public abstract Builder newCommitMessage(String newCommitMessage);

    public abstract CommitModification build();
  }
}
