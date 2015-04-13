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
import static com.google.gerrit.acceptance.api.group.GroupAssert.assertGroupInfo;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;

import org.junit.Test;

import java.util.List;
import java.util.Map;

@NoHttpd
public class ListGroupsIT extends AbstractDaemonTest {
  @Test
  public void defaultGroupsCreated() throws Exception {
    Iterable<String> names = gApi.groups().list().getAsMap().keySet();
    assertThat(names).containsAllOf("Administrators", "Non-Interactive Users")
        .inOrder();
  }

  @Test
  public void testListAllGroups() throws Exception {
    List<String> expectedGroups = FluentIterable
          .from(groupCache.all())
          .transform(new Function<AccountGroup, String>() {
            @Override
            public String apply(AccountGroup group) {
              return group.getName();
            }
          }).toSortedList(Ordering.natural());
    assertThat(expectedGroups.size()).isAtLeast(2);
    assertThat((Iterable<?>) gApi.groups().list().getAsMap().keySet())
        .containsExactlyElementsIn(expectedGroups).inOrder();
  }

  @Test
  public void testOnlyVisibleGroupsReturned() throws Exception {
    String newGroupName = name("newGroup");
    GroupInput in = new GroupInput();
    in.name = newGroupName;
    in.description = "a hidden group";
    in.visibleToAll = false;
    in.ownerId = getFromCache("Administrators").getGroupUUID().get();
    gApi.groups().create(in);

    setApiUser(user);
    assertThat(gApi.groups().list().getAsMap())
        .doesNotContainKey(newGroupName);

    setApiUser(admin);
    gApi.groups().id(newGroupName).addMembers(user.username);

    setApiUser(user);
    assertThat(gApi.groups().list().getAsMap()).containsKey(newGroupName);
  }

  @Test
  public void testAllGroupInfoFieldsSetCorrectly() throws Exception {
    AccountGroup adminGroup = getFromCache("Administrators");
    Map<String, GroupInfo> groups =
        gApi.groups().list().addGroup(adminGroup.getName()).getAsMap();
    assertThat(groups).hasSize(1);
    assertThat(groups).containsKey("Administrators");
    assertGroupInfo(adminGroup, Iterables.getOnlyElement(groups.values()));
  }

  private AccountGroup getFromCache(String name) throws Exception {
    return groupCache.get(new AccountGroup.NameKey(name));
  }
}
