// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.gerrit.config.ConfigUtil.skipField;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.Preferences;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Field;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
public class SetPreferences implements RestModifyView<ConfigResource, GeneralPreferencesInfo> {
  private static final Logger log = LoggerFactory.getLogger(SetPreferences.class);

  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final AllUsersName allUsersName;
  private final AccountCache accountCache;

  @Inject
  SetPreferences(
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      AllUsersName allUsersName,
      AccountCache accountCache) {
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allUsersName = allUsersName;
    this.accountCache = accountCache;
  }

  @Override
  public GeneralPreferencesInfo apply(ConfigResource rsrc, GeneralPreferencesInfo input)
      throws BadRequestException, IOException, ConfigInvalidException {
    if (!hasSetFields(input)) {
      throw new BadRequestException("unsupported option");
    }
    Preferences.validateMy(input.my);
    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsersName)) {
      GeneralPreferencesInfo updatedPrefs = Preferences.updateDefaultGeneralPreferences(md, input);
      accountCache.evictAll();
      return updatedPrefs;
    }
  }

  private static boolean hasSetFields(GeneralPreferencesInfo in) {
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
      log.warn("Unable to verify input", e);
    }
    return false;
  }
}
