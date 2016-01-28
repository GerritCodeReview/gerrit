// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.git.ChangeSet;
import com.google.gerrit.server.git.MergeSuperSet;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class SubmitResolvingMergeCommitIT extends AbstractDaemonTest {
  @Inject
  private MergeSuperSet mergeSuperSet;

  @Inject
  private Submit submit;

  @Test
  public void resolvingMergeCommitAtEndOfChain() throws Exception {
    /*
      A <- B <- C <------- D
      ^                    ^
      |                    |
      E <- F <- G <- H <-- M*

      G has a conflict with C and is resolved in M which is a merge
      commit of H and D.
    */

    PushOneCommit.Result a = createChange("A");
    PushOneCommit.Result b = createChange("B", "new.txt", "No conflict line",
        ImmutableList.of(a.getCommit()));
    PushOneCommit.Result c = createChange("C", ImmutableList.of(b.getCommit()));
    PushOneCommit.Result d = createChange("D", ImmutableList.of(c.getCommit()));

    PushOneCommit.Result e = createChange("E", ImmutableList.of(a.getCommit()));
    PushOneCommit.Result f = createChange("F", ImmutableList.of(e.getCommit()));
    PushOneCommit.Result g = createChange("G", "new.txt", "Conflicting line",
        ImmutableList.of(f.getCommit()));
    PushOneCommit.Result h = createChange("H", ImmutableList.of(g.getCommit()));

    approve(a.getChangeId());
    approve(b.getChangeId());
    approve(c.getChangeId());
    approve(d.getChangeId());
    submit(d.getChangeId());

    approve(e.getChangeId());
    approve(f.getChangeId());
    approve(g.getChangeId());
    approve(h.getChangeId());

    assertMergeable(e.getChange(), true);
    assertMergeable(f.getChange(), true);
    assertMergeable(g.getChange(), false);
    assertMergeable(h.getChange(), false);

    PushOneCommit.Result m = createChange("M", "new.txt", "Resolved conflict",
        ImmutableList.of(d.getCommit(), h.getCommit()));
    approve(m.getChangeId());

    assertProblemsForSubmittingChangeset(m.getChange(), null);

    assertMergeable(m.getChange(), true);
    submit(m.getChangeId());

    assertMerged(e.getChangeId());
    assertMerged(f.getChangeId());
    assertMerged(g.getChangeId());
    assertMerged(h.getChangeId());
    assertMerged(m.getChangeId());
  }

  @Test
  public void resolvingMergeCommitComingBeforeConflict() throws Exception {
    /*
      A <- B <- C <- D
      ^    ^
      |    |
      E <- F* <- G

      F is a merge commit of E and B and resolves any conflict.
      However G is conflicting with C.
    */

    PushOneCommit.Result a = createChange("A");
    PushOneCommit.Result b = createChange("B", "new.txt", "No conflict line",
        ImmutableList.of(a.getCommit()));
    PushOneCommit.Result c = createChange("C", "new.txt", "No conflict line #2",
        ImmutableList.of(b.getCommit()));
    PushOneCommit.Result d = createChange("D", ImmutableList.of(c.getCommit()));
    PushOneCommit.Result e = createChange("E", "new.txt", "Conflicting line",
        ImmutableList.of(a.getCommit()));
    PushOneCommit.Result f = createChange("F", "new.txt", "Resolved conflict",
        ImmutableList.of(b.getCommit(), e.getCommit()));
    PushOneCommit.Result g = createChange("G", "new.txt", "Conflicting line #2",
        ImmutableList.of(f.getCommit()));

    assertMergeable(e.getChange(), true);

    approve(a.getChangeId());
    approve(b.getChangeId());
    submit(b.getChangeId());

    assertMergeable(e.getChange(), false);
    assertMergeable(f.getChange(), true);
    assertMergeable(g.getChange(), true);

    approve(c.getChangeId());
    approve(d.getChangeId());
    submit(d.getChangeId());

    approve(e.getChangeId());
    approve(f.getChangeId());
    approve(g.getChangeId());

    assertMergeable(g.getChange(), false);

    assertProblemsForSubmittingChangeset(g.getChange(),
        Submit.CLICK_FAILURE_OTHER_TOOLTIP);
  }

  @ConfigSuite.Default
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Test
  public void resolvingMergeCommitWithTopics() throws Exception {
    /*
      Project1:
        A <- B <-- C <---
        ^    ^          |
        |    |          |
        E <- F* <- G <- L*

      G clashes with C, and F resolves the clashes between E and B.
      Later, L resolves the clashes between C and G.

      Project2:
        H <- I
        ^    ^
        |    |
        J <- K*

      J clashes with I, and K resolves all problems.
      G, K and L are in the same topic.
    */
    assume().that(isSubmitWholeTopicEnabled()).isTrue();

    gApi.projects().create("Project1");
    gApi.projects().create("Project2");
    TestRepository<InMemoryRepository> project1 =
        cloneProject(Project.NameKey.parse("Project1"));
    TestRepository<InMemoryRepository> project2 =
        cloneProject(Project.NameKey.parse("Project2"));

    setTestProject(project1);
    PushOneCommit.Result a = createChange("A");
    PushOneCommit.Result b = createChange("B", "new.txt", "No conflict line",
        ImmutableList.of(a.getCommit()));
    PushOneCommit.Result c = createChange("C", "new.txt", "No conflict line #2",
        ImmutableList.of(b.getCommit()));
    PushOneCommit.Result e = createChange("E", "new.txt", "Conflicting line",
        ImmutableList.of(a.getCommit()));
    PushOneCommit.Result f = createChange("F", "new.txt", "Resolved conflict",
        ImmutableList.of(b.getCommit(), e.getCommit()));
    PushOneCommit.Result g = createChange("G", "new.txt", "Conflicting line #2",
        ImmutableList.of(f.getCommit()), "refs/for/master/" + name("topic1"));

    setTestProject(project2);
    PushOneCommit.Result h = createChange("H");
    PushOneCommit.Result i = createChange("I", "new.txt", "No conflict line",
        ImmutableList.of(h.getCommit()));
    PushOneCommit.Result j = createChange("J", "new.txt", "Conflicting line",
        ImmutableList.of(h.getCommit()));
    PushOneCommit.Result k =
        createChange("K", "new.txt", "Sadly conflicting topic-wise",
            ImmutableList.of(i.getCommit(), j.getCommit()),
            "refs/for/master/" + name("topic1"));

    approve(a.getChangeId());
    approve(b.getChangeId());
    approve(c.getChangeId());
    submit(c.getChangeId());

    approve(h.getChangeId());
    approve(i.getChangeId());
    submit(i.getChangeId());

    approve(e.getChangeId());
    approve(f.getChangeId());
    approve(g.getChangeId());
    approve(j.getChangeId());
    approve(k.getChangeId());

    assertProblemsForSubmittingChangeset(g.getChange(),
        Submit.CLICK_FAILURE_OTHER_TOOLTIP);
    assertProblemsForSubmittingChangeset(k.getChange(),
        Submit.CLICK_FAILURE_OTHER_TOOLTIP);

    setTestProject(project1);
    PushOneCommit.Result l =
        createChange("L", "new.txt", "Resolving conflicts again",
            ImmutableList.of(c.getCommit(), g.getCommit()),
            "refs/for/master/" + name("topic1"));

    approve(l.getChangeId());
    assertProblemsForSubmittingChangeset(l.getChange(), null);
  }

  private void assertProblemsForSubmittingChangeset(ChangeData change,
      String expected) throws MissingObjectException,
          IncorrectObjectTypeException, IOException, OrmException {
    String result = "";
    ChangeSet cs = mergeSuperSet.completeChangeSet(db, change.change());
    result = submit.problemsForSubmittingChangeset(cs,
        identifiedUserFactory.create(admin.getId()), db);
    assertThat(result).isEqualTo(expected);
  }

  private void assertMergeable(ChangeData change, boolean expected)
      throws Exception {
    change.setMergeable(null);
    assertThat(change.isMergeable()).isEqualTo(expected);
  }

  private void submit(String changeId) throws Exception {
    gApi.changes()
        .id(changeId)
        .current()
        .submit();
  }

  private void assertMerged(String changeId) throws Exception {
    assertThat(gApi
        .changes()
        .id(changeId)
        .get()
        .status).isEqualTo(ChangeStatus.MERGED);
  }

  private void setTestProject(TestRepository<InMemoryRepository> project) {
    testRepo = project;
  }

  private PushOneCommit.Result createChange(String subject) throws Exception {
    return createChange(subject, "", "", null, "refs/for/master");
  }

  private PushOneCommit.Result createChange(String subject,
      List<RevCommit> parents) throws Exception {
    return createChange(subject, "", "", parents, "refs/for/master");
  }

  private PushOneCommit.Result createChange(String subject, String fileName,
      String content, List<RevCommit> parents) throws Exception {
    return createChange(subject, fileName, content, parents, "refs/for/master");
  }
}
