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

package com.google.gerrit.acceptance.testsuite.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.capabilityKey;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.labelPermissionKey;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.common.data.GlobalCapability.ADMINISTRATE_SERVER;
import static com.google.gerrit.common.data.GlobalCapability.BATCH_CHANGES_LIMIT;
import static com.google.gerrit.common.data.GlobalCapability.DEFAULT_MAX_BATCH_CHANGES_LIMIT;
import static com.google.gerrit.common.data.GlobalCapability.DEFAULT_MAX_QUERY_LIMIT;
import static com.google.gerrit.common.data.GlobalCapability.QUERY_LIMIT;
import static com.google.gerrit.entities.Permission.ABANDON;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.TestCapability;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.TestLabelPermission;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.TestPermissionKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.AllProjectsName;
import java.util.function.Function;
import org.junit.Test;

public class TestProjectUpdateTest {
  private static final AllProjectsName ALL_PROJECTS_NAME = new AllProjectsName("All-Projects");

  @Test
  public void testCapabilityDisallowsZeroRangeOnCapabilityThatHasNoRange() throws Exception {
    assertThrows(
        RuntimeException.class,
        () -> allowCapability(ADMINISTRATE_SERVER).group(REGISTERED_USERS).range(0, 0).build());
  }

  @Test
  public void testCapabilityAllowsZeroRangeOnCapabilityThatHasRange() throws Exception {
    TestCapability c = allowCapability(QUERY_LIMIT).group(REGISTERED_USERS).range(0, 0).build();
    assertThat(c.min()).isEqualTo(0);
    assertThat(c.max()).isEqualTo(0);
  }

  @Test
  public void testCapabilityDisallowsInvertedRange() throws Exception {
    assertThrows(
        RuntimeException.class,
        () -> allowCapability(QUERY_LIMIT).group(REGISTERED_USERS).range(1, 0).build());
  }

  @Test
  public void testCapabilityDisallowsRangeIfCapabilityDoesNotSupportRange() throws Exception {
    assertThrows(
        RuntimeException.class,
        () -> allowCapability(ADMINISTRATE_SERVER).group(REGISTERED_USERS).range(-1, 1).build());
  }

  @Test
  public void testCapabilityRangeIsZeroIfCapabilityDoesNotSupportRange() throws Exception {
    TestCapability c = allowCapability(ADMINISTRATE_SERVER).group(REGISTERED_USERS).build();
    assertThat(c.min()).isEqualTo(0);
    assertThat(c.max()).isEqualTo(0);
  }

  @Test
  public void testCapabilityUsesDefaultRangeIfUnspecified() throws Exception {
    TestCapability c = allowCapability(QUERY_LIMIT).group(REGISTERED_USERS).build();
    assertThat(c.min()).isEqualTo(0);
    assertThat(c.max()).isEqualTo(DEFAULT_MAX_QUERY_LIMIT);

    c = allowCapability(BATCH_CHANGES_LIMIT).group(REGISTERED_USERS).build();
    assertThat(c.min()).isEqualTo(0);
    assertThat(c.max()).isEqualTo(DEFAULT_MAX_BATCH_CHANGES_LIMIT);
  }

  @Test
  public void testCapabilityUsesExplicitRangeIfSpecified() throws Exception {
    TestCapability c = allowCapability(QUERY_LIMIT).group(REGISTERED_USERS).range(5, 20).build();
    assertThat(c.min()).isEqualTo(5);
    assertThat(c.max()).isEqualTo(20);
  }

  @Test
  public void testLabelPermissionRequiresValidLabelName() throws Exception {
    Function<String, TestLabelPermission.Builder> labelBuilder =
        name -> allowLabel(name).ref("refs/*").group(REGISTERED_USERS).range(-1, 1);
    assertThat(labelBuilder.apply("Code-Review").build().name()).isEqualTo("Code-Review");
    assertThrows(RuntimeException.class, () -> labelBuilder.apply("not a label").build());
    assertThrows(RuntimeException.class, () -> labelBuilder.apply("label-Code-Review").build());
  }

  @Test
  public void testLabelPermissionDisallowsZeroRange() throws Exception {
    assertThrows(
        RuntimeException.class,
        () -> allowLabel("Code-Review").ref("refs/*").group(REGISTERED_USERS).range(0, 0).build());
  }

