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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.server.config.ListCaches.CacheInfo;

import org.apache.http.HttpStatus;
import org.junit.Test;

public class FlushCacheIT extends AbstractDaemonTest {

  @Test
  public void flushCache() throws Exception {
    RestResponse r = adminSession.get("/config/server/caches/groups");
    CacheInfo result = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertThat(result.entries.mem).isGreaterThan((long)0);

    r = adminSession.post("/config/server/caches/groups/flush");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    r.consume();

    r = adminSession.get("/config/server/caches/groups");
    result = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertThat(result.entries.mem).isNull();
  }

  @Test
  public void flushCache_Forbidden() throws Exception {
    RestResponse r = userSession.post("/config/server/caches/accounts/flush");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void flushCache_NotFound() throws Exception {
    RestResponse r = adminSession.post("/config/server/caches/nonExisting/flush");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void flushCacheWithGerritPrefix() throws Exception {
    RestResponse r = adminSession.post("/config/server/caches/gerrit-accounts/flush");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
  }

  @Test
  public void flushWebSessionsCache() throws Exception {
    RestResponse r = adminSession.post("/config/server/caches/web_sessions/flush");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
  }

  @Test
  public void flushWebSessionsCache_Forbidden() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS,
        GlobalCapability.VIEW_CACHES, GlobalCapability.FLUSH_CACHES);
    try {
      RestResponse r = userSession.post("/config/server/caches/accounts/flush");
      assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
      r.consume();

      r = userSession.post("/config/server/caches/web_sessions/flush");
      assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_FORBIDDEN);
    } finally {
      removeGlobalCapabilities(REGISTERED_USERS,
          GlobalCapability.VIEW_CACHES, GlobalCapability.FLUSH_CACHES);
    }
  }
}
