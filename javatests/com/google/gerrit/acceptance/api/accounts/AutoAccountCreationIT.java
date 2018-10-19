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

package com.google.gerrit.acceptance.api.accounts;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.AutoAccountCreator;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AutoAccountCreationIT extends AbstractDaemonTest {
  @Inject private DynamicSet<AutoAccountCreator> autoAccountCreators;
  @Inject private Sequences seq;
  @Inject private @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider;

  private TestAutoAccountCreator testAutoAccountCreator;
  private RegistrationHandle testAutoAccountCreatorRegistrationHandle;

  @Before
  public void setup() {
    testAutoAccountCreator = new TestAutoAccountCreator();
    testAutoAccountCreatorRegistrationHandle =
        autoAccountCreators.add("myPlugin", testAutoAccountCreator);
  }

  @After
  public void cleanup() {
    testAutoAccountCreatorRegistrationHandle.remove();
    testAutoAccountCreatorRegistrationHandle = null;
  }

  @Test
  public void autoCreateAccountOnAddingMemberToGroup() throws Exception {
    String groupName = "testGroup";
    GroupApi groupApi = gApi.groups().create(groupName);

    // check that the user doesn't exist yet
    String user = "foo";
    try {
      gApi.accounts().id(user).get();
      fail("Expected exception");
    } catch (ResourceNotFoundException e) {
      assertThat(e.getMessage()).isEqualTo("Account '" + user + "' is not found or ambiguous");
    }

    // check that the user can be added to a group anyway since an account is automatically created
    groupApi.addMembers(user);
    List<AccountInfo> members = gApi.groups().id(groupName).members();
    assertThat(Iterables.transform(members, i -> i.name))
        .containsExactly(admin.fullName, user)
        .inOrder();

    // check that the user exists now
    assertThat(gApi.accounts().id(user).get().name).isEqualTo(user);

    // check that adding a non-existing user as group member fails if auto account creation is
    // disabled
    user = "bar";
    testAutoAccountCreator.setEnabled(false);
    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage("Account '" + user + "' is not found or ambiguous");
    groupApi.addMembers(user);
  }

  private class TestAutoAccountCreator implements AutoAccountCreator {
    private boolean enabled = true;

    void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    @Override
    public Optional<Account> createAccount(String userId)
        throws OrmException, IOException, ConfigInvalidException {
      if (!enabled) {
        return Optional.empty();
      }

      Account.Id accountId = new Account.Id(seq.nextAccountId());
      AccountState newAccountState =
          accountsUpdateProvider
              .get()
              .insert(
                  "Auto-create account",
                  accountId,
                  u ->
                      u.setFullName(userId)
                          .addExternalId(ExternalId.createUsername(userId, accountId, null))
                          .build());
      return Optional.of(newAccountState.getAccount());
    }
  }
}
