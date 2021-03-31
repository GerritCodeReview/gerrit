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
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

@NoHttpd
public class ChangeExternalIdCaseSensitivityIT extends StandaloneSiteTest {
  @Test
  public void externalIdNoteNameIsMigratedToCaseInsensitive() throws Exception {
    initSite();
    try (ServerContext ctx = startServer()) {
      ExternalIdFactory extIdFactory = ctx.getInjector().getInstance(ExternalIdFactory.class);
      Project.NameKey allUsers = ctx.getInjector().getInstance(AllUsersName.class);
      ExternalIdNotes extIdNotes = getExternalIdNotes(ctx, allUsers);
      MetaDataUpdate md = getMetaDataUpdate(ctx, allUsers);

      extIdNotes.insert(extIdFactory.create(SCHEME_USERNAME, "johndoe", Account.id(0)));
      extIdNotes.insert(extIdFactory.create(SCHEME_GERRIT, "johndoe", Account.id(0)));

      extIdNotes.insert(extIdFactory.create(SCHEME_USERNAME, "JaneDoe", Account.id(1)));
      extIdNotes.insert(extIdFactory.create(SCHEME_GERRIT, "JaneDoe", Account.id(1)));

      extIdNotes.insert(extIdFactory.create(SCHEME_MAILTO, "Jane@Doe.com", Account.id(1)));
      extIdNotes.insert(extIdFactory.create(SCHEME_UUID, "Abc123", Account.id(1)));
      extIdNotes.insert(extIdFactory.create(SCHEME_GPGKEY, "Abc123", Account.id(1)));
      extIdNotes.insert(extIdFactory.create(SCHEME_EXTERNAL, "saml/JaneDoe", Account.id(1)));
      extIdNotes.commit(md);

      assertThat(extIdNotes.get(ExternalId.Key.parse("username:johndoe", true)).isPresent())
          .isTrue();
      assertThat(extIdNotes.get(ExternalId.Key.parse("username:JaneDoe", true)).isPresent())
          .isFalse();
      assertThat(extIdNotes.get(ExternalId.Key.parse("username:JaneDoe", false)).isPresent())
          .isTrue();

      assertThat(extIdNotes.get(ExternalId.Key.parse("gerrit:johndoe", true)).isPresent()).isTrue();
      assertThat(extIdNotes.get(ExternalId.Key.parse("gerrit:JaneDoe", true)).isPresent())
          .isFalse();
      assertThat(extIdNotes.get(ExternalId.Key.parse("gerrit:JaneDoe", false)).isPresent())
          .isTrue();

      assertThat(extIdNotes.get(ExternalId.Key.parse("mailto:Jane@Doe.com", false)).isPresent())
          .isTrue();
      assertThat(extIdNotes.get(ExternalId.Key.parse("uuid:Abc123", false)).isPresent()).isTrue();
      assertThat(extIdNotes.get(ExternalId.Key.parse("gpgkey:Abc123", false)).isPresent()).isTrue();
      assertThat(extIdNotes.get(ExternalId.Key.parse("external:saml/JaneDoe", false)).isPresent())
          .isTrue();
    }
    runGerrit(
        "ChangeExternalIdCaseSensitivity",
        "-d",
        sitePaths.site_path.toString(),
        "--to",
        "insensitive");
    try (ServerContext ctx = startServer()) {
      ExternalIdNotes extIdNotes = getExternalIdNotes(ctx);
      assertThat(extIdNotes.get(ExternalId.Key.parse("username:johndoe", true)).isPresent())
          .isTrue();
      assertThat(extIdNotes.get(ExternalId.Key.parse("username:JaneDoe", true)).isPresent())
          .isTrue();
      assertThat(extIdNotes.get(ExternalId.Key.parse("username:JaneDoe", false)).isPresent())
          .isFalse();

      assertThat(extIdNotes.get(ExternalId.Key.parse("gerrit:johndoe", true)).isPresent()).isTrue();
      assertThat(extIdNotes.get(ExternalId.Key.parse("gerrit:JaneDoe", true)).isPresent()).isTrue();
      assertThat(extIdNotes.get(ExternalId.Key.parse("gerrit:JaneDoe", false)).isPresent())
          .isFalse();

      assertThat(extIdNotes.get(ExternalId.Key.parse("mailto:Jane@Doe.com", false)).isPresent())
          .isTrue();
      assertThat(extIdNotes.get(ExternalId.Key.parse("uuid:Abc123", false)).isPresent()).isTrue();
      assertThat(extIdNotes.get(ExternalId.Key.parse("gpgkey:Abc123", false)).isPresent()).isTrue();
      assertThat(extIdNotes.get(ExternalId.Key.parse("external:saml/JaneDoe", false)).isPresent())
          .isTrue();
    }
  }

