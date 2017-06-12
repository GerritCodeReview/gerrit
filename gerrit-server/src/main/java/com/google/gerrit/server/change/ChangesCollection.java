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
import com.google.gerrit.extensions.restapi.AcceptsPost;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeFinder;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.change.QueryChanges;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class ChangesCollection
    implements RestCollection<TopLevelResource, ChangeResource>, AcceptsPost<TopLevelResource> {
  private final Provider<ReviewDb> db;
  private final Provider<CurrentUser> user;
  private final Provider<QueryChanges> queryFactory;
  private final DynamicMap<RestView<ChangeResource>> views;
  private final ChangeFinder changeFinder;
  private final CreateChange createChange;
  private final ChangeResource.Factory changeResourceFactory;

  @Inject
  ChangesCollection(
      Provider<ReviewDb> db,
      Provider<CurrentUser> user,
      Provider<QueryChanges> queryFactory,
      DynamicMap<RestView<ChangeResource>> views,
      ChangeFinder changeFinder,
      CreateChange createChange,
      ChangeResource.Factory changeResourceFactory) {
    this.db = db;
    this.user = user;
    this.queryFactory = queryFactory;
    this.views = views;
    this.changeFinder = changeFinder;
    this.createChange = createChange;
    this.changeResourceFactory = changeResourceFactory;
  }

  @Override
  public QueryChanges list() {
    return queryFactory.get();
  }

  @Override
  public DynamicMap<RestView<ChangeResource>> views() {
    return views;
  }

  @Override
  public ChangeResource parse(TopLevelResource root, IdString id)
      throws ResourceNotFoundException, OrmException {
    List<ChangeControl> ctls = changeFinder.find(id.encoded(), user.get());
    if (ctls.isEmpty()) {
      throw new ResourceNotFoundException(id);
    } else if (ctls.size() != 1) {
      throw new ResourceNotFoundException("Multiple changes found for " + id);
    }

    ChangeControl ctl = ctls.get(0);
    if (!ctl.isVisible(db.get())) {
      throw new ResourceNotFoundException(id);
    }
    return changeResourceFactory.create(ctl);
  }

  public ChangeResource parse(Change.Id id) throws ResourceNotFoundException, OrmException {
    List<ChangeControl> ctls = changeFinder.find(id, user.get());
    if (ctls.isEmpty()) {
      throw new ResourceNotFoundException(toIdString(id));
    } else if (ctls.size() != 1) {
      throw new ResourceNotFoundException("Multiple changes found for " + id);
    }

    ChangeControl ctl = ctls.get(0);
    if (!ctl.isVisible(db.get())) {
      throw new ResourceNotFoundException(toIdString(id));
    }
    return changeResourceFactory.create(ctl);
  }

  private static IdString toIdString(Change.Id id) {
    return IdString.fromDecoded(id.toString());
  }

  public ChangeResource parse(ChangeControl control) {
    return changeResourceFactory.create(control);
  }

  @SuppressWarnings("unchecked")
  @Override
  public CreateChange post(TopLevelResource parent) throws RestApiException {
    return createChange;
  }
}
