// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.update;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.TestActionRefUpdateContext.openTestRefUpdateContext;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.PendingIndexUpdate;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests for the pending-index lifecycle in {@link BatchUpdate} and {@link BatchUpdates}. */
public class BatchUpdateIndexIntentTest {
  @Rule
  public InMemoryTestEnvironment testEnvironment =
      new InMemoryTestEnvironment(
          () -> {
            Config cfg = new Config();
            cfg.setString("index", null, "type", "fake");
            cfg.setBoolean("index", null, "staleChangeRecovery", true);
            return cfg;
          });

  @Inject private BatchUpdate.Factory batchUpdateFactory;
  @Inject private ChangeInserter.Factory changeInserterFactory;
  @Inject private GitRepositoryManager repoManager;
  @Inject private Provider<CurrentUser> user;
  @Inject private Sequences sequences;
  @Inject private SitePaths sitePaths;
  @Inject private PendingIndexUpdate pendingIndexUpdate;

  private Project.NameKey project;
  private TestRepository<Repository> repo;
  private RefUpdateContext testRefUpdateContext;

  @Before
  public void setUp() throws Exception {
    project = Project.nameKey("test");
    repo = new TestRepository<>(repoManager.createRepository(project));
    testRefUpdateContext = openTestRefUpdateContext();
  }

  @After
  public void tearDown() {
    testRefUpdateContext.close();
  }

  @Test
  public void pendingIndexIntentFilePresentDuringUpdate() throws Exception {
    Change.Id id = createChange();
    AtomicBoolean intentFound = new AtomicBoolean(false);

    BatchUpdateListener listener =
        new BatchUpdateListener() {
          @Override
          public void afterUpdateRefs() throws Exception {
            intentFound.set(hasPendingIntentFile(id));
          }
        };

    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      bu.addOp(id, addMessageOp("Pending intent test"));
      bu.execute(listener);
    }

    assertThat(intentFound.get()).isTrue();
  }

  @Test
  public void pendingIndexIntentFilesRemovedAfterSuccessfulUpdate() throws Exception {
    Change.Id id = createChange();

    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      bu.addOp(id, addMessageOp("Cleanup test"));
      bu.execute();
    }

    assertThat(hasPendingIntentFile(id)).isFalse();
  }

  private boolean hasPendingIntentFile(Change.Id id) throws IOException {
    Path intentDir = sitePaths.data_dir.resolve("pending-index");
    String expectedFilename = pendingIndexUpdate.filename(project, id);
    try (var stream = Files.walk(intentDir)) {
      return stream
          .filter(Files::isRegularFile)
          .anyMatch(p -> p.getFileName().toString().equals(expectedFilename));
    }
  }

  private Change.Id createChange() throws Exception {
    Change.Id id = Change.id(sequences.nextChangeId());
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      bu.insertChange(
          changeInserterFactory.create(
              id, repo.commit().message("Change").insertChangeId().create(), "refs/heads/master"));
      bu.execute();
    }
    return id;
  }

  private static BatchUpdateOp addMessageOp(String message) {
    return new BatchUpdateOp() {
      @Override
      public boolean updateChange(ChangeContext ctx) {
        ctx.getUpdate(ctx.getChange().currentPatchSetId()).setChangeMessage(message);
        return true;
      }
    };
  }
}
