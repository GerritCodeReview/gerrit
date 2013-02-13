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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gson.Gson;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

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
    session = new RestSession(admin);
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
    Reader r = session.get("/groups/");
    @SuppressWarnings("serial")
    Map<String, GroupInfo> result =
        (new Gson()).fromJson(r, new TypeToken<Map<String, GroupInfo>>() {}.getType());
    assertGroups(expectedGroups, result.keySet());
  }

  @Test
  public void testOnlyVisibleGroupsReturned() throws OrmException,
      JSchException, IOException {
    Set<String> expectedGroups = Sets.newHashSet();
    expectedGroups.add("Anonymous Users");
    expectedGroups.add("Registered Users");
    TestAccount user = accounts.create("user", "user@example.com", "User");
    Reader r = (new RestSession(user)).get("/groups/");
    @SuppressWarnings("serial")
    Map<String, GroupInfo> result =
        (new Gson()).fromJson(r, new TypeToken<Map<String, GroupInfo>>() {}.getType());
    assertGroups(expectedGroups, result.keySet());
  }

  @Test
  public void testAllGroupInfoFieldsSetCorrectly() throws IOException,
      OrmException {
    AccountGroup adminGroup = groupCache.get(new AccountGroup.NameKey("Administrators"));
    Reader r = session.get("/groups/?q=" + adminGroup.getName());
    @SuppressWarnings("serial")
    Map<String, GroupInfo> result =
        (new Gson()).fromJson(r, new TypeToken<Map<String, GroupInfo>>() {}.getType());
    GroupInfo adminGroupInfo = result.get(adminGroup.getName());
    assertEquals(adminGroup.getGroupUUID().get(), adminGroupInfo.id);
    assertEquals(Integer.valueOf(adminGroup.getId().get()), adminGroupInfo.group_id);
    assertEquals("#/admin/groups/uuid-" + adminGroup.getGroupUUID().get(), adminGroupInfo.url);
    assertEquals(adminGroup.isVisibleToAll(), toBoolean(adminGroupInfo.options.visible_to_all));
    assertEquals(adminGroup.getDescription(), adminGroupInfo.description);
    assertEquals(adminGroup.getOwnerGroupUUID().get(), adminGroupInfo.owner_id);
    if (adminGroupInfo.name != null) {
      // 'name' is not set if returned in a map,
      // but if 'name' is set it must contain the correct name
      assertEquals(adminGroup.getName(), adminGroupInfo.name);
    }
  }

  private static void assertGroups(Iterable<String> expected, Set<String> actual) {
    for (String g : expected) {
      assertTrue("missing group " + g, actual.remove(g));
    }
    assertTrue("unexpected groups: " + actual, actual.isEmpty());
  }

  private static boolean toBoolean(Boolean b) {
    if (b == null) {
      return false;
    }
    return b.booleanValue();
  }
}
