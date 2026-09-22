// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.index.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.entities.Change.Status.ABANDONED;
import static com.google.gerrit.entities.Change.Status.NEW;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ChangeIndexedCounter;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.index.RefState;
import com.google.gerrit.index.SiteIndexer.Result;
import com.google.gerrit.server.index.change.AllChangesIndexer;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.IndexedChangeQuery;
import com.google.gerrit.server.index.change.StalenessChecker;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class LuceneChangeIndexerIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setBoolean("index", null, "autoReindexIfStale", false);
    cfg.setString("index", null, "type", "lucene");
    return cfg;
  }

  @Inject private ExtensionRegistry extensionRegistry;

  @Inject private Collection<IndexDefinition<?, ?, ?>> indexDefs;
  @Inject private IndexConfig indexConfig;
  private AllChangesIndexer allChangesIndexer;
  private ChangeIndex index;

  @Before
  public void setup() {
    IndexDefinition<?, ?, ?> changeIndex =
        indexDefs.stream().filter(i -> i.getName().equals("changes")).findFirst().get();
    allChangesIndexer = (AllChangesIndexer) changeIndex.getSiteIndexer();
    index = (ChangeIndex) changeIndex.getIndexCollection().getWriteIndexes().iterator().next();
  }

  @Test
  @UseClockStep
  public void reindexIfStaleRemovesOldOpenDocument() throws Exception {
    ChangeData openChange = createChange().getChange();
    gApi.changes().id(openChange.getId().get()).abandon();

    assertReindexRemovesDuplicate(openChange, ABANDONED);
  }

  @Test
  @UseClockStep
  public void reindexIfStaleRemovesOldClosedDocument() throws Exception {
    PushOneCommit.Result change = createChange();
    gApi.changes().id(change.getChangeId()).abandon();
    ChangeData abandonedChange = change.getChange();
    gApi.changes().id(change.getChangeId()).restore();

    assertReindexRemovesDuplicate(abandonedChange, NEW);
  }

  private void assertReindexRemovesDuplicate(ChangeData staleChange, Change.Status currentStatus)
      throws Exception {
    // calling `insert` on the stale change will leave a copy in the other sub-index.
    index.insert(staleChange);
    assertThat(indexedStatuses(staleChange.getId())).containsExactly(NEW, ABANDONED);

    assertThat(indexer.reindexIfStale(project, staleChange.getId())).isTrue();
    assertThat(indexedStatuses(staleChange.getId())).containsExactly(currentStatus);
    assertThat(indexer.reindexIfStale(project, staleChange.getId())).isFalse();
  }

  private List<Change.Status> indexedStatuses(Change.Id id) throws Exception {
    return index
        .getSource(
            index.keyPredicate(id),
            IndexedChangeQuery.createOptions(indexConfig, 0, 2, StalenessChecker.FIELDS))
        .read()
        .toList()
        .stream()
        .map(cd -> cd.change().getStatus())
        .collect(toList());
  }

  @Test
  @GerritConfig(name = "index.reuseExistingDocuments", value = "false")
  public void testReindexWithoutReuse() throws Exception {
    ChangeIndexedCounter changeIndexedCounter = new ChangeIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedCounter)) {
      createChange();
      assertThat(changeIndexedCounter.getTotalCount()).isEqualTo(1);
      changeIndexedCounter.clear();
      reindexChanges();
      assertThat(changeIndexedCounter.getTotalCount()).isEqualTo(1);

      createIndexWithMissingChangeAndReindex(changeIndexedCounter);
      assertThat(changeIndexedCounter.getTotalCount()).isEqualTo(2);

      createIndexWithStaleChangeAndReindex(changeIndexedCounter);
      assertThat(changeIndexedCounter.getTotalCount()).isEqualTo(3);
    }
  }

  @Test
  @GerritConfig(name = "index.reuseExistingDocuments", value = "true")
  public void testReindexWithReuse() throws Exception {
    ChangeIndexedCounter changeIndexedCounter = new ChangeIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedCounter)) {
      createChange();
      assertThat(changeIndexedCounter.getTotalCount()).isEqualTo(1);
      changeIndexedCounter.clear();
      reindexChanges();
      assertThat(changeIndexedCounter.getTotalCount()).isEqualTo(0);

      createIndexWithMissingChangeAndReindex(changeIndexedCounter);
      assertThat(changeIndexedCounter.getTotalCount()).isEqualTo(1);

      createIndexWithStaleChangeAndReindex(changeIndexedCounter);
      assertThat(changeIndexedCounter.getTotalCount()).isEqualTo(1);

      createIndexWithOneChangeMissingInNoteDb(changeIndexedCounter);
      assertThat(changeIndexedCounter.getTotalCount()).isEqualTo(1);
      assertThat(changeIndexedCounter.getTotalDeletions()).isEqualTo(1);
    }
  }

  @Test
  public void deleteAllForProjectDeletesFromIndex() throws Exception {
    createChange();
    createChange();
    createChange();

    List<ChangeInfo> result = gApi.changes().query("project:" + project.get()).get();
    assertThat(result).hasSize(3);

    index.deleteAllForProject(project);

    result = gApi.changes().query("project:" + project.get()).get();
    assertThat(result).isEmpty();
  }

  private void createIndexWithMissingChangeAndReindex(ChangeIndexedCounter changeIndexedCounter)
      throws Exception {
    PushOneCommit.Result res = createChange();
    index.delete(res.getChange().getId());
    changeIndexedCounter.clear();
    reindexChanges();
  }

  private void createIndexWithStaleChangeAndReindex(ChangeIndexedCounter changeIndexedCounter)
      throws Exception {
    PushOneCommit.Result res = createChange();
    ChangeData wrongChangeData = res.getChange();
    ListMultimap<NameKey, RefState> refStates =
        LinkedListMultimap.create(wrongChangeData.getRefStates());
    refStates.replaceValues(
        project,
        Set.of(
            RefState.create(
                "refs/changes/abcd",
                ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))));
    wrongChangeData.setRefStates(ImmutableSetMultimap.copyOf(refStates));
    index.replace(wrongChangeData);
    changeIndexedCounter.clear();
    reindexChanges();
  }

  private void createIndexWithOneChangeMissingInNoteDb(ChangeIndexedCounter changeIndexedCounter)
      throws Exception {
    PushOneCommit.Result res = createChange();
    try (Repository repo = repoManager.openRepository(project);
        TestRepository<Repository> testRepo = new TestRepository<>(repo)) {
      testRepo.delete(RefNames.changeMetaRef(res.getChange().getId()));
    }
    changeIndexedCounter.clear();
    reindexChanges();
  }

  private void reindexChanges() throws Exception {
    Result res = allChangesIndexer.indexAll(index);
    assertThat(res.success()).isTrue();
  }
}
