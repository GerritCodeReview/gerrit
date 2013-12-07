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

package com.google.gerrit.httpd.rpc.patch;

import com.google.gerrit.common.data.PatchDetailService;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.httpd.rpc.changedetail.ChangeDetailFactory;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.patch.PatchScriptFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.VoidResult;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;

class PatchDetailServiceImpl extends BaseServiceImplementation implements
    PatchDetailService {
  private final PatchScriptFactory.Factory patchScriptFactoryFactory;
  private final SaveDraft.Factory saveDraftFactory;
  private final ChangeControl.Factory changeControlFactory;

  @Inject
  PatchDetailServiceImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final PatchScriptFactory.Factory patchScriptFactoryFactory,
      final SaveDraft.Factory saveDraftFactory,
      final ChangeDetailFactory.Factory changeDetailFactory,
      final ChangeControl.Factory changeControlFactory) {
    super(schema, currentUser);

    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
    this.saveDraftFactory = saveDraftFactory;
    this.changeControlFactory = changeControlFactory;
  }

  public void patchScript(final Patch.Key patchKey, final PatchSet.Id psa,
      final PatchSet.Id psb, final AccountDiffPreference dp,
      final AsyncCallback<PatchScript> callback) {
    if (psb == null) {
      callback.onFailure(new NoSuchEntityException());
      return;
    }

    new Handler<PatchScript>() {
      @Override
      public PatchScript call() throws Exception {
        Change.Id changeId = patchKey.getParentKey().getParentKey();
        ChangeControl control = changeControlFactory.validateFor(changeId);
        return patchScriptFactoryFactory.create(
            control, patchKey.getFileName(), psa, psb, dp).call();
      }
    }.to(callback);
  }

  public void saveDraft(final PatchLineComment comment,
      final AsyncCallback<PatchLineComment> callback) {
    saveDraftFactory.create(comment).to(callback);
  }

  public void deleteDraft(final PatchLineComment.Key commentKey,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(ReviewDb db) throws OrmException, Failure {
        Change.Id id = commentKey.getParentKey().getParentKey().getParentKey();
        db.changes().beginTransaction(id);
        try {
          final PatchLineComment comment = db.patchComments().get(commentKey);
          if (comment == null) {
            throw new Failure(new NoSuchEntityException());
          }
          if (!getAccountId().equals(comment.getAuthor())) {
            throw new Failure(new NoSuchEntityException());
          }
          if (comment.getStatus() != PatchLineComment.Status.DRAFT) {
            throw new Failure(new IllegalStateException("Comment published"));
          }
          db.patchComments().delete(Collections.singleton(comment));
          db.commit();
          return VoidResult.INSTANCE;
        } finally {
          db.rollback();
        }
      }
    });
  }
}
