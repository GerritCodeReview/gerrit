// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.DeleteDraftComment.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;

class DeleteDraftComment
    implements RestModifyView<DraftCommentResource, Input> {
  static class Input {
  }

  private final Provider<ReviewDb> db;

  @Inject
  DeleteDraftComment(Provider<ReviewDb> db) {
    this.db = db;
  }

  @Override
  public Object apply(DraftCommentResource rsrc, Input input)
      throws OrmException {
    db.get().patchComments().delete(Collections.singleton(rsrc.getComment()));
    return Response.none();
  }
}
