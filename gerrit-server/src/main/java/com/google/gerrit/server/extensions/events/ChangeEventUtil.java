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

package com.google.gerrit.server.extensions.events;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.IOException;

public class ChangeEventUtil {

  private final ChangeData.Factory changeDataFactory;
  private final Provider<ReviewDb> db;
  private final ChangeJson changeJson;

  @Inject
  ChangeEventUtil(ChangeJson.Factory changeJsonFactory,
      ChangeData.Factory changeDataFactory,
      Provider<ReviewDb> db) {
    this.changeDataFactory = changeDataFactory;
    this.db = db;
    this.changeJson = changeJsonFactory.create(ChangeJson.NO_OPTIONS);
  }

  public ChangeInfo changeInfo(Change change) throws OrmException {
    return changeJson.format(change);
  }

  public RevisionInfo revisionInfo(PatchSet ps)
      throws OrmException, PatchListNotAvailableException, GpgException, IOException {
    ChangeData cd = changeDataFactory.create(db.get(), ps.getId().getParentKey());
    ChangeControl ctl = cd.changeControl();
    return changeJson.toRevisionInfo(ctl, ps);
  }

  public AccountInfo accountInfo(Account a) {
    AccountInfo ai = new AccountInfo(a.getId().get());
    ai.email = a.getPreferredEmail();
    ai.name = a.getFullName();
    ai.username =  a.getUserName();
    return ai;
  }
}
