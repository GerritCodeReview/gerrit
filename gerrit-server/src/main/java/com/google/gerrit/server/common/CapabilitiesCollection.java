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

package com.google.gerrit.server.common;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class CapabilitiesCollection implements
    RestCollection<TopLevelResource, ProjectResource>,
    AcceptsCreate<TopLevelResource> {
  private final DynamicMap<RestView<ProjectResource>> views;
  private final Provider<ListCapabilities> list;

  @Inject
  CapabilitiesCollection(DynamicMap<RestView<ProjectResource>> views,
      Provider<ListCapabilities> list) {
    this.views = views;
    this.list = list;
  }

  @Override
  public RestView<TopLevelResource> list() {
    return list.get();
  }

  @Override
  public <I> RestModifyView<TopLevelResource, I> create(
      TopLevelResource parent, IdString id) throws RestApiException {
    return null;
  }

  @Override
  public ProjectResource parse(TopLevelResource parent, IdString id)
      throws ResourceNotFoundException, Exception {
    return null;
  }

  @Override
  public DynamicMap<RestView<ProjectResource>> views() {
    return views;
  }
}
