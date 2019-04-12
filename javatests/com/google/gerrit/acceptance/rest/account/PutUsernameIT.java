// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.accounts.UsernameInput;
import org.junit.Test;

public class PutUsernameIT extends AbstractDaemonTest {
  @Test
  public void set() throws Exception {
    UsernameInput in = new UsernameInput();
    in.username = "myUsername";
    RestResponse r =
        adminRestSession.put("/accounts/" + accountCreator.create().id().get() + "/username", in);
    r.assertOK();
    assertThat(newGson().fromJson(r.getReader(), String.class)).isEqualTo(in.username);
  }

  @Test
  public void setExisting_Conflict() throws Exception {
    UsernameInput in = new UsernameInput();
    in.username = admin.username();
    adminRestSession
        .put("/accounts/" + accountCreator.create().id().get() + "/username", in)
        .assertConflict();
  }

  @Test
  public void setNew_MethodNotAllowed() throws Exception {
    UsernameInput in = new UsernameInput();
    in.username = "newUsername";
    adminRestSession
        .put("/accounts/" + admin.username() + "/username", in)
        .assertMethodNotAllowed();
  }

  @Test
  public void delete_MethodNotAllowed() throws Exception {
    adminRestSession.put("/accounts/" + admin.username() + "/username").assertMethodNotAllowed();
  }
}
