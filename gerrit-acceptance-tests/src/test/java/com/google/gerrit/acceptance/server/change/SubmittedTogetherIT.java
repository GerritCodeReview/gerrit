// Copyright (C) 2015 The Android Open Source Project
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
import static com.google.gerrit.acceptance.GitUtil.pushHead;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.ProjectConfig;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class SubmittedTogetherIT extends AbstractDaemonTest {

  @Test
  public void returnsAncestors() throws Exception {
    // Create two commits and push.
    RevCommit c1_1 = commitBuilder()
        .add("a.txt", "1")
        .message("subject: 1")
        .create();
    String id1 = getChangeId(c1_1);
    RevCommit c2_1 = commitBuilder()
        .add("b.txt", "2")
        .message("subject: 2")
        .create();
    String id2 = getChangeId(c2_1);
    pushHead(testRepo, "refs/for/master", false);

    assertSubmittedTogether(id1);
    assertSubmittedTogether(id2, id2, id1);
  }

  @Test
  public void respectsWholeTopicAndAncestors() throws Exception {
    RevCommit initialHead = getRemoteHead();
    // Create two independent commits and push.
    RevCommit c1_1 = commitBuilder()
        .add("a.txt", "1")
        .message("subject: 1")
        .create();
    String id1 = getChangeId(c1_1);
    pushHead(testRepo, "refs/for/master/" + name("connectingTopic"), false);

    testRepo.reset(initialHead);
    RevCommit c2_1 = commitBuilder()
        .add("b.txt", "2")
        .message("subject: 2")
        .create();
    String id2 = getChangeId(c2_1);
    pushHead(testRepo, "refs/for/master/" + name("connectingTopic"), false);

    if (isSubmitWholeTopicEnabled()) {
      assertSubmittedTogether(id1, id2, id1);
      assertSubmittedTogether(id2, id2, id1);
    } else {
      assertSubmittedTogether(id1);
      assertSubmittedTogether(id2);
    }
  }

  @Test
  public void testTopicChaining() throws Exception {
    RevCommit initialHead = getRemoteHead();
    // Create two independent commits and push.
    RevCommit c1_1 = commitBuilder()
        .add("a.txt", "1")
        .message("subject: 1")
        .create();
    String id1 = getChangeId(c1_1);
    pushHead(testRepo, "refs/for/master/" + name("connectingTopic"), false);

    testRepo.reset(initialHead);
    RevCommit c2_1 = commitBuilder()
        .add("b.txt", "2")
        .message("subject: 2")
        .create();
    String id2 = getChangeId(c2_1);
    pushHead(testRepo, "refs/for/master/" + name("connectingTopic"), false);

    RevCommit c3_1 = commitBuilder()
        .add("b.txt", "2")
        .message("subject: 2")
        .create();
    String id3 = getChangeId(c3_1);
    pushHead(testRepo, "refs/for/master/" + name("unrelated-topic"), false);

    if (isSubmitWholeTopicEnabled()) {
      assertSubmittedTogether(id1, id2, id1);
      assertSubmittedTogether(id2, id2, id1);
      assertSubmittedTogether(id3, id3, id2, id1);
    } else {
      assertSubmittedTogether(id1);
      assertSubmittedTogether(id2);
      assertSubmittedTogether(id3, id3, id2);
    }
  }

  @Test
  public void testNewBranchTwoChangesTogether() throws Exception {
    Project.NameKey p1 = createProject("a-new-project", null, false);
    TestRepository<?> repo1 = cloneProject(p1);

    RevCommit c1 = repo1.branch("HEAD").commit().insertChangeId()
        .add("a.txt", "1")
        .message("subject: 1")
        .create();
    String id1 = GitUtil.getChangeId(repo1, c1).get();
    pushHead(repo1, "refs/for/master", false);

    RevCommit c2 = repo1.branch("HEAD").commit().insertChangeId()
        .add("b.txt", "2")
        .message("subject: 2")
        .create();
    String id2 = GitUtil.getChangeId(repo1, c2).get();
    pushHead(repo1, "refs/for/master", false);
    assertSubmittedTogether(id1);
    assertSubmittedTogether(id2, id2, id1);
  }

  @Test
  public void testCherryPickWithoutAncestors() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.getProject().setSubmitType(SubmitType.CHERRY_PICK);
    saveProjectConfig(project, cfg);

    // Create two commits and push.
    RevCommit c1_1 = commitBuilder()
        .add("a.txt", "1")
        .message("subject: 1")
        .create();
    String id1 = getChangeId(c1_1);
    RevCommit c2_1 = commitBuilder()
        .add("b.txt", "2")
        .message("subject: 2")
        .create();
    String id2 = getChangeId(c2_1);
    pushHead(testRepo, "refs/for/master", false);

    assertSubmittedTogether(id1);
    assertSubmittedTogether(id2);
  }

  private void assertSubmittedTogether(String chId, String... expected)
      throws Exception {
    List<ChangeInfo> actual = gApi.changes().id(chId).submittedTogether();
    assertThat(actual).hasSize(expected.length);
    assertThat(Arrays.asList(expected))
        .containsExactlyElementsIn(
            Iterables.transform(actual, new Function<ChangeInfo, String>() {
              @Override
              public String apply(ChangeInfo input) {
                return input.changeId;
              }
            })).inOrder();
  }

  private RevCommit getRemoteHead() throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      return rw.parseCommit(repo.getRef("refs/heads/master").getObjectId());
    }
  }

  private String getChangeId(RevCommit c) throws Exception {
    return GitUtil.getChangeId(testRepo, c).get();
  }
}