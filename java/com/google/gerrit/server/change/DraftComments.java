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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class DraftComments implements ChildCollection<RevisionResource, DraftCommentResource> {
  private final DynamicMap<RestView<DraftCommentResource>> views;
  private final Provider<CurrentUser> user;
  private final ListRevisionDrafts list;
  private final Provider<ReviewDb> dbProvider;
  private final CommentsUtil commentsUtil;

  @Inject
  DraftComments(
      DynamicMap<RestView<DraftCommentResource>> views,
      Provider<CurrentUser> user,
      ListRevisionDrafts list,
      Provider<ReviewDb> dbProvider,
      CommentsUtil commentsUtil) {
    this.views = views;
    this.user = user;
    this.list = list;
    this.dbProvider = dbProvider;
    this.commentsUtil = commentsUtil;
  }

  @Override
  public DynamicMap<RestView<DraftCommentResource>> views() {
    return views;
  }

  @Override
  public ListRevisionDrafts list() throws AuthException {
    checkIdentifiedUser();
    return list;
  }

  @Override
  public DraftCommentResource parse(RevisionResource rev, IdString id)
      throws ResourceNotFoundException, OrmException, AuthException {
    checkIdentifiedUser();
    String uuid = id.get();
    for (Comment c :
        commentsUtil.draftByPatchSetAuthor(
            dbProvider.get(), rev.getPatchSet().getId(), rev.getAccountId(), rev.getNotes())) {
      if (uuid.equals(c.key.uuid)) {
        return new DraftCommentResource(rev, c);
      }
    }
    throw new ResourceNotFoundException(id);
  }

  private void checkIdentifiedUser() throws AuthException {
    if (!(user.get().isIdentifiedUser())) {
      throw new AuthException("drafts only available to authenticated users");
    }
  }
}
