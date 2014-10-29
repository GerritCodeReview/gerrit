// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.gerrit.acceptance.rest.account.AccountAssert.assertAccountInfo;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.account.AccountInfo;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;

public class GetAccountIT extends AbstractDaemonTest {
  @Test
  public void getNonExistingAccount_NotFound() throws Exception {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        adminSession.get("/accounts/non-existing").getStatusCode());
  }

  @Test
  public void getAccount() throws Exception {
    // by formatted string
    testGetAccount("/accounts/"
        + Url.encode(admin.fullName + " <" + admin.email + ">"), admin);

    // by email
    testGetAccount("/accounts/" + admin.email, admin);

    // by full name
    testGetAccount("/accounts/" + admin.fullName, admin);

    // by account ID
    testGetAccount("/accounts/" + admin.id.get(), admin);

    // by user name
    testGetAccount("/accounts/" + admin.username, admin);

    // by 'self'
    testGetAccount("/accounts/self", admin);
  }

  private void testGetAccount(String url, TestAccount expectedAccount)
      throws IOException {
    RestResponse r = adminSession.get(url);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertAccountInfo(expectedAccount, newGson()
        .fromJson(r.getReader(), AccountInfo.class));
  }
}
