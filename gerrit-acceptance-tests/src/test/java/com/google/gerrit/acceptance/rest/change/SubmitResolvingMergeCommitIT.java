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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NoHttpd
public class SubmitResolvingMergeCommitIT extends AbstractDaemonTest {
  @Inject
  private MergeSuperSet mergeSuperSet;

  @Inject
  private Submit submit;

  @ConfigSuite.Default
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

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

    PushOneCommit.Result a = createTestChange("A");
    PushOneCommit.Result b = createTestChange("B", "new.txt", "No conflict line",
        ImmutableList.of(a.getCommit()));
    PushOneCommit.Result c = createTestChange("C", ImmutableList.of(b.getCommit()));
    PushOneCommit.Result d = createTestChange("D", ImmutableList.of(c.getCommit()));

    PushOneCommit.Result e = createTestChange("E", ImmutableList.of(a.getCommit()));
    PushOneCommit.Result f = createTestChange("F", ImmutableList.of(e.getCommit()));
    PushOneCommit.Result g = createTestChange("G", "new.txt", "Conflicting line",
        ImmutableList.of(f.getCommit()));
    PushOneCommit.Result h = createTestChange("H", ImmutableList.of(g.getCommit()));

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

    PushOneCommit.Result m = createTestChange("M", "new.txt", "Resolved conflict",
        ImmutableList.of(d.getCommit(), h.getCommit()));
    approve(m.getChangeId());

    assertChangeSetMergeable(m.getChange(), true);

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

    PushOneCommit.Result a = createTestChange("A");
    PushOneCommit.Result b = createTestChange("B", "new.txt", "No conflict line",
        ImmutableList.of(a.getCommit()));
    PushOneCommit.Result c = createTestChange("C", "new.txt", "No conflict line #2",
        ImmutableList.of(b.getCommit()));
    PushOneCommit.Result d = createTestChange("D", ImmutableList.of(c.getCommit()));
    PushOneCommit.Result e = createTestChange("E", "new.txt", "Conflicting line",
        ImmutableList.of(a.getCommit()));
    PushOneCommit.Result f = createTestChange("F", "new.txt", "Resolved conflict",
        ImmutableList.of(b.getCommit(), e.getCommit()));
    PushOneCommit.Result g = createTestChange("G", "new.txt", "Conflicting line #2",
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
    assertChangeSetMergeable(g.getChange(), false);
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

    String project1Name = name("Project1");
    String project2Name = name("Project2");
    gApi.projects().create(project1Name);
    gApi.projects().create(project2Name);
    TestRepository<InMemoryRepository> project1 =
        cloneProject(new Project.NameKey(project1Name));
    TestRepository<InMemoryRepository> project2 =
        cloneProject(new Project.NameKey(project2Name));

    PushOneCommit.Result a = createTestChange(project1, "A");
    PushOneCommit.Result b = createTestChange(project1, "B", "new.txt",
        "No conflict line", ImmutableList.of(a.getCommit()));
    PushOneCommit.Result c = createTestChange(project1, "C", "new.txt",
        "No conflict line #2", ImmutableList.of(b.getCommit()));

    approve(a.getChangeId());
    approve(b.getChangeId());
    approve(c.getChangeId());
    submit(c.getChangeId());

    PushOneCommit.Result e = createTestChange(project1, "E", "new.txt",
        "Conflicting line", ImmutableList.of(a.getCommit()));
    PushOneCommit.Result f = createTestChange(project1, "F", "new.txt",
        "Resolved conflict", ImmutableList.of(b.getCommit(), e.getCommit()));
    PushOneCommit.Result g = createTestChange(project1, "G", "new.txt",
        "Conflicting line #2", ImmutableList.of(f.getCommit()),
        "refs/for/master/" + name("topic1"));

    PushOneCommit.Result h = createTestChange(project2, "H");
    PushOneCommit.Result i = createTestChange(project2, "I", "new.txt",
        "No conflict line", ImmutableList.of(h.getCommit()));
    PushOneCommit.Result j = createTestChange(project2, "J", "new.txt",
        "Conflicting line", ImmutableList.of(h.getCommit()));
    PushOneCommit.Result k =
        createTestChange(project2, "K", "new.txt", "Sadly conflicting topic-wise",
            ImmutableList.of(i.getCommit(), j.getCommit()),
            "refs/for/master/" + name("topic1"));

    approve(h.getChangeId());
    approve(i.getChangeId());
    submit(i.getChangeId());

    approve(e.getChangeId());
    approve(f.getChangeId());
    approve(g.getChangeId());
    approve(j.getChangeId());
    approve(k.getChangeId());

    assertChangeSetMergeable(g.getChange(), false);
    assertChangeSetMergeable(k.getChange(), false);

    PushOneCommit.Result l =
        createTestChange(project1, "L", "new.txt", "Resolving conflicts again",
            ImmutableList.of(c.getCommit(), g.getCommit()),
            "refs/for/master/" + name("topic1"));

    approve(l.getChangeId());
    assertChangeSetMergeable(l.getChange(), true);

    submit(l.getChangeId());
    assertMerged(c.getChangeId());
    assertMerged(g.getChangeId());
    assertMerged(k.getChangeId());
  }

