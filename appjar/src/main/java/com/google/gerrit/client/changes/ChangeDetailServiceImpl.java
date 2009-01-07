// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.data.AccountInfoCacheFactory;
import com.google.gerrit.client.data.ChangeDetail;
import com.google.gerrit.client.data.PatchSetDetail;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.client.workflow.RightRule;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;

public class ChangeDetailServiceImpl extends BaseServiceImplementation
    implements ChangeDetailService {
  public void changeDetail(final Change.Id id,
      final AsyncCallback<ChangeDetail> callback) {
    run(callback, new Action<ChangeDetail>() {
      public ChangeDetail run(final ReviewDb db) throws OrmException, Failure {
        final Change change = db.changes().get(id);
        if (change == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final RightRule rules =
            new RightRule(Common.getGerritConfig(), Common.getGroupCache(), db);
        final ChangeDetail d = new ChangeDetail();
        d.load(db, new AccountInfoCacheFactory(db), rules, change);
        return d;
      }
    });
  }

  public void patchSetDetail(final PatchSet.Id id,
      final AsyncCallback<PatchSetDetail> callback) {
    run(callback, new Action<PatchSetDetail>() {
      public PatchSetDetail run(final ReviewDb db) throws OrmException, Failure {
        final PatchSet ps = db.patchSets().get(id);
        if (ps == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final PatchSetDetail d = new PatchSetDetail();
        d.load(db, ps);
        return d;
      }
    });
  }
}
