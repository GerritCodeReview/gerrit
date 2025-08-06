// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.config;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.GlobalLabelResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class GlobalLabelsCollection
    implements ChildCollection<ConfigResource, GlobalLabelResource> {
  private final Provider<ListGlobalLabels> list;
  private final DynamicMap<RestView<GlobalLabelResource>> views;

  @Inject
  GlobalLabelsCollection(
      Provider<ListGlobalLabels> list, DynamicMap<RestView<GlobalLabelResource>> views) {
    this.list = list;
    this.views = views;
  }

  @Override
  public RestView<ConfigResource> list() {
    return list.get();
  }

  @Override
  public GlobalLabelResource parse(ConfigResource parent, IdString id)
      throws ResourceNotFoundException {
    // We don't support "GET /config/server/labels/<label-name>" as we have no need for this REST
    // endpoint. Return "404 Not Found" if anyone tries to call this non-existing REST endpoint.
    throw new ResourceNotFoundException();
  }

  @Override
  public DynamicMap<RestView<GlobalLabelResource>> views() {
    return views;
  }
}
