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

package com.google.gerrit.server.api.config;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Version;
import com.google.gerrit.extensions.api.config.CachesApi;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInput;
import com.google.gerrit.extensions.api.config.ExperimentApi;
import com.google.gerrit.extensions.api.config.Server;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.common.CacheInfo;
import com.google.gerrit.extensions.common.ExperimentInfo;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.common.ValidationOptionInfos;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.restapi.config.CachesCollection;
import com.google.gerrit.server.restapi.config.CheckConsistency;
import com.google.gerrit.server.restapi.config.ExperimentsCollection;
import com.google.gerrit.server.restapi.config.GetDiffPreferences;
import com.google.gerrit.server.restapi.config.GetEditPreferences;
import com.google.gerrit.server.restapi.config.GetPreferences;
import com.google.gerrit.server.restapi.config.GetServerInfo;
import com.google.gerrit.server.restapi.config.GetValidationOptions;
import com.google.gerrit.server.restapi.config.ListCaches;
import com.google.gerrit.server.restapi.config.ListExperiments;
import com.google.gerrit.server.restapi.config.ListTopMenus;
import com.google.gerrit.server.restapi.config.SetDiffPreferences;
import com.google.gerrit.server.restapi.config.SetEditPreferences;
import com.google.gerrit.server.restapi.config.SetPreferences;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class ServerImpl implements Server {
  private final GetPreferences getPreferences;
  private final SetPreferences setPreferences;
  private final GetDiffPreferences getDiffPreferences;
  private final SetDiffPreferences setDiffPreferences;
  private final GetEditPreferences getEditPreferences;
  private final GetValidationOptions getValidationOptions;
  private final SetEditPreferences setEditPreferences;
  private final GetServerInfo getServerInfo;
  private final Provider<CheckConsistency> checkConsistency;
  private final ListTopMenus listTopMenus;
  private final ExperimentApiImpl.Factory experimentApi;
  private final ExperimentsCollection experimentsCollection;
  private final Provider<ListExperiments> listExperimentsProvider;
  private final CachesApiImpl.Factory cachesApi;
  private final CachesCollection cachesCollection;
  private final Provider<ListCaches> listCachesProvider;

  @Inject
  ServerImpl(
      GetPreferences getPreferences,
      SetPreferences setPreferences,
      GetDiffPreferences getDiffPreferences,
      SetDiffPreferences setDiffPreferences,
      GetEditPreferences getEditPreferences,
      SetEditPreferences setEditPreferences,
      GetValidationOptions getValidationOptions,
      GetServerInfo getServerInfo,
      Provider<CheckConsistency> checkConsistency,
      ListTopMenus listTopMenus,
      ExperimentApiImpl.Factory experimentApi,
      ExperimentsCollection experimentsCollection,
      Provider<ListExperiments> listExperimentsProvider,
      CachesApiImpl.Factory cachesApi,
      CachesCollection cachesCollection,
      Provider<ListCaches> listCachesProvider) {
    this.getPreferences = getPreferences;
    this.setPreferences = setPreferences;
    this.getDiffPreferences = getDiffPreferences;
    this.setDiffPreferences = setDiffPreferences;
    this.getEditPreferences = getEditPreferences;
    this.setEditPreferences = setEditPreferences;
    this.getValidationOptions = getValidationOptions;
    this.getServerInfo = getServerInfo;
    this.checkConsistency = checkConsistency;
    this.listTopMenus = listTopMenus;
    this.experimentApi = experimentApi;
    this.experimentsCollection = experimentsCollection;
    this.listExperimentsProvider = listExperimentsProvider;
    this.cachesApi = cachesApi;
    this.cachesCollection = cachesCollection;
    this.listCachesProvider = listCachesProvider;
  }

  @Override
  public String getVersion() throws RestApiException {
    return Version.getVersion();
  }

  @Override
  public ServerInfo getInfo() throws RestApiException {
    try {
      return getServerInfo.apply(new ConfigResource()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get server info", e);
    }
  }

  @Override
  public GeneralPreferencesInfo getDefaultPreferences() throws RestApiException {
    try {
      return getPreferences.apply(new ConfigResource()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get default general preferences", e);
    }
  }

  @Override
  public GeneralPreferencesInfo setDefaultPreferences(GeneralPreferencesInfo in)
      throws RestApiException {
    try {
      return setPreferences.apply(new ConfigResource(), in).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot set default general preferences", e);
    }
  }

  @Override
  public DiffPreferencesInfo getDefaultDiffPreferences() throws RestApiException {
    try {
      return getDiffPreferences.apply(new ConfigResource()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get default diff preferences", e);
    }
  }

  @Override
  public DiffPreferencesInfo setDefaultDiffPreferences(DiffPreferencesInfo in)
      throws RestApiException {
    try {
      return setDiffPreferences.apply(new ConfigResource(), in).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot set default diff preferences", e);
    }
  }

  @Override
  public EditPreferencesInfo getDefaultEditPreferences() throws RestApiException {
    try {
      return getEditPreferences.apply(new ConfigResource()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get default edit preferences", e);
    }
  }

  @Override
  public EditPreferencesInfo setDefaultEditPreferences(EditPreferencesInfo in)
      throws RestApiException {
    try {
      return setEditPreferences.apply(new ConfigResource(), in).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot set default edit preferences", e);
    }
  }

  @Override
  public ValidationOptionInfos getValidationOptions() throws RestApiException {
    try {
      return getValidationOptions.apply(new ConfigResource()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get validation options", e);
    }
  }

  @Override
  public ConsistencyCheckInfo checkConsistency(ConsistencyCheckInput in) throws RestApiException {
    try {
      return checkConsistency.get().apply(new ConfigResource(), in).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot check consistency", e);
    }
  }

  @Override
  public List<TopMenu.MenuEntry> topMenus() throws RestApiException {
    try {
      return listTopMenus.apply(new ConfigResource()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get top menus", e);
    }
  }

  @Override
  public ExperimentApi experiment(String name) throws RestApiException {
    try {
      return experimentApi.create(
          experimentsCollection.parse(new ConfigResource(), IdString.fromDecoded(name)));
    } catch (Exception e) {
      throw asRestApiException("Cannot parse experiment", e);
    }
  }

  @Override
  public ListExperimentsRequest listExperiments() throws RestApiException {
    return new ListExperimentsRequest() {
      @Override
      public ImmutableMap<String, ExperimentInfo> get() throws RestApiException {
        return ServerImpl.this.listExperiments(this);
      }
    };
  }

  private ImmutableMap<String, ExperimentInfo> listExperiments(ListExperimentsRequest r)
      throws RestApiException {
    try {
      ListExperiments listExperiments = listExperimentsProvider.get();
      if (r.getEnabledOnly()) {
        listExperiments.setEnabledOnly(true);
      }
      return listExperiments.apply(new ConfigResource()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve experiments", e);
    }
  }

  @Override
  public CachesApi caches(String name) throws RestApiException {
    try {
      return cachesApi.create(
          cachesCollection.parse(new ConfigResource(), IdString.fromDecoded(name)));
    } catch (Exception e) {
      throw asRestApiException("Cannot parse cache", e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, CacheInfo> listCaches() throws RestApiException {
    try {
      ListCaches listCaches = listCachesProvider.get();
      return (Map<String, CacheInfo>) listCaches.apply(new ConfigResource()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve caches", e);
    }
  }
}
