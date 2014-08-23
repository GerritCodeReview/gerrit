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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Ordering;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.PostCaches;
import com.google.gerrit.server.config.ListCaches.CacheInfo;
import com.google.gerrit.server.config.ListCaches.CacheType;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.Util;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.util.Base64;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CachesIT extends AbstractDaemonTest {

  @Inject
  private AllProjectsName allProjects;

  @Test
  public void testAll() throws Exception {
    listCaches();
    listCaches_Forbidden();
    listCacheNames();
    listCacheNamesTextList();
    listCaches_BadRequest();
    flushCache();
    flushCache_Forbidden();
    flushCache_NotFound();
    flushCacheWithGerritPrefix();
    reset();
    flushWebSessionsCache();
    flushWebSessionsCache_Forbidden();
    reset();
    getCache();
    getCache_Forbidden();
    getCache_NotFound();
    getCacheWithGerritPrefix();
    flushAll();
    flushAll_Forbidden();
    flushAll_BadRequest();
    reset();
    flush();
    flush_Forbidden();
    flush_BadRequest();
    flush_UnprocessableEntity();
    flushWebSessions_Forbidden();
  }

  public void listCaches() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Map<String, CacheInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<Map<String, CacheInfo>>() {}.getType());

    assertTrue(result.containsKey("accounts"));
    CacheInfo accountsCacheInfo = result.get("accounts");
    assertEquals(CacheType.MEM, accountsCacheInfo.type);
    assertEquals(1, accountsCacheInfo.entries.mem.longValue());
    assertNotNull(accountsCacheInfo.averageGet);
    assertTrue(accountsCacheInfo.averageGet.endsWith("s"));
    assertNull(accountsCacheInfo.entries.disk);
    assertNull(accountsCacheInfo.entries.space);
    assertTrue(accountsCacheInfo.hitRatio.mem >= 0);
    assertTrue(accountsCacheInfo.hitRatio.mem <= 100);
    assertNull(accountsCacheInfo.hitRatio.disk);

    userSession.get("/config/server/version").consume();
    r = adminSession.get("/config/server/caches/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    result = newGson().fromJson(r.getReader(),
        new TypeToken<Map<String, CacheInfo>>() {}.getType());
    assertEquals(2, result.get("accounts").entries.mem.longValue());
  }

  public void listCaches_Forbidden() throws IOException {
    RestResponse r = userSession.get("/config/server/caches/");
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  public void listCacheNames() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/?format=LIST");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    List<String> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<List<String>>() {}.getType());
    assertTrue(result.contains("accounts"));
    assertTrue(result.contains("projects"));
    assertTrue(Ordering.natural().isOrdered(result));
  }

  public void listCacheNamesTextList() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/?format=TEXT_LIST");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    String result = new String(Base64.decode(r.getEntityContent()), UTF_8.name());
    List<String> list = Arrays.asList(result.split("\n"));
    assertTrue(list.contains("accounts"));
    assertTrue(list.contains("projects"));
    assertTrue(Ordering.natural().isOrdered(list));
  }

  public void listCaches_BadRequest() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/?format=NONSENSE");
    assertEquals(HttpStatus.SC_BAD_REQUEST, r.getStatusCode());
  }

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

  public void flushCache_Forbidden() throws IOException {
    RestResponse r = userSession.post("/config/server/caches/accounts/flush");
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  public void flushCache_NotFound() throws IOException {
    RestResponse r = adminSession.post("/config/server/caches/nonExisting/flush");
    assertEquals(HttpStatus.SC_NOT_FOUND, r.getStatusCode());
  }

  public void flushCacheWithGerritPrefix() throws IOException {
    RestResponse r = adminSession.post("/config/server/caches/gerrit-accounts/flush");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
  }

  public void flushWebSessionsCache() throws IOException {
    RestResponse r = adminSession.post("/config/server/caches/web_sessions/flush");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
  }

  public void flushWebSessionsCache_Forbidden() throws IOException {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    AccountGroup.UUID registeredUsers =
        SystemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    Util.allow(cfg, GlobalCapability.VIEW_CACHES, registeredUsers);
    Util.allow(cfg, GlobalCapability.FLUSH_CACHES, registeredUsers);
    saveProjectConfig(cfg);

    RestResponse r = userSession.post("/config/server/caches/accounts/flush");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();

    r = userSession.post("/config/server/caches/web_sessions/flush");
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  public void getCache() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/accounts");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    CacheInfo result = newGson().fromJson(r.getReader(), CacheInfo.class);

    assertEquals("accounts", result.name);
    assertEquals(CacheType.MEM, result.type);
    assertEquals(1, result.entries.mem.longValue());
    assertNotNull(result.averageGet);
    assertTrue(result.averageGet.endsWith("s"));
    assertNull(result.entries.disk);
    assertNull(result.entries.space);
    assertTrue(result.hitRatio.mem >= 0);
    assertTrue(result.hitRatio.mem <= 100);
    assertNull(result.hitRatio.disk);

    userSession.get("/config/server/version").consume();
    r = adminSession.get("/config/server/caches/accounts");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    result = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertEquals(2, result.entries.mem.longValue());
  }

  public void getCache_Forbidden() throws IOException {
    RestResponse r = userSession.get("/config/server/caches/accounts");
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  public void getCache_NotFound() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/nonExisting");
    assertEquals(HttpStatus.SC_NOT_FOUND, r.getStatusCode());
  }

  public void getCacheWithGerritPrefix() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/gerrit-accounts");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
  }

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

  public void flushAll_Forbidden() throws IOException {
    RestResponse r = userSession.post("/config/server/caches/",
        new PostCaches.Input(FLUSH_ALL));
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  public void flushAll_BadRequest() throws IOException {
    RestResponse r = adminSession.post("/config/server/caches/",
        new PostCaches.Input(FLUSH_ALL, Arrays.asList("projects")));
    assertEquals(HttpStatus.SC_BAD_REQUEST, r.getStatusCode());
  }

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

  public void flush_Forbidden() throws IOException {
    RestResponse r = userSession.post("/config/server/caches/",
        new PostCaches.Input(FLUSH, Arrays.asList("projects")));
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  public void flush_BadRequest() throws IOException {
    RestResponse r = adminSession.post("/config/server/caches/",
        new PostCaches.Input(FLUSH));
    assertEquals(HttpStatus.SC_BAD_REQUEST, r.getStatusCode());
  }

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

  public void flushWebSessions_Forbidden() throws IOException {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    AccountGroup.UUID registeredUsers =
        SystemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    Util.allow(cfg, GlobalCapability.VIEW_CACHES, registeredUsers);
    Util.allow(cfg, GlobalCapability.FLUSH_CACHES, registeredUsers);
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
