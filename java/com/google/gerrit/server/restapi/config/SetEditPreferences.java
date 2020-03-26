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

import static com.google.gerrit.server.config.ConfigUtil.skipField;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.StoredPreferences;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Field;
import org.eclipse.jgit.errors.ConfigInvalidException;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
public class SetEditPreferences implements RestModifyView<ConfigResource, EditPreferencesInfo> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final AllUsersName allUsersName;
  private final AccountCache accountCache;

  @Inject
  SetEditPreferences(
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      AllUsersName allUsersName,
      AccountCache accountCache) {
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allUsersName = allUsersName;
    this.accountCache = accountCache;
  }

  @Override
  public Response<EditPreferencesInfo> apply(
      ConfigResource configResource, EditPreferencesInfo input)
      throws BadRequestException, IOException, ConfigInvalidException {
    if (input == null) {
      throw new BadRequestException("input must be provided");
    }
    if (!hasSetFields(input)) {
      throw new BadRequestException("unsupported option");
    }

    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsersName)) {
      EditPreferencesInfo updatedPrefs = StoredPreferences.updateDefaultEditPreferences(md, input);
      return Response.ok(updatedPrefs);
    }
  }

  private static boolean hasSetFields(EditPreferencesInfo in) {
    try {
      for (Field field : in.getClass().getDeclaredFields()) {
        if (skipField(field)) {
          continue;
        }
        if (field.get(in) != null) {
          return true;
        }
      }
    } catch (IllegalAccessException e) {
      logger.atSevere().withCause(e).log("Unable to verify input");
    }
    return false;
  }
}
