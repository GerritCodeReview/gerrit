// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.PutUsername.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class PutUsername implements RestModifyView<AccountResource, Input> {
  public static class Input {
    @DefaultInput public String username;
  }

  private final Provider<CurrentUser> self;
  private final ChangeUserName.Factory changeUserNameFactory;
  private final Realm realm;
  private final Provider<ReviewDb> db;

  @Inject
  PutUsername(
      Provider<CurrentUser> self,
      ChangeUserName.Factory changeUserNameFactory,
      Realm realm,
      Provider<ReviewDb> db) {
    this.self = self;
    this.changeUserNameFactory = changeUserNameFactory;
    this.realm = realm;
    this.db = db;
  }

  @Override
  public String apply(AccountResource rsrc, Input input)
      throws AuthException, MethodNotAllowedException, UnprocessableEntityException,
          ResourceConflictException, OrmException, IOException {
    if (self.get() != rsrc.getUser() && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("not allowed to set username");
    }

    if (!realm.allowsEdit(AccountFieldName.USER_NAME)) {
      throw new MethodNotAllowedException("realm does not allow editing username");
    }

    if (input == null) {
      input = new Input();
    }

    try {
      changeUserNameFactory.create(db.get(), rsrc.getUser(), input.username).call();
    } catch (IllegalStateException e) {
      if (ChangeUserName.USERNAME_CANNOT_BE_CHANGED.equals(e.getMessage())) {
        throw new MethodNotAllowedException(e.getMessage());
      }
      throw e;
    } catch (InvalidUserNameException e) {
      throw new UnprocessableEntityException("invalid username");
    } catch (NameAlreadyUsedException e) {
      throw new ResourceConflictException("username already used");
    }

    return input.username;
  }
}
