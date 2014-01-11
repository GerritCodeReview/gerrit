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

package com.google.gerrit.acceptance.rest.group;

import static com.google.gerrit.acceptance.rest.group.GroupAssert.assertGroupInfo;
import static com.google.gerrit.acceptance.rest.group.GroupAssert.assertGroups;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.group.CreateGroup;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class ListGroupsIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private GroupCache groupCache;

  private TestAccount admin;
  private RestSession session;

  @Before
  public void setUp() throws Exception {
    admin = accounts.create("admin", "admin@example.com", "Administrator",
            "Administrators");
    session = new RestSession(server, admin);
  }

  @Test
  public void testListAllGroups() throws IOException, OrmException {
    Iterable<String> expectedGroups = Iterables.transform(groupCache.all(),
        new Function<AccountGroup, String>() {
          @Override
          @Nullable
          public String apply(@Nullable AccountGroup group) {
            return group.getName();
          }
        });
    RestResponse r = session.get("/groups/");
    Map<String, GroupInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<Map<String, GroupInfo>>() {}.getType());
    assertGroups(expectedGroups, result.keySet());
  }

  @Test
  public void testOnlyVisibleGroupsReturned() throws OrmException,
      JSchException, IOException {
    TestAccount user = accounts.create("user", "user@example.com", "User");
    RestSession userSession = new RestSession(server, user);

    String newGroupName = "newGroup";
    CreateGroup.Input in = new CreateGroup.Input();
    in.description = "a hidden group";
    in.visibleToAll = false;
    in.ownerId = groupCache.get(new AccountGroup.NameKey("Administrators"))
        .getGroupUUID().get();
    session.put("/groups/" + newGroupName, in).consume();

    Set<String> expectedGroups = Sets.newHashSet(newGroupName);
    RestResponse r = userSession.get("/groups/");
    Map<String, GroupInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<Map<String, GroupInfo>>() {}.getType());
    assertTrue("no groups visible", result.isEmpty());

    assertEquals(HttpStatus.SC_CREATED, session.put(
        String.format("/groups/%s/members/%s", newGroupName, user.username)
      ).getStatusCode());

    r = userSession.get("/groups/");
    result = newGson().fromJson(r.getReader(),
        new TypeToken<Map<String, GroupInfo>>() {}.getType());
    assertGroups(expectedGroups, result.keySet());
  }

  @Test
  public void testAllGroupInfoFieldsSetCorrectly() throws IOException,
      OrmException {
    AccountGroup adminGroup = groupCache.get(new AccountGroup.NameKey("Administrators"));
    RestResponse r = session.get("/groups/?q=" + adminGroup.getName());
    Map<String, GroupInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<Map<String, GroupInfo>>() {}.getType());
    GroupInfo adminGroupInfo = result.get(adminGroup.getName());
    assertGroupInfo(adminGroup, adminGroupInfo);
  }
}
