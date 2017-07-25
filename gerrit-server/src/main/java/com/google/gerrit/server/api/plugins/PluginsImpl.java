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

import com.google.gerrit.extensions.api.plugins.Plugins;
import com.google.gerrit.extensions.common.PluginInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.plugins.ListPlugins;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.SortedMap;

@Singleton
public class PluginsImpl implements Plugins {
  private final Provider<ListPlugins> listProvider;

  @Inject
  PluginsImpl(Provider<ListPlugins> listProvider) {
    this.listProvider = listProvider;
  }

  @Override
  public ListRequest list() {
    return new ListRequest() {
      @Override
      public SortedMap<String, PluginInfo> getAsMap() throws RestApiException {
        ListPlugins list = listProvider.get();
        list.setAll(this.getAll());
        return list.apply();
      }
    };
  }
}
