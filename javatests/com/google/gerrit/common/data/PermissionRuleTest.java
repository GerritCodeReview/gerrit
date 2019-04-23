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

import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.testing.GerritBaseTests;
import org.junit.Before;
import org.junit.Test;

public class PermissionRuleTest extends GerritBaseTests {
  private GroupReference groupReference;
  private PermissionRule permissionRule;

  @Before
  public void setup() {
    this.groupReference = new GroupReference(AccountGroup.uuid("uuid"), "group");
    this.permissionRule = new PermissionRule(groupReference);
  }

  @Test
  public void getAndSetAction() {
    assertThat(permissionRule.getAction()).isEqualTo(Action.ALLOW);

    permissionRule.setAction(Action.DENY);
    assertThat(permissionRule.getAction()).isEqualTo(Action.DENY);
  }

  @Test
  public void cannotSetActionToNull() {
    exception.expect(NullPointerException.class);
    permissionRule.setAction(null);
  }

  @Test
  public void setDeny() {
    assertThat(permissionRule.isDeny()).isFalse();

    permissionRule.setDeny();
    assertThat(permissionRule.isDeny()).isTrue();
  }

  @Test
  public void setBlock() {
    assertThat(permissionRule.isBlock()).isFalse();

    permissionRule.setBlock();
    assertThat(permissionRule.isBlock()).isTrue();
  }

  @Test
  public void setForce() {
    assertThat(permissionRule.getForce()).isFalse();

    permissionRule.setForce(true);
    assertThat(permissionRule.getForce()).isTrue();

    permissionRule.setForce(false);
    assertThat(permissionRule.getForce()).isFalse();
  }

  @Test
  public void setMin() {
    assertThat(permissionRule.getMin()).isEqualTo(0);

    permissionRule.setMin(-2);
    assertThat(permissionRule.getMin()).isEqualTo(-2);

    permissionRule.setMin(2);
    assertThat(permissionRule.getMin()).isEqualTo(2);
  }

  @Test
  public void setMax() {
    assertThat(permissionRule.getMax()).isEqualTo(0);

    permissionRule.setMax(2);
    assertThat(permissionRule.getMax()).isEqualTo(2);

    permissionRule.setMax(-2);
    assertThat(permissionRule.getMax()).isEqualTo(-2);
  }

  @Test
  public void setRange() {
    assertThat(permissionRule.getMin()).isEqualTo(0);
    assertThat(permissionRule.getMax()).isEqualTo(0);

    permissionRule.setRange(-2, 2);
    assertThat(permissionRule.getMin()).isEqualTo(-2);
    assertThat(permissionRule.getMax()).isEqualTo(2);

    permissionRule.setRange(2, -2);
    assertThat(permissionRule.getMin()).isEqualTo(-2);
    assertThat(permissionRule.getMax()).isEqualTo(2);

    permissionRule.setRange(1, 1);
    assertThat(permissionRule.getMin()).isEqualTo(1);
    assertThat(permissionRule.getMax()).isEqualTo(1);
  }

  @Test
  public void hasRange() {
    assertThat(permissionRule.hasRange()).isFalse();

    permissionRule.setMin(-1);
    assertThat(permissionRule.hasRange()).isTrue();

    permissionRule.setMax(1);
    assertThat(permissionRule.hasRange()).isTrue();
  }

  @Test
  public void getGroup() {
    assertThat(permissionRule.getGroup()).isEqualTo(groupReference);
  }

  @Test
  public void setGroup() {
    GroupReference groupReference2 = new GroupReference(AccountGroup.uuid("uuid2"), "group2");
    assertThat(groupReference2).isNotEqualTo(groupReference);

    assertThat(permissionRule.getGroup()).isEqualTo(groupReference);

    permissionRule.setGroup(groupReference2);
    assertThat(permissionRule.getGroup()).isEqualTo(groupReference2);
  }

