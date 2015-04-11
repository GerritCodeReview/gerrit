// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.group;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.GroupOptionsInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.SystemGroupBackend;

import org.junit.Test;

@NoHttpd
public class GroupPropertiesIT extends AbstractDaemonTest {
  @Test
  public void testGroupName() throws Exception {
    String name = name("group");
    gApi.groups().create(name);

    // get name
    assertThat(gApi.groups().id(name).name()).isEqualTo(name);

    // set name with name conflict
    String other = name("other");
    gApi.groups().create(other);
    try {
      gApi.groups().id(name).name(other);
    } catch (ResourceConflictException expected) {
      // Expected.
    }

    // set name to same name
    gApi.groups().id(name).name(name);
    assertThat(gApi.groups().id(name).name()).isEqualTo(name);

    // rename
    String newName = name("newName");
    gApi.groups().id(name).name(newName);
    assertThat(getFromCache(newName)).isNotNull();
    assertThat(gApi.groups().id(newName).name()).isEqualTo(newName);

    assertThat(getFromCache(name)).isNull();
    try {
      gApi.groups().id(name).get();
    } catch (ResourceNotFoundException expected) {
      // Expceted.
    }
  }

  @Test
  public void testGroupDescription() throws Exception {
    String name = name("group");
    gApi.groups().create(name);

    // get description
    assertThat(gApi.groups().id(name).description()).isEmpty();

    // set description
    String desc = "New description for the group.";
    gApi.groups().id(name).description(desc);
    assertThat(gApi.groups().id(name).description()).isEqualTo(desc);

    // set description to null
    gApi.groups().id(name).description(null);
    assertThat(gApi.groups().id(name).description()).isEmpty();

    // set description to empty string
    gApi.groups().id(name).description("");
    assertThat(gApi.groups().id(name).description()).isEmpty();
  }

  @Test
  public void testGroupOptions() throws Exception {
    String name = name("group");
    gApi.groups().create(name);

    // get options
    assertThat(gApi.groups().id(name).options().visibleToAll).isNull();

    // set options
    GroupOptionsInfo options = new GroupOptionsInfo();
    options.visibleToAll = true;
    gApi.groups().id(name).options(options);
    assertThat(gApi.groups().id(name).options().visibleToAll).isTrue();
  }

  @Test
  public void testGroupOwner() throws Exception {
    String name = name("group");
    GroupInfo info = gApi.groups().create(name).get();
    String adminUUID = getFromCache("Administrators").getGroupUUID().get();
    String registeredUUID = SystemGroupBackend.REGISTERED_USERS.get();

    // get owner
    assertThat(Url.decode(gApi.groups().id(name).owner().id))
        .isEqualTo(info.id);

    // set owner by name
    gApi.groups().id(name).owner("Registered Users");
    assertThat(Url.decode(gApi.groups().id(name).owner().id))
        .isEqualTo(registeredUUID);

    // set owner by UUID
    gApi.groups().id(name).owner(adminUUID);
    assertThat(Url.decode(gApi.groups().id(name).owner().id))
        .isEqualTo(adminUUID);

    // set non existing owner
    try {
      gApi.groups().id(name).owner("Non-Existing Group");
    } catch (UnprocessableEntityException expected) {
      // Expected.
    }
  }

  private AccountGroup getFromCache(String name) throws Exception {
    return groupCache.get(new AccountGroup.NameKey(name));
  }
}
