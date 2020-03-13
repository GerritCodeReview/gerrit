// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.DefaultPreferencesCache;
import com.google.gerrit.server.account.PreferenceConverter;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class GetDiffPreferences implements RestReadView<ConfigResource> {

  private final DefaultPreferencesCache defaultPreferencesCache;

  @Inject
  GetDiffPreferences(DefaultPreferencesCache defaultPreferencesCache) {
    this.defaultPreferencesCache = defaultPreferencesCache;
  }

  @Override
  public Response<DiffPreferencesInfo> apply(ConfigResource configResource)
      throws BadRequestException, ResourceConflictException, IOException, ConfigInvalidException {
    return Response.ok(PreferenceConverter.diff(defaultPreferencesCache.get()));
  }
}
