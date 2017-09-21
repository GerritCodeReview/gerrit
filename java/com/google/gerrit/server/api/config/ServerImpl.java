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

import com.google.gerrit.common.Version;
import com.google.gerrit.extensions.api.config.AccessCheckInfo;
import com.google.gerrit.extensions.api.config.AccessCheckInput;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInput;
import com.google.gerrit.extensions.api.config.Server;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.CheckAccess;
import com.google.gerrit.server.config.CheckConsistency;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.GetDiffPreferences;
import com.google.gerrit.server.config.GetPreferences;
import com.google.gerrit.server.config.GetServerInfo;
import com.google.gerrit.server.config.SetDiffPreferences;
import com.google.gerrit.server.config.SetPreferences;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class ServerImpl implements Server {
  private final GetPreferences getPreferences;
  private final SetPreferences setPreferences;
  private final GetDiffPreferences getDiffPreferences;
  private final SetDiffPreferences setDiffPreferences;
  private final GetServerInfo getServerInfo;
  private final Provider<CheckConsistency> checkConsistency;
  private final Provider<CheckAccess> checkAccess;

  @Inject
  ServerImpl(
      GetPreferences getPreferences,
      SetPreferences setPreferences,
      GetDiffPreferences getDiffPreferences,
      SetDiffPreferences setDiffPreferences,
      GetServerInfo getServerInfo,
      Provider<CheckConsistency> checkConsistency,
      Provider<CheckAccess> checkAccess) {
    this.getPreferences = getPreferences;
    this.setPreferences = setPreferences;
    this.getDiffPreferences = getDiffPreferences;
    this.setDiffPreferences = setDiffPreferences;
    this.getServerInfo = getServerInfo;
    this.checkConsistency = checkConsistency;
    this.checkAccess = checkAccess;
  }

  @Override
  public String getVersion() throws RestApiException {
    return Version.getVersion();
  }

  @Override
  public ServerInfo getInfo() throws RestApiException {
    try {
      return getServerInfo.apply(new ConfigResource());
    } catch (Exception e) {
      throw asRestApiException("Cannot get server info", e);
    }
  }

  @Override
  public GeneralPreferencesInfo getDefaultPreferences() throws RestApiException {
    try {
      return getPreferences.apply(new ConfigResource());
    } catch (Exception e) {
      throw asRestApiException("Cannot get default general preferences", e);
    }
  }

  @Override
  public GeneralPreferencesInfo setDefaultPreferences(GeneralPreferencesInfo in)
      throws RestApiException {
    try {
      return setPreferences.apply(new ConfigResource(), in);
    } catch (Exception e) {
      throw asRestApiException("Cannot set default general preferences", e);
    }
  }

  @Override
  public DiffPreferencesInfo getDefaultDiffPreferences() throws RestApiException {
    try {
      return getDiffPreferences.apply(new ConfigResource());
    } catch (Exception e) {
      throw asRestApiException("Cannot get default diff preferences", e);
    }
  }

  @Override
  public DiffPreferencesInfo setDefaultDiffPreferences(DiffPreferencesInfo in)
      throws RestApiException {
    try {
      return setDiffPreferences.apply(new ConfigResource(), in);
    } catch (Exception e) {
      throw asRestApiException("Cannot set default diff preferences", e);
    }
  }

  @Override
  public ConsistencyCheckInfo checkConsistency(ConsistencyCheckInput in) throws RestApiException {
    try {
      return checkConsistency.get().apply(new ConfigResource(), in);
    } catch (Exception e) {
      throw asRestApiException("Cannot check consistency", e);
    }
  }

  @Override
  public AccessCheckInfo checkAccess(AccessCheckInput in) throws RestApiException {
    try {
      return checkAccess.get().apply(new ConfigResource(), in);
    } catch (Exception e) {
      throw asRestApiException("Cannot check access", e);
    }
  }
}
