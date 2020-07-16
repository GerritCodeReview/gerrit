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

package com.google.gerrit.entities;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

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
    AccessSection builtAccessSection = accessSection.build();
    assertThat(builtAccessSection.getPermissions()).isNotNull();
    assertThat(builtAccessSection.getPermissions()).isEmpty();
  }

  @Test
  public void setAndGetPermissions() {
    Permission.Builder abandonPermission = Permission.builder(Permission.ABANDON);
    Permission.Builder rebasePermission = Permission.builder(Permission.REBASE);
    accessSection.modifyPermissions(
        permissions -> {
          permissions.clear();
          permissions.add(abandonPermission);
          permissions.add(rebasePermission);
        });

    AccessSection builtAccessSection = accessSection.build();
    assertThat(builtAccessSection.getPermissions()).hasSize(2);
    assertThat(builtAccessSection.getPermission(abandonPermission.getName())).isNotNull();
    assertThat(builtAccessSection.getPermission(rebasePermission.getName())).isNotNull();

    Permission.Builder submitPermission = Permission.builder(Permission.SUBMIT);
    accessSection.modifyPermissions(
        p -> {
          p.clear();
          p.add(submitPermission);
        });
    builtAccessSection = accessSection.build();
    assertThat(builtAccessSection.getPermissions()).hasSize(1);
    assertThat(builtAccessSection.getPermission(submitPermission.getName())).isNotNull();
    assertThrows(NullPointerException.class, () -> accessSection.setPermissions(null));
  }

  @Test
  public void cannotSetDuplicatePermissions() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            accessSection
                .addPermission(Permission.builder(Permission.ABANDON))
                .addPermission(Permission.builder(Permission.ABANDON))
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
                .addPermission(abandonPermissionLowerCase)
                .addPermission(abandonPermissionUpperCase)
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
    accessSection.addPermission(submitPermission);
    assertThat(accessSection.upsertPermission(Permission.SUBMIT)).isEqualTo(submitPermission);
    assertThrows(NullPointerException.class, () -> accessSection.upsertPermission(null));
  }

  @Test
  public void getPermissionWithOtherCase() {
    Permission.Builder submitPermissionLowerCase =
        Permission.builder(Permission.SUBMIT.toLowerCase(Locale.US));
    accessSection.addPermission(submitPermissionLowerCase);
    assertThat(accessSection.upsertPermission(Permission.SUBMIT.toUpperCase(Locale.US)))
        .isEqualTo(submitPermissionLowerCase);
  }

  @Test
  public void createMissingPermissionOnGet() {
    assertThat(accessSection.build().getPermission(Permission.SUBMIT)).isNull();

    assertThat(accessSection.upsertPermission(Permission.SUBMIT).build())
        .isEqualTo(Permission.create(Permission.SUBMIT));

    assertThrows(NullPointerException.class, () -> accessSection.upsertPermission(null));
  }

  @Test
  public void addPermission() {
    Permission.Builder abandonPermission = Permission.builder(Permission.ABANDON);
    Permission.Builder rebasePermission = Permission.builder(Permission.REBASE);

    accessSection.addPermission(abandonPermission);
    accessSection.addPermission(rebasePermission);
    assertThat(accessSection.build().getPermission(Permission.SUBMIT)).isNull();

    Permission.Builder submitPermission = Permission.builder(Permission.SUBMIT);
    accessSection.addPermission(submitPermission);
    assertThat(accessSection.build().getPermission(Permission.SUBMIT))
        .isEqualTo(submitPermission.build());
    assertThat(accessSection.build().getPermissions())
        .containsExactly(
            abandonPermission.build(), rebasePermission.build(), submitPermission.build())
        .inOrder();
    assertThrows(NullPointerException.class, () -> accessSection.addPermission(null));
  }

  @Test
  public void removePermission() {
    Permission.Builder abandonPermission = Permission.builder(Permission.ABANDON);
    Permission.Builder rebasePermission = Permission.builder(Permission.REBASE);
    Permission.Builder submitPermission = Permission.builder(Permission.SUBMIT);

    accessSection.addPermission(abandonPermission);
    accessSection.addPermission(rebasePermission);
    accessSection.addPermission(submitPermission);
    assertThat(accessSection.build().getPermission(Permission.SUBMIT)).isNotNull();

    accessSection.remove(submitPermission);
    assertThat(accessSection.build().getPermission(Permission.SUBMIT)).isNull();
    assertThat(accessSection.build().getPermissions())
        .containsExactly(abandonPermission.build(), rebasePermission.build())
        .inOrder();
    assertThrows(NullPointerException.class, () -> accessSection.remove(null));
  }

  @Test
  public void removePermissionByName() {
    Permission.Builder abandonPermission = Permission.builder(Permission.ABANDON);
    Permission.Builder rebasePermission = Permission.builder(Permission.REBASE);
    Permission.Builder submitPermission = Permission.builder(Permission.SUBMIT);

    accessSection.addPermission(abandonPermission);
    accessSection.addPermission(rebasePermission);
    accessSection.addPermission(submitPermission);
    AccessSection builtAccessSection = accessSection.build();
    assertThat(builtAccessSection.getPermission(Permission.SUBMIT)).isNotNull();

    accessSection.removePermission(Permission.SUBMIT);
    builtAccessSection = accessSection.build();
    assertThat(builtAccessSection.getPermission(Permission.SUBMIT)).isNull();
    assertThat(builtAccessSection.getPermissions())
        .containsExactly(abandonPermission.build(), rebasePermission.build())
        .inOrder();

    assertThrows(NullPointerException.class, () -> accessSection.removePermission(null));
  }

  @Test
  public void removePermissionByNameOtherCase() {
    Permission.Builder abandonPermission = Permission.builder(Permission.ABANDON);
    Permission.Builder rebasePermission = Permission.builder(Permission.REBASE);

    String submitLowerCase = Permission.SUBMIT.toLowerCase(Locale.US);
    String submitUpperCase = Permission.SUBMIT.toUpperCase(Locale.US);
    Permission.Builder submitPermissionLowerCase = Permission.builder(submitLowerCase);

    accessSection.addPermission(abandonPermission);
    accessSection.addPermission(rebasePermission);
    accessSection.addPermission(submitPermissionLowerCase);
    AccessSection builtAccessSection = accessSection.build();
    assertThat(builtAccessSection.getPermission(submitLowerCase)).isNotNull();
    assertThat(builtAccessSection.getPermission(submitUpperCase)).isNotNull();

    accessSection.removePermission(submitUpperCase);
    builtAccessSection = accessSection.build();
    assertThat(builtAccessSection.getPermission(submitLowerCase)).isNull();
    assertThat(builtAccessSection.getPermission(submitUpperCase)).isNull();
    assertThat(builtAccessSection.getPermissions())
        .containsExactly(abandonPermission.build(), rebasePermission.build())
        .inOrder();
  }

  @Test
  public void testEquals() {
    Permission.Builder abandonPermission = Permission.builder(Permission.ABANDON);
    Permission.Builder rebasePermission = Permission.builder(Permission.REBASE);

    accessSection.addPermission(abandonPermission);
    accessSection.addPermission(rebasePermission);

    AccessSection builtAccessSection = accessSection.build();
    AccessSection.Builder accessSectionSamePermissionsOtherRef =
        AccessSection.builder("refs/heads/other");
    accessSectionSamePermissionsOtherRef.addPermission(abandonPermission);
    accessSectionSamePermissionsOtherRef.addPermission(rebasePermission);
    assertThat(builtAccessSection.equals(accessSectionSamePermissionsOtherRef.build())).isFalse();

    AccessSection.Builder accessSectionOther = AccessSection.builder(REF_PATTERN);
    accessSectionOther.addPermission(abandonPermission);
    assertThat(builtAccessSection.equals(accessSectionOther.build())).isFalse();

    accessSectionOther.addPermission(rebasePermission);
    assertThat(builtAccessSection.equals(accessSectionOther.build())).isTrue();
  }
}
