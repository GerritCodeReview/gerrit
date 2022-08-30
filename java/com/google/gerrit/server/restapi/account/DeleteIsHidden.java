// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.restapi.account;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.errors.ConfigInvalidException;

@RequiresCapability(GlobalCapability.MODIFY_ACCOUNT)
@Singleton
public class DeleteIsHidden implements RestModifyView<AccountResource, Input> {

  private final Provider<AccountsUpdate> accountsUpdateProvider;

  @Inject
  DeleteIsHidden(@ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider) {
    this.accountsUpdateProvider = accountsUpdateProvider;
  }

  @Override
  public Response<String> apply(AccountResource rsrc, Input input)
      throws RestApiException, ConfigInvalidException, IOException {

    AtomicBoolean alreadyHidden = new AtomicBoolean(false);
    Optional<AccountState> updatedAccount =
        accountsUpdateProvider
            .get()
            .update(
                "Unset IsHidden Account via API",
                rsrc.getUser().getAccountId(),
                (a, u) -> {
                  if (a.account().isHidden().isPresent() && !a.account().isHidden().get()) {
                    alreadyHidden.set(true);
                  } else {
                    u.setHidden(false);
                  }
                });
    if (!updatedAccount.isPresent()) {
      throw new ResourceNotFoundException("account not found");
    }
    if (alreadyHidden.get()) {
      throw new ResourceConflictException("account not active");
    }

    return Response.none();
  }
}
