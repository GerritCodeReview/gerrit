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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

/**
 * An example test that tests presence of default groups in a newly initialized
 * review site.
 *
 * The test shows how to perform these checks via SSH, REST or using Gerrit
 * internals.
 */
public class DefaultGroupsIT extends AbstractDaemonTest {

  @Test
  public void defaultGroupsCreated_ssh() throws Exception {
    SshSession session = new SshSession(server, admin);
    String result = session.exec("gerrit ls-groups");
    assertFalse(session.getError(), session.hasError());
    assertTrue(result.contains("Administrators"));
    assertTrue(result.contains("Non-Interactive Users"));
    session.close();
  }

  @Test
  public void defaultGroupsCreated_rest() throws Exception {
    RestSession session = new RestSession(server, admin);
    RestResponse r = session.get("/groups/");
    Map<String, GroupInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<Map<String, GroupInfo>>() {}.getType());
    Set<String> names = result.keySet();
    assertTrue(names.contains("Administrators"));
    assertTrue(names.contains("Non-Interactive Users"));
  }

  @Test
  public void defaultGroupsCreated_internals() throws Exception {
    Set<String> names = Sets.newHashSet();
    for (AccountGroup g : db.accountGroups().all()) {
      names.add(g.getName());
    }
    assertTrue(names.contains("Administrators"));
    assertTrue(names.contains("Non-Interactive Users"));
  }
}
