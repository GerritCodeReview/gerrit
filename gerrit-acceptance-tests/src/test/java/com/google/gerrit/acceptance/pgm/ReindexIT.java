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
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

@NoHttpd
public class ReindexIT extends StandaloneSiteTest {
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
    setUpChange();

    // Hackily rename the latest version's index directory to the previous version. This probably
    // breaks some things, but it should not break upgrading (which doesn't require a schema), and
    // it should still support searching by the fields we care about in our sanity checks below.
    Schema<ChangeData> prevSchema = ChangeSchemaDefinitions.INSTANCE.getPrevious();
    assertThat(prevSchema).isNotNull();
    assertThat(prevSchema.getFields()).containsKey(ChangeField.ID.getName());
    assertThat(prevSchema.getFields()).containsKey(ChangeField.EXACT_TOPIC.getName());
    int prevVersion = prevSchema.getVersion();
    Path prevDir = sitePaths.index_dir.resolve(String.format("changes_%04d", prevVersion));

    Schema<ChangeData> currSchema = ChangeSchemaDefinitions.INSTANCE.getLatest();
    assertThat(currSchema).isNotNull();
    assertThat(currSchema.getFields()).containsKey(ChangeField.ID.getName());
    assertThat(prevSchema.getFields()).containsKey(ChangeField.EXACT_TOPIC.getName());
    int currVersion = currSchema.getVersion();
    Path currDir = sitePaths.index_dir.resolve(String.format("changes_%04d", currVersion));

    Files.move(currDir, prevDir);

    String changes = ChangeSchemaDefinitions.NAME;
    GerritIndexStatus status = new GerritIndexStatus(sitePaths);
    status.setReady(changes, currVersion, false);
    status.setReady(changes, prevVersion, true);
    status.save();
    assertReady(prevVersion);

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
          .containsExactly(UpgradeAttempt.create(changes, prevVersion, currVersion));
      assertThat(u.getSucceededAttempts())
          .containsExactly(UpgradeAttempt.create(changes, prevVersion, currVersion));
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
    Map<Integer, Boolean> expected = new LinkedHashMap<>();
    ChangeSchemaDefinitions.INSTANCE.getSchemas().keySet().forEach(v -> expected.put(v, false));
    expected.put(expectedReady, true);

    GerritIndexStatus status = new GerritIndexStatus(sitePaths);
    assertThat(
            ChangeSchemaDefinitions.INSTANCE
                .getSchemas()
                .keySet()
                .stream()
                .collect(
                    toImmutableMap(v -> v, v -> status.getReady(ChangeSchemaDefinitions.NAME, v))))
        .named("ready state for index versions")
        .containsExactlyEntriesIn(expected);
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
    private final CountDownLatch started;
    private final CountDownLatch finished;

    private final List<UpgradeAttempt> startedAttempts;
    private final List<UpgradeAttempt> succeededAttempts;
    private final List<UpgradeAttempt> failedAttempts;

    UpgradeController(int numExpected) {
      this.numExpected = numExpected;
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
      checkState(
          finished.getCount() > 0, "already started %s upgrades, can't start %s", numExpected, a);
      out.add(a);
      finished.countDown();
    }

    void runUpgrades() throws Exception {
      // Wait with a timeout. Startup should happen quickly, but bugs preventing upgrading from
      // starting might not be that uncommon, so we don't want to have to wait forever to discover
      // them.
      // TODO(dborowitz): Tune after testing for flakiness.
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
