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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class Comments implements ChildCollection<RevisionResource, CommentResource> {
  private final DynamicMap<RestView<CommentResource>> views;
  private final ListRevisionComments list;
  private final Provider<ReviewDb> dbProvider;
  private final CommentsUtil commentsUtil;

  @Inject
  Comments(
      DynamicMap<RestView<CommentResource>> views,
      ListRevisionComments list,
      Provider<ReviewDb> dbProvider,
      CommentsUtil commentsUtil) {
    this.views = views;
    this.list = list;
    this.dbProvider = dbProvider;
    this.commentsUtil = commentsUtil;
  }

  @Override
  public DynamicMap<RestView<CommentResource>> views() {
    return views;
  }

  @Override
  public ListRevisionComments list() {
    return list;
  }

  @Override
  public CommentResource parse(RevisionResource rev, IdString id)
      throws ResourceNotFoundException, OrmException {
    String uuid = id.get();
    ChangeNotes notes = rev.getNotes();

    for (Comment c :
        commentsUtil.publishedByPatchSet(dbProvider.get(), notes, rev.getPatchSet().getId())) {
      if (uuid.equals(c.key.uuid)) {
        return new CommentResource(rev, c);
      }
    }
    throw new ResourceNotFoundException(id);
  }
}
