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

import com.google.gerrit.common.Version;
import com.google.gerrit.extensions.api.config.Server;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.GetDiffPreferences;
import com.google.gerrit.server.config.SetDiffPreferences;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;

import java.io.IOException;

@Singleton
public class ServerImpl implements Server {
  private final GetDiffPreferences getDiffPreferences;
  private final SetDiffPreferences setDiffPreferences;

  @Inject
  ServerImpl(GetDiffPreferences getDiffPreferences,
      SetDiffPreferences setDiffPreferences) {
    this.getDiffPreferences = getDiffPreferences;
    this.setDiffPreferences = setDiffPreferences;
  }

  @Override
  public String getVersion() throws RestApiException {
    return Version.getVersion();
  }

  @Override
  public DiffPreferencesInfo getDefaultDiffPreferences()
      throws RestApiException {
    try {
      return getDiffPreferences.apply(new ConfigResource());
    } catch (IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot get default diff preferences", e);
    }
  }

  @Override
  public DiffPreferencesInfo setDefaultDiffPreferences(DiffPreferencesInfo in)
      throws RestApiException {
    try {
      return setDiffPreferences.apply(new ConfigResource(), in);
    } catch (IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot set default diff preferences", e);
    }
  }
}