  @Test
  public void mergeFromAnyBlock() {
    GroupReference groupReference1 = new GroupReference(AccountGroup.uuid("uuid1"), "group1");
    PermissionRule permissionRule1 = new PermissionRule(groupReference1);

    GroupReference groupReference2 = new GroupReference(AccountGroup.uuid("uuid2"), "group2");
    PermissionRule permissionRule2 = new PermissionRule(groupReference2);

    permissionRule1.mergeFrom(permissionRule2);
    assertThat(permissionRule1.isBlock()).isFalse();
    assertThat(permissionRule2.isBlock()).isFalse();

    permissionRule2.setBlock();
    permissionRule1.mergeFrom(permissionRule2);
    assertThat(permissionRule1.isBlock()).isTrue();
    assertThat(permissionRule2.isBlock()).isTrue();

    permissionRule2.setDeny();
    permissionRule1.mergeFrom(permissionRule2);
    assertThat(permissionRule1.isBlock()).isTrue();
    assertThat(permissionRule2.isBlock()).isFalse();

    permissionRule2.setAction(Action.BATCH);
    permissionRule1.mergeFrom(permissionRule2);
    assertThat(permissionRule1.isBlock()).isTrue();
    assertThat(permissionRule2.isBlock()).isFalse();
  }

  @Test
  public void mergeFromAnyDeny() {
    GroupReference groupReference1 = new GroupReference(AccountGroup.uuid("uuid1"), "group1");
    PermissionRule permissionRule1 = new PermissionRule(groupReference1);

    GroupReference groupReference2 = new GroupReference(AccountGroup.uuid("uuid2"), "group2");
    PermissionRule permissionRule2 = new PermissionRule(groupReference2);

    permissionRule1.mergeFrom(permissionRule2);
    assertThat(permissionRule1.isDeny()).isFalse();
    assertThat(permissionRule2.isDeny()).isFalse();

    permissionRule2.setDeny();
    permissionRule1.mergeFrom(permissionRule2);
    assertThat(permissionRule1.isDeny()).isTrue();
    assertThat(permissionRule2.isDeny()).isTrue();

    permissionRule2.setAction(Action.BATCH);
    permissionRule1.mergeFrom(permissionRule2);
    assertThat(permissionRule1.isDeny()).isTrue();
    assertThat(permissionRule2.isDeny()).isFalse();
  }

  @Test
  public void mergeFromAnyBatch() {
    GroupReference groupReference1 = new GroupReference(AccountGroup.uuid("uuid1"), "group1");
    PermissionRule permissionRule1 = new PermissionRule(groupReference1);

    GroupReference groupReference2 = new GroupReference(AccountGroup.uuid("uuid2"), "group2");
    PermissionRule permissionRule2 = new PermissionRule(groupReference2);

    permissionRule1.mergeFrom(permissionRule2);
    assertThat(permissionRule1.getAction()).isNotEqualTo(Action.BATCH);
    assertThat(permissionRule2.getAction()).isNotEqualTo(Action.BATCH);

    permissionRule2.setAction(Action.BATCH);
    permissionRule1.mergeFrom(permissionRule2);
    assertThat(permissionRule1.getAction()).isEqualTo(Action.BATCH);
    assertThat(permissionRule2.getAction()).isEqualTo(Action.BATCH);

    permissionRule2.setAction(Action.ALLOW);
    permissionRule1.mergeFrom(permissionRule2);
    assertThat(permissionRule1.getAction()).isEqualTo(Action.BATCH);
    assertThat(permissionRule2.getAction()).isNotEqualTo(Action.BATCH);
  }

  @Test
  public void mergeFromAnyForce() {
    GroupReference groupReference1 = new GroupReference(AccountGroup.uuid("uuid1"), "group1");
    PermissionRule permissionRule1 = new PermissionRule(groupReference1);

    GroupReference groupReference2 = new GroupReference(AccountGroup.uuid("uuid2"), "group2");
    PermissionRule permissionRule2 = new PermissionRule(groupReference2);

    permissionRule1.mergeFrom(permissionRule2);
    assertThat(permissionRule1.getForce()).isFalse();
    assertThat(permissionRule2.getForce()).isFalse();

    permissionRule2.setForce(true);
    permissionRule1.mergeFrom(permissionRule2);
    assertThat(permissionRule1.getForce()).isTrue();
    assertThat(permissionRule2.getForce()).isTrue();

    permissionRule2.setForce(false);
    permissionRule1.mergeFrom(permissionRule2);
    assertThat(permissionRule1.getForce()).isTrue();
    assertThat(permissionRule2.getForce()).isFalse();
  }

