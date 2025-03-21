// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.acceptance.pgm;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.auth.AuthTokenInfo;
import com.google.gerrit.server.account.PasswordMigrator;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNotes;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import java.util.List;
import org.junit.Test;

@NoHttpd
public class MigratePasswordsToTokensIT extends StandaloneSiteTest {

  @Test
  public void httpPasswordIsBeingMigratedToToken() throws Exception {
    initSite();

    String username = "foo";
    String httpPassword = "secret";
    int accountId;

    try (ServerContext ctx = startServer()) {
      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
      accountId = gApi.accounts().create(username).detail()._accountId;
      Project.NameKey allUsers = ctx.getInjector().getInstance(AllUsersName.class);
      ExternalIdNotes extIdNotes = getExternalIdNotes(ctx, allUsers);
      ExternalIdFactory extIdFactory = ctx.getInjector().getInstance(ExternalIdFactory.class);
      MetaDataUpdate md = getMetaDataUpdate(ctx, allUsers);
      extIdNotes.upsert(
          extIdFactory.create(
              SCHEME_USERNAME, username, Account.id(accountId), null, httpPassword));
      extIdNotes.commit(md);
    }

    runGerrit("MigratePasswordsToTokens", "-d", sitePaths.site_path.toString());

    try (ServerContext ctx = startServer()) {
      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
      List<AuthTokenInfo> actual = gApi.accounts().id(accountId).getTokens();
      assertThat(actual.size()).isEqualTo(1);
      assertThat(actual.get(0).id).isEqualTo(PasswordMigrator.DEFAULT_ID);
      assertThat(actual.get(0).token).isNull();

      Project.NameKey allUsers = ctx.getInjector().getInstance(AllUsersName.class);
      ExternalIdNotes extIdNotes = getExternalIdNotes(ctx, allUsers);
      ExternalIdKeyFactory extIdKeyFactory =
          ctx.getInjector().getInstance(ExternalIdKeyFactory.class);
      assertThat(extIdNotes.get(extIdKeyFactory.create(SCHEME_USERNAME, username)).get().password())
          .isNull();
    }
  }

  private void initSite() throws Exception {
    runGerrit("init", "-d", sitePaths.site_path.toString(), "--show-stack-trace");
  }

  private static ExternalIdNotes getExternalIdNotes(ServerContext ctx, Project.NameKey allUsers)
      throws Exception {
    GitRepositoryManager repoManager = ctx.getInjector().getInstance(GitRepositoryManager.class);
    ExternalIdNotes.FactoryNoReindex extIdNotesFactory =
        ctx.getInjector().getInstance(ExternalIdNotes.FactoryNoReindex.class);
    return extIdNotesFactory.load(repoManager.openRepository(allUsers));
  }

  private static MetaDataUpdate getMetaDataUpdate(ServerContext ctx, Project.NameKey allUsers)
      throws Exception {
    MetaDataUpdate.Server metaDataUpdateFactory =
        ctx.getInjector().getInstance(MetaDataUpdate.Server.class);
    return metaDataUpdateFactory.create(allUsers);
  }
}
