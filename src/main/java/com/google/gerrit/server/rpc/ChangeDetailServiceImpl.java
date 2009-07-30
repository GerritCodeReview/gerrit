// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.rpc;

import com.google.gerrit.client.changes.ChangeDetailService;
import com.google.gerrit.client.changes.PatchSetPublishDetail;
import com.google.gerrit.client.data.ChangeDetail;
import com.google.gerrit.client.data.PatchSetDetail;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.server.BaseServiceImplementation;
import com.google.gerrit.server.changes.PatchSetPublishDetailFactory;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;

class ChangeDetailServiceImpl extends BaseServiceImplementation implements
    ChangeDetailService {

  private final PatchSetInfoFactory infoFactory;

  @Inject
  ChangeDetailServiceImpl(final SchemaFactory<ReviewDb> sf,
      PatchSetInfoFactory infoFactory) {
    super(sf);
    this.infoFactory = infoFactory;
  }

  public void changeDetail(final Change.Id id,
      final AsyncCallback<ChangeDetail> callback) {
    run(callback, new ChangeDetailFactory(id));
  }

  public void patchSetDetail(final PatchSet.Id id,
      final AsyncCallback<PatchSetDetail> callback) {
    run(callback, new Action<PatchSetDetail>() {
      public PatchSetDetail run(final ReviewDb db) throws OrmException, Failure {
        final PatchSet ps = db.patchSets().get(id);
        if (ps == null) {
          throw new Failure(new NoSuchEntityException());
        }
        assertCanRead(db.changes().get(ps.getId().getParentKey()));

        final PatchSetDetail d = new PatchSetDetail();
        d.load(db, ps);
        return d;
      }
    });
  }

  public void patchSetPublishDetail(final PatchSet.Id id,
      final AsyncCallback<PatchSetPublishDetail> callback) {
    run(callback, new PatchSetPublishDetailFactory(id, infoFactory));
  }
}
