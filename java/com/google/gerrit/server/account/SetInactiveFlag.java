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

import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class SetInactiveFlag {

  private final AccountsUpdate.Server accountsUpdate;

  @Inject
  SetInactiveFlag(AccountsUpdate.Server accountsUpdate) {
    this.accountsUpdate = accountsUpdate;
  }

  public Response<?> deactivate(Account.Id accountId)
      throws RestApiException, IOException, ConfigInvalidException {
    AtomicBoolean alreadyInactive = new AtomicBoolean(false);
    Account account =
        accountsUpdate
            .create()
            .update(
                accountId,
                (a, u) -> {
                  if (!a.isActive()) {
                    alreadyInactive.set(true);
                  } else {
                    u.setActive(false);
                  }
                });
    if (account == null) {
      throw new ResourceNotFoundException("account not found");
    }
    if (alreadyInactive.get()) {
      throw new ResourceConflictException("account not active");
    }
    return Response.none();
  }

  public Response<String> activate(Account.Id accountId)
      throws ResourceNotFoundException, IOException, ConfigInvalidException {
    AtomicBoolean alreadyActive = new AtomicBoolean(false);
    Account account =
        accountsUpdate
            .create()
            .update(
                accountId,
                (a, u) -> {
                  if (a.isActive()) {
                    alreadyActive.set(true);
                  } else {
                    u.setActive(true);
                  }
                });
    if (account == null) {
      throw new ResourceNotFoundException("account not found");
    }
    return alreadyActive.get() ? Response.ok("") : Response.created("");
  }
}
