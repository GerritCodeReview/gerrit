// Copyright (C) 2021 The Android Open Source Project
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
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_EXTERNAL;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GERRIT;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GPGKEY;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_MAILTO;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_UUID;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.account.externalids.DuplicateExternalIdKeyException;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNotes;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.After;
import org.junit.Test;

@NoHttpd
public class ChangeExternalIdCaseSensitivityIT extends StandaloneSiteTest {

  private static final boolean CASE_SENSITIVE = false;
  private static final boolean CASE_INSENSITIVE = true;

  private ServerContext ctx;
  private ExternalIdNotes extIdNotes;
  private ExternalIdFactory extIdFactory;
  private MetaDataUpdate md;
  private FileBasedConfig config;

  @After
  public void cleanup() throws Exception {
    if (ctx != null) {
      ctx.close();
    }
  }

  @Test
  public void externalIdNoteNameIsMigratedToCaseInsensitive() throws Exception {
    prepareExternalIdNotes(CASE_SENSITIVE);

    ctx.close();
    runChangeExternalIdCaseSensitivity();
    ctx = startServer();
    extIdNotes = getExternalIdNotes(ctx);

    assertExternalIdNotes(CASE_INSENSITIVE);
  }

  @Test
  public void externalIdNoteNameIsMigratedToCaseSensitive() throws Exception {
    prepareExternalIdNotes(CASE_INSENSITIVE);

    ctx.close();
    runChangeExternalIdCaseSensitivity();
    ctx = startServer();
    extIdNotes = getExternalIdNotes(ctx);

    assertExternalIdNotes(CASE_SENSITIVE);
  }

  @Test
  public void migrationFailsWithDuplicates() throws Exception {
    prepareExternalIdNotes(CASE_SENSITIVE);
    extIdNotes.insert(extIdFactory.create(SCHEME_USERNAME, "JohnDoe", Account.id(1)));
    extIdNotes.commit(md);

    assertThat(extIdNotes.get(ExternalId.Key.parse("username:johndoe", false)).isPresent())
        .isTrue();
    assertThat(extIdNotes.get(ExternalId.Key.parse("username:JohnDoe", false)).isPresent())
        .isTrue();

    ctx.close();
    assertThrows(DuplicateExternalIdKeyException.class, () -> runChangeExternalIdCaseSensitivity());
    ctx = startServer();
    extIdNotes = getExternalIdNotes(ctx);

    assertExternalIdNotes(CASE_SENSITIVE);
    assertThat(extIdNotes.get(ExternalId.Key.parse("username:johndoe", false)).isPresent())
        .isTrue();
    assertThat(extIdNotes.get(ExternalId.Key.parse("username:JohnDoe", false)).isPresent())
        .isTrue();
  }

  @Test
  public void userNameCaseInsensitiveOptionIsSwitched() throws Exception {
    configureUserNameCaseInsensitive(CASE_SENSITIVE);
    assertThat(config.getBoolean("auth", "userNameCaseInsensitive", false)).isFalse();
    runChangeExternalIdCaseSensitivity();
    config.load();
    assertThat(config.getBoolean("auth", "userNameCaseInsensitive", false)).isTrue();
    runChangeExternalIdCaseSensitivity();
    config.load();
    assertThat(config.getBoolean("auth", "userNameCaseInsensitive", false)).isFalse();
  }

  @Test
  public void dryrunDoesNotPersistChanges() throws Exception {
    prepareExternalIdNotes(CASE_SENSITIVE);
    ctx.close();
    runGerrit("ChangeExternalIdCaseSensitivity", "-d", sitePaths.site_path.toString(), "--dryrun");

    config.load();
    assertThat(config.getBoolean("auth", "userNameCaseInsensitive", false)).isFalse();

    ctx = startServer();
    assertExternalIdNotes(CASE_SENSITIVE);
  }

