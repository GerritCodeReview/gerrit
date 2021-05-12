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

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GERRIT;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

public class MigrateUsernameCaseSensitivity implements RestModifyView<ConfigResource, Input> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ReloadConfig reloadConfig;
  private final PermissionBackend permissions;
  private final ExternalIdFactory externalIdFactory;
  private final Config gerritServerConfig;
  private final SitePaths site;
  private final boolean isUserNameCaseInsensitive;
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final MetaDataUpdate.Server metaDataUpdateServerFactory;
  private final ExternalIdNotes.FactoryNoReindex externalIdNotesFactory;
  private final ExternalIds externalIds;

  @Inject
  MigrateUsernameCaseSensitivity(
      ReloadConfig reloadConfig,
      PermissionBackend permissions,
      ExternalIdFactory externalIdFactory,
      @GerritServerConfig Config gerritServerConfig,
      SitePaths site,
      AuthConfig authConfig,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      MetaDataUpdate.Server metaDataUpdateServerFactory,
      ExternalIdNotes.FactoryNoReindex externalIdNotesFactory,
      ExternalIds externalIds) {
    this.reloadConfig = reloadConfig;
    this.permissions = permissions;
    this.externalIdFactory = externalIdFactory;
    this.gerritServerConfig = gerritServerConfig;
    this.site = site;
    this.isUserNameCaseInsensitive = authConfig.isUserNameCaseInsensitive();
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.metaDataUpdateServerFactory = metaDataUpdateServerFactory;
    this.externalIdNotesFactory = externalIdNotesFactory;
    this.externalIds = externalIds;
  }

  @Override
  public Response<?> apply(ConfigResource resource, Input input)
      throws RestApiException, PermissionBackendException, IOException, IOException,
          ConfigInvalidException {
    permissions.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(repo);
      Set<ExternalId> done = new HashSet<>();
      while (!done.containsAll(externalIds.all())) {
        for (ExternalId extId : Sets.difference(externalIds.all(), done)) {
          convertExternalIdNoteIdToLowerCase(extIdNotes, extId);
          done.add(extId);
        }
      }
      try (MetaDataUpdate metaDataUpdate = metaDataUpdateServerFactory.create(allUsersName)) {
        metaDataUpdate.setMessage(
            String.format(
                "Migration to case %ssensitive usernames", isUserNameCaseInsensitive ? "" : "in"));
        extIdNotes.commit(metaDataUpdate);
      }
    }
    updateGerritConfig();
    return Response.ok();
  }

  private void convertExternalIdNoteIdToLowerCase(ExternalIdNotes extIdNotes, ExternalId extId)
      throws DuplicateKeyException, IOException {
    if (extId.isScheme(SCHEME_GERRIT) || extId.isScheme(SCHEME_USERNAME)) {
      ExternalId.Key updatedKey =
          ExternalId.Key.create(extId.key().scheme(), extId.key().id(), !isUserNameCaseInsensitive);
      if (!extId.key().sha1().getName().equals(updatedKey.sha1().getName())) {
        logger.atInfo().log("Converting note name of external ID: %s", extId.key());
        ExternalId updatedExtId =
            externalIdFactory.create(
                updatedKey, extId.accountId(), extId.email(), extId.password(), extId.blobId());
        extIdNotes.replace(extId, updatedExtId);
      }
    }
  }

  private void updateGerritConfig()
      throws IOException, ConfigInvalidException, RestApiException, PermissionBackendException {
    logger.atInfo().log("Setting auth.userNameCaseInsensitive to true in gerrit.config.");
    FileBasedConfig config =
        new FileBasedConfig(gerritServerConfig, site.gerrit_config.toFile(), FS.DETECTED);
    config.load();
    config.setBoolean("auth", null, "userNameCaseInsensitive", !isUserNameCaseInsensitive);
    config.save();
    reloadConfig.apply(null, null);
  }
}
