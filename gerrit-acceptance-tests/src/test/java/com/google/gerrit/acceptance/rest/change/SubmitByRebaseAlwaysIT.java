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
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.events.RefReceivedEvent;
import com.google.gerrit.server.git.ChangeMessageModifier;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SubmitByRebaseAlwaysIT extends AbstractSubmitByRebase {
  @Inject
  private DynamicSet<ChangeMessageModifier> changeMessageModifiers;
  @Inject
  private DynamicSet<RefOperationValidationListener> refValidators;

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
  public void validateRefOperations() throws Exception {
    RevCommit base = getRemoteHead();
    PushOneCommit.Result change1 = createChange();
    PushOneCommit.Result change2 = createChange();

    ArrayList<RefReceivedEvent> events = new ArrayList<>();

    RegistrationHandle handle =
        refValidators.add(new RefOperationValidationListener() {
          @Override
          public List<ValidationMessage> onRefOperation(RefReceivedEvent event)
              throws ValidationException {
            events.add(event);
            if (events.size() == 3) {
              // Each change results in 1 event to update refs/heads/master.
              // TODO(tandrii): new patchset insertions also do ref modification,
              // but are not validated.
              throw new ValidationException("change3 is rejected");
            }
            return Collections.emptyList();
          }
        });

    PushOneCommit.Result change3 = null;
    try {
      approve(change1.getChangeId());
      submit(change2.getChangeId());

      testRepo = cloneProject(project); // Essentially, git pull.
      change3 = createChange();
      try{
        submit(change3.getChangeId());
        assertThat("unreachable").isNull();
      }
      catch (Exception e) {
        assertThat(e.getMessage()).contains("change3 is rejected");
      }
    } finally {
      handle.remove();
    }
    // Validation listener is no longer active, change3 can be submitted.
    submit(change3.getChangeId());

    RevCommit commit1 = getCurrentCommit(change1);
    RevCommit commit2 = getCurrentCommit(change2);

//    assertRefEvent(events.get(0), change1.getPatchSet().getRefName(),
//        ReceiveCommand.Type.CREATE, ObjectId.zeroId(), commit1);
    assertRefEvent(events.get(0), "refs/heads/master",
        ReceiveCommand.Type.UPDATE, base, commit1);

//    assertRefEvent(events.get(2), change2.getPatchSet().getRefName(),
//        ReceiveCommand.Type.CREATE, ObjectId.zeroId(), commit2);
    assertRefEvent(events.get(1), "refs/heads/master",
        ReceiveCommand.Type.UPDATE, commit1, commit2);

    assertThat(events.size()).isEqualTo(3);
  }

  private void assertRefEvent(RefReceivedEvent event, String refName, ReceiveCommand.Type type, ObjectId oldId, ObjectId newId){
    ReceiveCommand cmd = event.command;
    assertThat(cmd.getRefName()).isEqualTo(refName);
    assertThat(cmd.getType()).isEqualTo(type);
    assertThat(cmd.getOldId().getName()).isEqualTo(oldId.getName());
    assertThat(cmd.getNewId().getName()).isEqualTo(newId.getName());
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
