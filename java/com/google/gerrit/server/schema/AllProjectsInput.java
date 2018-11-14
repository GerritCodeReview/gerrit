// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.server.UsedAt;
import com.google.gerrit.server.notedb.Sequences;
import java.util.List;
import java.util.Optional;

@AutoValue
public abstract class AllProjectsInput {
  @UsedAt(UsedAt.Project.GOOGLE)
  public static LabelType getDefaultCodeReviewLabel() {
    LabelType type =
        new LabelType(
            "Code-Review",
            ImmutableList.of(
                new LabelValue((short) 2, "Looks good to me, approved"),
                new LabelValue((short) 1, "Looks good to me, but someone else must approve"),
                new LabelValue((short) 0, "No score"),
                new LabelValue((short) -1, "I would prefer this is not merged as is"),
                new LabelValue((short) -2, "This shall not be merged")));
    type.setCopyMinScore(true);
    type.setCopyAllScoresOnTrivialRebase(true);
    return type;
  }

  /** The administrator group which gets default permissions granted. */
  public abstract Optional<GroupReference> administratorsGroup();

  /** The group which gets stream-events permission granted and appropriate properties set. */
  public abstract Optional<GroupReference> batchUsersGroup();

  /** The commit message used when commit the project config change. */
  public abstract Optional<String> commitMessage();

  /** The first change-id used in this host. */
  @UsedAt(UsedAt.Project.GOOGLE)
  public abstract int firstChangeIdForNoteDb();

  /** The "Code-Review" label to be defined in All-Projects. */
  @UsedAt(UsedAt.Project.GOOGLE)
  public abstract LabelType codeReviewLabel();

  /** Other labels to be defined in All-Projects. */
  @UsedAt(UsedAt.Project.GOOGLE)
  public abstract ImmutableList<LabelType> additionalLabelType();

  public abstract Builder toBuilder();

  public static Builder builderWithDefaults() {
    return new AutoValue_AllProjectsInput.Builder()
        .codeReviewLabel(getDefaultCodeReviewLabel())
        .firstChangeIdForNoteDb(Sequences.FIRST_CHANGE_ID)
        .additionalLabelType(ImmutableList.of());
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder administratorsGroup(GroupReference adminGroup);

    public abstract Builder batchUsersGroup(GroupReference batchGroup);

    public abstract Builder commitMessage(String commitMessage);

    public abstract Builder firstChangeIdForNoteDb(int firstChangeId);

    @UsedAt(UsedAt.Project.GOOGLE)
    public abstract Builder codeReviewLabel(LabelType codeReviewLabel);

    public abstract Builder additionalLabelType(List<LabelType> additionalLabelType);

    public abstract AllProjectsInput build();
  }
}
