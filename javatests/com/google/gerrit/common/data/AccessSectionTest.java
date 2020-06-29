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

package com.google.gerrit.common.data;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import java.util.Locale;
import org.junit.Before;
import org.junit.Test;

public class AccessSectionTest {
  private static final String REF_PATTERN = "refs/heads/master";

  private AccessSection.Builder accessSection;

  @Before
  public void setup() {
    this.accessSection = AccessSection.builder(REF_PATTERN);
  }

  @Test
  public void getName() {
    assertThat(accessSection.getName()).isEqualTo(REF_PATTERN);
  }

  @Test
  public void getEmptyPermissions() {
    assertThat(accessSection.getPermissionBuilders()).isNotNull();
    assertThat(accessSection.getPermissionBuilders()).isEmpty();
  }

  @Test
  public void setAndGetPermissions() {
    Permission.Builder abandonPermission = Permission.builder(Permission.ABANDON);
    Permission.Builder rebasePermission = Permission.builder(Permission.REBASE);
    accessSection.setPermissionBuilders(ImmutableList.of(abandonPermission, rebasePermission));
    assertThat(accessSection.getPermissionBuilders())
        .containsExactly(abandonPermission, rebasePermission)
        .inOrder();

    Permission.Builder submitPermission = Permission.builder(Permission.SUBMIT);
    accessSection.setPermissionBuilders(ImmutableList.of(submitPermission));
    assertThat(accessSection.getPermissionBuilders()).containsExactly(submitPermission);
    assertThrows(NullPointerException.class, () -> accessSection.setPermissions(null));
  }

