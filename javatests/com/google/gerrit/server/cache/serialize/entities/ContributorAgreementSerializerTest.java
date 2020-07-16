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
import static com.google.gerrit.server.cache.serialize.entities.ContributorAgreementSerializer.deserialize;
import static com.google.gerrit.server.cache.serialize.entities.ContributorAgreementSerializer.serialize;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.ContributorAgreement;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.PermissionRule;
import org.junit.Test;

public class ContributorAgreementSerializerTest {
  @Test
  public void roundTrip() {
    ContributorAgreement autoValue =
        ContributorAgreement.builder("name")
            .setDescription("desc")
            .setAgreementUrl("url")
            .setAutoVerify(GroupReference.create("auto-verify"))
            .setAccepted(
                ImmutableList.of(
                    PermissionRule.create(GroupReference.create("accepted1")),
                    PermissionRule.create(GroupReference.create("accepted2"))))
            .setExcludeProjectsRegexes(ImmutableList.of("refs/*"))
            .setMatchProjectsRegexes(ImmutableList.of("refs/heads/*"))
            .build();
    assertThat(deserialize(serialize(autoValue))).isEqualTo(autoValue);
  }

  @Test
  public void roundTripWithMinimalValues() {
    ContributorAgreement autoValue =
        ContributorAgreement.builder("name")
            .setAccepted(
                ImmutableList.of(
                    PermissionRule.create(GroupReference.create("accepted1")),
                    PermissionRule.create(GroupReference.create("accepted2"))))
            .build();
    assertThat(deserialize(serialize(autoValue))).isEqualTo(autoValue);
  }
}
