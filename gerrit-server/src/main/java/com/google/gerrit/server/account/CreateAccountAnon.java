// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_UUID;

import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.UUID;
import java.io.IOException;

@Singleton
public class CreateAccountAnon implements RestReadView<AccountResource> {
  private AccountManager accountManager;

  @Inject
  CreateAccountAnon(AccountManager accountManager) {
    this.accountManager = accountManager;
  }

  @Override
  public AuthResult apply(AccountResource rsrc) throws IOException, OrmException, ResourceConflictException {
    try {
      return accountManager.authenticate(
          new AuthRequest(ExternalId.Key.create(SCHEME_UUID, UUID.randomUUID().toString())));
    } catch (AccountException e) {
      if (e != null) {
        throw new ResourceConflictException("cannot create new account " + e);
      } else {
        throw new ResourceConflictException("cannot create new account");
      }
    }
  }
}