  @Test
  public void cannotSetDuplicatePermissions() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            accessSection
                .setPermissionBuilders(
                    ImmutableList.of(
                        Permission.builder(Permission.ABANDON),
                        Permission.builder(Permission.ABANDON)))
                .build());
  }

  @Test
  public void cannotSetPermissionsWithConflictingNames() {
    Permission.Builder abandonPermissionLowerCase =
        Permission.builder(Permission.ABANDON.toLowerCase(Locale.US));
    Permission.Builder abandonPermissionUpperCase =
        Permission.builder(Permission.ABANDON.toUpperCase(Locale.US));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            accessSection
                .setPermissionBuilders(
                    ImmutableList.of(abandonPermissionLowerCase, abandonPermissionUpperCase))
                .build());
  }

  @Test
  public void getNonExistingPermission() {
    assertThat(accessSection.build().getPermission("non-existing")).isNull();
    assertThat(accessSection.build().getPermission("non-existing")).isNull();
  }

  @Test
  public void getPermission() {
    Permission.Builder submitPermission = Permission.builder(Permission.SUBMIT);
    accessSection.setPermissionBuilders(ImmutableList.of(submitPermission));
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isEqualTo(submitPermission);
    assertThrows(IllegalArgumentException.class, () -> accessSection.getPermission(null));
  }

  @Test
  public void getPermissionWithOtherCase() {
    Permission.Builder submitPermissionLowerCase =
        Permission.builder(Permission.SUBMIT.toLowerCase(Locale.US));
    accessSection.setPermissionBuilders(ImmutableList.of(submitPermissionLowerCase));
    assertThat(accessSection.getPermission(Permission.SUBMIT.toUpperCase(Locale.US)))
        .isEqualTo(submitPermissionLowerCase);
  }

  @Test
  public void createMissingPermissionOnGet() {
    assertThat(accessSection.build().getPermission(Permission.SUBMIT)).isNull();

    assertThat(accessSection.getPermission(Permission.SUBMIT).build())
        .isEqualTo(Permission.create(Permission.SUBMIT));

    assertThrows(IllegalArgumentException.class, () -> accessSection.getPermission(null));
  }

  @Test
  public void addPermission() {
    Permission.Builder abandonPermission = Permission.builder(Permission.ABANDON);
    Permission.Builder rebasePermission = Permission.builder(Permission.REBASE);

    accessSection.setPermissionBuilders(ImmutableList.of(abandonPermission, rebasePermission));
    assertThat(accessSection.build().getPermission(Permission.SUBMIT)).isNull();

    Permission.Builder submitPermission = Permission.builder(Permission.SUBMIT);
    accessSection.addPermission(submitPermission);
    assertThat(accessSection.build().getPermission(Permission.SUBMIT))
        .isEqualTo(submitPermission.build());
    assertThat(accessSection.build().getPermissions())
        .containsExactly(
            abandonPermission.build(), rebasePermission.build(), submitPermission.build())
        .inOrder();
    assertThrows(IllegalArgumentException.class, () -> accessSection.addPermission(null));
  }

  @Test
  public void removePermission() {
    Permission.Builder abandonPermission = Permission.builder(Permission.ABANDON);
    Permission.Builder rebasePermission = Permission.builder(Permission.REBASE);
    Permission.Builder submitPermission = Permission.builder(Permission.SUBMIT);

    accessSection.setPermissionBuilders(
        ImmutableList.of(abandonPermission, rebasePermission, submitPermission));
    assertThat(accessSection.build().getPermission(Permission.SUBMIT)).isNotNull();

    accessSection.remove(submitPermission);
    assertThat(accessSection.build().getPermission(Permission.SUBMIT)).isNull();
    assertThat(accessSection.build().getPermissions())
        .containsExactly(abandonPermission.build(), rebasePermission.build())
        .inOrder();
    assertThrows(IllegalArgumentException.class, () -> accessSection.remove(null));
  }

  @Test
  public void removePermissionByName() {
    Permission.Builder abandonPermission = Permission.builder(Permission.ABANDON);
    Permission.Builder rebasePermission = Permission.builder(Permission.REBASE);
    Permission.Builder submitPermission = Permission.builder(Permission.SUBMIT);

    accessSection.setPermissionBuilders(
        ImmutableList.of(abandonPermission, rebasePermission, submitPermission));
    assertThat(accessSection.build().getPermission(Permission.SUBMIT)).isNotNull();

    accessSection.removePermission(Permission.SUBMIT);
    assertThat(accessSection.build().getPermission(Permission.SUBMIT)).isNull();
    assertThat(accessSection.build().getPermissions())
        .containsExactly(abandonPermission.build(), rebasePermission.build())
        .inOrder();

    assertThrows(IllegalArgumentException.class, () -> accessSection.removePermission(null));
  }

  @Test
  public void removePermissionByNameOtherCase() {
    Permission.Builder abandonPermission = Permission.builder(Permission.ABANDON);
    Permission.Builder rebasePermission = Permission.builder(Permission.REBASE);

    String submitLowerCase = Permission.SUBMIT.toLowerCase(Locale.US);
    String submitUpperCase = Permission.SUBMIT.toUpperCase(Locale.US);
    Permission.Builder submitPermissionLowerCase = Permission.builder(submitLowerCase);

    accessSection.setPermissionBuilders(
        ImmutableList.of(abandonPermission, rebasePermission, submitPermissionLowerCase));
    assertThat(accessSection.build().getPermission(submitLowerCase)).isNotNull();
    assertThat(accessSection.build().getPermission(submitUpperCase)).isNotNull();

    accessSection.removePermission(submitUpperCase);
    assertThat(accessSection.build().getPermission(submitLowerCase)).isNull();
    assertThat(accessSection.build().getPermission(submitUpperCase)).isNull();
    assertThat(accessSection.build().getPermissions())
        .containsExactly(abandonPermission.build(), rebasePermission.build())
        .inOrder();
  }

  @Test
  public void testEquals() {
    Permission.Builder abandonPermission = Permission.builder(Permission.ABANDON);
    Permission.Builder rebasePermission = Permission.builder(Permission.REBASE);

    accessSection.setPermissionBuilders(ImmutableList.of(abandonPermission, rebasePermission));

    AccessSection.Builder accessSectionSamePermissionsOtherRef =
        AccessSection.builder("refs/heads/other");
    accessSectionSamePermissionsOtherRef.setPermissionBuilders(
        ImmutableList.of(abandonPermission, rebasePermission));
    assertThat(accessSection.build().equals(accessSectionSamePermissionsOtherRef.build()))
        .isFalse();

    AccessSection.Builder accessSectionOther = AccessSection.builder(REF_PATTERN);
    accessSectionOther.setPermissionBuilders(ImmutableList.of(abandonPermission));
    assertThat(accessSection.build().equals(accessSectionOther.build())).isFalse();

    accessSectionOther.addPermission(rebasePermission);
    assertThat(accessSection.build().equals(accessSectionOther.build())).isTrue();
  }
}
