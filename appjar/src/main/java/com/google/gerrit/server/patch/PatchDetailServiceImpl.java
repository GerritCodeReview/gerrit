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

package com.google.gerrit.server.patch;

import com.google.gerrit.client.data.SideBySidePatchDetail;
import com.google.gerrit.client.data.UnifiedPatchDetail;
import com.google.gerrit.client.patches.PatchDetailService;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.git.RepositoryCache;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritServer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;

import java.util.Collections;

public class PatchDetailServiceImpl extends BaseServiceImplementation implements
    PatchDetailService {
  private final GerritServer server;

  public PatchDetailServiceImpl(final GerritServer gs) {
    server = gs;
  }

  public void sideBySidePatchDetail(final Patch.Key key,
      final AsyncCallback<SideBySidePatchDetail> callback) {
    final RepositoryCache rc = server.getRepositoryCache();
    if (rc == null) {
      callback.onFailure(new Exception("No Repository Cache configured"));
      return;
    }
    run(callback, new SideBySidePatchDetailAction(rc, key));
  }

  public void unifiedPatchDetail(final Patch.Key key,
      final AsyncCallback<UnifiedPatchDetail> callback) {
    run(callback, new UnifiedPatchDetailAction(key));
  }

  public void saveDraft(final PatchLineComment comment,
      final AsyncCallback<PatchLineComment> callback) {
    run(callback, new Action<PatchLineComment>() {
      public PatchLineComment run(ReviewDb db) throws OrmException, Failure {
        if (comment.getStatus() != PatchLineComment.Status.DRAFT) {
          throw new Failure(new IllegalStateException("Comment published"));
        }

        final Patch patch = db.patches().get(comment.getKey().getParentKey());
        final Change change;
        if (patch == null) {
          throw new Failure(new NoSuchEntityException());
        }
        change = db.changes().get(patch.getKey().getParentKey().getParentKey());
        assertCanRead(change);

        final Account.Id me = Common.getAccountId();
        if (comment.getKey().get() == null) {
          final PatchLineComment nc =
              new PatchLineComment(new PatchLineComment.Key(patch.getKey(),
                  ChangeUtil.messageUUID(db)), comment.getLine(), me);
          nc.setSide(comment.getSide());
          nc.setMessage(comment.getMessage());
          db.patchComments().insert(Collections.singleton(nc));
          return nc;

        } else {
          if (!me.equals(comment.getAuthor())) {
            throw new Failure(new NoSuchEntityException());
          }
          comment.updated();
          db.patchComments().update(Collections.singleton(comment));
          return comment;
        }
      }
    });
  }

  public void deleteDraft(final PatchLineComment.Key commentKey,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(ReviewDb db) throws OrmException, Failure {
        final PatchLineComment comment = db.patchComments().get(commentKey);
        if (comment == null) {
          throw new Failure(new NoSuchEntityException());
        }
        if (!Common.getAccountId().equals(comment.getAuthor())) {
          throw new Failure(new NoSuchEntityException());
        }
        if (comment.getStatus() != PatchLineComment.Status.DRAFT) {
          throw new Failure(new IllegalStateException("Comment published"));
        }
        db.patchComments().delete(Collections.singleton(comment));
        return VoidResult.INSTANCE;
      }
    });
  }
}
