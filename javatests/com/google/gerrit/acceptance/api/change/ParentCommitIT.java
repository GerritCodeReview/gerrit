// Copyright (C) 2026 Monty Taylor
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.query.change.ChangePredicates;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.gerrit.server.query.change.CurrentRevisionParents;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import java.util.Set;
import org.junit.Test;

@NoHttpd
public class ParentCommitIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ChangeIndexCollection indexes;
  @Inject private Provider<ChangeQueryProcessor> changeQueryProcessorProvider;
  @Inject private Provider<InternalChangeQuery> internalChangeQueryProvider;
  @Inject private IndexConfig indexConfig;

  @Test
  @GerritConfig(name = "index.type", value = "lucene")
  public void storedParentsRequireAnExplicitFieldSelection() throws Exception {
    var change = createChange();
    try (AutoCloseable ignored = disableNoteDb()) {
      var ordinary =
          changeQueryProcessorProvider.get().query(ChangePredicates.project(project)).entities();
      assertThat(ordinary).hasSize(1);
      assertThat(ordinary.get(0).indexedCurrentRevisionParents()).isNull();

      var explicit =
          internalChangeQueryProvider
              .get()
              .setRequestedFields(ChangeField.CURRENT_REVISION_PARENTS_SPEC)
              .byProject(project);
      assertThat(explicit).hasSize(1);
      assertThat(explicit.get(0).indexedCurrentRevisionParents().commitId())
          .isEqualTo(change.getCommit().name());
    }
  }

  @Test
  public void directChildrenAreExactAndIndexOnly() throws Exception {
    var base = createChange();
    var child = createChange();
    createChange();
    requestScopeOperations.setApiUser(user.id());
    try (AutoCloseable ignored = disableNoteDb()) {
      assertThat(queryNumbers("parentcommit:" + base.getCommit().name()))
          .containsExactly(child.getChange().getId().get());
      assertThat(queryNumbers("parentcommit:" + base.getCommit().name().toUpperCase()))
          .containsExactly(child.getChange().getId().get());
      assertThat(queryNumbers("branch:other parentcommit:" + base.getCommit().name())).isEmpty();
      var stored = stored(child.getChange().getId());
      assertThat(stored.patchSetNumber()).isEqualTo(1);
      assertThat(stored.commitId()).isEqualTo(child.getCommit().name());
      assertThat(stored.parentIds()).containsExactly(base.getCommit().name());
    }
  }

  @Test
  public void supersededParentPatchSetsRemainResolvable() throws Exception {
    var base = createChange();
    var child = createChange();
    testRepo.reset(base.getCommit());
    var updatedBase = amendChange(base.getChangeId());
    assertThat(updatedBase.getCommit()).isNotEqualTo(base.getCommit());
    try (AutoCloseable ignored = disableNoteDb()) {
      assertThat(queryNumbers("status:open commit:" + base.getCommit().name()))
          .containsExactly(base.getChange().getId().get());
      assertThat(queryNumbers("parentcommit:" + base.getCommit().name()))
          .containsExactly(child.getChange().getId().get());
      assertThat(queryNumbers("parentcommit:" + updatedBase.getCommit().name())).isEmpty();
    }
  }

  @Test
  public void newPatchSetReplacesParentPostingsAndSnapshot() throws Exception {
    var oldBase = createChange();
    var child = createChange();
    testRepo.reset(oldBase.getCommit().getParent(0));
    var newBase = createChange("new base", "other.txt", "base");
    testRepo.reset(newBase.getCommit());
    var updated =
        pushFactory
            .create(admin.newIdent(), testRepo, "child", "child.txt", "new", child.getChangeId())
            .setParent(newBase.getCommit())
            .to("refs/for/master");
    updated.assertOkStatus();
    try (AutoCloseable ignored = disableNoteDb()) {
      assertThat(queryNumbers("parentcommit:" + oldBase.getCommit().name())).isEmpty();
      assertThat(queryNumbers("parentcommit:" + newBase.getCommit().name()))
          .containsExactly(child.getChange().getId().get());
      var stored = stored(child.getChange().getId());
      assertThat(stored.patchSetNumber()).isEqualTo(2);
      assertThat(stored.commitId()).isEqualTo(updated.getCommit().name());
      assertThat(stored.parentIds()).containsExactly(newBase.getCommit().name());
    }
  }

  @Test
  public void abandonAndRestoreRetainDependencyFieldsAcrossIndexPartitions() throws Exception {
    var base = createChange();
    var child = createChange();
    gApi.changes().id(child.getChangeId()).abandon();
    try (AutoCloseable ignored = disableNoteDb()) {
      assertThat(queryNumbers("parentcommit:" + base.getCommit().name()))
          .containsExactly(child.getChange().getId().get());
      assertThat(queryNumbers("status:abandoned parentcommit:" + base.getCommit().name()))
          .containsExactly(child.getChange().getId().get());
      assertThat(queryNumbers("status:open parentcommit:" + base.getCommit().name())).isEmpty();
      assertThat(queryNumbers("status:closed change:" + child.getChange().getId()))
          .containsExactly(child.getChange().getId().get());
      assertThat(stored(child.getChange().getId())).isNotNull();
    }
    gApi.changes().id(child.getChangeId()).restore();
    try (AutoCloseable ignored = disableNoteDb()) {
      assertThat(queryNumbers("parentcommit:" + base.getCommit().name()))
          .containsExactly(child.getChange().getId().get());
      assertThat(stored(child.getChange().getId())).isNotNull();
    }
  }

  @Test
  public void mergedChangesOmitDependencyFieldsWithoutRemovingClosedSearch() throws Exception {
    var child = createChange();
    String parent = child.getCommit().getParent(0).name();
    gApi.changes().id(child.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(child.getChangeId()).current().submit();
    try (AutoCloseable ignored = disableNoteDb()) {
      assertThat(queryNumbers("parentcommit:" + parent)).isEmpty();
      assertThat(queryNumbers("status:merged change:" + child.getChange().getId()))
          .containsExactly(child.getChange().getId().get());
      assertThat(stored(child.getChange().getId())).isNull();
    }
  }

  @Test
  public void workInProgressChangesRetainDependencyFields() throws Exception {
    var base = createChange();
    var child = createChange();
    gApi.changes().id(child.getChangeId()).setWorkInProgress();
    try (AutoCloseable ignored = disableNoteDb()) {
      assertThat(queryNumbers("is:wip parentcommit:" + base.getCommit().name()))
          .containsExactly(child.getChange().getId().get());
      assertThat(stored(child.getChange().getId())).isNotNull();
    }
  }

  @Test
  public void visibilityFilteringStillAppliesToParentQueries() throws Exception {
    var base = createChange();
    var child = createChange();
    gApi.changes().id(child.getChangeId()).setPrivate(true);
    requestScopeOperations.setApiUser(user.id());
    try (AutoCloseable ignored = disableNoteDb()) {
      assertThat(queryNumbers("parentcommit:" + base.getCommit().name())).isEmpty();
    }
  }

  @Test
  public void abbreviatedOrMalformedParentCommitIsRejected() throws Exception {
    assertThrows(BadRequestException.class, () -> queryNumbers("parentcommit:abcd"));
    assertThrows(BadRequestException.class, () -> queryNumbers("parentcommit:" + "x".repeat(40)));
  }

  private List<Integer> queryNumbers(String query) throws Exception {
    return gApi
        .changes()
        .query()
        .withQuery("project:{" + project.get() + "} " + query)
        .get()
        .stream()
        .map(change -> change._number)
        .toList();
  }

  private CurrentRevisionParents stored(Change.Id id) {
    return indexes
        .getSearchIndex()
        .get(
            id,
            QueryOptions.create(
                indexConfig,
                0,
                1,
                Set.of(
                    ChangeField.PROJECT_SPEC.getName(),
                    ChangeField.NUMERIC_ID_STR_SPEC.getName(),
                    ChangeField.CURRENT_REVISION_PARENTS_SPEC.getName())))
        .orElseThrow()
        .indexedCurrentRevisionParents();
  }
}
