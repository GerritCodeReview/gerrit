// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Test;

public class SubmitByRebaseIfNecessaryIT extends AbstractSubmitByRebase {
  @Inject private ProjectOperations projectOperations;

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.REBASE_IF_NECESSARY;
  }

  @Test
  public void submitChangeAfterCommitWasDirectlyPushed() throws Throwable {
    TestRepository<?> localRepo = cloneProject(project);

    ObjectId revChange =
        localRepo
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .message("Test commit")
            .add("testDev.txt", "content")
            .create();
    pushChangeTo(localRepo, "refs/for/master", revChange);

    ObjectId revChangeDev =
        localRepo
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .message("Test commit")
            .add("testDev.txt", "content")
            .create();
    pushChangeTo(localRepo, "refs/heads/dev", revChange);

    submit(getChangeId(localRepo, revChange).get());
    RevCommit head = projectOperations.project(project).getHead("master");
    assertThat(head.getId()).isEqualTo(revChange);
  }

  protected void pushChangeTo(TestRepository<?> repo, String targetRef, ObjectId rev)
      throws Exception {
    String pushedRef = targetRef;
    String refspec = "HEAD:" + pushedRef;

    Iterable<PushResult> res =
        repo.git().push().setRemote("origin").setRefSpecs(new RefSpec(refspec)).call();

    RemoteRefUpdate u = Iterables.getOnlyElement(res).getRemoteUpdate(pushedRef);
    assertThat(u).isNotNull();
    assertThat(u.getStatus()).isEqualTo(Status.OK);
    assertThat(u.getNewObjectId()).isEqualTo(rev);
  }

  private static Optional<String> getChangeId(TestRepository<?> tr, ObjectId id)
      throws IOException {
    RevCommit c = tr.getRevWalk().parseCommit(id);
    tr.getRevWalk().parseBody(c);
    return Lists.reverse(c.getFooterLines(FooterConstants.CHANGE_ID)).stream().findFirst();
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithFastForward() throws Throwable {
    RevCommit oldHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());
    RevCommit head = projectOperations.project(project).getHead("master");
    assertThat(head.getId()).isEqualTo(change.getCommit());
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertApproved(change.getChangeId());
    assertCurrentRevision(change.getChangeId(), 1, head);
    assertSubmitter(change.getChangeId(), 1);
    assertPersonEquals(admin.newIdent(), head.getAuthorIdent());
    assertPersonEquals(admin.newIdent(), head.getCommitterIdent());
    assertRefUpdatedEvents(oldHead, head);
    assertChangeMergedEvents(change.getChangeId(), head.name());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "aaa\nbbb\nccc\n");
    submit(change.getChangeId());
    RevCommit headAfterFirstSubmit = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "aaa\nbbb\nccc\nddd\n");
    submit(change2.getChangeId());

    RevCommit headAfterSecondSubmit = projectOperations.project(project).getHead("master");
    testRepo.reset(change.getCommit());
    PushOneCommit.Result change3 = createChange("Change 3", "a.txt", "bbb\nccc\n");
    submit(change3.getChangeId());
    assertRebase(testRepo, true);
    RevCommit headAfterThirdSubmit = projectOperations.project(project).getHead("master");
    assertThat(headAfterThirdSubmit.getParent(0)).isEqualTo(headAfterSecondSubmit);
    assertApproved(change3.getChangeId());
    assertCurrentRevision(change3.getChangeId(), 2, headAfterThirdSubmit);
    assertSubmitter(change3.getChangeId(), 1);
    assertSubmitter(change3.getChangeId(), 2);

    assertRefUpdatedEvents(
        initialHead,
        headAfterFirstSubmit,
        headAfterFirstSubmit,
        headAfterSecondSubmit,
        headAfterSecondSubmit,
        headAfterThirdSubmit);
    assertChangeMergedEvents(
        change.getChangeId(),
        headAfterFirstSubmit.name(),
        change2.getChangeId(),
        headAfterSecondSubmit.name(),
        change3.getChangeId(),
        headAfterThirdSubmit.name());
  }
}
