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
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

class Drafts implements ChildCollection<RevisionResource, DraftResource> {
  private final DynamicMap<RestView<DraftResource>> views;
  private final Provider<CurrentUser> user;
  private final Provider<ListDrafts> list;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  Drafts(DynamicMap<RestView<DraftResource>> views,
      Provider<CurrentUser> user,
      Provider<ListDrafts> list,
      Provider<ReviewDb> dbProvider) {
    this.views = views;
    this.user = user;
    this.list = list;
    this.dbProvider = dbProvider;
  }

  @Override
  public DynamicMap<RestView<DraftResource>> views() {
    return views;
  }

  @Override
  public RestView<RevisionResource> list() throws AuthException {
    checkIdentifiedUser();
    return list.get();
  }

  @Override
  public DraftResource parse(RevisionResource rev, IdString id)
      throws ResourceNotFoundException, OrmException, AuthException {
    checkIdentifiedUser();
    rev.checkPublished();
    String uuid = id.get();
    for (PatchLineComment c : dbProvider.get().patchComments()
        .draftByPatchSetAuthor(
            rev.getPatchSet().getId(),
            rev.getAccountId())) {
      if (uuid.equals(c.getKey().get())) {
        return new DraftResource(rev, c);
      }
    }
    throw new ResourceNotFoundException(id);
  }

  private void checkIdentifiedUser() throws AuthException {
    if (!(user.get() instanceof IdentifiedUser)) {
      throw new AuthException("drafts only available to authenticated users");
    }
  }
}
