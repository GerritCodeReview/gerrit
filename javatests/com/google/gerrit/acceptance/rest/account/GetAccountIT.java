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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import org.junit.Test;

@NoHttpd
public class GetAccountIT extends AbstractDaemonTest {
  @Test(expected = ResourceNotFoundException.class)
  public void getNonExistingAccount_NotFound() throws Exception {
    gApi.accounts().id("non-existing").get();
  }

  @Test
  public void getAccount() throws Exception {
    // by formatted string
    testGetAccount(admin.fullName + " <" + admin.email + ">", admin);

    // by email
    testGetAccount(admin.email, admin);

    // by full name
    testGetAccount(admin.fullName, admin);

    // by account ID
    testGetAccount(Integer.toString(admin.id.get()), admin);

    // by user name
    testGetAccount(admin.username, admin);

    // by 'self'
    testGetAccount("self", admin);
  }

  private void testGetAccount(String id, TestAccount expectedAccount) throws Exception {
    assertAccountInfo(expectedAccount, gApi.accounts().id(id).get());
  }
}
