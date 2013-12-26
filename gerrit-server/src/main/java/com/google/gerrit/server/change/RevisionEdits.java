// Copyright (C) 2014 The Android Open Source Project
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
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class RevisionEdits implements ChildCollection<ChangeResource, RevisionEditResource> {
  private final DynamicMap<RestView<RevisionEditResource>> views;
  private final Provider<ListRevisionEdits> list;

  @Inject
  RevisionEdits(DynamicMap<RestView<RevisionEditResource>> views,
      Provider<ListRevisionEdits> list,
      Provider<ReviewDb> dbProvider) {
    this.views = views;
    this.list = list;
  }

  @Override
  public DynamicMap<RestView<RevisionEditResource>> views() {
    return views;
  }

  @Override
  public RestView<ChangeResource> list() throws ResourceNotFoundException {
    return list.get();
  }

  @Override
  public RevisionEditResource parse(ChangeResource change, IdString id)
      throws ResourceNotFoundException, OrmException {
    throw new IllegalStateException("not yet implemented");
  }
}