  @Test
  public void mergeFromMergeRange() {
    GroupReference groupReference1 = new GroupReference(AccountGroup.uuid("uuid1"), "group1");
    PermissionRule permissionRule1 = new PermissionRule(groupReference1);
    permissionRule1.setRange(-1, 2);

    GroupReference groupReference2 = new GroupReference(AccountGroup.uuid("uuid2"), "group2");
    PermissionRule permissionRule2 = new PermissionRule(groupReference2);
    permissionRule2.setRange(-2, 1);

    permissionRule1.mergeFrom(permissionRule2);
    assertThat(permissionRule1.getMin()).isEqualTo(-2);
    assertThat(permissionRule1.getMax()).isEqualTo(2);
    assertThat(permissionRule2.getMin()).isEqualTo(-2);
    assertThat(permissionRule2.getMax()).isEqualTo(1);
  }

  @Test
  public void mergeFromGroupNotChanged() {
    GroupReference groupReference1 = new GroupReference(AccountGroup.uuid("uuid1"), "group1");
    PermissionRule permissionRule1 = new PermissionRule(groupReference1);

    GroupReference groupReference2 = new GroupReference(AccountGroup.uuid("uuid2"), "group2");
    PermissionRule permissionRule2 = new PermissionRule(groupReference2);

    permissionRule1.mergeFrom(permissionRule2);
    assertThat(permissionRule1.getGroup()).isEqualTo(groupReference1);
    assertThat(permissionRule2.getGroup()).isEqualTo(groupReference2);
  }

  @Test
  public void asString() {
    assertThat(permissionRule.asString(true)).isEqualTo("group " + groupReference.getName());

    permissionRule.setDeny();
    assertThat(permissionRule.asString(true)).isEqualTo("deny group " + groupReference.getName());

    permissionRule.setBlock();
    assertThat(permissionRule.asString(true)).isEqualTo("block group " + groupReference.getName());

    permissionRule.setAction(Action.BATCH);
    assertThat(permissionRule.asString(true)).isEqualTo("batch group " + groupReference.getName());

    permissionRule.setAction(Action.INTERACTIVE);
    assertThat(permissionRule.asString(true))
        .isEqualTo("interactive group " + groupReference.getName());

    permissionRule.setForce(true);
    assertThat(permissionRule.asString(true))
        .isEqualTo("interactive +force group " + groupReference.getName());

    permissionRule.setAction(Action.ALLOW);
    assertThat(permissionRule.asString(true)).isEqualTo("+force group " + groupReference.getName());

    permissionRule.setMax(1);
    assertThat(permissionRule.asString(true))
        .isEqualTo("+force +0..+1 group " + groupReference.getName());

    permissionRule.setMin(-1);
    assertThat(permissionRule.asString(true))
        .isEqualTo("+force -1..+1 group " + groupReference.getName());

    assertThat(permissionRule.asString(false))
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
    GroupReference groupReference2 = new GroupReference(AccountGroup.uuid("uuid2"), "group2");
    PermissionRule permissionRuleOther = new PermissionRule(groupReference2);
    assertThat(permissionRule.equals(permissionRuleOther)).isFalse();

    permissionRuleOther.setGroup(groupReference);
    assertThat(permissionRule.equals(permissionRuleOther)).isTrue();

    permissionRule.setDeny();
    assertThat(permissionRule.equals(permissionRuleOther)).isFalse();

    permissionRuleOther.setDeny();
    assertThat(permissionRule.equals(permissionRuleOther)).isTrue();

    permissionRule.setForce(true);
    assertThat(permissionRule.equals(permissionRuleOther)).isFalse();

    permissionRuleOther.setForce(true);
    assertThat(permissionRule.equals(permissionRuleOther)).isTrue();

    permissionRule.setMin(-1);
    assertThat(permissionRule.equals(permissionRuleOther)).isFalse();

    permissionRuleOther.setMin(-1);
    assertThat(permissionRule.equals(permissionRuleOther)).isTrue();

    permissionRule.setMax(1);
    assertThat(permissionRule.equals(permissionRuleOther)).isFalse();

    permissionRuleOther.setMax(1);
    assertThat(permissionRule.equals(permissionRuleOther)).isTrue();
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
