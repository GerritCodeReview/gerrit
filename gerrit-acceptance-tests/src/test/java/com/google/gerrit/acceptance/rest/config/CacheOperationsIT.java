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

import static com.google.gerrit.server.config.PostCaches.Operation.FLUSH_ALL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.server.config.ListCaches.CacheInfo;
import com.google.gerrit.server.config.PostCaches;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;

public class CacheOperationsIT extends AbstractDaemonTest {

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
}
