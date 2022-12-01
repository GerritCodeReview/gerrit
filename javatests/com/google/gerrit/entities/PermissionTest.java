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

import org.junit.Before;
import org.junit.Test;

public class PermissionTest {
  private static final String PERMISSION_NAME = "foo";

  private Permission.Builder permission;

  @Before
  public void setup() {
    this.permission = Permission.builder(PERMISSION_NAME);
  }

  @Test
  public void isPermission() {
    assertThat(Permission.isPermission(Permission.ABANDON)).isTrue();
    assertThat(Permission.isPermission("no-permission")).isFalse();

    assertThat(Permission.isPermission(Permission.LABEL + LabelId.CODE_REVIEW)).isTrue();
    assertThat(Permission.isPermission(Permission.LABEL_AS + LabelId.CODE_REVIEW)).isTrue();
    assertThat(Permission.isPermission(Permission.REMOVE_LABEL_AS + LabelId.CODE_REVIEW)).isTrue();
    assertThat(Permission.isPermission(LabelId.CODE_REVIEW)).isFalse();
  }

  @Test
  public void hasRange() {
    assertThat(Permission.hasRange(Permission.ABANDON)).isFalse();
    assertThat(Permission.hasRange("no-permission")).isFalse();

    assertThat(Permission.hasRange(Permission.LABEL + LabelId.CODE_REVIEW)).isTrue();
    assertThat(Permission.hasRange(Permission.LABEL_AS + LabelId.CODE_REVIEW)).isTrue();
    assertThat(Permission.hasRange(LabelId.CODE_REVIEW)).isFalse();
  }

  @Test
  public void isLabel() {
    assertThat(Permission.isLabel(Permission.ABANDON)).isFalse();
    assertThat(Permission.isLabel("no-permission")).isFalse();

    assertThat(Permission.isLabel(Permission.LABEL + LabelId.CODE_REVIEW)).isTrue();
    assertThat(Permission.isLabel(Permission.LABEL_AS + LabelId.CODE_REVIEW)).isFalse();
    assertThat(Permission.isLabel(Permission.REMOVE_LABEL_AS + LabelId.CODE_REVIEW)).isFalse();
    assertThat(Permission.isLabel(LabelId.CODE_REVIEW)).isFalse();
  }

  @Test
  public void isLabelAs() {
    assertThat(Permission.isLabelAs(Permission.ABANDON)).isFalse();
    assertThat(Permission.isLabelAs("no-permission")).isFalse();

    assertThat(Permission.isLabelAs(Permission.LABEL + LabelId.CODE_REVIEW)).isFalse();
    assertThat(Permission.isLabelAs(Permission.LABEL_AS + LabelId.CODE_REVIEW)).isTrue();
    assertThat(Permission.isLabelAs(Permission.REMOVE_LABEL_AS + LabelId.CODE_REVIEW)).isFalse();
    assertThat(Permission.isLabelAs(LabelId.CODE_REVIEW)).isFalse();
  }

  @Test
  public void isRemoveLabelAs() {
    assertThat(Permission.isRemoveLabelAs(Permission.ABANDON)).isFalse();
    assertThat(Permission.isRemoveLabelAs("no-permission")).isFalse();

    assertThat(Permission.isRemoveLabelAs(Permission.LABEL + LabelId.CODE_REVIEW)).isFalse();
    assertThat(Permission.isRemoveLabelAs(Permission.LABEL_AS + LabelId.CODE_REVIEW)).isFalse();
    assertThat(Permission.isRemoveLabelAs(Permission.REMOVE_LABEL_AS + LabelId.CODE_REVIEW))
        .isTrue();
    assertThat(Permission.isRemoveLabelAs(LabelId.CODE_REVIEW)).isFalse();
  }

  @Test
  public void forLabel() {
    assertThat(Permission.forLabel(LabelId.CODE_REVIEW))
        .isEqualTo(Permission.LABEL + LabelId.CODE_REVIEW);
  }

  @Test
  public void forLabelAs() {
    assertThat(Permission.forLabelAs(LabelId.CODE_REVIEW))
        .isEqualTo(Permission.LABEL_AS + LabelId.CODE_REVIEW);
  }

  @Test
  public void forRemoveLabelAs() {
    assertThat(Permission.forRemoveLabelAs(LabelId.CODE_REVIEW))
        .isEqualTo(Permission.REMOVE_LABEL_AS + LabelId.CODE_REVIEW);
  }

  @Test
  public void extractLabel() {
    assertThat(Permission.extractLabel(Permission.LABEL + LabelId.CODE_REVIEW))
        .isEqualTo(LabelId.CODE_REVIEW);
    assertThat(Permission.extractLabel(Permission.LABEL_AS + LabelId.CODE_REVIEW))
        .isEqualTo(LabelId.CODE_REVIEW);
    assertThat(Permission.extractLabel(Permission.REMOVE_LABEL_AS + LabelId.CODE_REVIEW))
        .isEqualTo(LabelId.CODE_REVIEW);
    assertThat(Permission.extractLabel(LabelId.CODE_REVIEW)).isNull();
    assertThat(Permission.extractLabel(Permission.ABANDON)).isNull();
  }

