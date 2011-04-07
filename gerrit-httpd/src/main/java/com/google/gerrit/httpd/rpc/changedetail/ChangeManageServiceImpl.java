// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.changedetail;

import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.ChangeManageService;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.reviewdb.ChangeLabel;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;

class ChangeManageServiceImpl extends BaseServiceImplementation implements
    ChangeManageService {
  private final SubmitAction.Factory submitAction;
  private final AbandonChange.Factory abandonChangeFactory;
  private final RestoreChange.Factory restoreChangeFactory;
  private final RevertChange.Factory revertChangeFactory;
  private final ChangeControl.Factory changeControlFactory;

  @Inject
  ChangeManageServiceImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final SubmitAction.Factory patchSetAction,
      final AbandonChange.Factory abandonChangeFactory,
      final RestoreChange.Factory restoreChangeFactory,
      final RevertChange.Factory revertChangeFactory,
      final ChangeControl.Factory changeControlFactory) {
    super(schema, currentUser);
    this.submitAction = patchSetAction;
    this.abandonChangeFactory = abandonChangeFactory;
    this.restoreChangeFactory = restoreChangeFactory;
    this.revertChangeFactory = revertChangeFactory;
    this.changeControlFactory = changeControlFactory;
  }

  public void submit(final PatchSet.Id patchSetId,
      final AsyncCallback<ChangeDetail> cb) {
    submitAction.create(patchSetId).to(cb);
  }

  public void abandonChange(final PatchSet.Id patchSetId, final String message,
      final AsyncCallback<ChangeDetail> callback) {
    abandonChangeFactory.create(patchSetId, message).to(callback);
  }

  public void revertChange(final PatchSet.Id patchSetId, final String message,
      final AsyncCallback<ChangeDetail> callback) {
    revertChangeFactory.create(patchSetId, message).to(callback);
  }

  public void restoreChange(final PatchSet.Id patchSetId, final String message,
      final AsyncCallback<ChangeDetail> callback) {
    restoreChangeFactory.create(patchSetId, message).to(callback);
  }

  public void addLabel(final ChangeLabel newChangeLabel,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(ReviewDb db) throws OrmException, Failure {
        try {
          final ChangeControl changeControl =
              changeControlFactory.controlFor(newChangeLabel.getChangeId());
          if (changeControl.canEditArbitraryLabels()) {
            // User can add labels.
            if (ChangeLabel.LabelKey.isValid(newChangeLabel.getLabel().get())) {
              // Label value is allowed.
              db.changeLabels().insert(
                  Collections.singletonList(newChangeLabel));
            }
          }
        } catch (NoSuchChangeException e) {
          throw new Failure(e);
        }
        return VoidResult.INSTANCE;
      }
    });
  }

  public void deleteLabel(final ChangeLabel changeLabel,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(ReviewDb db) throws OrmException, Failure {
        try {
          final ChangeControl changeControl =
              changeControlFactory.controlFor(changeLabel.getChangeId());
          if (changeControl.canEditArbitraryLabels()) {
            // User can delete labels.
            db.changeLabels().delete(Collections.singletonList(changeLabel));
          }
        } catch (NoSuchChangeException e) {
          throw new Failure(e);
        }
        return VoidResult.INSTANCE;
      }
    });
  }
}
