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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import org.junit.Test;

public class GetCacheIT extends AbstractDaemonTest {

  @Test
  public void getCache() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/caches/accounts");
    r.assertOK();

    // GetCache is implemented using ListCaches. See ListCacheIT.listCaches for detailed coverage.
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
