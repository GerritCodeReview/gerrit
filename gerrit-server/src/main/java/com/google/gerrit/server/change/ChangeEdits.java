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
import com.google.gerrit.extensions.restapi.RestView;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
class ChangeEdits implements
    ChildCollection<ChangeResource, ChangeEditResource> {
  private final DynamicMap<RestView<ChangeEditResource>> views;
  private final ListChangeEdits.Factory listFactory;

  @Inject
  ChangeEdits(DynamicMap<RestView<ChangeEditResource>> views,
      ListChangeEdits.Factory listFactory) {
    this.views = views;
    this.listFactory = listFactory;
  }

  @Override
  public DynamicMap<RestView<ChangeEditResource>> views() {
    return views;
  }

  @Override
  public RestView<ChangeResource> list() {
    return listFactory.create();
  }

  @Override
  public ChangeEditResource parse(ChangeResource change, IdString id) {
    throw new IllegalStateException("not yet implemented");
  }
}
