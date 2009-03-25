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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.data.AccountInfoCacheFactory;
import com.google.gerrit.client.data.ChangeDetail;
import com.google.gerrit.client.data.PatchSetDetail;
import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;

public class ChangeDetailServiceImpl extends BaseServiceImplementation
    implements ChangeDetailService {
  public void changeDetail(final Change.Id id,
      final AsyncCallback<ChangeDetail> callback) {
    run(callback, new Action<ChangeDetail>() {
      public ChangeDetail run(final ReviewDb db) throws OrmException, Failure {
        final Account.Id me = Common.getAccountId();
        final Change change = db.changes().get(id);
        if (change == null) {
          throw new Failure(new NoSuchEntityException());
        }
        final PatchSet patch = db.patchSets().get(change.currentPatchSetId());
        final ProjectCache.Entry projEnt =
            Common.getProjectCache().get(change.getDest().getParentKey());
        if (patch == null || projEnt == null) {
          throw new Failure(new NoSuchEntityException());
        }
        final Project proj = projEnt.getProject();
        assertCanRead(change);

        final boolean anon;
        boolean canAbandon = false;
        if (me == null) {
          // Safe assumption, this wouldn't be allowed if it wasn't.
          //
          anon = true;
        } else {
          // Ask if the anonymous user can read this project; even if
          // we can that doesn't mean the anonymous user could.
          //
          anon = canRead(null, change.getDest().getParentKey());

          // The change owner, current patchset uploader, Gerrit administrator,
          // and project administrator can mark the change as abandoned.
          //
          canAbandon = change.getStatus().isOpen();
          canAbandon &=
              me.equals(change.getOwner())
                  || me.equals(patch.getUploader())
                  || Common.getGroupCache().isAdministrator(me)
                  || Common.getGroupCache().isInGroup(me,
                      proj.getOwnerGroupId());
        }
        final ChangeDetail d = new ChangeDetail();

        d.load(db, new AccountInfoCacheFactory(db), change, anon, canAbandon,
            ChangeListServiceImpl.starredBy(db, me).contains(id));
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
        assertCanRead(db.changes().get(ps.getId().getParentKey()));

        final PatchSetDetail d = new PatchSetDetail();
        d.load(db, ps);
        return d;
      }
    });
  }

  public void patchSetPublishDetail(final PatchSet.Id id,
      final AsyncCallback<PatchSetPublishDetail> callback) {
    run(callback, new Action<PatchSetPublishDetail>() {
      public PatchSetPublishDetail run(final ReviewDb db) throws OrmException,
          Failure {
        final PatchSet ps = db.patchSets().get(id);
        final Change change = db.changes().get(ps.getId().getParentKey());
        if (ps == null || change == null) {
          throw new Failure(new NoSuchEntityException());
        }
        assertCanRead(change);

        final PatchSetPublishDetail d = new PatchSetPublishDetail();
        d.load(db, change, id);
        return d;
      }
    });
  }
}
