// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.auth;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import java.io.BufferedReader;
import java.util.stream.Collectors;
import org.junit.Test;

public class AuthenticationCheckIT extends AbstractDaemonTest {
  @Test
  public void authCheck_loggedInUser_returnsOk() throws Exception {
    RestResponse r = adminRestSession.get("/auth-check");
    r.assertNoContent();
    // Jetty strips Content-Length when status is NO_CONTENT
  }

  @Test
  public void authCheck_anonymousUser_returnsForbidden() throws Exception {
    RestResponse r = anonymousRestSession.get("/auth-check");
    r.assertForbidden();
  }

  @Test
  public void authCheckSvg_loggedInUser_returnsOk() throws Exception {
    RestResponse r = adminRestSession.get("/auth-check.svg");
    r.assertOK();
    BufferedReader br = new BufferedReader(r.getReader());
    String content = br.lines().collect(Collectors.joining());
    assertThat(content).contains("<svg xmlns");
    assertThat(r.getHeader("Content-Type")).isEqualTo("image/svg+xml;charset=utf-8");
  }
}
