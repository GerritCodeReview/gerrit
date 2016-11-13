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
import com.google.gerrit.extensions.api.changes.SubmittedTogetherInfo;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.testutil.ConfigSuite;
import java.util.EnumSet;
import java.util.List;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class SubmittedTogetherIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

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

    assertThat(info.changes.get(0).currentRevision).isEqualTo(c2_1.name());
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

    assertThat(info.changes.get(0).currentRevision).isEqualTo(c2_1.name());
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

    setApiUserAnonymous();
    assertSubmittedTogether(getChangeId(a));
    assertSubmittedTogether(getChangeId(b), getChangeId(b), getChangeId(a));
  }

  @Test
  public void respectsWholeTopicAndAncestors() throws Exception {
    RevCommit initialHead = getRemoteHead();
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
    RevCommit initialHead = getRemoteHead();
    RevCommit a = commitBuilder().add("a", "1").message("change 1").create();
    pushHead(testRepo, "refs/for/master/" + name("topic"), false);
    String id1 = getChangeId(a);

    testRepo.reset(initialHead);
    RevCommit b = commitBuilder().add("b", "1").message("change 2").create();
    pushHead(testRepo, "refs/for/master/" + name("topic"), false);
    String id2 = getChangeId(b);

    setApiUserAnonymous();
    if (isSubmitWholeTopicEnabled()) {
      assertSubmittedTogether(id1, id2, id1);
      assertSubmittedTogether(id2, id2, id1);
    } else {
      assertSubmittedTogether(id1);
      assertSubmittedTogether(id2);
    }
  }

  @Test
  public void hiddenDraftInTopic() throws Exception {
    RevCommit initialHead = getRemoteHead();
    RevCommit a = commitBuilder().add("a", "1").message("change 1").create();
    pushHead(testRepo, "refs/for/master/" + name("topic"), false);
    String id1 = getChangeId(a);

    testRepo.reset(initialHead);
    commitBuilder().add("b", "2").message("invisible change").create();
    pushHead(testRepo, "refs/drafts/master/" + name("topic"), false);

    setApiUser(user);
    SubmittedTogetherInfo result =
        gApi.changes().id(id1).submittedTogether(EnumSet.of(NON_VISIBLE_CHANGES));

    if (isSubmitWholeTopicEnabled()) {
      assertThat(result.changes).hasSize(1);
      assertThat(result.changes.get(0).changeId).isEqualTo(id1);
      assertThat(result.nonVisibleChanges).isEqualTo(1);
    } else {
      assertThat(result.changes).hasSize(0);
      assertThat(result.nonVisibleChanges).isEqualTo(0);
    }
  }

  @Test
  public void hiddenDraftInTopicOldApi() throws Exception {
    RevCommit initialHead = getRemoteHead();
    RevCommit a = commitBuilder().add("a", "1").message("change 1").create();
    pushHead(testRepo, "refs/for/master/" + name("topic"), false);
    String id1 = getChangeId(a);

    testRepo.reset(initialHead);
    commitBuilder().add("b", "2").message("invisible change").create();
    pushHead(testRepo, "refs/drafts/master/" + name("topic"), false);

    setApiUser(user);
    if (isSubmitWholeTopicEnabled()) {
      exception.expect(AuthException.class);
      exception.expectMessage("change would be submitted with a change that you cannot see");
      gApi.changes().id(id1).submittedTogether();
    } else {
      List<ChangeInfo> result = gApi.changes().id(id1).submittedTogether();
      assertThat(result).hasSize(0);
    }
  }

  @Test
  public void draftPatchSetInTopic() throws Exception {
    RevCommit initialHead = getRemoteHead();
    RevCommit a1 = commitBuilder().add("a", "1").message("change 1").create();
    pushHead(testRepo, "refs/for/master/" + name("topic"), false);
    String id1 = getChangeId(a1);

    testRepo.reset(initialHead);
    RevCommit parent = commitBuilder().message("parent").create();
    pushHead(testRepo, "refs/for/master", false);
    String parentId = getChangeId(parent);

    // TODO(jrn): use insertChangeId(id1) once jgit TestRepository accepts
    // the leading "I".
    commitBuilder()
        .insertChangeId(id1.substring(1))
        .add("a", "2")
        .message("draft patch set on change 1")
        .create();
    pushHead(testRepo, "refs/drafts/master/" + name("topic"), false);

    testRepo.reset(initialHead);
    RevCommit b = commitBuilder().message("change with same topic").create();
    pushHead(testRepo, "refs/for/master/" + name("topic"), false);
    String id2 = getChangeId(b);

    if (isSubmitWholeTopicEnabled()) {
      setApiUser(user);
      assertSubmittedTogether(id2, id2, id1);
      setApiUser(admin);
      assertSubmittedTogether(id2, id2, id1, parentId);
    } else {
      setApiUser(user);
      assertSubmittedTogether(id2);
      setApiUser(admin);
      assertSubmittedTogether(id2);
    }
  }

  @Test
  public void doNotRevealVisibleAncestorOfHiddenDraft() throws Exception {
    RevCommit initialHead = getRemoteHead();
    commitBuilder().message("parent").create();
    pushHead(testRepo, "refs/for/master", false);

    commitBuilder().message("draft").create();
    pushHead(testRepo, "refs/drafts/master/" + name("topic"), false);

    testRepo.reset(initialHead);
    RevCommit change = commitBuilder().message("same topic").create();
    pushHead(testRepo, "refs/for/master/" + name("topic"), false);
    String id = getChangeId(change);

    setApiUser(user);
    SubmittedTogetherInfo result =
        gApi.changes().id(id).submittedTogether(EnumSet.of(NON_VISIBLE_CHANGES));
    if (isSubmitWholeTopicEnabled()) {
      assertThat(result.changes).hasSize(1);
      assertThat(result.changes.get(0).changeId).isEqualTo(id);
      assertThat(result.nonVisibleChanges).isEqualTo(2);
    } else {
      assertThat(result.changes).isEmpty();
      assertThat(result.nonVisibleChanges).isEqualTo(0);
    }
  }

  @Test
  public void testTopicChaining() throws Exception {
    RevCommit initialHead = getRemoteHead();
    // Create two independent commits and push.
    RevCommit c1_1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    String id1 = getChangeId(c1_1);
    pushHead(testRepo, "refs/for/master/" + name("connectingTopic"), false);

    testRepo.reset(initialHead);
    RevCommit c2_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    String id2 = getChangeId(c2_1);
    pushHead(testRepo, "refs/for/master/" + name("connectingTopic"), false);

    RevCommit c3_1 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
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
  public void testSubmissionIdSavedOnMergeInOneProject() throws Exception {
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
