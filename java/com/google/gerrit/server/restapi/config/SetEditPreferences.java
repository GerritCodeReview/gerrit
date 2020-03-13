// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.DefaultPreferencesCache;
import com.google.gerrit.server.account.PreferenceConverter;
import com.google.gerrit.server.account.VersionedPreferences;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
public class SetEditPreferences implements RestModifyView<ConfigResource, EditPreferencesInfo> {
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;

  private final AllUsersName allUsersName;
  private final DefaultPreferencesCache defaultPreferencesCache;

  @Inject
  SetEditPreferences(
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      AllUsersName allUsersName,
      DefaultPreferencesCache defaultPreferencesCache) {
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allUsersName = allUsersName;
    this.defaultPreferencesCache = defaultPreferencesCache;
  }

  @Override
  public Response<EditPreferencesInfo> apply(
      ConfigResource configResource, EditPreferencesInfo input)
      throws BadRequestException, IOException, ConfigInvalidException {
    if (input == null) {
      throw new BadRequestException("input must be provided");
    }

    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsersName)) {
      VersionedPreferences prefs = VersionedPreferences.defaults();
      prefs.load(md);
      prefs.update(PreferenceConverter.forUpdate(input));
      prefs.commit(md);
    }
    defaultPreferencesCache.invalidate();
    return Response.ok(PreferenceConverter.edit(defaultPreferencesCache.get()));
  }
}