  @Test
  public void resolvingMergeCommitAtEndOfChainAndNotUpToDate() throws Exception {
    /*
        A <-- B
         \
          C  <- D
           \   /
             E

        B is the target branch, and D should be merged with B, but one
        of C conflicts with B
    */

    PushOneCommit.Result a = createTestChange("A");
    PushOneCommit.Result b = createTestChange("B", "new.txt", "No conflict line",
        ImmutableList.of(a.getCommit()));

    approve(a.getChangeId());
    approve(b.getChangeId());
    submit(b.getChangeId());

    PushOneCommit.Result c = createTestChange("C", "new.txt", "Create conflicts",
        ImmutableList.of(a.getCommit()));
    PushOneCommit.Result e = createTestChange("E", ImmutableList.of(c.getCommit()));
    PushOneCommit.Result d = createTestChange("D", "new.txt", "Resolves conflicts",
        ImmutableList.of(c.getCommit(), e.getCommit()));

    approve(c.getChangeId());
    approve(e.getChangeId());
    approve(d.getChangeId());
    assertMergeable(d.getChange(), false);
    assertChangeSetMergeable(d.getChange(), false);
  }

  private void submit(String changeId) throws Exception {
    gApi.changes()
        .id(changeId)
        .current()
        .submit();
  }

  private void assertChangeSetMergeable(ChangeData change,
      boolean expected) throws MissingObjectException,
          IncorrectObjectTypeException, IOException, OrmException {
    ChangeSet cs = mergeSuperSet.completeChangeSet(db, change.change(), user(admin));
    assertThat(submit.isPatchSetMergeable(cs)).isEqualTo(expected);
  }

  private void assertMergeable(ChangeData change, boolean expected)
      throws Exception {
    change.setMergeable(null);
    assertThat(change.isMergeable()).isEqualTo(expected);
  }

  private void assertMerged(String changeId) throws Exception {
    assertThat(gApi
        .changes()
        .id(changeId)
        .get()
        .status).isEqualTo(ChangeStatus.MERGED);
  }

  private PushOneCommit.Result createTestChange(TestRepository<?> repo,
      String subject, String fileName, String content, List<RevCommit> parents,
      String ref) throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), repo,
        subject, fileName, content);

    if (!parents.isEmpty()) {
      push.setParents(parents);
    }

    PushOneCommit.Result result;
    if (fileName.isEmpty()) {
      result = push.execute(ref);
    } else {
      result = push.to(ref);
    }
    result.assertOkStatus();
    return result;
  }

  private PushOneCommit.Result createTestChange(TestRepository<?> repo,
      String subject) throws Exception {
    return createTestChange(repo, subject, "x", "x", new ArrayList<RevCommit>(),
        "refs/for/master");
  }

  private PushOneCommit.Result createTestChange(TestRepository<?> repo,
      String subject, String fileName, String content, List<RevCommit> parents)
          throws Exception {
    return createTestChange(repo, subject, fileName, content, parents,
        "refs/for/master");
  }

  private PushOneCommit.Result createTestChange(String subject) throws Exception {
    return createTestChange(testRepo, subject, "", "",
        Collections.<RevCommit> emptyList(), "refs/for/master");
  }

  private PushOneCommit.Result createTestChange(String subject,
      List<RevCommit> parents) throws Exception {
    return createTestChange(testRepo, subject, "", "", parents, "refs/for/master");
  }

  private PushOneCommit.Result createTestChange(String subject, String fileName,
      String content, List<RevCommit> parents) throws Exception {
    return createTestChange(testRepo, subject, fileName, content, parents,
        "refs/for/master");
  }
}
