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
import static com.google.gerrit.server.cache.serialize.entities.CachedProjectConfigSerializer.deserialize;
import static com.google.gerrit.server.cache.serialize.entities.CachedProjectConfigSerializer.serialize;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.AccountsSection;
import com.google.gerrit.entities.CachedProjectConfig;
import com.google.gerrit.entities.ConfiguredMimeTypes;
import com.google.gerrit.entities.PermissionRule;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class CachedProjectConfigSerializerTest {
  static final CachedProjectConfig MINIMAL_VALUES_SET =
      CachedProjectConfig.builder()
          .setProject(ProjectSerializerTest.ALL_VALUES_SET)
          .setMimeTypes(
              ConfiguredMimeTypes.create(
                  ImmutableList.of(new ConfiguredMimeTypes.ReType("type", "pattern"))))
          .setAccountsSection(
              AccountsSection.create(
                  ImmutableList.of(
                      PermissionRule.create(GroupReferenceSerializerTest.ALL_VALUES_SET))))
          .setMaxObjectSizeLimit(123)
          .setCheckReceivedObjects(true)
          .build();

  static final CachedProjectConfig ALL_VALUES_SET =
      MINIMAL_VALUES_SET
          .toBuilder()
          .addGroup(GroupReferenceSerializerTest.ALL_VALUES_SET)
          .addAccessSection(AccessSectionSerializerTest.ALL_VALUES_SET)
          .setBranchOrderSection(Optional.of(BranchOrderSectionSerializerTest.ALL_VALUES_SET))
          .addNotifySection(NotifyConfigSerializerTest.ALL_VALUES_SET)
          .addLabelSection(LabelTypeSerializerTest.ALL_VALUES_SET)
          .addSubscribeSection(SubscribeSectionSerializerTest.ALL_VALUES_SET)
          .addCommentLinkSection(StoredCommentLinkInfoSerializerTest.HTML_ONLY)
          .setRevision(Optional.of(ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef")))
          .setRulesId(Optional.of(ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef")))
          .setExtensionPanelSections(ImmutableMap.of("key1", ImmutableList.of("val1", "val2")))
          .build();

  @Test
  public void roundTrip() {
    assertThat(deserialize(serialize(ALL_VALUES_SET))).isEqualTo(ALL_VALUES_SET);
  }

  @Test
  public void roundTripWithMinimalValues() {
    assertThat(deserialize(serialize(MINIMAL_VALUES_SET))).isEqualTo(MINIMAL_VALUES_SET);
  }
}
