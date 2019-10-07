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
import static com.google.gerrit.extensions.api.changes.SubmittedTogetherOption.NON_VISIBLE_CHANGES;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.SubmittedTogetherInfo;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.util.EnumSet;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class SubmittedTogetherIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void doesNotIncludeCurrentFiles() throws Exception {
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    String id2 = getChangeId(c2_1);
    pushHead(testRepo, "refs/for/master", false);

    SubmittedTogetherInfo info =
        gApi.changes().id(id2).submittedTogether(EnumSet.of(NON_VISIBLE_CHANGES));
    assertThat(info.changes).hasSize(2);
    assertThat(info.changes.get(0).currentRevision).isEqualTo(c2_1.name());
    assertThat(info.changes.get(1).currentRevision).isEqualTo(c1_1.name());

    RevisionInfo rev = info.changes.get(0).revisions.get(c2_1.name());
    assertThat(rev.files).isNull();
  }

  @Test
  public void returnsCurrentFilesIfOptionRequested() throws Exception {
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    String id2 = getChangeId(c2_1);
    pushHead(testRepo, "refs/for/master", false);

    SubmittedTogetherInfo info =
        gApi.changes()
            .id(id2)
            .submittedTogether(
                EnumSet.of(ListChangesOption.CURRENT_FILES), EnumSet.of(NON_VISIBLE_CHANGES));
    assertThat(info.changes).hasSize(2);
    assertThat(info.changes.get(0).currentRevision).isEqualTo(c2_1.name());
    assertThat(info.changes.get(1).currentRevision).isEqualTo(c1_1.name());

    RevisionInfo rev = info.changes.get(0).revisions.get(c2_1.name());
    assertThat(rev).isNotNull();
    FileInfo file = rev.files.get("b.txt");
    assertThat(file).isNotNull();
    assertThat(file.status).isEqualTo('A');
  }

  @Test
  public void returnsAncestors() throws Exception {
    // Create two commits and push.
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    String id1 = getChangeId(c1_1);
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    String id2 = getChangeId(c2_1);
    pushHead(testRepo, "refs/for/master", false);

    assertSubmittedTogether(id1);
    assertSubmittedTogether(id2, id2, id1);
  }

  @Test
  public void anonymousAncestors() throws Exception {
    RevCommit a = commitBuilder().add("a", "1").message("change 1").create();
    RevCommit b = commitBuilder().add("b", "1").message("change 2").create();
    pushHead(testRepo, "refs/for/master", false);

    requestScopeOperations.setApiUserAnonymous();
    assertSubmittedTogether(getChangeId(a));
    assertSubmittedTogether(getChangeId(b), getChangeId(b), getChangeId(a));
  }

  @Test
  public void respectWholeTopic() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    // Create two independent commits and push.
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    String id1 = getChangeId(c1_1);
    pushHead(testRepo, "refs/for/master/" + name("connectingTopic"), false);

    testRepo.reset(initialHead);
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
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
  public void anonymousWholeTopic() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    RevCommit a = commitBuilder().add("a", "1").message("change 1").create();
    pushHead(testRepo, "refs/for/master/" + name("topic"), false);
    String id1 = getChangeId(a);

    testRepo.reset(initialHead);
    RevCommit b = commitBuilder().add("b", "1").message("change 2").create();
    pushHead(testRepo, "refs/for/master/" + name("topic"), false);
    String id2 = getChangeId(b);

    requestScopeOperations.setApiUserAnonymous();
    if (isSubmitWholeTopicEnabled()) {
      assertSubmittedTogether(id1, id2, id1);
      assertSubmittedTogether(id2, id2, id1);
    } else {
      assertSubmittedTogether(id1);
      assertSubmittedTogether(id2);
    }
  }

  @Test
  public void topicChaining() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    String id1 = getChangeId(c1_1);
    pushHead(testRepo, "refs/for/master/" + name("connectingTopic"), false);

    testRepo.reset(initialHead);
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    String id2 = getChangeId(c2_1);
    pushHead(testRepo, "refs/for/master/" + name("connectingTopic"), false);

    RevCommit c3_1 = commitBuilder().add("b.txt", "3").message("subject: 3").create();
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
  public void respectTopicsOnAncestors() throws Exception {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    String id1 = getChangeId(c1_1);
    pushHead(testRepo, "refs/for/master/" + name("connectingTopic"), false);

    testRepo.reset(initialHead);
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    String id2 = getChangeId(c2_1);
    pushHead(testRepo, "refs/for/master/" + name("otherConnectingTopic"), false);

    RevCommit c3_1 = commitBuilder().add("b.txt", "3").message("subject: 3").create();
    String id3 = getChangeId(c3_1);
    pushHead(testRepo, "refs/for/master/" + name("connectingTopic"), false);

    RevCommit c4_1 = commitBuilder().add("b.txt", "4").message("subject: 4").create();
    String id4 = getChangeId(c4_1);
    pushHead(testRepo, "refs/for/master", false);

    testRepo.reset(initialHead);
    RevCommit c5_1 = commitBuilder().add("c.txt", "5").message("subject: 5").create();
    String id5 = getChangeId(c5_1);
    pushHead(testRepo, "refs/for/master", false);

    RevCommit c6_1 = commitBuilder().add("c.txt", "6").message("subject: 6").create();
    String id6 = getChangeId(c6_1);
    pushHead(testRepo, "refs/for/master/" + name("otherConnectingTopic"), false);

    if (isSubmitWholeTopicEnabled()) {
      assertSubmittedTogether(id1, id6, id5, id3, id2, id1);
      assertSubmittedTogether(id2, id6, id5, id2);
      assertSubmittedTogether(id3, id6, id5, id3, id2, id1);
      assertSubmittedTogether(id4, id6, id5, id4, id3, id2, id1);
      assertSubmittedTogether(id5);
      assertSubmittedTogether(id6, id6, id5, id2);
    } else {
      assertSubmittedTogether(id1);
      assertSubmittedTogether(id2);
      assertSubmittedTogether(id3, id3, id2);
      assertSubmittedTogether(id4, id4, id3, id2);
      assertSubmittedTogether(id5);
      assertSubmittedTogether(id6, id6, id5);
    }
  }

  @Test
  public void newBranchTwoChangesTogether() throws Exception {
    Project.NameKey p1 = projectOperations.newProject().noEmptyCommit().create();

    TestRepository<?> repo1 = cloneProject(p1);

    RevCommit c1 =
        repo1
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .add("a.txt", "1")
            .message("subject: 1")
            .create();
    String id1 = GitUtil.getChangeId(repo1, c1).get();
    pushHead(repo1, "refs/for/master", false);

    RevCommit c2 =
        repo1
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .add("b.txt", "2")
            .message("subject: 2")
            .create();
    String id2 = GitUtil.getChangeId(repo1, c2).get();
    pushHead(repo1, "refs/for/master", false);
    assertSubmittedTogether(id1);
    assertSubmittedTogether(id2, id2, id1);
  }

  @Test
  @TestProjectInput(submitType = SubmitType.CHERRY_PICK)
  public void testCherryPickWithoutAncestors() throws Exception {
    // Create two commits and push.
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    String id1 = getChangeId(c1_1);
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    String id2 = getChangeId(c2_1);
    pushHead(testRepo, "refs/for/master", false);

    assertSubmittedTogether(id1);
    assertSubmittedTogether(id2);
  }

  @Test
  public void submissionIdSavedOnMergeInOneProject() throws Exception {
    // Create two commits and push.
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    String id1 = getChangeId(c1_1);
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    String id2 = getChangeId(c2_1);
    pushHead(testRepo, "refs/for/master", false);

    assertSubmittedTogether(id1);
    assertSubmittedTogether(id2, id2, id1);

    approve(id1);
    approve(id2);
    submit(id2);
    assertMerged(id1);
    assertMerged(id2);

    // Prior to submission this was empty, but the post-merge value is what was
    // actually submitted.
    assertSubmittedTogether(id1, id2, id1);

    assertSubmittedTogether(id2, id2, id1);
  }

  private String getChangeId(RevCommit c) throws Exception {
    return GitUtil.getChangeId(testRepo, c).get();
  }

  private void submit(String changeId) throws Exception {
    gApi.changes().id(changeId).current().submit();
  }

  private void assertMerged(String changeId) throws Exception {
    assertThat(gApi.changes().id(changeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }
}
