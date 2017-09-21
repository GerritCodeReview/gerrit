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

package com.google.gerrit.server.config;

import static com.google.gerrit.server.config.ConfigUtil.loadSection;
import static com.google.gerrit.server.config.ConfigUtil.skipField;
import static com.google.gerrit.server.config.ConfigUtil.storeSection;
import static com.google.gerrit.server.config.GetPreferences.readFromGit;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GeneralPreferencesLoader;
import com.google.gerrit.server.account.VersionedAccountPreferences;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Field;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
public class SetPreferences implements RestModifyView<ConfigResource, GeneralPreferencesInfo> {
  private static final Logger log = LoggerFactory.getLogger(SetPreferences.class);

  private final GeneralPreferencesLoader loader;
  private final GitRepositoryManager gitManager;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final AllUsersName allUsersName;
  private final AccountCache accountCache;

  @Inject
  SetPreferences(
      GeneralPreferencesLoader loader,
      GitRepositoryManager gitManager,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      AllUsersName allUsersName,
      AccountCache accountCache) {
    this.loader = loader;
    this.gitManager = gitManager;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allUsersName = allUsersName;
    this.accountCache = accountCache;
  }

  @Override
  public GeneralPreferencesInfo apply(ConfigResource rsrc, GeneralPreferencesInfo i)
      throws BadRequestException, IOException, ConfigInvalidException {
    if (!hasSetFields(i)) {
      throw new BadRequestException("unsupported option");
    }
    return writeToGit(readFromGit(gitManager, loader, allUsersName, i));
  }

  private GeneralPreferencesInfo writeToGit(GeneralPreferencesInfo i)
      throws RepositoryNotFoundException, IOException, ConfigInvalidException {
    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsersName)) {
      VersionedAccountPreferences p = VersionedAccountPreferences.forDefault();
      p.load(md);
      storeSection(
          p.getConfig(), UserConfigSections.GENERAL, null, i, GeneralPreferencesInfo.defaults());
      com.google.gerrit.server.account.SetPreferences.storeMyMenus(p, i.my);
      com.google.gerrit.server.account.SetPreferences.storeUrlAliases(p, i.urlAliases);
      p.commit(md);

      accountCache.evictAllNoReindex();

      GeneralPreferencesInfo r =
          loadSection(
              p.getConfig(),
              UserConfigSections.GENERAL,
              null,
              new GeneralPreferencesInfo(),
              GeneralPreferencesInfo.defaults(),
              null);
      return loader.loadMyMenusAndUrlAliases(r, p, null);
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
