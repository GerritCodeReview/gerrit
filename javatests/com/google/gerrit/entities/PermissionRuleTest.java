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

import com.google.gerrit.entities.PermissionRule.Action;
import org.junit.Before;
import org.junit.Test;

public class PermissionRuleTest {
  private GroupReference groupReference;
  private PermissionRule permissionRule;

  @Before
  public void setup() {
    this.groupReference = GroupReference.create(AccountGroup.uuid("uuid"), "group");
    this.permissionRule = PermissionRule.create(groupReference);
  }

  @Test
  public void mergeFromAnyBlock() {
    GroupReference groupReference1 = GroupReference.create(AccountGroup.uuid("uuid1"), "group1");
    PermissionRule permissionRule1 = PermissionRule.builder(groupReference1).build();

    GroupReference groupReference2 = GroupReference.create(AccountGroup.uuid("uuid2"), "group2");
    PermissionRule permissionRule2 = PermissionRule.builder(groupReference2).build();

    permissionRule1 = PermissionRule.merge(permissionRule2, permissionRule1);
    assertThat(permissionRule1.isBlock()).isFalse();
    assertThat(permissionRule2.isBlock()).isFalse();

    permissionRule2 = permissionRule2.toBuilder().setBlock().build();
    permissionRule1 = PermissionRule.merge(permissionRule2, permissionRule1);
    assertThat(permissionRule1.isBlock()).isTrue();
    assertThat(permissionRule2.isBlock()).isTrue();

    permissionRule2 = permissionRule2.toBuilder().setDeny().build();
    permissionRule1 = PermissionRule.merge(permissionRule2, permissionRule1);
    assertThat(permissionRule1.isBlock()).isTrue();
    assertThat(permissionRule2.isBlock()).isFalse();

    permissionRule2 = permissionRule2.toBuilder().setAction(Action.BATCH).build();
    permissionRule1 = PermissionRule.merge(permissionRule2, permissionRule1);
    assertThat(permissionRule1.isBlock()).isTrue();
    assertThat(permissionRule2.isBlock()).isFalse();
  }

  @Test
  public void mergeFromAnyDeny() {
    GroupReference groupReference1 = GroupReference.create(AccountGroup.uuid("uuid1"), "group1");
    PermissionRule permissionRule1 = PermissionRule.builder(groupReference1).build();

    GroupReference groupReference2 = GroupReference.create(AccountGroup.uuid("uuid2"), "group2");
    PermissionRule permissionRule2 = PermissionRule.builder(groupReference2).build();

    permissionRule1 = PermissionRule.merge(permissionRule2, permissionRule1);
    assertThat(permissionRule1.isDeny()).isFalse();
    assertThat(permissionRule2.isDeny()).isFalse();

    permissionRule2 = permissionRule2.toBuilder().setDeny().build();
    permissionRule1 = PermissionRule.merge(permissionRule2, permissionRule1);
    assertThat(permissionRule1.isDeny()).isTrue();
    assertThat(permissionRule2.isDeny()).isTrue();

    permissionRule2 = permissionRule2.toBuilder().setAction(Action.BATCH).build();
    permissionRule1 = PermissionRule.merge(permissionRule2, permissionRule1);
    assertThat(permissionRule1.isDeny()).isTrue();
    assertThat(permissionRule2.isDeny()).isFalse();
  }

  @Test
  public void mergeFromAnyBatch() {
    GroupReference groupReference1 = GroupReference.create(AccountGroup.uuid("uuid1"), "group1");
    PermissionRule permissionRule1 = PermissionRule.builder(groupReference1).build();

    GroupReference groupReference2 = GroupReference.create(AccountGroup.uuid("uuid2"), "group2");
    PermissionRule permissionRule2 = PermissionRule.builder(groupReference2).build();

    permissionRule1 = PermissionRule.merge(permissionRule2, permissionRule1);
    assertThat(permissionRule1.getAction()).isNotEqualTo(Action.BATCH);
    assertThat(permissionRule2.getAction()).isNotEqualTo(Action.BATCH);

    permissionRule2 = permissionRule2.toBuilder().setAction(Action.BATCH).build();
    permissionRule1 = PermissionRule.merge(permissionRule2, permissionRule1);
    assertThat(permissionRule1.getAction()).isEqualTo(Action.BATCH);
    assertThat(permissionRule2.getAction()).isEqualTo(Action.BATCH);

    permissionRule2 = permissionRule2.toBuilder().setAction(Action.ALLOW).build();
    permissionRule1 = PermissionRule.merge(permissionRule2, permissionRule1);
    assertThat(permissionRule1.getAction()).isEqualTo(Action.BATCH);
    assertThat(permissionRule2.getAction()).isNotEqualTo(Action.BATCH);
  }

