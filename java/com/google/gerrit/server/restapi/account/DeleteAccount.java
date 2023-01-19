// Copyright (C) 2023 The Android Open Source Project
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

import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * REST endpoint for deleting an account.
 *
 * <p>This REST endpoint handles {@code DELETE /accounts/<account-identifier>} requests. Currently, only self deletions are allowed.
 */
@Singleton
public class DeleteAccount implements RestModifyView<AccountResource, AccountInput> {
    private final Provider<CurrentUser> self;
    private final Provider<AccountsUpdate> accountsUpdateProvider;

    @Inject
    public DeleteAccount(Provider<CurrentUser> self, Provider<AccountsUpdate> accountsUpdateProvider) {
        this.self = self;
        this.accountsUpdateProvider = accountsUpdateProvider;
    }

    @Override
    public Response<?> apply(AccountResource rsrc, AccountInput input) throws AuthException, BadRequestException, ResourceConflictException, Exception {
        if (!self.get().hasSameAccountId(rsrc.getUser())) {
            throw new AuthException("Delete account is only permitted for self");
        }

        // DO NOT SUBMIT - implement
//        try {
//            accountsUpdateProvider.get().update()
    }
}
