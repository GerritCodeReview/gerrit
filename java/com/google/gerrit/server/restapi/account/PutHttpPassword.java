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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.common.HttpPasswordInput;
import com.google.gerrit.extensions.common.TokenInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AuthTokenConflictException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * REST endpoint to set/delete the password for HTTP access of an account.
 *
 * <p>This REST endpoint handles {@code PUT /accounts/<account-identifier>/password.http} and {@code
 * DELETE /accounts/<account-identifier>/password.http} requests.
 *
 * <p>Gerrit only stores the hash of the HTTP password, hence if an HTTP password was set it's not
 * possible to get it back from Gerrit.
 */
@Singleton
@Deprecated
public class PutHttpPassword implements RestModifyView<AccountResource, HttpPasswordInput> {
  @VisibleForTesting public static final String LEGACY_ID = "legacy";

  private final CreateToken putToken;
  private final DeleteToken deleteToken;

  @Inject
  PutHttpPassword(CreateToken putToken, DeleteToken deleteToken) {
    this.putToken = putToken;
    this.deleteToken = deleteToken;
  }

  @Override
  public Response<String> apply(AccountResource rsrc, HttpPasswordInput input)
      throws AuthException,
          ResourceNotFoundException,
          ResourceConflictException,
          IOException,
          ConfigInvalidException,
          PermissionBackendException,
          AuthTokenConflictException {

    if (input == null) {
      input = new HttpPasswordInput();
    }
    input.httpPassword = Strings.emptyToNull(input.httpPassword);
    boolean isDeleteOp = !input.generate && input.httpPassword == null;
    Response<String> resp = deleteToken.apply(rsrc.getUser(), LEGACY_ID, isDeleteOp);

    if (isDeleteOp) {
      return resp;
    }
    TokenInput tokenInput = new TokenInput();
    tokenInput.token = input.httpPassword;
    return Response.created(
        putToken.apply(rsrc, IdString.fromDecoded(LEGACY_ID), tokenInput).value().token);
  }
}