  @Test
  public void canBeOnAllProjects() {
    assertThat(Permission.canBeOnAllProjects(AccessSection.ALL, Permission.ABANDON)).isTrue();
    assertThat(Permission.canBeOnAllProjects(AccessSection.ALL, Permission.OWNER)).isFalse();
    assertThat(
            Permission.canBeOnAllProjects(
                AccessSection.ALL, Permission.LABEL + LabelId.CODE_REVIEW))
        .isTrue();
    assertThat(
            Permission.canBeOnAllProjects(
                AccessSection.ALL, Permission.LABEL_AS + LabelId.CODE_REVIEW))
        .isTrue();
    assertThat(
            Permission.canBeOnAllProjects(
                AccessSection.ALL, Permission.REMOVE_LABEL_AS + LabelId.CODE_REVIEW))
        .isTrue();

    assertThat(Permission.canBeOnAllProjects("refs/heads/*", Permission.ABANDON)).isTrue();
    assertThat(Permission.canBeOnAllProjects("refs/heads/*", Permission.OWNER)).isTrue();
    assertThat(
            Permission.canBeOnAllProjects("refs/heads/*", Permission.LABEL + LabelId.CODE_REVIEW))
        .isTrue();
    assertThat(
            Permission.canBeOnAllProjects(
                "refs/heads/*", Permission.LABEL_AS + LabelId.CODE_REVIEW))
        .isTrue();
    assertThat(
            Permission.canBeOnAllProjects(
                "refs/heads/*", Permission.REMOVE_LABEL_AS + LabelId.CODE_REVIEW))
        .isTrue();
  }

  @Test
  public void getName() {
    assertThat(permission.getName()).isEqualTo(PERMISSION_NAME);
  }

  @Test
  public void getLabel() {
    assertThat(Permission.create(Permission.LABEL + LabelId.CODE_REVIEW).getLabel())
        .isEqualTo(LabelId.CODE_REVIEW);
    assertThat(Permission.create(Permission.LABEL_AS + LabelId.CODE_REVIEW).getLabel())
        .isEqualTo(LabelId.CODE_REVIEW);
    assertThat(Permission.create(Permission.REMOVE_LABEL_AS + LabelId.CODE_REVIEW).getLabel())
        .isEqualTo(LabelId.CODE_REVIEW);
    assertThat(Permission.create(LabelId.CODE_REVIEW).getLabel()).isNull();
    assertThat(Permission.create(Permission.ABANDON).getLabel()).isNull();
  }

  @Test
  public void exclusiveGroup() {
    assertThat(permission.build().getExclusiveGroup()).isFalse();

    permission.setExclusiveGroup(true);
    assertThat(permission.build().getExclusiveGroup()).isTrue();

    permission.setExclusiveGroup(false);
    assertThat(permission.build().getExclusiveGroup()).isFalse();
  }

  @Test
  public void noExclusiveGroupOnOwnerPermission() {
    Permission permission = Permission.create(Permission.OWNER);
    assertThat(permission.getExclusiveGroup()).isFalse();

    permission = permission.toBuilder().setExclusiveGroup(true).build();
    assertThat(permission.getExclusiveGroup()).isFalse();
  }

  @Test
  public void getEmptyRules() {
    assertThat(permission.getRulesBuilders()).isNotNull();
    assertThat(permission.getRulesBuilders()).isEmpty();
  }

  @Test
  public void setAndGetRules() {
    PermissionRule.Builder permissionRule1 =
        PermissionRule.builder(GroupReference.create(AccountGroup.uuid("uuid-1"), "group1"));
    PermissionRule.Builder permissionRule2 =
        PermissionRule.builder(GroupReference.create(AccountGroup.uuid("uuid-2"), "group2"));
    permission.add(permissionRule1);
    permission.add(permissionRule2);
    assertThat(permission.getRulesBuilders())
        .containsExactly(permissionRule1, permissionRule2)
        .inOrder();

    PermissionRule.Builder permissionRule3 =
        PermissionRule.builder(GroupReference.create(AccountGroup.uuid("uuid-3"), "group3"));
    permission.modifyRules(
        rules -> {
          rules.clear();
          rules.add(permissionRule3);
        });
    assertThat(permission.getRulesBuilders()).containsExactly(permissionRule3);
  }

  @Test
  public void getNonExistingRule() {
    GroupReference groupReference = GroupReference.create(AccountGroup.uuid("uuid-1"), "group1");
    assertThat(permission.build().getRule(groupReference)).isNull();
    assertThat(permission.build().getRule(groupReference)).isNull();
  }

  @Test
  public void getRule() {
    GroupReference groupReference = GroupReference.create(AccountGroup.uuid("uuid-1"), "group1");
    PermissionRule.Builder permissionRule = PermissionRule.builder(groupReference);
    permission.add(permissionRule);
    assertThat(permission.build().getRule(groupReference)).isEqualTo(permissionRule.build());
  }

