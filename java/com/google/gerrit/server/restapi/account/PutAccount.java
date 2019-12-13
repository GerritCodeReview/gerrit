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

package com.google.gerrit.server.restapi.account;

import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.AccountResource;
import com.google.inject.Singleton;

/**
 * REST endpoint for updating an existing account.
 *
 * <p>This REST endpoint handles {@code PUT /accounts/<account-identifier>} requests if the
 * specified account already exists. If it doesn't exist yet, the request is handled by {@link
 * CreateAccount}.
 *
 * <p>We do not support account updates via this path, hence this REST endpoint always throws a
 * {@link ResourceConflictException} which results in a {@code 409 Conflict} response. Account
 * properties can only be updated via the dedicated REST endpoints that serve {@code PUT} requests
 * on {@code /accounts/<account-identifier>/<account-view>}.
 *
 * <p>This REST endpoint solely exists to avoid user confusion if they create a new account with
 * {@code PUT /accounts/<account-identifier>} and then repeat the same request. Without this REST
 * endpoint the second request would fail with {@code 404 Not Found}, which would be surprising to
 * the user.
 */
@Singleton
public class PutAccount implements RestModifyView<AccountResource, AccountInput> {
  @Override
  public Response<AccountInfo> apply(AccountResource resource, AccountInput input)
      throws ResourceConflictException {
    throw new ResourceConflictException("account exists");
  }
}