  @Test
  public void mergeFromAnyForce() {
    GroupReference groupReference1 = GroupReference.create(AccountGroup.uuid("uuid1"), "group1");
    PermissionRule permissionRule1 = PermissionRule.builder(groupReference1).build();

    GroupReference groupReference2 = GroupReference.create(AccountGroup.uuid("uuid2"), "group2");
    PermissionRule permissionRule2 = PermissionRule.builder(groupReference2).build();

    permissionRule1 = PermissionRule.merge(permissionRule2, permissionRule1);
    assertThat(permissionRule1.getForce()).isFalse();
    assertThat(permissionRule2.getForce()).isFalse();

    permissionRule2 = permissionRule2.toBuilder().setForce(true).build();
    permissionRule1 = PermissionRule.merge(permissionRule2, permissionRule1);
    assertThat(permissionRule1.getForce()).isTrue();
    assertThat(permissionRule2.getForce()).isTrue();

    permissionRule2 = permissionRule2.toBuilder().setForce(false).build();
    permissionRule1 = PermissionRule.merge(permissionRule2, permissionRule1);
    assertThat(permissionRule1.getForce()).isTrue();
    assertThat(permissionRule2.getForce()).isFalse();
  }

  @Test
  public void mergeFromMergeRange() {
    GroupReference groupReference1 = GroupReference.create(AccountGroup.uuid("uuid1"), "group1");
    PermissionRule permissionRule1 =
        PermissionRule.builder(groupReference1).setRange(-1, 2).build();

    GroupReference groupReference2 = GroupReference.create(AccountGroup.uuid("uuid2"), "group2");
    PermissionRule permissionRule2 =
        PermissionRule.builder(groupReference2).setRange(-2, 1).build();

    permissionRule1 = PermissionRule.merge(permissionRule2, permissionRule1);
    assertThat(permissionRule1.getMin()).isEqualTo(-2);
    assertThat(permissionRule1.getMax()).isEqualTo(2);
    assertThat(permissionRule2.getMin()).isEqualTo(-2);
    assertThat(permissionRule2.getMax()).isEqualTo(1);
  }

  @Test
  public void mergeFromGroupNotChanged() {
    GroupReference groupReference1 = GroupReference.create(AccountGroup.uuid("uuid1"), "group1");
    PermissionRule permissionRule1 = PermissionRule.builder(groupReference1).build();

    GroupReference groupReference2 = GroupReference.create(AccountGroup.uuid("uuid2"), "group2");
    PermissionRule permissionRule2 = PermissionRule.builder(groupReference2).build();

    permissionRule1 = PermissionRule.merge(permissionRule2, permissionRule1);
    assertThat(permissionRule1.getGroup()).isEqualTo(groupReference1);
    assertThat(permissionRule2.getGroup()).isEqualTo(groupReference2);
  }

  @Test
  public void asString() {
    PermissionRule.Builder permissionRule = this.permissionRule.toBuilder();

    assertThat(permissionRule.build().asString(true))
        .isEqualTo("group " + groupReference.getName());

    permissionRule.setDeny();
    assertThat(permissionRule.build().asString(true))
        .isEqualTo("deny group " + groupReference.getName());

    permissionRule.setBlock();
    assertThat(permissionRule.build().asString(true))
        .isEqualTo("block group " + groupReference.getName());

    permissionRule.setAction(Action.BATCH);
    assertThat(permissionRule.build().asString(true))
        .isEqualTo("batch group " + groupReference.getName());

    permissionRule.setAction(Action.INTERACTIVE);
    assertThat(permissionRule.build().asString(true))
        .isEqualTo("interactive group " + groupReference.getName());

    permissionRule.setForce(true);
    assertThat(permissionRule.build().asString(true))
        .isEqualTo("interactive +force group " + groupReference.getName());

    permissionRule.setAction(Action.ALLOW);
    assertThat(permissionRule.build().asString(true))
        .isEqualTo("+force group " + groupReference.getName());

    permissionRule.setMax(1);
    assertThat(permissionRule.build().asString(true))
        .isEqualTo("+force +0..+1 group " + groupReference.getName());

    permissionRule.setMin(-1);
    assertThat(permissionRule.build().asString(true))
        .isEqualTo("+force -1..+1 group " + groupReference.getName());

    assertThat(permissionRule.build().asString(false))
        .isEqualTo("+force group " + groupReference.getName());
  }

