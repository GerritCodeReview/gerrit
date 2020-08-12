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

package com.google.gerrit.server.cache.serialize.entities;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.cache.serialize.entities.LabelTypeSerializer.deserialize;
import static com.google.gerrit.server.cache.serialize.entities.LabelTypeSerializer.serialize;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import org.junit.Test;

public class LabelTypeSerializerTest {
  static final LabelType ALL_VALUES_SET =
      LabelType.builder(
              "name",
              ImmutableList.of(
                  LabelValue.create((short) 0, "no vote"),
                  LabelValue.create((short) 1, "approved")))
          .setCanOverride(!LabelType.DEF_CAN_OVERRIDE)
          .setAllowPostSubmit(!LabelType.DEF_ALLOW_POST_SUBMIT)
          .setIgnoreSelfApproval(!LabelType.DEF_IGNORE_SELF_APPROVAL)
          .setRefPatterns(ImmutableList.of("refs/heads/*", "refs/tags/*"))
          .setDefaultValue((short) 1)
          .setCopyAnyScore(!LabelType.DEF_COPY_ANY_SCORE)
          .setCopyMaxScore(!LabelType.DEF_COPY_MAX_SCORE)
          .setCopyMinScore(!LabelType.DEF_COPY_MIN_SCORE)
          .setCopyAllScoresOnMergeFirstParentUpdate(
              !LabelType.DEF_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE)
          .setCopyAllScoresOnTrivialRebase(!LabelType.DEF_COPY_ALL_SCORES_ON_TRIVIAL_REBASE)
          .setCopyAllScoresIfNoCodeChange(!LabelType.DEF_COPY_ALL_SCORES_IF_NO_CODE_CHANGE)
          .setCopyAllScoresIfNoChange(!LabelType.DEF_COPY_ALL_SCORES_IF_NO_CHANGE)
          .setCopyValues(ImmutableList.of((short) 0, (short) 1))
          .setMaxNegative((short) -1)
          .setMaxPositive((short) 1)
          .build();

  @Test
  public void roundTrip() {
    assertThat(deserialize(serialize(ALL_VALUES_SET))).isEqualTo(ALL_VALUES_SET);
  }

  @Test
  public void roundTripWithMinimalValues() {
    LabelType autoValue = ALL_VALUES_SET.toBuilder().setRefPatterns(null).build();
    assertThat(deserialize(serialize(autoValue))).isEqualTo(autoValue);
  }
}
