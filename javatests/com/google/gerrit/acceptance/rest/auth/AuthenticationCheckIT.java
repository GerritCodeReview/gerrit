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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import org.junit.Test;

public class AuthenticationCheckIT extends AbstractDaemonTest {
  @Test
  public void authCheck_loggedInUser_returnsOk() throws Exception {
    RestResponse r = adminRestSession.get("/auth-check");
    r.assertNoContent();
  }

  @Test
  public void authCheck_anonymousUser_returnsForbidden() throws Exception {
    RestSession anonymous = new RestSession(server, null);
    RestResponse r = anonymous.get("/auth-check");
    r.assertForbidden();
  }
}
