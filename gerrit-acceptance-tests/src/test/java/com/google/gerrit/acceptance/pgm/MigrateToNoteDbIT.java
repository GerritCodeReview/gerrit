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

package com.google.gerrit.acceptance.pgm;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.GerritServer;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ConfigNotesMigration;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NoteDbChangeState.RefState;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.TempFileUtil;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

@UseLocalDisk
@NoHttpd
public class MigrateToNoteDbIT {
  @Rule
  public TestWatcher testWatcher =
      new TestWatcher() {
        @Override
        protected void starting(Description description) {
          serverDesc = GerritServer.Description.forTestMethod(description, ConfigSuite.DEFAULT);
        }
      };

  private GerritServer.Description serverDesc;

  private Path site;
  private StoredConfig gerritConfig;

  @Before
  public void setUp() throws Exception {
    site = TempFileUtil.createTempDirectory().toPath();
    GerritServer.init(serverDesc, new Config(), site);
    gerritConfig = new FileBasedConfig(new SitePaths(site).gerrit_config.toFile(), FS.detect());
  }

  @After
  public void tearDown() throws Exception {
    TempFileUtil.cleanup();
  }

  @Test
  public void rebuildEmptySiteStartingWithNoteDbDisabed() throws Exception {
    assertNotesMigrationState(NotesMigrationState.REVIEW_DB);
    migrate();
    assertNotesMigrationState(NotesMigrationState.READ_WRITE_NO_SEQUENCE);
  }

  @Test
  public void rebuildEmptySiteStartingWithNoteDbEnabled() throws Exception {
    setNotesMigrationState(NotesMigrationState.READ_WRITE_NO_SEQUENCE);
    migrate();
    assertNotesMigrationState(NotesMigrationState.READ_WRITE_NO_SEQUENCE);
  }

  @Test
  public void rebuildOneChangeTrialMode() throws Exception {
    assertNotesMigrationState(NotesMigrationState.REVIEW_DB);
    Project.NameKey project = new Project.NameKey("project");

    Account.Id accountId;
    Change.Id id;
    try (GerritServer server = startServer();
        ManualRequestContext ctx = openContext(server)) {
      accountId = ctx.getUser().getAccountId();
      GerritApi gApi = server.getTestInjector().getInstance(GerritApi.class);
      gApi.projects().create("project");

      ChangeInput in = new ChangeInput(project.get(), "master", "Test change");
      in.newBranch = true;
      id = new Change.Id(gApi.changes().create(in).info()._number);
    }

    migrate("--trial");
    assertNotesMigrationState(NotesMigrationState.READ_WRITE_NO_SEQUENCE);

    try (GerritServer server = startServer();
        ManualRequestContext ctx = openContext(server, accountId)) {
      GitRepositoryManager repoManager =
          server.getTestInjector().getInstance(GitRepositoryManager.class);
      ObjectId metaId;
      try (Repository repo = repoManager.openRepository(project)) {
        Ref ref = repo.exactRef(RefNames.changeMetaRef(id));
        assertThat(ref).isNotNull();
        metaId = ref.getObjectId();
      }

      ReviewDb db = ReviewDbUtil.unwrapDb(ctx.getReviewDbProvider().get());
      Change c = db.changes().get(id);
      assertThat(c).isNotNull();
      NoteDbChangeState state = NoteDbChangeState.parse(c);
      assertThat(state).isNotNull();
      assertThat(state.getPrimaryStorage()).isEqualTo(PrimaryStorage.REVIEW_DB);
      assertThat(state.getRefState()).hasValue(RefState.create(metaId, ImmutableMap.of()));
    }
  }

  private GerritServer startServer() throws Exception {
    return GerritServer.start(serverDesc, new Config(), site);
  }

  private ManualRequestContext openContext(GerritServer server) throws Exception {
    Injector i = server.getTestInjector();
    TestAccount a = i.getInstance(AccountCreator.class).admin();
    return openContext(server, a.getId());
  }

  private ManualRequestContext openContext(GerritServer server, Account.Id accountId)
      throws Exception {
    return server.getTestInjector().getInstance(OneOffRequestContext.class).openAs(accountId);
  }

  private void migrate(String... additionalArgs) throws Exception {
    String[] args =
        Stream.concat(
                Stream.of("migrate-to-note-db", "-d", site.toString(), "--show-stack-trace"),
                Stream.of(additionalArgs))
            .toArray(String[]::new);
    assertThat(GerritLauncher.mainImpl(args)).isEqualTo(0);
  }

  private void setNotesMigrationState(NotesMigrationState state) throws Exception {
    gerritConfig.load();
    ConfigNotesMigration.setConfigValues(gerritConfig, state.migration());
    gerritConfig.save();
  }

  private void assertNotesMigrationState(NotesMigrationState expected) throws Exception {
    gerritConfig.load();
    assertThat(NotesMigrationState.forNotesMigration(new ConfigNotesMigration(gerritConfig)))
        .hasValue(expected);
  }
}
