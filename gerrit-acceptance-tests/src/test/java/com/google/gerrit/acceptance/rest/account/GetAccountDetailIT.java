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
import static com.google.gerrit.acceptance.rest.account.AccountAssert.assertAccountInfo;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.GetDetail.AccountDetailInfo;
import org.junit.Test;

public class GetAccountDetailIT extends AbstractDaemonTest {
  @Test
  public void getDetail() throws Exception {
    RestResponse r = adminRestSession.get("/accounts/" + admin.username + "/detail/");
    AccountDetailInfo info = newGson().fromJson(r.getReader(), AccountDetailInfo.class);
    assertAccountInfo(admin, info);
    Account account = accountCache.get(admin.getId()).getAccount();
    assertThat(info.registeredOn).isEqualTo(account.getRegisteredOn());
  }
}
