// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class PluginCollection implements ChildCollection<ConfigResource, PluginResource> {

  private final DynamicMap<RestView<PluginResource>> views;
  private final Provider<ListTestPlugin> list;

  @Inject
  PluginCollection(DynamicMap<RestView<PluginResource>> views, Provider<ListTestPlugin> list) {
    this.list = list;
    this.views = views;
  }

  @Override
  public RestView<ConfigResource> list() throws RestApiException {
    return list.get();
  }

  @Override
  public PluginResource parse(ConfigResource parent, IdString id)
      throws ResourceNotFoundException, Exception {
    throw new ResourceNotFoundException();
  }

  @Override
  public DynamicMap<RestView<PluginResource>> views() {
    return views;
  }
}
