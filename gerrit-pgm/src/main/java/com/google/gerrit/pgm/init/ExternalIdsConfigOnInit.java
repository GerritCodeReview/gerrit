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

package com.google.gerrit.pgm.init;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.common.collect.Multimap;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.VersionedMetaDataOnInit;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.ExternalIdsConfig;
import com.google.gerrit.server.account.ExternalIdsConfig.ExternalId;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;

import java.io.IOException;
import java.util.Collection;

public class ExternalIdsConfigOnInit extends VersionedMetaDataOnInit {
  public interface Factory {
    ExternalIdsConfigOnInit create(Account.Id accountId);
  }

  private Multimap<String, ExternalId> externalIds;

  @Inject
  public ExternalIdsConfigOnInit(
      AllUsersNameOnInitProvider allUsers,
      SitePaths site,
      InitFlags flags,
      @Assisted Account.Id accountId) {
    super(flags, site, allUsers.get(), RefNames.refsUsers(accountId));
  }

  @Override
  public ExternalIdsConfigOnInit load()
      throws IOException, ConfigInvalidException {
    super.load();
    return this;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    externalIds = ExternalIdsConfig
        .parse(readConfig(ExternalIdsConfig.EXTERNAL_IDS_CONFIG));
  }

  public ExternalIdsConfigOnInit add(Collection<ExternalId> newExternalIds) {
    checkState(externalIds != null, "external IDs not loaded yet");
    for (ExternalId externalId : newExternalIds) {
      externalIds.put(externalId.key().scheme(), externalId);
    }
    return this;
  }

  @Override
  protected boolean onSave(CommitBuilder commit)
      throws IOException, ConfigInvalidException {
    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Updated external IDs\n");
    }

    Config cfg = readConfig(ExternalIdsConfig.EXTERNAL_IDS_CONFIG);
    ExternalIdsConfig.writeToConfig(cfg, externalIds);
    saveConfig(ExternalIdsConfig.EXTERNAL_IDS_CONFIG, cfg);
    return true;
  }
}
