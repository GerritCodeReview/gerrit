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

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;

public class GetCacheIT extends AbstractDaemonTest {

  @Test
  public void getCache() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/accounts");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    CacheInfo result = newGson().fromJson(r.getReader(), CacheInfo.class);

    assertEquals("accounts", result.name);
    assertNull(result.type);
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

  @Test
  public void getCache_Forbidden() throws IOException {
    RestResponse r = userSession.get("/config/server/caches/accounts");
    assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatusCode());
  }

  @Test
  public void getCache_NotFound() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/nonExisting");
    assertEquals(HttpStatus.SC_NOT_FOUND, r.getStatusCode());
  }

  @Test
  public void getCacheWithGerritPrefix() throws IOException {
    RestResponse r = adminSession.get("/config/server/caches/gerrit-accounts");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
  }
}
