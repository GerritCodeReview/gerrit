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

import static com.google.gerrit.server.config.PostCaches.Operation.FLUSH;
import static com.google.gerrit.server.config.PostCaches.Operation.FLUSH_ALL;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.Util.allow;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.config.ListCaches.CacheInfo;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.PostCaches;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

public class CacheOperationsIT extends AbstractDaemonTest {

  @Inject
  private ProjectCache projectCache;

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  @Test
  public void flushAll() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/project_list");
    CacheInfo cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertTrue(cacheInfo.entries.mem.longValue() > 0);

    r = adminSession.post("/config/server/caches/", new PostCaches.Input(FLUSH_ALL));
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();

    r = adminSession.get("/config/server/caches/project_list");
    cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertNull(cacheInfo.entries.mem);
  }

  @Test
  public void flushAll_Forbidden() throws IOException {
    RestResponse r = userSession.post("/config/server/caches/",
        new PostCaches.Input(FLUSH_ALL));
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  @Test
  public void flushAll_BadRequest() throws IOException {
    RestResponse r = adminSession.post("/config/server/caches/",
        new PostCaches.Input(FLUSH_ALL, Arrays.asList("projects")));
    assertEquals(HttpStatus.SC_BAD_REQUEST, r.getStatusCode());
  }

  @Test
  public void flush() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/project_list");
    CacheInfo cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertTrue(cacheInfo.entries.mem.longValue() > 0);

    r = adminSession.get("/config/server/caches/projects");
    cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertTrue(cacheInfo.entries.mem.longValue() > 1);

    r = adminSession.post("/config/server/caches/",
        new PostCaches.Input(FLUSH, Arrays.asList("accounts", "project_list")));
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();

    r = adminSession.get("/config/server/caches/project_list");
    cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertNull(cacheInfo.entries.mem);

    r = adminSession.get("/config/server/caches/projects");
    cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertTrue(cacheInfo.entries.mem.longValue() > 1);
  }

  @Test
  public void flush_Forbidden() throws IOException {
    RestResponse r = userSession.post("/config/server/caches/",
        new PostCaches.Input(FLUSH, Arrays.asList("projects")));
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  @Test
  public void flush_BadRequest() throws IOException {
    RestResponse r = adminSession.post("/config/server/caches/",
        new PostCaches.Input(FLUSH));
    assertEquals(HttpStatus.SC_BAD_REQUEST, r.getStatusCode());
  }

  @Test
  public void flush_UnprocessableEntity() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/projects");
    CacheInfo cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertTrue(cacheInfo.entries.mem.longValue() > 0);

    r = adminSession.post("/config/server/caches/",
        new PostCaches.Input(FLUSH, Arrays.asList("projects", "unprocessable")));
    assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, r.getStatusCode());
    r.consume();

    r = adminSession.get("/config/server/caches/projects");
    cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertTrue(cacheInfo.entries.mem.longValue() > 0);
  }

  @Test
  public void flushWebSessions_Forbidden() throws IOException {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    AccountGroup.UUID registeredUsers =
        SystemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    allow(cfg, GlobalCapability.VIEW_CACHES, registeredUsers);
    allow(cfg, GlobalCapability.FLUSH_CACHES, registeredUsers);
    saveProjectConfig(cfg);

    RestResponse r = userSession.post("/config/server/caches/",
        new PostCaches.Input(FLUSH, Arrays.asList("projects")));
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();

    r = userSession.post("/config/server/caches/",
        new PostCaches.Input(FLUSH, Arrays.asList("web_sessions")));
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
