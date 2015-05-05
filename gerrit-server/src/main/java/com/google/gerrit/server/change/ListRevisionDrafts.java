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

import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.List;
import java.util.Map;

@Singleton
public class ListRevisionDrafts implements RestReadView<RevisionResource> {
  protected final Provider<ReviewDb> db;
  protected final Provider<CommentJson> commentJson;
  protected final PatchLineCommentsUtil plcUtil;

  @Inject
  ListRevisionDrafts(Provider<ReviewDb> db,
      Provider<CommentJson> commentJson,
      PatchLineCommentsUtil plcUtil) {
    this.db = db;
    this.commentJson = commentJson;
    this.plcUtil = plcUtil;
  }

  protected Iterable<PatchLineComment> listComments(RevisionResource rsrc)
      throws OrmException {
    return plcUtil.draftByPatchSetAuthor(db.get(), rsrc.getPatchSet().getId(),
        rsrc.getAccountId(), rsrc.getNotes());
  }

  protected boolean includeAuthorInfo() {
    return false;
  }

  @Override
  public Map<String, List<CommentInfo>> apply(RevisionResource rsrc)
      throws OrmException {
    return commentJson.get()
        .setFillAccounts(includeAuthorInfo())
        .format(listComments(rsrc));
  }

  public List<CommentInfo> getComments(RevisionResource rsrc)
      throws OrmException {
    return commentJson.get()
        .setFillAccounts(includeAuthorInfo())
        .formatAsList(listComments(rsrc));
  }
}
