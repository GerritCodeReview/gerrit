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
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class DeleteUnregisteredCc implements RestModifyView<ChangeResource, AddressInfo> {

  private final BatchUpdate.Factory batchUpdateFactory;
  private final Provider<ReviewDb> db;

  @Inject
  DeleteUnregisteredCc(BatchUpdate.Factory batchUpdateFactory, Provider<ReviewDb> db) {
    this.batchUpdateFactory = batchUpdateFactory;
    this.db = db;
  }

  @Override
  public Response<Void> apply(ChangeResource rsrc, AddressInfo in)
      throws RestApiException, UpdateException, OrmException {
    // Don't check if unregistered CCs are enabled for this project as it should still be possible
    // to remove unregistered CCs even if the feature was disabled since they were added.
    if (Strings.isNullOrEmpty(in.name) || Strings.isNullOrEmpty(in.email)) {
      throw new BadRequestException("name and email are required");
    }
    Address adr = new Address(in.name, in.email);
    if (!rsrc.getNotes().getUnregisteredCcs().contains(adr)) {
      throw new BadRequestException("did not match any unregistered CC");
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
              PatchSet.Id psId = ctx.getChange().currentPatchSetId();
              String msg = "Removed CC " + adr;
              ChangeMessage cm =
                  new ChangeMessage(
                      new ChangeMessage.Key(rsrc.getChange().getId(), ChangeUtil.messageUuid()),
                      ctx.getAccountId(),
                      ctx.getWhen(),
                      psId);
              cm.setMessage(msg);

              ctx.getUpdate(psId).setChangeMessage(msg);
              ctx.getUpdate(psId).removeUnregisteredCc(adr);
              return true;
            }
          });
      bu.execute();
      return Response.none();
    }
  }
}
