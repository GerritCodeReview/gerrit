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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class ListChangeComments extends ListChangeDrafts {

  @Inject
  ListChangeComments(
      Provider<ReviewDb> db,
      ChangeData.Factory changeDataFactory,
      Provider<CommentJson> commentJson,
      CommentsUtil commentsUtil) {
    super(db, changeDataFactory, commentJson, commentsUtil);
  }

  @Override
  protected Iterable<Comment> listComments(ChangeResource rsrc) throws OrmException {
    ChangeData cd = changeDataFactory.create(db.get(), rsrc.getNotes());
    return commentsUtil.publishedByChange(db.get(), cd.notes());
  }

  @Override
  protected boolean includeAuthorInfo() {
    return true;
  }

  @Override
  public boolean requireAuthentication() {
    return false;
  }
}
