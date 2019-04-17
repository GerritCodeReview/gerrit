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

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;
import com.google.gerrit.testing.GerritBaseTests;
import org.junit.Test;

public class GroupReferenceTest extends GerritBaseTests {
  @Test
  public void forGroupDescription() {
    String name = "foo";
    AccountGroup.UUID uuid = AccountGroup.uuid("uuid-foo");
    GroupReference groupReference =
        GroupReference.forGroup(
            new GroupDescription.Basic() {

              @Override
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
    GroupReference groupReference = new GroupReference(uuid, name);
    assertThat(groupReference.getUUID()).isEqualTo(uuid);
    assertThat(groupReference.getName()).isEqualTo(name);
  }

  @Test
  public void createWithoutUuid() {
    // GroupReferences where the UUID is null are used to represent groups from project.config that
    // cannot be resolved.
    String name = "foo";
    GroupReference groupReference = new GroupReference(null, name);
    assertThat(groupReference.getUUID()).isNull();
    assertThat(groupReference.getName()).isEqualTo(name);
  }

  @Test
  public void cannotCreateWithoutName() {
    exception.expect(NullPointerException.class);
    new GroupReference(AccountGroup.uuid("uuid"), null);
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
  public void getAndSetUuid() {
    AccountGroup.UUID uuid = AccountGroup.uuid("uuid-foo");
    String name = "foo";
    GroupReference groupReference = new GroupReference(uuid, name);
    assertThat(groupReference.getUUID()).isEqualTo(uuid);

    AccountGroup.UUID uuid2 = AccountGroup.uuid("uuid-bar");
    groupReference.setUUID(uuid2);
    assertThat(groupReference.getUUID()).isEqualTo(uuid2);

    // GroupReferences where the UUID is null are used to represent groups from project.config that
    // cannot be resolved.
    groupReference.setUUID(null);
    assertThat(groupReference.getUUID()).isNull();
  }

  @Test
  public void getAndSetName() {
    AccountGroup.UUID uuid = AccountGroup.uuid("uuid-foo");
    String name = "foo";
    GroupReference groupReference = new GroupReference(uuid, name);
    assertThat(groupReference.getName()).isEqualTo(name);

    String name2 = "bar";
    groupReference.setName(name2);
    assertThat(groupReference.getName()).isEqualTo(name2);

    exception.expect(NullPointerException.class);
    groupReference.setName(null);
  }

  @Test
  public void toConfigValue() {
    String name = "foo";
    GroupReference groupReference = new GroupReference(AccountGroup.uuid("uuid-foo"), name);
    assertThat(groupReference.toConfigValue()).isEqualTo("group " + name);
  }

  @Test
  public void testEquals() {
    AccountGroup.UUID uuid1 = AccountGroup.uuid("uuid-foo");
    AccountGroup.UUID uuid2 = AccountGroup.uuid("uuid-bar");
    String name1 = "foo";
    String name2 = "bar";

    GroupReference groupReference1 = new GroupReference(uuid1, name1);
    GroupReference groupReference2 = new GroupReference(uuid1, name2);
    GroupReference groupReference3 = new GroupReference(uuid2, name1);

    assertThat(groupReference1.equals(groupReference2)).isTrue();
    assertThat(groupReference1.equals(groupReference3)).isFalse();
    assertThat(groupReference2.equals(groupReference3)).isFalse();
  }

  @Test
  public void testHashcode() {
    AccountGroup.UUID uuid1 = AccountGroup.uuid("uuid1");
    assertThat(new GroupReference(uuid1, "foo").hashCode())
        .isEqualTo(new GroupReference(uuid1, "bar").hashCode());

    // Check that the following calls don't fail with an exception.
    new GroupReference(null, "bar").hashCode();
    new GroupReference(AccountGroup.uuid(null), "bar").hashCode();
  }
}
