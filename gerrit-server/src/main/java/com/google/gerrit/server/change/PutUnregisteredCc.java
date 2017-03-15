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

package com.google.gerrit.server.change;

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.common.AddressInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class PutUnregisteredCc implements RestModifyView<ChangeResource, AddressInfo> {

  private final AccountByEmailCache accountByEmailCache;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final Provider<ReviewDb> db;
  private final Provider<AnonymousUser> anonymousProvider;
  private final ProjectCache projectCache;

  @Inject
  PutUnregisteredCc(
      AccountByEmailCache accountByEmailCache,
      BatchUpdate.Factory batchUpdateFactory,
      Provider<ReviewDb> db,
      Provider<AnonymousUser> anonymousProvider,
      ProjectCache projectCache) {
    this.accountByEmailCache = accountByEmailCache;
    this.batchUpdateFactory = batchUpdateFactory;
    this.db = db;
    this.anonymousProvider = anonymousProvider;
    this.projectCache = projectCache;
  }

  @Override
  public Response<Void> apply(ChangeResource rsrc, AddressInfo in)
      throws RestApiException, UpdateException, OrmException, IOException {
    ProjectConfig cfg = projectCache.checkedGet(rsrc.getProject()).getConfig();
    if (!cfg.getEnableUnregisteredCcs()) {
      throw new BadRequestException("adding unregistered CCs not allowed");
    }
    if (!rsrc.getControl().forUser(anonymousProvider.get()).isVisible(db.get())) {
      throw new BadRequestException("change is not publicly visible");
    }
    if (Strings.isNullOrEmpty(in.name) || Strings.isNullOrEmpty(in.email)) {
      throw new BadRequestException("name and email are required");
    }
    if (!Address.isValidAddress(in.email)) {
      throw new BadRequestException("email invalid");
    }
    if (accountByEmailCache.get(in.email).size() > 0) {
      throw new BadRequestException("can't add existing account");
    }

    try (BatchUpdate bu =
        batchUpdateFactory.create(
            db.get(),
            rsrc.getChange().getProject(),
            rsrc.getControl().getUser(),
            TimeUtil.nowTs())) {
      bu.addOp(
          rsrc.getChange().getId(),
          new BatchUpdateOp() {
            @Override
            public boolean updateChange(ChangeContext ctx) throws OrmException {
              Address adr = new Address(in.name, in.email);
              PatchSet.Id psId = ctx.getChange().currentPatchSetId();

              String msg = "Added CC";
              ChangeMessage cm =
                  new ChangeMessage(
                      new ChangeMessage.Key(rsrc.getChange().getId(), ChangeUtil.messageUuid()),
                      ctx.getAccountId(),
                      ctx.getWhen(),
                      psId);
              cm.setMessage(msg);

              ctx.getUpdate(psId).setChangeMessage(msg);
              ctx.getUpdate(psId).putUnregisteredCc(adr);
              return true;
            }
          });
      bu.execute();
      return Response.none();
    }
  }
}