  @Test
  public void testLabelPermissionDisallowsInvertedRange() throws Exception {
    assertThrows(
        RuntimeException.class,
        () -> allowLabel("Code-Review").ref("refs/*").group(REGISTERED_USERS).range(1, 0).build());
  }

  @Test
  public void testPermissionKeyRequiresValidRefName() throws Exception {
    Function<String, TestPermissionKey.Builder> keyBuilder =
        ref -> permissionKey(ABANDON).ref(ref).group(REGISTERED_USERS);
    assertThat(keyBuilder.apply("refs/*").build().section()).isEqualTo("refs/*");
    assertThrows(RuntimeException.class, () -> keyBuilder.apply(null).build());
    assertThrows(RuntimeException.class, () -> keyBuilder.apply("foo").build());
  }

  @Test
  public void testLabelPermissionKeyRequiresValidLabelName() throws Exception {
    Function<String, TestPermissionKey.Builder> keyBuilder =
        label -> labelPermissionKey(label).ref("refs/*").group(REGISTERED_USERS);
    assertThat(keyBuilder.apply("Code-Review").build().name()).isEqualTo("label-Code-Review");
    assertThrows(RuntimeException.class, () -> keyBuilder.apply(null).build());
    assertThrows(RuntimeException.class, () -> keyBuilder.apply("not a label").build());
    assertThrows(RuntimeException.class, () -> keyBuilder.apply("label-Code-Review").build());
  }

  @Test
  public void testPermissionKeyDisallowsSettingRefOnGlobalCapability() throws Exception {
    assertThrows(RuntimeException.class, () -> capabilityKey(ADMINISTRATE_SERVER).ref("refs/*"));
  }

  @Test
  public void testProjectUpdateDisallowsGroupOnExclusiveGroupPermissionKey() throws Exception {
    TestPermissionKey.Builder b = permissionKey(ABANDON).ref("refs/*");
    Function<TestPermissionKey.Builder, TestProjectUpdate.Builder> updateBuilder =
        kb -> builder().setExclusiveGroup(kb, true);

    assertThat(updateBuilder.apply(b).build().exclusiveGroupPermissions())
        .containsExactly(b.build(), true);

    b.group(REGISTERED_USERS);
    assertThrows(RuntimeException.class, () -> updateBuilder.apply(b).build());
  }

  @Test
  public void hasCapabilityUpdates() throws Exception {
    assertThat(builder().build().hasCapabilityUpdates()).isFalse();
    assertThat(
            builder()
                .add(allow(ABANDON).ref("refs/*").group(REGISTERED_USERS))
                .add(allowLabel("Code-Review").ref("refs/*").group(REGISTERED_USERS).range(0, 1))
                .remove(permissionKey(ABANDON).ref("refs/foo"))
                .remove(labelPermissionKey("Code-Review").ref("refs/foo"))
                .setExclusiveGroup(permissionKey(ABANDON).ref("refs/bar"), true)
                .setExclusiveGroup(labelPermissionKey(ABANDON).ref("refs/bar"), true)
                .build()
                .hasCapabilityUpdates())
        .isFalse();
    assertThat(
            builder(ALL_PROJECTS_NAME)
                .add(allowCapability(ADMINISTRATE_SERVER).group(REGISTERED_USERS))
                .build()
                .hasCapabilityUpdates())
        .isTrue();
    assertThat(
            builder(ALL_PROJECTS_NAME)
                .remove(capabilityKey(ADMINISTRATE_SERVER))
                .build()
                .hasCapabilityUpdates())
        .isTrue();
  }

  @Test
  public void updatingCapabilitiesNotAllowedForNonAllProjects() throws Exception {
    assertThrows(
        RuntimeException.class,
        () -> builder().add(allowCapability(ADMINISTRATE_SERVER).group(REGISTERED_USERS)).update());
    assertThrows(
        RuntimeException.class,
        () -> builder().remove(capabilityKey(ADMINISTRATE_SERVER)).update());
  }

  private static TestProjectUpdate.Builder builder() {
    return builder(Project.nameKey("test-project"));
  }

  private static TestProjectUpdate.Builder builder(Project.NameKey nameKey) {
    return TestProjectUpdate.builder(nameKey, ALL_PROJECTS_NAME, u -> {});
  }
}
