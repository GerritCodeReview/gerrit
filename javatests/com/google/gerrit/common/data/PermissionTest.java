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
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.testing.GerritBaseTests;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class PermissionTest extends GerritBaseTests {
  private static final String PERMISSION_NAME = "foo";

  private Permission permission;

  @Before
  public void setup() {
    this.permission = new Permission(PERMISSION_NAME);
  }

  @Test
  public void isPermission() {
    assertThat(Permission.isPermission(Permission.ABANDON)).isTrue();
    assertThat(Permission.isPermission("no-permission")).isFalse();

    assertThat(Permission.isPermission(Permission.LABEL + "Code-Review")).isTrue();
    assertThat(Permission.isPermission(Permission.LABEL_AS + "Code-Review")).isTrue();
    assertThat(Permission.isPermission("Code-Review")).isFalse();
  }

  @Test
  public void hasRange() {
    assertThat(Permission.hasRange(Permission.ABANDON)).isFalse();
    assertThat(Permission.hasRange("no-permission")).isFalse();

    assertThat(Permission.hasRange(Permission.LABEL + "Code-Review")).isTrue();
    assertThat(Permission.hasRange(Permission.LABEL_AS + "Code-Review")).isTrue();
    assertThat(Permission.hasRange("Code-Review")).isFalse();
  }

  @Test
  public void isLabel() {
    assertThat(Permission.isLabel(Permission.ABANDON)).isFalse();
    assertThat(Permission.isLabel("no-permission")).isFalse();

    assertThat(Permission.isLabel(Permission.LABEL + "Code-Review")).isTrue();
    assertThat(Permission.isLabel(Permission.LABEL_AS + "Code-Review")).isFalse();
    assertThat(Permission.isLabel("Code-Review")).isFalse();
  }

  @Test
  public void isLabelAs() {
    assertThat(Permission.isLabelAs(Permission.ABANDON)).isFalse();
    assertThat(Permission.isLabelAs("no-permission")).isFalse();

    assertThat(Permission.isLabelAs(Permission.LABEL + "Code-Review")).isFalse();
    assertThat(Permission.isLabelAs(Permission.LABEL_AS + "Code-Review")).isTrue();
    assertThat(Permission.isLabelAs("Code-Review")).isFalse();
  }

  @Test
  public void forLabel() {
    assertThat(Permission.forLabel("Code-Review")).isEqualTo(Permission.LABEL + "Code-Review");
  }

  @Test
  public void forLabelAs() {
    assertThat(Permission.forLabelAs("Code-Review")).isEqualTo(Permission.LABEL_AS + "Code-Review");
  }

  @Test
  public void extractLabel() {
    assertThat(Permission.extractLabel(Permission.LABEL + "Code-Review")).isEqualTo("Code-Review");
    assertThat(Permission.extractLabel(Permission.LABEL_AS + "Code-Review"))
        .isEqualTo("Code-Review");
    assertThat(Permission.extractLabel("Code-Review")).isNull();
    assertThat(Permission.extractLabel(Permission.ABANDON)).isNull();
  }

  @Test
  public void canBeOnAllProjects() {
    assertThat(Permission.canBeOnAllProjects(AccessSection.ALL, Permission.ABANDON)).isTrue();
    assertThat(Permission.canBeOnAllProjects(AccessSection.ALL, Permission.OWNER)).isFalse();
    assertThat(Permission.canBeOnAllProjects(AccessSection.ALL, Permission.LABEL + "Code-Review"))
        .isTrue();
    assertThat(
            Permission.canBeOnAllProjects(AccessSection.ALL, Permission.LABEL_AS + "Code-Review"))
        .isTrue();

    assertThat(Permission.canBeOnAllProjects("refs/heads/*", Permission.ABANDON)).isTrue();
    assertThat(Permission.canBeOnAllProjects("refs/heads/*", Permission.OWNER)).isTrue();
    assertThat(Permission.canBeOnAllProjects("refs/heads/*", Permission.LABEL + "Code-Review"))
        .isTrue();
    assertThat(Permission.canBeOnAllProjects("refs/heads/*", Permission.LABEL_AS + "Code-Review"))
        .isTrue();
  }

  @Test
  public void getName() {
    assertThat(permission.getName()).isEqualTo(PERMISSION_NAME);
  }

  @Test
  public void getLabel() {
    assertThat(new Permission(Permission.LABEL + "Code-Review").getLabel())
        .isEqualTo("Code-Review");
    assertThat(new Permission(Permission.LABEL_AS + "Code-Review").getLabel())
        .isEqualTo("Code-Review");
    assertThat(new Permission("Code-Review").getLabel()).isNull();
    assertThat(new Permission(Permission.ABANDON).getLabel()).isNull();
  }

  @Test
  public void exclusiveGroup() {
    assertThat(permission.getExclusiveGroup()).isFalse();

    permission.setExclusiveGroup(true);
    assertThat(permission.getExclusiveGroup()).isTrue();

    permission.setExclusiveGroup(false);
    assertThat(permission.getExclusiveGroup()).isFalse();
  }

  @Test
  public void noExclusiveGroupOnOwnerPermission() {
    Permission permission = new Permission(Permission.OWNER);
    assertThat(permission.getExclusiveGroup()).isFalse();

    permission.setExclusiveGroup(true);
    assertThat(permission.getExclusiveGroup()).isFalse();
  }

  @Test
  public void getEmptyRules() {
    assertThat(permission.getRules()).isNotNull();
    assertThat(permission.getRules()).isEmpty();
  }

  @Test
  public void setAndGetRules() {
    PermissionRule permissionRule1 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-1"), "group1"));
    PermissionRule permissionRule2 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-2"), "group2"));
    permission.setRules(ImmutableList.of(permissionRule1, permissionRule2));
    assertThat(permission.getRules()).containsExactly(permissionRule1, permissionRule2).inOrder();

    PermissionRule permissionRule3 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-3"), "group3"));
    permission.setRules(ImmutableList.of(permissionRule3));
    assertThat(permission.getRules()).containsExactly(permissionRule3);
  }

  @Test
  public void cannotAddPermissionByModifyingListThatWasProvidedToAccessSection() {
    PermissionRule permissionRule1 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-1"), "group1"));
    PermissionRule permissionRule2 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-2"), "group2"));
    GroupReference groupReference3 = new GroupReference(AccountGroup.uuid("uuid-3"), "group3");

    List<PermissionRule> rules = new ArrayList<>();
    rules.add(permissionRule1);
    rules.add(permissionRule2);
    permission.setRules(rules);
    assertThat(permission.getRule(groupReference3)).isNull();

    PermissionRule permissionRule3 = new PermissionRule(groupReference3);
    rules.add(permissionRule3);
    assertThat(permission.getRule(groupReference3)).isNull();
  }

  @Test
  public void getNonExistingRule() {
    GroupReference groupReference = new GroupReference(AccountGroup.uuid("uuid-1"), "group1");
    assertThat(permission.getRule(groupReference)).isNull();
    assertThat(permission.getRule(groupReference, false)).isNull();
  }

  @Test
  public void getRule() {
    GroupReference groupReference = new GroupReference(AccountGroup.uuid("uuid-1"), "group1");
    PermissionRule permissionRule = new PermissionRule(groupReference);
    permission.setRules(ImmutableList.of(permissionRule));
    assertThat(permission.getRule(groupReference)).isEqualTo(permissionRule);
  }

  @Test
  public void createMissingRuleOnGet() {
    GroupReference groupReference = new GroupReference(AccountGroup.uuid("uuid-1"), "group1");
    assertThat(permission.getRule(groupReference)).isNull();

    assertThat(permission.getRule(groupReference, true))
        .isEqualTo(new PermissionRule(groupReference));
  }

  @Test
  public void addRule() {
    PermissionRule permissionRule1 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-1"), "group1"));
    PermissionRule permissionRule2 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-2"), "group2"));
    permission.setRules(ImmutableList.of(permissionRule1, permissionRule2));
    GroupReference groupReference3 = new GroupReference(AccountGroup.uuid("uuid-3"), "group3");
    assertThat(permission.getRule(groupReference3)).isNull();

    PermissionRule permissionRule3 = new PermissionRule(groupReference3);
    permission.add(permissionRule3);
    assertThat(permission.getRule(groupReference3)).isEqualTo(permissionRule3);
    assertThat(permission.getRules())
        .containsExactly(permissionRule1, permissionRule2, permissionRule3)
        .inOrder();
  }

  @Test
  public void removeRule() {
    PermissionRule permissionRule1 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-1"), "group1"));
    PermissionRule permissionRule2 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-2"), "group2"));
    GroupReference groupReference3 = new GroupReference(AccountGroup.uuid("uuid-3"), "group3");
    PermissionRule permissionRule3 = new PermissionRule(groupReference3);

    permission.setRules(ImmutableList.of(permissionRule1, permissionRule2, permissionRule3));
    assertThat(permission.getRule(groupReference3)).isNotNull();

    permission.remove(permissionRule3);
    assertThat(permission.getRule(groupReference3)).isNull();
    assertThat(permission.getRules()).containsExactly(permissionRule1, permissionRule2).inOrder();
  }

  @Test
  public void removeRuleByGroupReference() {
    PermissionRule permissionRule1 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-1"), "group1"));
    PermissionRule permissionRule2 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-2"), "group2"));
    GroupReference groupReference3 = new GroupReference(AccountGroup.uuid("uuid-3"), "group3");
    PermissionRule permissionRule3 = new PermissionRule(groupReference3);

    permission.setRules(ImmutableList.of(permissionRule1, permissionRule2, permissionRule3));
    assertThat(permission.getRule(groupReference3)).isNotNull();

    permission.removeRule(groupReference3);
    assertThat(permission.getRule(groupReference3)).isNull();
    assertThat(permission.getRules()).containsExactly(permissionRule1, permissionRule2).inOrder();
  }

  @Test
  public void clearRules() {
    PermissionRule permissionRule1 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-1"), "group1"));
    PermissionRule permissionRule2 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-2"), "group2"));

    permission.setRules(ImmutableList.of(permissionRule1, permissionRule2));
    assertThat(permission.getRules()).isNotEmpty();

    permission.clearRules();
    assertThat(permission.getRules()).isEmpty();
  }

  @Test
  public void mergePermissions() {
    PermissionRule permissionRule1 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-1"), "group1"));
    PermissionRule permissionRule2 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-2"), "group2"));
    PermissionRule permissionRule3 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-3"), "group3"));

    Permission permission1 = new Permission("foo");
    permission1.setRules(ImmutableList.of(permissionRule1, permissionRule2));

    Permission permission2 = new Permission("bar");
    permission2.setRules(ImmutableList.of(permissionRule2, permissionRule3));

    permission1.mergeFrom(permission2);
    assertThat(permission1.getRules())
        .containsExactly(permissionRule1, permissionRule2, permissionRule3)
        .inOrder();
  }

  @Test
  public void testEquals() {
    PermissionRule permissionRule1 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-1"), "group1"));
    PermissionRule permissionRule2 =
        new PermissionRule(new GroupReference(AccountGroup.uuid("uuid-2"), "group2"));

    permission.setRules(ImmutableList.of(permissionRule1, permissionRule2));

    Permission permissionSameRulesOtherName = new Permission("bar");
    permissionSameRulesOtherName.setRules(ImmutableList.of(permissionRule1, permissionRule2));
    assertThat(permission.equals(permissionSameRulesOtherName)).isFalse();

    Permission permissionSameRulesSameNameOtherExclusiveGroup = new Permission("foo");
    permissionSameRulesSameNameOtherExclusiveGroup.setRules(
        ImmutableList.of(permissionRule1, permissionRule2));
    permissionSameRulesSameNameOtherExclusiveGroup.setExclusiveGroup(true);
    assertThat(permission.equals(permissionSameRulesSameNameOtherExclusiveGroup)).isFalse();

    Permission permissionOther = new Permission(PERMISSION_NAME);
    permissionOther.setRules(ImmutableList.of(permissionRule1));
    assertThat(permission.equals(permissionOther)).isFalse();

    permissionOther.add(permissionRule2);
    assertThat(permission.equals(permissionOther)).isTrue();
  }
}
