// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.List;
import java.util.Map;

@Singleton
public class ListChangeDrafts implements RestReadView<ChangeResource> {
  private final Provider<ReviewDb> db;
  private final ChangeData.Factory changeDataFactory;
  private final Provider<CommentJson> commentJson;
  private final PatchLineCommentsUtil plcUtil;

  @Inject
  ListChangeDrafts(Provider<ReviewDb> db,
      ChangeData.Factory changeDataFactory,
      Provider<CommentJson> commentJson,
      PatchLineCommentsUtil plcUtil) {
    this.db = db;
    this.changeDataFactory = changeDataFactory;
    this.commentJson = commentJson;
    this.plcUtil = plcUtil;
  }

  @Override
  public Map<String, List<CommentInfo>> apply(
      ChangeResource rsrc) throws AuthException, OrmException {
    if (!rsrc.getControl().getCurrentUser().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    IdentifiedUser user = (IdentifiedUser) rsrc.getControl().getCurrentUser();
    ChangeData cd = changeDataFactory.create(db.get(), rsrc.getControl());
    List<PatchLineComment> drafts =
        plcUtil.draftByChangeAuthor(db.get(), cd.notes(), user.getAccountId());
    return commentJson.get()
        .setFillAccounts(false)
        .setFillPatchSet(true)
        .format(drafts);
  }
}
