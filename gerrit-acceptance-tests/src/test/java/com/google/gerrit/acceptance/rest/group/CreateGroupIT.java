// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.group;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import org.junit.Test;

public class CreateGroupIT extends AbstractDaemonTest {

  @Test
  public void createGroup() throws Exception {
    GroupInput in = new GroupInput();
    in.name = name("group");
    gApi.groups().create(in);
    AccountGroup accountGroup = groupCache.get(new AccountGroup.NameKey(in.name));
    assertThat(accountGroup).isNotNull();
    assertThat(accountGroup.getName()).isEqualTo(in.name);
  }

  @Test
  public void createGroupAlreadyExists() throws Exception {
    GroupInput in = new GroupInput();
    in.name = name("group");
    gApi.groups().create(in);
    assertThat(groupCache.get(new AccountGroup.NameKey(in.name))).isNotNull();

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("group '" + in.name + "' already exists");
    gApi.groups().create(in);
  }

  @Test
  public void createGroupWithDifferentCase() throws Exception {
    GroupInput in = new GroupInput();
    in.name = name("group");
    gApi.groups().create(in);
    assertThat(groupCache.get(new AccountGroup.NameKey(in.name))).isNotNull();

    GroupInput inLowerCase = new GroupInput();
    inLowerCase.name = in.name.toUpperCase();
    gApi.groups().create(inLowerCase);
    assertThat(groupCache.get(new AccountGroup.NameKey(inLowerCase.name))).isNotNull();
  }

  @Test
  public void createSystemGroupWithDifferentCase() throws Exception {
    String registeredUsers = "Registered Users";
    GroupInput in = new GroupInput();
    in.name = registeredUsers.toUpperCase();

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("group '" + registeredUsers + "' already exists");
    gApi.groups().create(in);
  }
}
