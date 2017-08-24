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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.Ordering;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.server.config.ListCaches.CacheInfo;
import com.google.gerrit.server.config.ListCaches.CacheType;
import com.google.gson.reflect.TypeToken;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.util.Base64;
import org.junit.Test;

public class ListCachesIT extends AbstractDaemonTest {

  @Test
  public void listCaches() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/caches/");
    r.assertOK();
    Map<String, CacheInfo> result =
        newGson().fromJson(r.getReader(), new TypeToken<Map<String, CacheInfo>>() {}.getType());

    assertThat(result).containsKey("accounts");
    CacheInfo accountsCacheInfo = result.get("accounts");
    assertThat(accountsCacheInfo.type).isEqualTo(CacheType.MEM);
    assertThat(accountsCacheInfo.entries.mem).isAtLeast(1L);
    assertThat(accountsCacheInfo.averageGet).isNotNull();
    assertThat(accountsCacheInfo.averageGet).endsWith("s");
    assertThat(accountsCacheInfo.entries.disk).isNull();
    assertThat(accountsCacheInfo.entries.space).isNull();
    assertThat(accountsCacheInfo.hitRatio.mem).isAtLeast(0);
    assertThat(accountsCacheInfo.hitRatio.mem).isAtMost(100);
    assertThat(accountsCacheInfo.hitRatio.disk).isNull();

    userRestSession.get("/config/server/version").consume();
    r = adminRestSession.get("/config/server/caches/");
    r.assertOK();
    result =
        newGson().fromJson(r.getReader(), new TypeToken<Map<String, CacheInfo>>() {}.getType());
    assertThat(result.get("accounts").entries.mem).isEqualTo(2);
  }

  @Test
  public void listCaches_Forbidden() throws Exception {
    userRestSession.get("/config/server/caches/").assertForbidden();
  }

  @Test
  public void listCacheNames() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/caches/?format=LIST");
    r.assertOK();
    List<String> result =
        newGson().fromJson(r.getReader(), new TypeToken<List<String>>() {}.getType());
    assertThat(result).contains("accounts");
    assertThat(result).contains("projects");
    assertThat(Ordering.natural().isOrdered(result)).isTrue();
  }

  @Test
  public void listCacheNamesTextList() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/caches/?format=TEXT_LIST");
    r.assertOK();
    String result = new String(Base64.decode(r.getEntityContent()), UTF_8.name());
    List<String> list = Arrays.asList(result.split("\n"));
    assertThat(list).contains("accounts");
    assertThat(list).contains("projects");
    assertThat(Ordering.natural().isOrdered(list)).isTrue();
  }

  @Test
  public void listCaches_BadRequest() throws Exception {
    adminRestSession.get("/config/server/caches/?format=NONSENSE").assertBadRequest();
  }
}