  private void prepareExternalIdNotes(boolean userNameCaseInsensitive) throws Exception {
    configureUserNameCaseInsensitive(userNameCaseInsensitive);
    initSite();
    ctx = startServer();
    extIdFactory = ctx.getInjector().getInstance(ExternalIdFactory.class);
    Project.NameKey allUsers = ctx.getInjector().getInstance(AllUsersName.class);
    extIdNotes = getExternalIdNotes(ctx, allUsers);
    md = getMetaDataUpdate(ctx, allUsers);

    extIdNotes.insert(extIdFactory.create(SCHEME_USERNAME, "johndoe", Account.id(0)));
    extIdNotes.insert(extIdFactory.create(SCHEME_GERRIT, "johndoe", Account.id(0)));

    extIdNotes.insert(extIdFactory.create(SCHEME_USERNAME, "JaneDoe", Account.id(1)));
    extIdNotes.insert(extIdFactory.create(SCHEME_GERRIT, "JaneDoe", Account.id(1)));

    extIdNotes.insert(extIdFactory.create(SCHEME_MAILTO, "Jane@Doe.com", Account.id(1)));
    extIdNotes.insert(extIdFactory.create(SCHEME_UUID, "Abc123", Account.id(1)));
    extIdNotes.insert(extIdFactory.create(SCHEME_GPGKEY, "Abc123", Account.id(1)));
    extIdNotes.insert(extIdFactory.create(SCHEME_EXTERNAL, "saml/JaneDoe", Account.id(1)));
    extIdNotes.commit(md);

    assertExternalIdNotes(userNameCaseInsensitive);
  }

  private void assertExternalIdNotes(boolean userNameCaseInsensitive) throws Exception {
    assertThat(
            extIdNotes
                .get(ExternalId.Key.parse("username:johndoe", userNameCaseInsensitive))
                .isPresent())
        .isTrue();
    assertThat(
            extIdNotes
                .get(ExternalId.Key.parse("username:JaneDoe", !userNameCaseInsensitive))
                .isPresent())
        .isFalse();
    assertThat(
            extIdNotes
                .get(ExternalId.Key.parse("username:JaneDoe", userNameCaseInsensitive))
                .isPresent())
        .isTrue();

    assertThat(
            extIdNotes
                .get(ExternalId.Key.parse("gerrit:johndoe", userNameCaseInsensitive))
                .isPresent())
        .isTrue();
    assertThat(
            extIdNotes
                .get(ExternalId.Key.parse("gerrit:JaneDoe", !userNameCaseInsensitive))
                .isPresent())
        .isFalse();
    assertThat(
            extIdNotes
                .get(ExternalId.Key.parse("gerrit:JaneDoe", userNameCaseInsensitive))
                .isPresent())
        .isTrue();

    assertThat(extIdNotes.get(ExternalId.Key.parse("mailto:Jane@Doe.com", false)).isPresent())
        .isTrue();
    assertThat(extIdNotes.get(ExternalId.Key.parse("uuid:Abc123", false)).isPresent()).isTrue();
    assertThat(extIdNotes.get(ExternalId.Key.parse("gpgkey:Abc123", false)).isPresent()).isTrue();
    assertThat(extIdNotes.get(ExternalId.Key.parse("external:saml/JaneDoe", false)).isPresent())
        .isTrue();
  }

  private void configureUserNameCaseInsensitive(boolean userNameCaseInsensitive)
      throws IOException, ConfigInvalidException {
    config = new FileBasedConfig(baseConfig, sitePaths.gerrit_config.toFile(), FS.DETECTED);
    config.load();
    config.setBoolean("auth", null, "userNameCaseInsensitive", userNameCaseInsensitive);
    config.save();
    if (userNameCaseInsensitive) {
      assertThat(config.getBoolean("auth", "userNameCaseInsensitive", false)).isTrue();
    } else {
      assertThat(config.getBoolean("auth", "userNameCaseInsensitive", false)).isFalse();
    }
  }

  private void initSite() throws Exception {
    runGerrit("init", "-d", sitePaths.site_path.toString(), "--show-stack-trace");
  }

  private void runChangeExternalIdCaseSensitivity() throws Exception {
    runGerrit("ChangeExternalIdCaseSensitivity", "-d", sitePaths.site_path.toString(), "--batch");
  }

  private static ExternalIdNotes getExternalIdNotes(ServerContext ctx) throws Exception {
    return getExternalIdNotes(ctx, ctx.getInjector().getInstance(AllUsersName.class));
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
