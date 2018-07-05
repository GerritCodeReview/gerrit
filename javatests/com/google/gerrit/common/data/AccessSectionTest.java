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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class AccessSectionTest {
  @Rule public ExpectedException exception = ExpectedException.none();

  private static final String REF_PATTERN = "refs/heads/master";

  private AccessSection accessSection;

  @Before
  public void setup() {
    this.accessSection = new AccessSection(REF_PATTERN);
  }

  @Test
  public void getName() {
    assertThat(accessSection.getName()).isEqualTo(REF_PATTERN);
  }

  @Test
  public void getEmptyPermissions() {
    assertThat(accessSection.getPermissions()).isNotNull();
    assertThat(accessSection.getPermissions()).isEmpty();
  }

  @Test
  public void setAndGetPermissions() {
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
    exception.expect(IllegalArgumentException.class);
    accessSection.setPermissions(
        ImmutableList.of(new Permission(Permission.ABANDON), new Permission(Permission.ABANDON)));
  }

  @Test
  public void cannotSetPermissionsWithConflictingNames() {
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
    assertThat(accessSection.getPermission("non-existing")).isNull();
    assertThat(accessSection.getPermission("non-existing", false)).isNull();
  }

  @Test
  public void getPermission() {
    Permission submitPermission = new Permission(Permission.SUBMIT);
    accessSection.setPermissions(ImmutableList.of(submitPermission));
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isEqualTo(submitPermission);
  }

  @Test
  public void getPermissionWithOtherCase() {
    Permission submitPermissionLowerCase = new Permission(Permission.SUBMIT.toLowerCase(Locale.US));
    accessSection.setPermissions(ImmutableList.of(submitPermissionLowerCase));
    assertThat(accessSection.getPermission(Permission.SUBMIT.toUpperCase(Locale.US)))
        .isEqualTo(submitPermissionLowerCase);
  }

  @Test
  public void createMissingPermissionOnGet() {
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isNull();

    assertThat(accessSection.getPermission(Permission.SUBMIT, true))
        .isEqualTo(new Permission(Permission.SUBMIT));
  }

  @Test
  public void addPermission() {
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
  public void cannotAddPermissionByModifyingListThatWasProvidedToAccessSection() {
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
  public void cannotAddPermissionByModifyingListThatWasRetrievedFromAccessSection() {
    Permission submitPermission = new Permission(Permission.SUBMIT);
    accessSection.getPermissions().add(submitPermission);
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isNull();

    List<Permission> permissions = new ArrayList<>();
    permissions.add(new Permission(Permission.ABANDON));
    permissions.add(new Permission(Permission.REBASE));
    accessSection.setPermissions(permissions);
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isNull();
    accessSection.getPermissions().add(submitPermission);
    assertThat(accessSection.getPermission(Permission.SUBMIT)).isNull();
  }

  @Test
  public void removePermission() {
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

    AccessSection accessSection1 = new AccessSection("refs/heads/foo");
    accessSection1.setPermissions(ImmutableList.of(abandonPermission, rebasePermission));

    AccessSection accessSection2 = new AccessSection("refs/heads/bar");
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

    accessSection.setPermissions(ImmutableList.of(abandonPermission, rebasePermission));

    AccessSection accessSectionSamePermissionsOtherRef = new AccessSection("refs/heads/other");
    accessSectionSamePermissionsOtherRef.setPermissions(
        ImmutableList.of(abandonPermission, rebasePermission));
    assertThat(accessSection.equals(accessSectionSamePermissionsOtherRef)).isFalse();

    AccessSection accessSectionOther = new AccessSection(REF_PATTERN);
    accessSectionOther.setPermissions(ImmutableList.of(abandonPermission));
    assertThat(accessSection.equals(accessSectionOther)).isFalse();

    accessSectionOther.addPermission(rebasePermission);
    assertThat(accessSection.equals(accessSectionOther)).isTrue();
  }
}
