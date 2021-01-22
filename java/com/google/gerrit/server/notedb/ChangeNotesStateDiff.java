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

package com.google.gerrit.server.notedb;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import java.util.List;
import java.util.Map;

/** The diff between two {@link ChangeNotesState} instances. */
@AutoValue
public abstract class ChangeNotesStateDiff {

  public static Builder builder() {
    return new AutoValue_ChangeNotesStateDiff.Builder();
  }

  /** Null if the old state had no topic. */
  @Nullable
  public abstract String oldTopic();

  /** Null if the new state has no topic. */
  @Nullable
  public abstract String newTopic();

  public abstract ImmutableList<ChangeMessage> addedMessages();

  public abstract ImmutableList<Map.Entry<PatchSet.Id, PatchSetApproval>> addedPatchSetApprovals();

  public abstract ImmutableList<Map.Entry<PatchSet.Id, PatchSetApproval>> removedPatchSetApprovals();

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setOldTopic(@Nullable String oldTopic);

    public abstract Builder setNewTopic(@Nullable String newTopic);

    public abstract Builder setAddedMessages(List<ChangeMessage> addedMessages);

    public abstract Builder setAddedPatchSetApprovals(
        List<Map.Entry<PatchSet.Id, PatchSetApproval>> addedPatchSetApprovals);

    public abstract Builder setRemovedPatchSetApprovals(
        List<Map.Entry<PatchSet.Id, PatchSetApproval>> removedPatchSetApprovals);

    public abstract ChangeNotesStateDiff build();
  }
}
