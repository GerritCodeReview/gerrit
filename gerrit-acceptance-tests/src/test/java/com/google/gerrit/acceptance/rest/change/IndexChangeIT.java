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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.GlobalCapability;

import org.apache.http.HttpStatus;
import org.junit.Test;

public class IndexChangeIT extends AbstractDaemonTest {
  @Test
  public void indexChangeWithCapability() throws Exception {
    allowGlobalCapability(GlobalCapability.INDEX_CHANGE, REGISTERED_USERS);
    String changeId = createChange().getChangeId();
    RestResponse r = userSession.post("/changes/" + changeId + "/index/");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_NO_CONTENT);
  }

  @Test
  public void indexChangeWithoutCapability() throws Exception {
    String changeId = createChange().getChangeId();
    RestResponse r = userSession.post("/changes/" + changeId + "/index/");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void indexChangeAsAdminWithoutCapability() throws Exception {
    String changeId = createChange().getChangeId();
    RestResponse r = adminSession.post("/changes/" + changeId + "/index/");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_FORBIDDEN);
  }
}
