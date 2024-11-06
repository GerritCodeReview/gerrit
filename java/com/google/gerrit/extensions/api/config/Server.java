// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.extensions.api.config;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.common.CacheInfo;
import com.google.gerrit.extensions.common.ExperimentInfo;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.TopMenu;
import java.util.List;
import java.util.Map;

public interface Server {
  /** Returns version of server. */
  String getVersion() throws RestApiException;

  ServerInfo getInfo() throws RestApiException;

  GeneralPreferencesInfo getDefaultPreferences() throws RestApiException;

  @CanIgnoreReturnValue
  GeneralPreferencesInfo setDefaultPreferences(GeneralPreferencesInfo in) throws RestApiException;

  DiffPreferencesInfo getDefaultDiffPreferences() throws RestApiException;

  @CanIgnoreReturnValue
  DiffPreferencesInfo setDefaultDiffPreferences(DiffPreferencesInfo in) throws RestApiException;

  EditPreferencesInfo getDefaultEditPreferences() throws RestApiException;

  @CanIgnoreReturnValue
  EditPreferencesInfo setDefaultEditPreferences(EditPreferencesInfo in) throws RestApiException;

  ConsistencyCheckInfo checkConsistency(ConsistencyCheckInput in) throws RestApiException;

  List<TopMenu.MenuEntry> topMenus() throws RestApiException;

  ExperimentApi experiment(String name) throws RestApiException;

  ListExperimentsRequest listExperiments() throws RestApiException;

  CachesApi caches(String name) throws RestApiException;

  Map<String, CacheInfo> listCaches() throws RestApiException;

  abstract class ListExperimentsRequest {
    private boolean enabledOnly;

    public abstract ImmutableMap<String, ExperimentInfo> get() throws RestApiException;

    public ListExperimentsRequest enabledOnly() {
      enabledOnly = true;
      return this;
    }

    public boolean getEnabledOnly() {
      return enabledOnly;
    }
  }
}
