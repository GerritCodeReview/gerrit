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
package com.google.gerrit.server.config;

import static com.google.gerrit.server.config.ConfigUtil.loadSection;
import static com.google.gerrit.server.config.ConfigUtil.skipField;
import static com.google.gerrit.server.config.ConfigUtil.storeSection;
import static com.google.gerrit.server.config.GetDiffPreferences.readFromGit;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
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
public class SetDiffPreferences implements RestModifyView<ConfigResource, DiffPreferencesInfo> {
  private static final Logger log = LoggerFactory.getLogger(SetDiffPreferences.class);

  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final AllUsersName allUsersName;
  private final GitRepositoryManager gitManager;

  @Inject
  SetDiffPreferences(
      GitRepositoryManager gitManager,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      AllUsersName allUsersName) {
    this.gitManager = gitManager;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allUsersName = allUsersName;
  }

  @Override
  public DiffPreferencesInfo apply(ConfigResource configResource, DiffPreferencesInfo in)
      throws BadRequestException, IOException, ConfigInvalidException {
    if (in == null) {
      throw new BadRequestException("input must be provided");
    }
    if (!hasSetFields(in)) {
      throw new BadRequestException("unsupported option");
    }
    return writeToGit(readFromGit(gitManager, allUsersName, in));
  }

  private DiffPreferencesInfo writeToGit(DiffPreferencesInfo in)
      throws RepositoryNotFoundException, IOException, ConfigInvalidException {
    DiffPreferencesInfo out = new DiffPreferencesInfo();
    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsersName)) {
      VersionedAccountPreferences prefs = VersionedAccountPreferences.forDefault();
      prefs.load(md);
      DiffPreferencesInfo defaults = DiffPreferencesInfo.defaults();
      storeSection(prefs.getConfig(), UserConfigSections.DIFF, null, in, defaults);
      prefs.commit(md);
      loadSection(
          prefs.getConfig(),
          UserConfigSections.DIFF,
          null,
          out,
          DiffPreferencesInfo.defaults(),
          null);
    }
    return out;
  }

  private static boolean hasSetFields(DiffPreferencesInfo in) {
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
