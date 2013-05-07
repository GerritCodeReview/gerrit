// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountPatchReview;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;

class Reviewed {
  static class Input {
  }

  static class PutReviewed implements RestModifyView<FileResource, Input> {
    private final Provider<ReviewDb> dbProvider;

    @Inject
    PutReviewed(Provider<ReviewDb> dbProvider) {
      this.dbProvider = dbProvider;
    }

    @Override
    public Object apply(FileResource resource, Input input)
        throws ResourceNotFoundException, OrmException {
      resource.getRevision().checkPublished();
      ReviewDb db = dbProvider.get();
      AccountPatchReview apr = getExisting(db, resource);
      if (apr == null) {
        db.accountPatchReviews().insert(
            Collections.singleton(new AccountPatchReview(resource.getPatchKey(),
                resource.getAccountId())));
        return Response.created("");
      } else {
        return Response.ok("");
      }
    }
  }

  static class DeleteReviewed implements RestModifyView<FileResource, Input> {
    private final Provider<ReviewDb> dbProvider;

    @Inject
    DeleteReviewed(Provider<ReviewDb> dbProvider) {
      this.dbProvider = dbProvider;
    }

    @Override
    public Object apply(FileResource resource, Input input)
        throws ResourceNotFoundException, OrmException {
      resource.getRevision().checkPublished();
      ReviewDb db = dbProvider.get();
      AccountPatchReview apr = getExisting(db, resource);
      if (apr != null) {
        db.accountPatchReviews().delete(Collections.singleton(apr));
      }
      return Response.none();
    }
  }

  private static AccountPatchReview getExisting(ReviewDb db,
      FileResource resource) throws OrmException {
    AccountPatchReview.Key key = new AccountPatchReview.Key(
        resource.getPatchKey(), resource.getAccountId());
    return db.accountPatchReviews().get(key);
  }

  private Reviewed() {
  }
}
