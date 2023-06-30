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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.rest.account.AccountAssert.assertAccountInfo;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.inject.Inject;
import org.junit.Test;

@NoHttpd
public class GetAccountIT extends AbstractDaemonTest {
  @Inject private AccountOperations accountOperations;

  @Test
  public void getNonExistingAccount_NotFound() throws Exception {
    assertThrows(ResourceNotFoundException.class, () -> gApi.accounts().id("non-existing").get());
  }

  @Test
  public void getAccount_resolveExactDisabled() throws Exception {
    // by formatted string
    testGetAccount(admin.fullName() + " <" + admin.email() + ">", admin);

    // by email
    testGetAccount(admin.email(), admin);

    // by full name
    testGetAccount(admin.fullName(), admin);

    // by account ID
    testGetAccount(Integer.toString(admin.id().get()), admin);

    // by user name
    testGetAccount(admin.username(), admin);

    // by 'self'
    testGetAccount("self", admin);
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      value = ExperimentFeaturesConstants.GERRIT_BACKEND_FEATURE_RESTRICT_ACCOUNT_API_EXACT)
  public void getAccount_resolveExactEnabled() throws Exception {
    // full name and email formats are not supported
    assertThrows(
        ResourceNotFoundException.class,
        () -> testGetAccount(admin.fullName() + " <" + admin.email() + ">", admin));

    // by email
    testGetAccount(admin.email(), admin);

    // by full name is not supported
    assertThrows(ResourceNotFoundException.class, () -> testGetAccount(admin.fullName(), admin));

    // by account ID
    testGetAccount(Integer.toString(admin.id().get()), admin);

    // by user name is not supported
    assertThrows(ResourceNotFoundException.class, () -> testGetAccount(admin.username(), admin));

    // by 'self'
    testGetAccount("self", admin);
  }

  @Test
  public void getInactiveAccount() throws Exception {
    accountOperations.account(user.id()).forUpdate().inactive().update();
    AccountInfo accountInfo = gApi.accounts().id(user.id().get()).get();
    assertThat(accountInfo._accountId).isEqualTo(user.id().get());
    assertThat(accountInfo.name).isEqualTo(user.fullName());
    assertThat(accountInfo.email).isEqualTo(user.email());
    assertThat(accountInfo.inactive).isTrue();
  }

  @Test
  public void getServiceUserAccount() throws Exception {
    TestAccount serviceUser =
        accountCreator.create(
            "robot1", "robot1@example.com", "Ro Bot", "Ro", ServiceUserClassifier.SERVICE_USERS);
    assertThat(serviceUser.tags()).containsExactly("SERVICE_USER");
    testGetAccount(serviceUser.id().toString(), serviceUser);
  }

  private void testGetAccount(String id, TestAccount expectedAccount) throws Exception {
    assertAccountInfo(expectedAccount, gApi.accounts().id(id).get());
  }
}
