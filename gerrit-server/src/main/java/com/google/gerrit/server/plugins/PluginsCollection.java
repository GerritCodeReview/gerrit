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

package com.google.gerrit.server.plugins;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class PluginsCollection implements
    RestCollection<TopLevelResource, PluginResource>,
    AcceptsCreate<TopLevelResource> {

  private final DynamicMap<RestView<PluginResource>> views;
  private final PluginLoader loader;
  private final Provider<ListPlugins> list;

  @Inject
  PluginsCollection(DynamicMap<RestView<PluginResource>> views,
      PluginLoader loader, Provider<ListPlugins> list) {
    this.views = views;
    this.loader = loader;
    this.list = list;
  }

  @Override
  public RestView<TopLevelResource> list() throws ResourceNotFoundException {
    return list.get();
  }

  @Override
  public PluginResource parse(TopLevelResource parent, String id)
      throws ResourceNotFoundException, Exception {
    Plugin p = loader.get(decode(id));
    if (p == null) {
      throw new ResourceNotFoundException(id);
    }
    return new PluginResource(p);
  }

  @SuppressWarnings("unchecked")
  @Override
  public InstallPlugin create(TopLevelResource parent, String id)
      throws ResourceNotFoundException {
    return new InstallPlugin(loader, decode(id));
  }

  @Override
  public DynamicMap<RestView<PluginResource>> views() {
    return views;
  }

  private static String decode(String id) {
    try {
      return URLDecoder.decode(id, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("JVM does not support UTF-8", e);
    }
  }
}