  @Test
  public void migrationFailsWithDuplicates() throws Exception {
    initSite();
    try (ServerContext ctx = startServer()) {
      ExternalIdFactory extIdFactory = ctx.getInjector().getInstance(ExternalIdFactory.class);
      Project.NameKey allUsers = ctx.getInjector().getInstance(AllUsersName.class);
      ExternalIdNotes extIdNotes = getExternalIdNotes(ctx, allUsers);
      MetaDataUpdate md = getMetaDataUpdate(ctx, allUsers);
      extIdNotes.insert(extIdFactory.create(SCHEME_USERNAME, "johndoe", Account.id(0)));
      extIdNotes.insert(extIdFactory.create(SCHEME_USERNAME, "JohnDoe", Account.id(1)));
      extIdNotes.commit(md);
      assertThat(extIdNotes.get(ExternalId.Key.parse("username:johndoe", false)).isPresent())
          .isTrue();
      assertThat(extIdNotes.get(ExternalId.Key.parse("username:JohnDoe", false)).isPresent())
          .isTrue();
    }
    assertThrows(
        DuplicateExternalIdKeyException.class,
        () ->
            runGerrit(
                "ChangeExternalIdCaseSensitivity",
                "-d",
                sitePaths.site_path.toString(),
                "--to",
                "insensitive"));
    try (ServerContext ctx = startServer()) {
      ExternalIdNotes extIdNotes = getExternalIdNotes(ctx);
      assertThat(extIdNotes.get(ExternalId.Key.parse("username:johndoe", false)).isPresent())
          .isTrue();
      assertThat(extIdNotes.get(ExternalId.Key.parse("username:JohnDoe", false)).isPresent())
          .isTrue();
    }
  }

  @Test
  public void userNameCaseInsensitiveOptionIsSwitched() throws Exception {
    FileBasedConfig config =
        new FileBasedConfig(baseConfig, sitePaths.gerrit_config.toFile(), FS.DETECTED);
    config.load();
    assertThat(config.getBoolean("auth", "userNameCaseInsensitive", false)).isFalse();
    runGerrit(
        "ChangeExternalIdCaseSensitivity",
        "-d",
        sitePaths.site_path.toString(),
        "--to",
        "insensitive");
    config.load();
    assertThat(config.getBoolean("auth", "userNameCaseInsensitive", false)).isTrue();
  }

  @Test
  public void failsWithInvalidConfig() throws Exception {
    FileBasedConfig config =
        new FileBasedConfig(baseConfig, sitePaths.gerrit_config.toFile(), FS.DETECTED);
    config.load();
    config.setBoolean("auth", null, "userNameCaseInsensitive", true);
    config.save();
    assertThat(config.getBoolean("auth", "userNameCaseInsensitive", false)).isTrue();
    assertThrows(
        ConfigInvalidException.class,
        () ->
            runGerrit(
                "ChangeExternalIdCaseSensitivity",
                "-d",
                sitePaths.site_path.toString(),
                "--to",
                "insensitive"));
  }

  private void initSite() throws Exception {
    runGerrit("init", "-d", sitePaths.site_path.toString(), "--show-stack-trace");
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
