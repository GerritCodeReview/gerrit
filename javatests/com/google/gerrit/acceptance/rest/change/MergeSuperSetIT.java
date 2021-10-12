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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableCollection;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.submit.ChangeSet;
import com.google.gerrit.server.submit.MergeSuperSet;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Set;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

/** * Tests {@link MergeSuperSet}. This classtests only the basic functionality. */
public class MergeSuperSetIT extends AbstractDaemonTest {
  @Inject private Provider<MergeSuperSet> mergeSuperSet;
  @Inject private ProjectOperations projectOperations;

  @ConfigSuite.Default
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Test
  public void mergeSuperSet() throws Exception {
    String topic1 = "topic1";
    String topic2 = "topic2";
    String topic3 = "topic3";
    String project2 = "project2";
    projectOperations.newProject().name(project2).create();
    String project3 = "project3";
    projectOperations.newProject().name(project3).create();
    String project4 = "project4";
    projectOperations.newProject().name(project4).create();
    TestRepository<InMemoryRepository> repo2 = cloneProject(Project.nameKey(project2), admin);
    TestRepository<InMemoryRepository> repo3 = cloneProject(Project.nameKey(project3), admin);
    TestRepository<InMemoryRepository> repo4 = cloneProject(Project.nameKey(project4), admin);

    // A -> B(topic2) -> C
    PushOneCommit.Result A = createChange(testRepo);
    PushOneCommit.Result B =
        createChangeWithTopic(testRepo, topic2, "commit message", "file", "content");
    PushOneCommit.Result C = createChange(testRepo);

    // D(topic1) -> E(topic2) -> F (topic3)
    PushOneCommit.Result D =
        createChangeWithTopic(repo2, topic1, "commit message", "file", "content");
    PushOneCommit.Result E =
        createChangeWithTopic(repo2, topic2, "commit message", "file", "content");
    PushOneCommit.Result F =
        createChangeWithTopic(repo2, topic3, "commit message", "file", "content");

    // G -> H(topic3) -> J
    PushOneCommit.Result G = createChange(repo3);
    PushOneCommit.Result H =
        createChangeWithTopic(repo3, topic3, "commit message", "file", "content");
    PushOneCommit.Result J = createChange(repo3);

    // K -> L(topic1) -> M
    PushOneCommit.Result K = createChange(repo4);
    PushOneCommit.Result L =
        createChangeWithTopic(repo4, topic1, "commit message", "file", "content");
    PushOneCommit.Result M = createChange(repo4);

    ChangeSet changeSet =
        mergeSuperSet
            .get()
            .completeChangeSetIncludingChangesNotRequiredForSubmission(
                B.getChange().change(), user(admin));
    assertThat(getIds(changeSet.changes()))
        .containsExactly(
            B.getChange().getId(),
            A.getChange().getId(),
            E.getChange().getId(),
            D.getChange().getId(),
            L.getChange().getId(),
            K.getChange().getId());
    // repository 3 (G, H, J) is missing completely as we don't have topic closure for related
    // changes yet.
    assertThat(getIds(changeSet.relatedChangesNotRequiredForSubmission()))
        .containsExactly(M.getChange().getId(), C.getChange().getId(), F.getChange().getId());

    // All the changes are needed to submit F or at least related to F.
    changeSet =
        mergeSuperSet
            .get()
            .completeChangeSetIncludingChangesNotRequiredForSubmission(
                F.getChange().change(), user(admin));
    assertThat(getIds(changeSet.changes()))
        .containsExactly(
            A.getChange().getId(),
            B.getChange().getId(),
            D.getChange().getId(),
            E.getChange().getId(),
            F.getChange().getId(),
            H.getChange().getId(),
            K.getChange().getId(),
            L.getChange().getId(),
            G.getChange().getId());
    assertThat(getIds(changeSet.relatedChangesNotRequiredForSubmission()))
        .containsExactly(M.getChange().getId(), C.getChange().getId(), J.getChange().getId());
  }

  private Set<Change.Id> getIds(ImmutableCollection<ChangeData> changes) {
    return changes.stream().map(c -> c.getId()).collect(toImmutableSet());
  }
}
