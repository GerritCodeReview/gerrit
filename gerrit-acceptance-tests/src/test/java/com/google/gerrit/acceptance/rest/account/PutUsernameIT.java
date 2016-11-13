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
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.PutUsername;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Test;

public class PutUsernameIT extends AbstractDaemonTest {
  @Inject private SchemaFactory<ReviewDb> reviewDbProvider;

  @Test
  public void set() throws Exception {
    PutUsername.Input in = new PutUsername.Input();
    in.username = "myUsername";
    RestResponse r = adminRestSession.put("/accounts/" + createUser().get() + "/username", in);
    r.assertOK();
    assertThat(newGson().fromJson(r.getReader(), String.class)).isEqualTo(in.username);
  }

  @Test
  public void setExisting_Conflict() throws Exception {
    PutUsername.Input in = new PutUsername.Input();
    in.username = admin.username;
    adminRestSession.put("/accounts/" + createUser().get() + "/username", in).assertConflict();
  }

  @Test
  public void setNew_MethodNotAllowed() throws Exception {
    PutUsername.Input in = new PutUsername.Input();
    in.username = "newUsername";
    adminRestSession.put("/accounts/" + admin.username + "/username", in).assertMethodNotAllowed();
  }

  @Test
  public void delete_MethodNotAllowed() throws Exception {
    adminRestSession.put("/accounts/" + admin.username + "/username").assertMethodNotAllowed();
  }

  private Account.Id createUser() throws Exception {
    try (ReviewDb db = reviewDbProvider.open()) {
      Account.Id id = new Account.Id(db.nextAccountId());
      Account a = new Account(id, TimeUtil.nowTs());
      db.accounts().insert(Collections.singleton(a));
      return id;
    }
  }
}
