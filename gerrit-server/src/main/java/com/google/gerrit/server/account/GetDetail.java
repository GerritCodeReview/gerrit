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

import com.google.common.base.Throwables;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountDirectory.DirectoryException;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.EnumSet;

@Singleton
public class GetDetail implements RestReadView<AccountResource> {

  private final InternalAccountDirectory directory;

  @Inject
  public GetDetail(InternalAccountDirectory directory) {
    this.directory = directory;
  }

  @Override
  public AccountDetailInfo apply(AccountResource rsrc) throws OrmException {
    Account a = rsrc.getUser().getAccount();
    AccountDetailInfo info = new AccountDetailInfo(a.getId().get());
    info.registeredOn = a.getRegisteredOn();
    try {
      directory.fillAccountInfo(Collections.singleton(info), EnumSet.allOf(FillOptions.class));
    } catch (DirectoryException e) {
      Throwables.throwIfInstanceOf(e.getCause(), OrmException.class);
      throw new OrmException(e);
    }
    return info;
  }

  public static class AccountDetailInfo extends AccountInfo {
    public Timestamp registeredOn;

    public AccountDetailInfo(Integer id) {
      super(id);
    }
  }
}
