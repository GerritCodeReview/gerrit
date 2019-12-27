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

import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.AccountResource;
import com.google.inject.Singleton;

/**
 * REST endpoint for updating an existing email address of an account.
 *
 * <p>This REST endpoint handles {@code PUT
 * /accounts/<account-identifier>/emails/<email-identifier>} requests if the specified email address
 * already exists for the account. If it doesn't exist yet, the request is handled by {@link
 * CreateEmail}.
 *
 * <p>We do not support email address updates via this path, hence this REST endpoint always throws
 * a {@link ResourceConflictException} which results in a {@code 409 Conflict} response.
 *
 * <p>This REST endpoint solely exists to avoid user confusion if they create a new email address
 * with {@code PUT /accounts/<account-identifier>/emails/<email-identifier>} and then repeat the
 * same request. Without this REST endpoint the second request would fail with {@code 404 Not
 * Found}, which would be surprising to the user.
 */
@Singleton
public class PutEmail implements RestModifyView<AccountResource.Email, EmailInput> {
  @Override
  public Response<?> apply(AccountResource.Email rsrc, EmailInput input)
      throws ResourceConflictException {
    throw new ResourceConflictException("email exists");
  }
}
