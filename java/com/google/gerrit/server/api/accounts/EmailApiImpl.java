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

package com.google.gerrit.server.api.accounts;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.accounts.EmailApi;
import com.google.gerrit.extensions.common.EmailInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.account.DeleteEmail;
import com.google.gerrit.server.restapi.account.EmailsCollection;
import com.google.gerrit.server.restapi.account.GetEmail;
import com.google.gerrit.server.restapi.account.PutPreferred;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class EmailApiImpl implements EmailApi {
  interface Factory {
    EmailApiImpl create(AccountResource account, String email);
  }

  private final EmailsCollection emails;
  private final GetEmail get;
  private final DeleteEmail delete;
  private final PutPreferred putPreferred;
  private final AccountResource account;
  private final String email;

  @Inject
  EmailApiImpl(
      EmailsCollection emails,
      GetEmail get,
      DeleteEmail delete,
      PutPreferred putPreferred,
      @Assisted AccountResource account,
      @Assisted String email) {
    this.emails = emails;
    this.get = get;
    this.delete = delete;
    this.putPreferred = putPreferred;
    this.account = account;
    this.email = email;
  }

  @Override
  public EmailInfo get() throws RestApiException {
    try {
      return get.apply(resource());
    } catch (Exception e) {
      throw asRestApiException("Cannot read email", e);
    }
  }

  @Override
  public void delete() throws RestApiException {
    try {
      delete.apply(resource(), new Input());
    } catch (Exception e) {
      throw asRestApiException("Cannot delete email", e);
    }
  }

  @Override
  public void setPreferred() throws RestApiException {
    try {
      putPreferred.apply(resource(), new Input());
    } catch (Exception e) {
      throw asRestApiException(String.format("Cannot set %s as preferred email", email), e);
    }
  }

  private AccountResource.Email resource() throws RestApiException, PermissionBackendException {
    return emails.parse(account, IdString.fromDecoded(email));
  }
}
