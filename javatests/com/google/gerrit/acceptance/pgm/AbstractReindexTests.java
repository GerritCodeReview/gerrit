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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.extensions.client.ListGroupsOption.MEMBERS;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.acceptance.pgm.IndexUpgradeController.UpgradeAttempt;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.index.GerritIndexStatus;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Injector;
import com.google.inject.Provider;
import java.nio.file.Files;
import java.util.Set;
import java.util.function.Consumer;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

@NoHttpd
public abstract class AbstractReindexTests extends StandaloneSiteTest {
  /** @param injector injector */
  public abstract void configureIndex(Injector injector) throws Exception;

  private static final String CHANGES = ChangeSchemaDefinitions.NAME;

  private Project.NameKey project;
  private String changeId;

  @Test
  public void reindexFromScratch() throws Exception {
    setUpChange();

    MoreFiles.deleteRecursively(sitePaths.index_dir, RecursiveDeleteOption.ALLOW_INSECURE);
    Files.createDirectory(sitePaths.index_dir);
    assertServerStartupFails();

    runGerrit("reindex", "-d", sitePaths.site_path.toString(), "--show-stack-trace");
    assertReady(ChangeSchemaDefinitions.INSTANCE.getLatest().getVersion());

    try (ServerContext ctx = startServer()) {
      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
      // Query change index
      assertThat(gApi.changes().query("message:Test").get().stream().map(c -> c.changeId))
          .containsExactly(changeId);
      // Query account index
      assertThat(gApi.accounts().query("admin").get().stream().map(a -> a._accountId))
          .containsExactly(adminId.get());
      // Query group index
      assertThat(
              gApi.groups().query("Group").withOption(MEMBERS).get().stream()
                  .flatMap(g -> g.members.stream())
                  .map(a -> a._accountId))
          .containsExactly(adminId.get());
      // Query project index
      assertThat(gApi.projects().query(project.get()).get().stream().map(p -> p.name))
          .containsExactly(project.get());
    }
  }

  @Test
  public void offlineReindexForChangesIsNotPossibleInSlaveMode() throws Exception {
    enableSlaveMode();

    int exitCode =
        runGerritAndReturnExitCode(
            "reindex",
            "--index",
            "changes",
            "-d",
            sitePaths.site_path.toString(),
            "--show-stack-trace");

    assertWithMessage("Slave hosts shouldn't allow to offline reindex changes")
        .that(exitCode)
        .isGreaterThan(0);
  }

  @Test
  public void offlineReindexForAccountsIsNotPossibleInSlaveMode() throws Exception {
    enableSlaveMode();

    int exitCode =
        runGerritAndReturnExitCode(
            "reindex",
            "--index",
            "accounts",
            "-d",
            sitePaths.site_path.toString(),
            "--show-stack-trace");

    assertWithMessage("Slave hosts shouldn't allow to offline reindex accounts")
        .that(exitCode)
        .isGreaterThan(0);
  }

  @Test
  public void offlineReindexForProjectsIsNotPossibleInSlaveMode() throws Exception {
    enableSlaveMode();

    int exitCode =
        runGerritAndReturnExitCode(
            "reindex",
            "--index",
            "projects",
            "-d",
            sitePaths.site_path.toString(),
            "--show-stack-trace");

    assertWithMessage("Slave hosts shouldn't allow to offline reindex projects")
        .that(exitCode)
        .isGreaterThan(0);
  }

  @Test
  public void offlineReindexForGroupsIsPossibleInSlaveMode() throws Exception {
    enableSlaveMode();

    int exitCode =
        runGerritAndReturnExitCode(
            "reindex",
            "--index",
            "groups",
            "-d",
            sitePaths.site_path.toString(),
            "--show-stack-trace");

    assertWithMessage("Slave hosts should allow to offline reindex groups")
        .that(exitCode)
        .isEqualTo(0);
  }

  @Test
  public void offlineReindexForAllAvailableIndicesIsPossibleInSlaveMode() throws Exception {
    enableSlaveMode();

    int exitCode =
        runGerritAndReturnExitCode(
            "reindex", "-d", sitePaths.site_path.toString(), "--show-stack-trace");

    assertWithMessage("Slave hosts should allow to perform a general offline reindex")
        .that(exitCode)
        .isEqualTo(0);
  }

