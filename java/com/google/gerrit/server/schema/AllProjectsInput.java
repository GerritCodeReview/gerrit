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
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.server.notedb.Sequences;
import java.util.Optional;

@AutoValue
public abstract class AllProjectsInput {

  /** Default boolean configs set when initializing All-Projects. */
  public static final ImmutableMap<BooleanProjectConfig, InheritableBoolean>
      DEFAULT_BOOLEAN_PROJECT_CONFIGS =
          ImmutableMap.of(
              BooleanProjectConfig.REQUIRE_CHANGE_ID,
              InheritableBoolean.TRUE,
              BooleanProjectConfig.USE_CONTENT_MERGE,
              InheritableBoolean.TRUE,
              BooleanProjectConfig.USE_CONTRIBUTOR_AGREEMENTS,
              InheritableBoolean.FALSE,
              BooleanProjectConfig.USE_SIGNED_OFF_BY,
              InheritableBoolean.FALSE,
              BooleanProjectConfig.ENABLE_SIGNED_PUSH,
              InheritableBoolean.FALSE);

  @UsedAt(UsedAt.Project.GOOGLE)
  public static LabelType getDefaultCodeReviewLabel() {
    return LabelType.builder(
            "Code-Review",
            ImmutableList.of(
                LabelValue.create((short) 2, "Looks good to me, approved"),
                LabelValue.create((short) 1, "Looks good to me, but someone else must approve"),
                LabelValue.create((short) 0, "No score"),
                LabelValue.create((short) -1, "I would prefer this is not merged as is"),
                LabelValue.create((short) -2, "This shall not be merged")))
        .setCopyMinScore(true)
        .setCopyAllScoresOnTrivialRebase(true)
        .build();
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
  public abstract Optional<LabelType> codeReviewLabel();

  /** Description for the All-Projects. */
  public abstract Optional<String> projectDescription();

  /** Boolean project configs to be set in All-Projects. */
  public abstract ImmutableMap<BooleanProjectConfig, InheritableBoolean> booleanProjectConfigs();

  /** Whether initializing default access sections in All-Projects. */
  public abstract boolean initDefaultAcls();

  public abstract Builder toBuilder();

  public static Builder builder() {
    Builder builder =
        new AutoValue_AllProjectsInput.Builder()
            .codeReviewLabel(getDefaultCodeReviewLabel())
            .firstChangeIdForNoteDb(Sequences.FIRST_CHANGE_ID)
            .initDefaultAcls(true);
    DEFAULT_BOOLEAN_PROJECT_CONFIGS.forEach(builder::addBooleanProjectConfig);

    return builder;
  }

  public static Builder builderWithNoDefault() {
    return new AutoValue_AllProjectsInput.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder administratorsGroup(GroupReference adminGroup);

    public abstract Builder batchUsersGroup(GroupReference batchGroup);

    public abstract Builder commitMessage(String commitMessage);

    public abstract Builder firstChangeIdForNoteDb(int firstChangeId);

    @UsedAt(UsedAt.Project.GOOGLE)
    public abstract Builder codeReviewLabel(LabelType codeReviewLabel);

    @UsedAt(UsedAt.Project.GOOGLE)
    public abstract Builder projectDescription(String projectDescription);

    public abstract ImmutableMap.Builder<BooleanProjectConfig, InheritableBoolean>
        booleanProjectConfigsBuilder();

    public Builder addBooleanProjectConfig(
        BooleanProjectConfig booleanProjectConfig, InheritableBoolean inheritableBoolean) {
      booleanProjectConfigsBuilder().put(booleanProjectConfig, inheritableBoolean);
      return this;
    }

    @UsedAt(UsedAt.Project.GOOGLE)
    public abstract Builder initDefaultAcls(boolean initDefaultACLs);

    public abstract AllProjectsInput build();
  }
}
