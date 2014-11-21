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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Test;

import java.io.IOException;

public class CapabilitiesIT extends AbstractDaemonTest {

  @Test
  public void testCapabilitiesUser() throws Exception {
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
      } else if (GlobalCapability.BATCH_CHANGES_LIMIT.equals(c)) {
        assertEquals(0, info.batchChangesLimit.max);
      } else {
        assertTrue(String.format("capability %s was not granted", c),
            (Boolean)CapabilityInfo.class.getField(c).get(info));
      }
    }
  }

  @Test
  public void testCapabilitiesAdmin() throws Exception {
    RestResponse r =
        adminSession.get("/accounts/self/capabilities");
    int code = r.getStatusCode();
    assertEquals(code, 200);
    CapabilityInfo info = (new Gson()).fromJson(r.getReader(),
        new TypeToken<CapabilityInfo>() {}.getType());
    for (String c: GlobalCapability.getAllNames()) {
      if (GlobalCapability.PRIORITY.equals(c)) {
        assertFalse(info.priority);
      } else if (GlobalCapability.QUERY_LIMIT.equals(c)) {
        assertNotNull("missing queryLimit", info.queryLimit);
        assertEquals(0, info.queryLimit.min);
        assertEquals(500, info.queryLimit.max);
      } else if (GlobalCapability.ACCESS_DATABASE.equals(c)) {
        assertFalse(info.accessDatabase);
      } else if (GlobalCapability.RUN_AS.equals(c)) {
        assertFalse(info.runAs);
      } else if (GlobalCapability.BATCH_CHANGES_LIMIT.equals(c)) {
        assertNotNull("missing batchChangesLimit", info.batchChangesLimit);
        assertEquals(0, info.batchChangesLimit.max);
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
      p.add(new PermissionRule(
          config.resolve(SystemGroupBackend.getGroup(
              SystemGroupBackend.REGISTERED_USERS))));
    }
    config.commit(md);
    projectCache.evict(config.getProject());
  }
}