  @Test
  public void onlineUpgradeChanges() throws Exception {
    int prevVersion = ChangeSchemaDefinitions.INSTANCE.getPrevious().getVersion();
    int currVersion = ChangeSchemaDefinitions.INSTANCE.getLatest().getVersion();

    // Before storing any changes, switch back to the previous version.
    GerritIndexStatus status = new GerritIndexStatus(sitePaths);
    status.setReady(CHANGES, currVersion, false);
    status.setReady(CHANGES, prevVersion, true);
    status.save();
    assertReady(prevVersion);

    setOnlineUpgradeConfig(false);
    setUpChange();
    setOnlineUpgradeConfig(true);

    IndexUpgradeController u = new IndexUpgradeController(1);
    try (ServerContext ctx = startServer(u.module())) {
      assertSearchVersion(ctx, prevVersion);
      assertWriteVersions(ctx, prevVersion, currVersion);

      // Updating and searching old schema version works.
      Provider<InternalChangeQuery> queryProvider =
          ctx.getInjector().getProvider(InternalChangeQuery.class);
      assertThat(queryProvider.get().byKey(Change.key(changeId))).hasSize(1);
      assertThat(queryProvider.get().byTopicOpen("topic1")).isEmpty();

      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
      gApi.changes().id(changeId).topic("topic1");
      assertThat(queryProvider.get().byTopicOpen("topic1")).hasSize(1);

      u.runUpgrades();
      assertThat(u.getStartedAttempts())
          .containsExactly(UpgradeAttempt.create(CHANGES, prevVersion, currVersion));
      assertThat(u.getSucceededAttempts())
          .containsExactly(UpgradeAttempt.create(CHANGES, prevVersion, currVersion));
      assertThat(u.getFailedAttempts()).isEmpty();

      assertReady(currVersion);
      assertSearchVersion(ctx, currVersion);
      assertWriteVersions(ctx, currVersion);

      // Updating and searching new schema version works.
      assertThat(queryProvider.get().byTopicOpen("topic1")).hasSize(1);
      assertThat(queryProvider.get().byTopicOpen("topic2")).isEmpty();
      gApi.changes().id(changeId).topic("topic2");
      assertThat(queryProvider.get().byTopicOpen("topic1")).isEmpty();
      assertThat(queryProvider.get().byTopicOpen("topic2")).hasSize(1);
    }
  }

  private void setUpChange() throws Exception {
    project = Project.nameKey("reindex-project-test");
    try (ServerContext ctx = startServer()) {
      configureIndex(ctx.getInjector());
      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
      gApi.projects().create(project.get());

      ChangeInput in = new ChangeInput(project.get(), "master", "Test change");
      in.newBranch = true;
      changeId = gApi.changes().create(in).info().changeId;
    }
  }

  private void setOnlineUpgradeConfig(boolean enable) throws Exception {
    updateConfig(cfg -> cfg.setBoolean("index", null, "onlineUpgrade", enable));
  }

  private void enableSlaveMode() throws Exception {
    updateConfig(config -> config.setBoolean("container", null, "slave", true));
  }

  private void updateConfig(Consumer<Config> configConsumer) throws Exception {
    FileBasedConfig cfg = new FileBasedConfig(sitePaths.gerrit_config.toFile(), FS.detect());
    cfg.load();
    configConsumer.accept(cfg);
    cfg.save();
  }

  private static int runGerritAndReturnExitCode(String... args) throws Exception {
    return GerritLauncher.mainImpl(args);
  }

  private void assertSearchVersion(ServerContext ctx, int expected) {
    assertThat(
            ctx.getInjector()
                .getInstance(ChangeIndexCollection.class)
                .getSearchIndex()
                .getSchema()
                .getVersion())
        .named("search version")
        .isEqualTo(expected);
  }

  private void assertWriteVersions(ServerContext ctx, Integer... expected) {
    assertThat(
            ctx.getInjector().getInstance(ChangeIndexCollection.class).getWriteIndexes().stream()
                .map(i -> i.getSchema().getVersion()))
        .named("write versions")
        .containsExactlyElementsIn(ImmutableSet.copyOf(expected));
  }

  private void assertReady(int expectedReady) throws Exception {
    Set<Integer> allVersions = ChangeSchemaDefinitions.INSTANCE.getSchemas().keySet();
    GerritIndexStatus status = new GerritIndexStatus(sitePaths);
    assertThat(
            allVersions.stream().collect(toImmutableMap(v -> v, v -> status.getReady(CHANGES, v))))
        .named("ready state for index versions")
        .isEqualTo(allVersions.stream().collect(toImmutableMap(v -> v, v -> v == expectedReady)));
  }
}
