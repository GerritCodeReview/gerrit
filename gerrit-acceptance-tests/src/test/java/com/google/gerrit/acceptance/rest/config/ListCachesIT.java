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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.server.config.ListCaches.CacheInfo;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ListCachesIT extends AbstractDaemonTest {

  @Test
  public void listCaches() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Map<String, CacheInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<Map<String, CacheInfo>>() {}.getType());

    assertTrue(result.containsKey("accounts"));
    CacheInfo accountsCacheInfo = result.get("accounts");
    assertNull(accountsCacheInfo.type);
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

  @Test
  public void listCaches_Forbidden() throws IOException {
    RestResponse r = userSession.get("/config/server/caches/");
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  @Test
  public void listCacheNames() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/?format=LIST");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    List<String> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<List<String>>() {}.getType());
    assertTrue(result.contains("accounts"));
    assertTrue(result.contains("projects"));
    assertTrue(result.indexOf("accounts") < result.indexOf("projects"));
  }

  @Test
  public void listCaches_BadRequest() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/?format=NONSENSE");
    assertEquals(HttpStatus.SC_BAD_REQUEST, r.getStatusCode());
  }
}
