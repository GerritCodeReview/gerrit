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

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.git.ChangeMessageModifier;
import com.google.gerrit.server.git.validators.OnSubmitValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class SubmitByRebaseAlwaysIT extends AbstractSubmitByRebase {
  @Inject
  private DynamicSet<ChangeMessageModifier> changeMessageModifiers;

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.REBASE_ALWAYS;
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithPossibleFastForward() throws Exception {
    RevCommit oldHead = getRemoteHead();
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());

    RevCommit head = getRemoteHead();
    assertThat(head.getId()).isNotEqualTo(change.getCommit());
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertApproved(change.getChangeId());
    assertCurrentRevision(change.getChangeId(), 2, head);
    assertSubmitter(change.getChangeId(), 1);
    assertSubmitter(change.getChangeId(), 2);
    assertPersonEquals(admin.getIdent(), head.getAuthorIdent());
    assertPersonEquals(admin.getIdent(), head.getCommitterIdent());
    assertRefUpdatedEvents(oldHead, head);
    assertChangeMergedEvents(change.getChangeId(), head.name());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void alwaysAddFooters() throws Exception {
    PushOneCommit.Result change1 = createChange();
    PushOneCommit.Result change2 = createChange();

    assertThat(
        getCurrentCommit(change1).getFooterLines(FooterConstants.REVIEWED_BY))
            .isEmpty();
    assertThat(
        getCurrentCommit(change2).getFooterLines(FooterConstants.REVIEWED_BY))
            .isEmpty();

    // change1 is a fast-forward, but should be rebased in cherry pick style
    // anyway, making change2 not a fast-forward, requiring a rebase.
    approve(change1.getChangeId());
    submit(change2.getChangeId());
    // ... but both changes should get reviewed-by footers.
    assertLatestRevisionHasFooters(change1);
    assertLatestRevisionHasFooters(change2);
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void changeMessageOnSubmit() throws Exception {
    PushOneCommit.Result change1 = createChange();
    PushOneCommit.Result change2 = createChange();

    RegistrationHandle handle =
        changeMessageModifiers.add(new ChangeMessageModifier() {
          @Override
          public String onSubmit(String newCommitMessage, RevCommit original,
              RevCommit mergeTip, Branch.NameKey destination) {
            List<String> custom = mergeTip.getFooterLines("Custom");
            if (!custom.isEmpty()) {
              newCommitMessage += "Custom-Parent: " + custom.get(0) + "\n";
            }
            return newCommitMessage + "Custom: " + destination.get();
          }
        });
    try {
      // change1 is a fast-forward, but should be rebased in cherry pick style
      // anyway, making change2 not a fast-forward, requiring a rebase.
      approve(change1.getChangeId());
      submit(change2.getChangeId());
    } finally {
      handle.remove();
    }
    // ... but both changes should get custom footers.
    assertThat(getCurrentCommit(change1).getFooterLines("Custom"))
        .containsExactly("refs/heads/master");
    assertThat(getCurrentCommit(change2).getFooterLines("Custom"))
        .containsExactly("refs/heads/master");
    assertThat(getCurrentCommit(change2).getFooterLines("Custom-Parent"))
        .containsExactly("refs/heads/master");
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void destRefValidationIsCalled() throws Exception {
    PushOneCommit.Result change1 = createChange("1", "1.txt", "");
    PushOneCommit.Result change2 = createChange("2", "2.txt", "");
    PushOneCommit.Result change3 = createChange("3", "3.txt", "");

    addOnSubmitValidationListener(new OnSubmitValidationListener() {
      @Override
      public void preBranchUpdate(Arguments args) throws ValidationException {
        assertThat(args.getCommands().keySet()).contains("refs/heads/master");
        try (RevWalk rw = args.newRevWalk()) {
          RevCommit newCommit = rw.parseCommit(
              args.getCommands().get("refs/heads/master").getNewId());
          rw.parseBody(newCommit);
          if (newCommit.getShortMessage().equals("3")){
            // Just change3 is being submitted.
            assertThat(args.getCommands()).hasSize(2);
            throw new ValidationException("3rd change won't be merged");
          }
          // Change1 + change2 are being submitted.
          assertThat(args.getCommands()).hasSize(3);
        } catch (IOException e) {
          assertThat(e).named("should not be raised").isNull();
        }
      }
    });
    // change1 is a fast-forward, but should be rebased in cherry pick style
    // anyway, making change2 not a fast-forward, requiring a rebase.
    approve(change1.getChangeId());
    submit(change2.getChangeId());
    // change3 has to be manually rebased first before submit.
    gApi.changes().id(change3.getChangeId()).current().rebase();
    try {
      submit(change3.getChangeId());
      // ...but should fail anyway in validation.
      assertThat("unrechable").isNull();
    } catch (ResourceConflictException e) {
      assertThat(e.getMessage()).isEqualTo("3rd change won't be merged");
    }
  }

  private void assertLatestRevisionHasFooters(PushOneCommit.Result change)
      throws Exception {
    RevCommit c = getCurrentCommit(change);
    assertThat(c.getFooterLines(FooterConstants.CHANGE_ID)).isNotEmpty();
    assertThat(c.getFooterLines(FooterConstants.REVIEWED_BY)).isNotEmpty();
    assertThat(c.getFooterLines(FooterConstants.REVIEWED_ON)).isNotEmpty();
  }

  private RevCommit getCurrentCommit(PushOneCommit.Result change)
      throws Exception {
    testRepo.git().fetch().setRemote("origin").call();
    ChangeInfo info = get(change.getChangeId());
    RevCommit c = testRepo.getRevWalk()
        .parseCommit(ObjectId.fromString(info.currentRevision));
    testRepo.getRevWalk().parseBody(c);
    return c;
  }
}
