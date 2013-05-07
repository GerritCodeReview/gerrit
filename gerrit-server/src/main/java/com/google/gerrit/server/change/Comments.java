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
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

class Comments implements ChildCollection<RevisionResource, CommentResource> {
  private final DynamicMap<RestView<CommentResource>> views;
  private final Provider<ListComments> list;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  Comments(DynamicMap<RestView<CommentResource>> views,
      Provider<ListComments> list,
      Provider<ReviewDb> dbProvider) {
    this.views = views;
    this.list = list;
    this.dbProvider = dbProvider;
  }

  @Override
  public DynamicMap<RestView<CommentResource>> views() {
    return views;
  }

  @Override
  public RestView<RevisionResource> list() {
    return list.get();
  }

  @Override
  public CommentResource parse(RevisionResource rev, IdString id)
      throws ResourceNotFoundException, OrmException {
    rev.checkPublished();
    String uuid = id.get();
    for (PatchLineComment c : dbProvider.get().patchComments()
        .publishedByPatchSet(rev.getPatchSet().getId())) {
      if (uuid.equals(c.getKey().get())) {
        return new CommentResource(rev, c);
      }
    }
    throw new ResourceNotFoundException(id);
  }
}
