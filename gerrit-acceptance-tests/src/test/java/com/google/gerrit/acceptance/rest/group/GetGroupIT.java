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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class GetGroupIT extends AbstractDaemonTest {

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
  public void testGetGroup() throws IOException {
    AccountGroup adminGroup = groupCache.get(new AccountGroup.NameKey("Administrators"));

    // by UUID
    testGetGroup("/groups/" + adminGroup.getGroupUUID().get(), adminGroup);

    // by name
    testGetGroup("/groups/" + adminGroup.getName(), adminGroup);

    // by legacy numeric ID
    testGetGroup("/groups/" + adminGroup.getId().get(), adminGroup);
  }

  private void testGetGroup(String url, AccountGroup expectedGroup) throws IOException {
    RestResponse r = session.get(url);
    GroupInfo group = (new Gson()).fromJson(r.getReader(), new TypeToken<GroupInfo>() {}.getType());
    assertGroupInfo(expectedGroup, group);
  }
}
