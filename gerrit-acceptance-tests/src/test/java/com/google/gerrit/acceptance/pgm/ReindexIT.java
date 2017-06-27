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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.common.truth.Truth8.assertThat;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.index.GerritIndexStatus;
import com.google.gerrit.server.index.OnlineUpgradeListener;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provider;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

@NoHttpd
public class ReindexIT extends StandaloneSiteTest {
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
      assertThat(gApi.changes().query("message:Test").get().stream().map(c -> c.changeId))
          .containsExactly(changeId);
    }
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

    UpgradeController u = new UpgradeController(1);
    try (ServerContext ctx = startServer(u.module())) {
      assertSearchVersion(ctx, prevVersion);
      assertWriteVersions(ctx, prevVersion, currVersion);

      // Updating and searching old schema version works.
      Provider<InternalChangeQuery> queryProvider =
          ctx.getInjector().getProvider(InternalChangeQuery.class);
      assertThat(queryProvider.get().byKey(new Change.Key(changeId))).hasSize(1);
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
    project = new Project.NameKey("project");
    try (ServerContext ctx = startServer()) {
      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
      gApi.projects().create(project.get());

      ChangeInput in = new ChangeInput(project.get(), "master", "Test change");
      in.newBranch = true;
      changeId = gApi.changes().create(in).info().changeId;
    }
  }

  private void setOnlineUpgradeConfig(boolean enable) throws Exception {
    FileBasedConfig cfg = new FileBasedConfig(sitePaths.gerrit_config.toFile(), FS.detect());
    cfg.load();
    cfg.setBoolean("index", null, "onlineUpgrade", enable);
    cfg.save();
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
            ctx.getInjector()
                .getInstance(ChangeIndexCollection.class)
                .getWriteIndexes()
                .stream()
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

  @AutoValue
  abstract static class UpgradeAttempt {
    static UpgradeAttempt create(String name, int oldVersion, int newVersion) {
      return new AutoValue_ReindexIT_UpgradeAttempt(name, oldVersion, newVersion);
    }

    abstract String name();

    abstract int oldVersion();

    abstract int newVersion();
  }

  private static class UpgradeController implements OnlineUpgradeListener {
    private final int numExpected;
    private final CountDownLatch readyToStart;
    private final CountDownLatch started;
    private final CountDownLatch finished;

    private final List<UpgradeAttempt> startedAttempts;
    private final List<UpgradeAttempt> succeededAttempts;
    private final List<UpgradeAttempt> failedAttempts;

    UpgradeController(int numExpected) {
      this.numExpected = numExpected;
      readyToStart = new CountDownLatch(1);
      started = new CountDownLatch(numExpected);
      finished = new CountDownLatch(numExpected);
      startedAttempts = new ArrayList<>();
      succeededAttempts = new ArrayList<>();
      failedAttempts = new ArrayList<>();
    }

    Module module() {
      return new AbstractModule() {
        @Override
        public void configure() {
          DynamicSet.bind(binder(), OnlineUpgradeListener.class).toInstance(UpgradeController.this);
        }
      };
    }

    @Override
    public synchronized void onStart(String name, int oldVersion, int newVersion) {
      UpgradeAttempt a = UpgradeAttempt.create(name, oldVersion, newVersion);
      try {
        readyToStart.await();
      } catch (InterruptedException e) {
        throw new AssertionError("interrupted waiting to start " + a, e);
      }
      checkState(
          started.getCount() > 0, "already started %s upgrades, can't start %s", numExpected, a);
      startedAttempts.add(a);
      started.countDown();
    }

    @Override
    public synchronized void onSuccess(String name, int oldVersion, int newVersion) {
      finish(UpgradeAttempt.create(name, oldVersion, newVersion), succeededAttempts);
    }

    @Override
    public synchronized void onFailure(String name, int oldVersion, int newVersion) {
      finish(UpgradeAttempt.create(name, oldVersion, newVersion), failedAttempts);
    }

    private synchronized void finish(UpgradeAttempt a, List<UpgradeAttempt> out) {
      checkState(readyToStart.getCount() == 0, "shouldn't be finishing upgrade before starting");
      checkState(
          finished.getCount() > 0, "already finished %s upgrades, can't finish %s", numExpected, a);
      out.add(a);
      finished.countDown();
    }

    void runUpgrades() throws Exception {
      readyToStart.countDown();

      // Wait with a timeout. Startup should happen quickly, but bugs preventing upgrading from
      // starting might not be that uncommon, so we don't want to have to wait forever to discover
      // them.
      int timeoutSec = 60;
      if (!started.await(timeoutSec, TimeUnit.SECONDS)) {
        assert_()
            .fail(
                "%s/%s online upgrades started after %ss",
                numExpected - started.getCount(), numExpected, timeoutSec);
      }

      // Wait with no timeout. Reindexing might be slow, and given that upgrading started
      // successfully, it's unlikely there is a bug preventing it from tripping the finished latch
      // eventually, even if it takes longer than we might guess.
      finished.await();
    }

    synchronized ImmutableList<UpgradeAttempt> getStartedAttempts() {
      return ImmutableList.copyOf(startedAttempts);
    }

    synchronized ImmutableList<UpgradeAttempt> getSucceededAttempts() {
      return ImmutableList.copyOf(succeededAttempts);
    }

    synchronized ImmutableList<UpgradeAttempt> getFailedAttempts() {
      return ImmutableList.copyOf(failedAttempts);
    }
  }
}
