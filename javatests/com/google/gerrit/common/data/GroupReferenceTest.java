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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.AccountGroup.UUID;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.GroupReference;
import org.junit.Test;

public class GroupReferenceTest {
  @Test
  public void forGroupDescription() {
    String name = "foo";
    AccountGroup.UUID uuid = AccountGroup.uuid("uuid-foo");
    GroupReference groupReference =
        GroupReference.forGroup(
            new GroupDescription.Basic() {

              @Override
              @Nullable
              public String getUrl() {
                return null;
              }

              @Override
              public String getName() {
                return name;
              }

              @Override
              public UUID getGroupUUID() {
                return uuid;
              }

              @Override
              @Nullable
              public String getEmailAddress() {
                return null;
              }
            });
    assertThat(groupReference.getName()).isEqualTo(name);
    assertThat(groupReference.getUUID()).isEqualTo(uuid);
  }

  @Test
  public void create() {
    AccountGroup.UUID uuid = AccountGroup.uuid("uuid");
    String name = "foo";
    GroupReference groupReference = GroupReference.create(uuid, name);
    assertThat(groupReference.getUUID()).isEqualTo(uuid);
    assertThat(groupReference.getName()).isEqualTo(name);
  }

  @Test
  public void createWithoutUuid() {
    // GroupReferences where the UUID is null are used to represent groups from project.config that
    // cannot be resolved.
    String name = "foo";
    GroupReference groupReference = GroupReference.create(name);
    assertThat(groupReference.getUUID()).isNull();
    assertThat(groupReference.getName()).isEqualTo(name);
  }

  @Test
  public void cannotCreateWithoutName() {
    assertThrows(
        NullPointerException.class, () -> GroupReference.create(AccountGroup.uuid("uuid"), null));
  }

  @Test
  public void isGroupReference() {
    assertThat(GroupReference.isGroupReference("foo")).isFalse();
    assertThat(GroupReference.isGroupReference("groupfoo")).isFalse();
    assertThat(GroupReference.isGroupReference("group foo")).isTrue();
    assertThat(GroupReference.isGroupReference("group foo-bar")).isTrue();
    assertThat(GroupReference.isGroupReference("group foo bar")).isTrue();
  }

  @Test
  public void extractGroupName() {
    assertThat(GroupReference.extractGroupName("foo")).isNull();
    assertThat(GroupReference.extractGroupName("groupfoo")).isNull();
    assertThat(GroupReference.extractGroupName("group foo")).isEqualTo("foo");
    assertThat(GroupReference.extractGroupName("group foo-bar")).isEqualTo("foo-bar");
    assertThat(GroupReference.extractGroupName("group foo bar")).isEqualTo("foo bar");
  }

  @Test
  public void toConfigValue() {
    String name = "foo";
    GroupReference groupReference = GroupReference.create(AccountGroup.uuid("uuid-foo"), name);
    assertThat(groupReference.toConfigValue()).isEqualTo("group " + name);
  }

  @Test
  public void testEquals() {
    AccountGroup.UUID uuid1 = AccountGroup.uuid("uuid-foo");
    AccountGroup.UUID uuid2 = AccountGroup.uuid("uuid-bar");
    String name1 = "foo";
    String name2 = "bar";

    GroupReference groupReference1 = GroupReference.create(uuid1, name1);
    GroupReference groupReference2 = GroupReference.create(uuid1, name2);
    GroupReference groupReference3 = GroupReference.create(uuid2, name1);

    assertThat(groupReference1.equals(groupReference2)).isTrue();
    assertThat(groupReference1.equals(groupReference3)).isFalse();
    assertThat(groupReference2.equals(groupReference3)).isFalse();
  }

  @Test
  public void testHashcode() {
    AccountGroup.UUID uuid1 = AccountGroup.uuid("uuid1");
    assertThat(GroupReference.create(uuid1, "foo").hashCode())
        .isEqualTo(GroupReference.create(uuid1, "bar").hashCode());
  }

  @Test
  public void testEqualsWithoutUuid() {
    assertThat(GroupReference.create("foo").equals(GroupReference.create("bar"))).isTrue();
  }

  @Test
  public void testHashCodeWithoutUuid() {
    assertThat(GroupReference.create("foo").hashCode())
        .isEqualTo(GroupReference.create("bar").hashCode());
  }
}