  @Test
  public void addRule() {
    PermissionRule.Builder permissionRule1 =
        PermissionRule.builder(GroupReference.create(AccountGroup.uuid("uuid-1"), "group1"));
    PermissionRule.Builder permissionRule2 =
        PermissionRule.builder(GroupReference.create(AccountGroup.uuid("uuid-2"), "group2"));
    permission.add(permissionRule1);
    permission.add(permissionRule2);
    GroupReference groupReference3 = GroupReference.create(AccountGroup.uuid("uuid-3"), "group3");
    assertThat(permission.build().getRule(groupReference3)).isNull();

    PermissionRule.Builder permissionRule3 = PermissionRule.builder(groupReference3);
    permission.add(permissionRule3);
    assertThat(permission.build().getRule(groupReference3)).isEqualTo(permissionRule3.build());
    assertThat(permission.build().getRules())
        .containsExactly(permissionRule1.build(), permissionRule2.build(), permissionRule3.build())
        .inOrder();
  }

  @Test
  public void removeRule() {
    PermissionRule.Builder permissionRule1 =
        PermissionRule.builder(GroupReference.create(AccountGroup.uuid("uuid-1"), "group1"));
    PermissionRule.Builder permissionRule2 =
        PermissionRule.builder(GroupReference.create(AccountGroup.uuid("uuid-2"), "group2"));
    GroupReference groupReference3 = GroupReference.create(AccountGroup.uuid("uuid-3"), "group3");
    PermissionRule.Builder permissionRule3 = PermissionRule.builder(groupReference3);

    permission.add(permissionRule1);
    permission.add(permissionRule2);
    permission.add(permissionRule3);
    assertThat(permission.build().getRule(groupReference3)).isNotNull();

    permission.remove(permissionRule3.build());
    assertThat(permission.build().getRule(groupReference3)).isNull();
    assertThat(permission.build().getRules())
        .containsExactly(permissionRule1.build(), permissionRule2.build())
        .inOrder();
  }

  @Test
  public void removeRuleByGroupReference() {
    PermissionRule.Builder permissionRule1 =
        PermissionRule.builder(GroupReference.create(AccountGroup.uuid("uuid-1"), "group1"));
    PermissionRule.Builder permissionRule2 =
        PermissionRule.builder(GroupReference.create(AccountGroup.uuid("uuid-2"), "group2"));
    GroupReference groupReference3 = GroupReference.create(AccountGroup.uuid("uuid-3"), "group3");
    PermissionRule.Builder permissionRule3 = PermissionRule.builder(groupReference3);

    permission.add(permissionRule1);
    permission.add(permissionRule2);
    permission.add(permissionRule3);
    assertThat(permission.build().getRule(groupReference3)).isNotNull();

    permission.removeRule(groupReference3);
    assertThat(permission.build().getRule(groupReference3)).isNull();
    assertThat(permission.build().getRules())
        .containsExactly(permissionRule1.build(), permissionRule2.build())
        .inOrder();
  }

  @Test
  public void clearRules() {
    PermissionRule.Builder permissionRule1 =
        PermissionRule.builder(GroupReference.create(AccountGroup.uuid("uuid-1"), "group1"));
    PermissionRule.Builder permissionRule2 =
        PermissionRule.builder(GroupReference.create(AccountGroup.uuid("uuid-2"), "group2"));

    permission.add(permissionRule1);
    permission.add(permissionRule2);
    assertThat(permission.build().getRules()).isNotEmpty();

    permission.clearRules();
    assertThat(permission.build().getRules()).isEmpty();
  }

  @Test
  public void testEquals() {
    PermissionRule.Builder permissionRule1 =
        PermissionRule.builder(GroupReference.create(AccountGroup.uuid("uuid-1"), "group1"));
    PermissionRule.Builder permissionRule2 =
        PermissionRule.builder(GroupReference.create(AccountGroup.uuid("uuid-2"), "group2"));

    permission.add(permissionRule1);
    permission.add(permissionRule2);

    Permission.Builder permissionSameRulesOtherName = Permission.builder("bar");
    permissionSameRulesOtherName.add(permissionRule1);
    permissionSameRulesOtherName.add(permissionRule2);
    assertThat(permission.equals(permissionSameRulesOtherName)).isFalse();

    Permission.Builder permissionSameRulesSameNameOtherExclusiveGroup = Permission.builder("foo");
    permissionSameRulesSameNameOtherExclusiveGroup.add(permissionRule1);
    permissionSameRulesSameNameOtherExclusiveGroup.add(permissionRule2);
    permissionSameRulesSameNameOtherExclusiveGroup.setExclusiveGroup(true);
    assertThat(permission.equals(permissionSameRulesSameNameOtherExclusiveGroup)).isFalse();

    Permission.Builder permissionOther = Permission.builder(PERMISSION_NAME);
    permissionOther.add(permissionRule1);
    assertThat(permission.build().equals(permissionOther.build())).isFalse();

    permissionOther.add(permissionRule2);
    assertThat(permission.build().equals(permissionOther.build())).isTrue();
  }
}