  @Test
  public void fromString() {
    PermissionRule permissionRule = PermissionRule.fromString("group A", true);
    assertPermissionRule(permissionRule, "A", Action.ALLOW, false, 0, 0);

    permissionRule = PermissionRule.fromString("deny group A", true);
    assertPermissionRule(permissionRule, "A", Action.DENY, false, 0, 0);

    permissionRule = PermissionRule.fromString("block group A", true);
    assertPermissionRule(permissionRule, "A", Action.BLOCK, false, 0, 0);

    permissionRule = PermissionRule.fromString("batch group A", true);
    assertPermissionRule(permissionRule, "A", Action.BATCH, false, 0, 0);

    permissionRule = PermissionRule.fromString("interactive group A", true);
    assertPermissionRule(permissionRule, "A", Action.INTERACTIVE, false, 0, 0);

    permissionRule = PermissionRule.fromString("interactive +force group A", true);
    assertPermissionRule(permissionRule, "A", Action.INTERACTIVE, true, 0, 0);

    permissionRule = PermissionRule.fromString("+force group A", true);
    assertPermissionRule(permissionRule, "A", Action.ALLOW, true, 0, 0);

    permissionRule = PermissionRule.fromString("+force +0..+1 group A", true);
    assertPermissionRule(permissionRule, "A", Action.ALLOW, true, 0, 1);

    permissionRule = PermissionRule.fromString("+force -1..+1 group A", true);
    assertPermissionRule(permissionRule, "A", Action.ALLOW, true, -1, 1);

    permissionRule = PermissionRule.fromString("+force group A", false);
    assertPermissionRule(permissionRule, "A", Action.ALLOW, true, 0, 0);
  }

  @Test
  public void parseInt() {
    assertThat(PermissionRule.parseInt("0")).isEqualTo(0);
    assertThat(PermissionRule.parseInt("+0")).isEqualTo(0);
    assertThat(PermissionRule.parseInt("-0")).isEqualTo(0);
    assertThat(PermissionRule.parseInt("1")).isEqualTo(1);
    assertThat(PermissionRule.parseInt("+1")).isEqualTo(1);
    assertThat(PermissionRule.parseInt("-1")).isEqualTo(-1);
  }

  @Test
  public void testEquals() {
    GroupReference groupReference2 = GroupReference.create(AccountGroup.uuid("uuid2"), "group2");
    PermissionRule.Builder permissionRuleOther = PermissionRule.builder(groupReference2);
    PermissionRule.Builder permissionRule = this.permissionRule.toBuilder();

    assertThat(permissionRule.equals(permissionRuleOther)).isFalse();

    permissionRuleOther.setGroup(groupReference);
    assertThat(permissionRuleEquals(permissionRule, permissionRuleOther)).isTrue();

    permissionRule.setDeny();
    assertThat(permissionRuleEquals(permissionRule, permissionRuleOther)).isFalse();

    permissionRuleOther.setDeny();
    assertThat(permissionRuleEquals(permissionRule, permissionRuleOther)).isTrue();

    permissionRule.setForce(true);
    assertThat(permissionRuleEquals(permissionRule, permissionRuleOther)).isFalse();

    permissionRuleOther.setForce(true);
    assertThat(permissionRuleEquals(permissionRule, permissionRuleOther)).isTrue();

    permissionRule.setMin(-1);
    assertThat(permissionRuleEquals(permissionRule, permissionRuleOther)).isFalse();

    permissionRuleOther.setMin(-1);
    assertThat(permissionRuleEquals(permissionRule, permissionRuleOther)).isTrue();

    permissionRule.setMax(1);
    assertThat(permissionRuleEquals(permissionRule, permissionRuleOther)).isFalse();

    permissionRuleOther.setMax(1);
    assertThat(permissionRuleEquals(permissionRule, permissionRuleOther)).isTrue();
  }

  private static boolean permissionRuleEquals(
      PermissionRule.Builder r1, PermissionRule.Builder r2) {
    return r1.build().equals(r2.build());
  }

  private void assertPermissionRule(
      PermissionRule permissionRule,
      String expectedGroupName,
      Action expectedAction,
      boolean expectedForce,
      int expectedMin,
      int expectedMax) {
    assertThat(permissionRule.getGroup().getName()).isEqualTo(expectedGroupName);
    assertThat(permissionRule.getAction()).isEqualTo(expectedAction);
    assertThat(permissionRule.getForce()).isEqualTo(expectedForce);
    assertThat(permissionRule.getMin()).isEqualTo(expectedMin);
    assertThat(permissionRule.getMax()).isEqualTo(expectedMax);
  }
}
