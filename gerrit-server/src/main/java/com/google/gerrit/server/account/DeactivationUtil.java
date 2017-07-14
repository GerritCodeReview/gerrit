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

import com.google.common.base.Throwables;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountDirectory.DirectoryException;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gerrit.server.account.GetDetail.AccountDetailInfo;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import javax.naming.NamingException;
import javax.security.auth.login.LoginException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DeactivationUtil {
  private static final Logger log = LoggerFactory.getLogger(DeactivationUtil.class);
  private final SetInactiveFlag sif;
  private final SchemaFactory<ReviewDb> reviewDbProvider;
  private final IdentifiedUser.GenericFactory genericUserFactory;
  private final Realm realm;
  private final InternalAccountDirectory directory;

  @Inject
  DeactivationUtil(
      SchemaFactory<ReviewDb> schema,
      SetInactiveFlag sif,
      IdentifiedUser.GenericFactory genericUserFactory,
      Realm realm,
      InternalAccountDirectory directory) {
    this.reviewDbProvider = schema;
    this.sif = sif;
    this.genericUserFactory = genericUserFactory;
    this.realm = realm;
    this.directory = directory;
  }

  public void deactivateInactiveAccounts()
      throws OrmException, NamingException, AccountException, IOException, ConfigInvalidException,
          RestApiException, LoginException {
    ReviewDb db = reviewDbProvider.open();
    List<Account> accs = db.accounts().all().toList();
    for (Account acc : accs) {
      AccountDetailInfo info = new AccountDetailInfo(acc.getId().get());
      try {
        directory.fillAccountInfo(Collections.singleton(info), EnumSet.allOf(FillOptions.class));
      } catch (DirectoryException e) {
        log.error("Failed to query gerrit DB accounts");
        Throwables.throwIfInstanceOf(e.getCause(), OrmException.class);
        throw new OrmException(e);
      }
      if (acc.isActive() && !realm.isActive(info.username)) {
        IdentifiedUser user = genericUserFactory.create(acc.getId());
        sif.deactivate(user);
      }
    }
  }
}
