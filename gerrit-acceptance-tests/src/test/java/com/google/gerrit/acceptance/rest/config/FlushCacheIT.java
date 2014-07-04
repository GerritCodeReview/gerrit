// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.config;

import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.Util.allow;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.ListCaches.CacheInfo;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;

public class FlushCacheIT extends AbstractDaemonTest {

  @Inject
  private ProjectCache projectCache;

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  @Test
  public void flushCache() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/groups");
    CacheInfo result = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertTrue(result.entries.mem.longValue() > 0);

    r = adminSession.post("/config/server/caches/groups/flush");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();

    r = adminSession.get("/config/server/caches/groups");
    result = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertNull(result.entries.mem);
  }

  @Test
  public void flushCache_Forbidden() throws IOException {
    RestResponse r = userSession.post("/config/server/caches/accounts/flush");
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  @Test
  public void flushCache_NotFound() throws IOException {
    RestResponse r = adminSession.post("/config/server/caches/nonExisting/flush");
    assertEquals(HttpStatus.SC_NOT_FOUND, r.getStatusCode());
  }

  @Test
  public void flushCacheWithGerritPrefix() throws IOException {
    RestResponse r = adminSession.post("/config/server/caches/gerrit-accounts/flush");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
  }

  @Test
  public void flushWebSessionsCache() throws IOException {
    RestResponse r = adminSession.post("/config/server/caches/web_sessions/flush");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
  }

  @Test
  public void flushWebSessionsCache_Forbidden() throws IOException {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    AccountGroup.UUID registeredUsers =
        SystemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    allow(cfg, GlobalCapability.VIEW_CACHES, registeredUsers);
    allow(cfg, GlobalCapability.FLUSH_CACHES, registeredUsers);
    saveProjectConfig(cfg);

    RestResponse r = userSession.post("/config/server/caches/accounts/flush");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();

    r = userSession.post("/config/server/caches/web_sessions/flush");
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  private void saveProjectConfig(ProjectConfig cfg) throws IOException {
    MetaDataUpdate md = metaDataUpdateFactory.create(allProjects);
    try {
      cfg.commit(md);
    } finally {
      md.close();
    }
    projectCache.evict(allProjects);
  }
}
