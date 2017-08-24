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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.server.config.ListCaches.CacheInfo;
import com.google.gerrit.server.config.ListCaches.CacheType;
import org.junit.Test;

public class GetCacheIT extends AbstractDaemonTest {

  @Test
  public void getCache() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/caches/accounts");
    r.assertOK();
    CacheInfo result = newGson().fromJson(r.getReader(), CacheInfo.class);

    assertThat(result.name).isEqualTo("accounts");
    assertThat(result.type).isEqualTo(CacheType.MEM);
    assertThat(result.entries.mem).isAtLeast(1L);
    assertThat(result.averageGet).isNotNull();
    assertThat(result.averageGet).endsWith("s");
    assertThat(result.entries.disk).isNull();
    assertThat(result.entries.space).isNull();
    assertThat(result.hitRatio.mem).isAtLeast(0);
    assertThat(result.hitRatio.mem).isAtMost(100);
    assertThat(result.hitRatio.disk).isNull();

    userRestSession.get("/config/server/version").consume();
    r = adminRestSession.get("/config/server/caches/accounts");
    r.assertOK();
    result = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertThat(result.entries.mem).isEqualTo(2);
  }

  @Test
  public void getCache_Forbidden() throws Exception {
    userRestSession.get("/config/server/caches/accounts").assertForbidden();
  }

  @Test
  public void getCache_NotFound() throws Exception {
    adminRestSession.get("/config/server/caches/nonExisting").assertNotFound();
  }

  @Test
  public void getCacheWithGerritPrefix() throws Exception {
    adminRestSession.get("/config/server/caches/gerrit-accounts").assertOK();
  }
}
