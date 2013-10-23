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

package com.google.gerrit.acceptance.rest.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class CapabilitiesIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  @Inject
  private GroupCache groupCache;

  @Inject
  private ProjectCache projectCache;

  private RestSession userSession;

  @Before
  public void setUp() throws Exception {
    TestAccount user = accounts.create("user", "user@example.com", "User");
    userSession = new RestSession(server, user);
  }

  @Test
  public void testCapabilities() throws IOException,
      ConfigInvalidException, IllegalArgumentException,
      IllegalAccessException, NoSuchFieldException,
      SecurityException {
    grantAllCapabilities();
    RestResponse r =
        userSession.get("/accounts/self/capabilities");
    int code = r.getStatusCode();
    assertEquals(code, 200);
    CapabilityInfo info = (new Gson()).fromJson(r.getReader(),
        new TypeToken<CapabilityInfo>() {}.getType());
    for (String c: GlobalCapability.getAllNames()) {
      if (GlobalCapability.ADMINISTRATE_SERVER.equals(c)) {
        assertFalse(info.administrateServer);
      } else if (GlobalCapability.PRIORITY.equals(c)) {
        assertFalse(info.priority);
      } else if (GlobalCapability.QUERY_LIMIT.equals(c)) {
        assertEquals(0, info.queryLimit.min);
        assertEquals(0, info.queryLimit.max);
      } else {
        assertTrue(String.format("capability %s was not granted", c),
            (Boolean)CapabilityInfo.class.getField(c).get(info));
      }
    }
  }

  private void grantAllCapabilities() throws IOException,
      ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(allProjects);
    md.setMessage("Make super user");
    ProjectConfig config = ProjectConfig.read(md);
    AccessSection s = config.getAccessSection(
        AccessSection.GLOBAL_CAPABILITIES);
    for (String c: GlobalCapability.getAllNames()) {
      if (GlobalCapability.ADMINISTRATE_SERVER.equals(c)) {
        continue;
      }
      Permission p = s.getPermission(c, true);
      AccountGroup projectOwnersGroup = groupCache.get(
          new AccountGroup.NameKey("Registered Users"));
      PermissionRule rule = new PermissionRule(
          config.resolve(projectOwnersGroup));
      p.add(rule);
    }
    config.commit(md);
    projectCache.evict(config.getProject());
  }
}
