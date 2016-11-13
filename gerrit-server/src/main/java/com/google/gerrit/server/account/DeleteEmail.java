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

package com.google.gerrit.server.account;

import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.DeleteEmail.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class DeleteEmail implements RestModifyView<AccountResource.Email, Input> {
  public static class Input {}

  private final Provider<CurrentUser> self;
  private final Realm realm;
  private final Provider<ReviewDb> dbProvider;
  private final AccountManager accountManager;

  @Inject
  DeleteEmail(
      Provider<CurrentUser> self,
      Realm realm,
      Provider<ReviewDb> dbProvider,
      AccountManager accountManager) {
    this.self = self;
    this.realm = realm;
    this.dbProvider = dbProvider;
    this.accountManager = accountManager;
  }

  @Override
  public Response<?> apply(AccountResource.Email rsrc, Input input)
      throws AuthException, ResourceNotFoundException, ResourceConflictException,
          MethodNotAllowedException, OrmException, IOException {
    if (self.get() != rsrc.getUser() && !self.get().getCapabilities().canModifyAccount()) {
      throw new AuthException("not allowed to delete email address");
    }
    return apply(rsrc.getUser(), rsrc.getEmail());
  }

  public Response<?> apply(IdentifiedUser user, String email)
      throws ResourceNotFoundException, ResourceConflictException, MethodNotAllowedException,
          OrmException, IOException {
    if (!realm.allowsEdit(AccountFieldName.REGISTER_NEW_EMAIL)) {
      throw new MethodNotAllowedException("realm does not allow deleting emails");
    }
    AccountExternalId.Key key = new AccountExternalId.Key(AccountExternalId.SCHEME_MAILTO, email);
    AccountExternalId extId = dbProvider.get().accountExternalIds().get(key);
    if (extId == null) {
      throw new ResourceNotFoundException(email);
    }
    try {
      accountManager.unlink(user.getAccountId(), AuthRequest.forEmail(email));
    } catch (AccountException e) {
      throw new ResourceConflictException(e.getMessage());
    }
    return Response.none();
  }
}
