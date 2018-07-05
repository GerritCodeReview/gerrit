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

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class AccessSectionTest {
  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void getName() {
    String refPattern = "refs/heads/master";
    AccessSection accessSection = new AccessSection(refPattern);
    assertThat(accessSection.getName()).isEqualTo(refPattern);
  }

  @Test
  public void getEmptyPermissions() {
    AccessSection accessSection = new AccessSection("refs/heads/master");
    assertThat(accessSection.getPermissions()).isNotNull();
    assertThat(accessSection.getPermissions()).isEmpty();
  }

  @Test
  public void setAndGetPermissions() {
    AccessSection accessSection = new AccessSection("refs/heads/master");
    Permission abandonPermission = new Permission(Permission.ABANDON);
    Permission rebasePermission = new Permission(Permission.REBASE);
    accessSection.setPermissions(ImmutableList.of(abandonPermission, rebasePermission));
    assertThat(accessSection.getPermissions())
        .containsExactly(abandonPermission, rebasePermission)
        .inOrder();

    Permission submitPermission = new Permission(Permission.SUBMIT);
    accessSection.setPermissions(ImmutableList.of(submitPermission));
    assertThat(accessSection.getPermissions()).containsExactly(submitPermission);
  }

  @Test
  public void cannotSetDuplicatePermissions() {
    AccessSection accessSection = new AccessSection("refs/heads/master");

    exception.expect(IllegalArgumentException.class);
    accessSection.setPermissions(
        ImmutableList.of(new Permission(Permission.ABANDON), new Permission(Permission.ABANDON)));
  }

  @Test
  public void cannotSetPermissionsWithConflictingNames() {
    AccessSection accessSection = new AccessSection("refs/heads/master");
    Permission abandonPermissionLowerCase =
        new Permission(Permission.ABANDON.toLowerCase(Locale.US));
    Permission abandonPermissionUpperCase =
        new Permission(Permission.ABANDON.toUpperCase(Locale.US));

    exception.expect(IllegalArgumentException.class);
    accessSection.setPermissions(
        ImmutableList.of(abandonPermissionLowerCase, abandonPermissionUpperCase));
  }

  @Test
  public void getNonExistingPermission() {
    AccessSection accessSection = new AccessSection("refs/heads/master");
    assertThat(accessSection.getPermission("non-existing")).isNull();
    assertThat(accessSection.getPermission("non-existing", false)).isNull();
  }

  @Test
  public void getPermission() {
    AccessSection accessSection = new AccessSection("refs/heads/master");
    Permission submitPermission = new Permission(Permission.SUBMIT);
    accessSection.setPermissions(ImmutableList.of(submitPermission));
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isEqualTo(submitPermission);
  }

  @Test
  public void getPermissionWithOtherCase() {
    AccessSection accessSection = new AccessSection("refs/heads/master");
    Permission submitPermissionLowerCase = new Permission(Permission.SUBMIT.toLowerCase(Locale.US));
    accessSection.setPermissions(ImmutableList.of(submitPermissionLowerCase));
    assertThat(accessSection.getPermission(Permission.SUBMIT.toUpperCase(Locale.US)))
        .isEqualTo(submitPermissionLowerCase);
  }

  @Test
  public void createMissingPermissionOnGet() {
    AccessSection accessSection = new AccessSection("refs/heads/master");
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isNull();

    assertThat(accessSection.getPermission(Permission.SUBMIT, true))
        .isEqualTo(new Permission(Permission.SUBMIT));
  }

  @Test
  public void addPermission() {
    AccessSection accessSection = new AccessSection("refs/heads/master");
    Permission abandonPermission = new Permission(Permission.ABANDON);
    Permission rebasePermission = new Permission(Permission.REBASE);

    accessSection.setPermissions(ImmutableList.of(abandonPermission, rebasePermission));
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isNull();

    Permission submitPermission = new Permission(Permission.SUBMIT);
    accessSection.addPermission(submitPermission);
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isEqualTo(submitPermission);
    assertThat(accessSection.getPermissions())
        .containsExactly(abandonPermission, rebasePermission, submitPermission)
        .inOrder();
  }

  @Test
  public void cannotAddPermissionByModifyingList() {
    AccessSection accessSection = new AccessSection("refs/heads/master");
    Permission abandonPermission = new Permission(Permission.ABANDON);
    Permission rebasePermission = new Permission(Permission.REBASE);

    List<Permission> permissions = new ArrayList<>();
    permissions.add(abandonPermission);
    permissions.add(rebasePermission);
    accessSection.setPermissions(permissions);
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isNull();

    Permission submitPermission = new Permission(Permission.SUBMIT);
    permissions.add(submitPermission);
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isNull();
  }

  @Test
  public void removePermission() {
    AccessSection accessSection = new AccessSection("refs/heads/master");
    Permission abandonPermission = new Permission(Permission.ABANDON);
    Permission rebasePermission = new Permission(Permission.REBASE);
    Permission submitPermission = new Permission(Permission.SUBMIT);

    accessSection.setPermissions(
        ImmutableList.of(abandonPermission, rebasePermission, submitPermission));
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isNotNull();

    accessSection.remove(submitPermission);
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isNull();
    assertThat(accessSection.getPermissions())
        .containsExactly(abandonPermission, rebasePermission)
        .inOrder();
  }

  @Test
  public void removePermissionByName() {
    AccessSection accessSection = new AccessSection("refs/heads/master");
    Permission abandonPermission = new Permission(Permission.ABANDON);
    Permission rebasePermission = new Permission(Permission.REBASE);
    Permission submitPermission = new Permission(Permission.SUBMIT);

    accessSection.setPermissions(
        ImmutableList.of(abandonPermission, rebasePermission, submitPermission));
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isNotNull();

    accessSection.removePermission(Permission.SUBMIT);
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isNull();
    assertThat(accessSection.getPermissions())
        .containsExactly(abandonPermission, rebasePermission)
        .inOrder();
  }

  @Test
  public void removePermissionByNameOtherCase() {
    AccessSection accessSection = new AccessSection("refs/heads/master");
    Permission abandonPermission = new Permission(Permission.ABANDON);
    Permission rebasePermission = new Permission(Permission.REBASE);

    String submitLowerCase = Permission.SUBMIT.toLowerCase(Locale.US);
    String submitUpperCase = Permission.SUBMIT.toUpperCase(Locale.US);
    Permission submitPermissionLowerCase = new Permission(submitLowerCase);

    accessSection.setPermissions(
        ImmutableList.of(abandonPermission, rebasePermission, submitPermissionLowerCase));
    assertThat(accessSection.getPermission(submitLowerCase)).isNotNull();
    assertThat(accessSection.getPermission(submitUpperCase)).isNotNull();

    accessSection.removePermission(submitUpperCase);
    assertThat(accessSection.getPermission(submitLowerCase)).isNull();
    assertThat(accessSection.getPermission(submitUpperCase)).isNull();
    assertThat(accessSection.getPermissions())
        .containsExactly(abandonPermission, rebasePermission)
        .inOrder();
  }

  @Test
  public void mergeAccessSections() {
    Permission abandonPermission = new Permission(Permission.ABANDON);
    Permission rebasePermission = new Permission(Permission.REBASE);
    Permission submitPermission = new Permission(Permission.SUBMIT);

    AccessSection accessSection1 = new AccessSection("refs/heads/master");
    accessSection1.setPermissions(ImmutableList.of(abandonPermission, rebasePermission));

    AccessSection accessSection2 = new AccessSection("refs/heads/master");
    accessSection2.setPermissions(ImmutableList.of(rebasePermission, submitPermission));

    accessSection1.mergeFrom(accessSection2);
    assertThat(accessSection1.getPermissions())
        .containsExactly(abandonPermission, rebasePermission, submitPermission)
        .inOrder();
  }

  @Test
  public void testEquals() {
    Permission abandonPermission = new Permission(Permission.ABANDON);
    Permission rebasePermission = new Permission(Permission.REBASE);

    AccessSection accessSection = new AccessSection("refs/heads/master");
    accessSection.setPermissions(ImmutableList.of(abandonPermission, rebasePermission));

    AccessSection accessSectionSamePermissionsOtherRef = new AccessSection("refs/heads/other");
    accessSectionSamePermissionsOtherRef.setPermissions(
        ImmutableList.of(abandonPermission, rebasePermission));
    assertThat(accessSection.equals(accessSectionSamePermissionsOtherRef)).isFalse();

    AccessSection accessSectionOther = new AccessSection("refs/heads/master");
    accessSectionOther.setPermissions(ImmutableList.of(abandonPermission));
    assertThat(accessSection.equals(accessSectionOther)).isFalse();

    accessSectionOther.addPermission(rebasePermission);
    assertThat(accessSection.equals(accessSectionOther)).isTrue();
  }
}
