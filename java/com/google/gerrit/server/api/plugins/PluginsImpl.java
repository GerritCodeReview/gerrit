// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.api.plugins;

import com.google.gerrit.extensions.api.plugins.InstallPluginInput;
import com.google.gerrit.extensions.api.plugins.PluginApi;
import com.google.gerrit.extensions.api.plugins.Plugins;
import com.google.gerrit.extensions.common.PluginInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.plugins.InstallPlugin;
import com.google.gerrit.server.plugins.ListPlugins;
import com.google.gerrit.server.plugins.PluginsCollection;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.SortedMap;

@Singleton
public class PluginsImpl implements Plugins {
  private final PluginsCollection plugins;
  private final Provider<ListPlugins> listProvider;
  private final Provider<InstallPlugin> installProvider;
  private final PluginApiImpl.Factory pluginApi;

  @Inject
  PluginsImpl(
      PluginsCollection plugins,
      Provider<ListPlugins> listProvider,
      Provider<InstallPlugin> installProvider,
      PluginApiImpl.Factory pluginApi) {
    this.plugins = plugins;
    this.listProvider = listProvider;
    this.installProvider = installProvider;
    this.pluginApi = pluginApi;
  }

  @Override
  public PluginApi name(String name) throws RestApiException {
    return pluginApi.create(plugins.parse(name));
  }

  @Override
  public ListRequest list() {
    return new ListRequest() {
      @Override
      public SortedMap<String, PluginInfo> getAsMap() throws RestApiException {
        return listProvider.get().request(this).apply(TopLevelResource.INSTANCE);
      }
    };
  }

  @Override
  @Deprecated
  public PluginApi install(
      String name, com.google.gerrit.extensions.common.InstallPluginInput input)
      throws RestApiException {
    return install(name, convertInput(input));
  }

  @SuppressWarnings("deprecation")
  private InstallPluginInput convertInput(
      com.google.gerrit.extensions.common.InstallPluginInput input) {
    InstallPluginInput result = new InstallPluginInput();
    result.url = input.url;
    result.raw = input.raw;
    return result;
  }

  @Override
  public PluginApi install(String name, InstallPluginInput input) throws RestApiException {
    try {
      Response<PluginInfo> created =
          installProvider.get().setName(name).apply(TopLevelResource.INSTANCE, input);
      return pluginApi.create(plugins.parse(created.value().id));
    } catch (IOException e) {
      throw new RestApiException("could not install plugin", e);
    }
  }
}
