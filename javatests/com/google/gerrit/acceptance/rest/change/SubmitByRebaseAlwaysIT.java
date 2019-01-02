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
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.git.ChangeMessageModifier;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class SubmitByRebaseAlwaysIT extends AbstractSubmitByRebase {
  @Inject private DynamicSet<ChangeMessageModifier> changeMessageModifiers;

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

    assertThat(getCurrentCommit(change1).getFooterLines(FooterConstants.REVIEWED_BY)).isEmpty();
    assertThat(getCurrentCommit(change2).getFooterLines(FooterConstants.REVIEWED_BY)).isEmpty();

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
        changeMessageModifiers.add(
            "gerrit",
            (newCommitMessage, original, mergeTip, destination) -> {
              List<String> custom = mergeTip.getFooterLines("Custom");
              if (!custom.isEmpty()) {
                newCommitMessage += "Custom-Parent: " + custom.get(0) + "\n";
              }
              return newCommitMessage + "Custom: " + destination.get();
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

  private void assertLatestRevisionHasFooters(PushOneCommit.Result change) throws Exception {
    RevCommit c = getCurrentCommit(change);
    assertThat(c.getFooterLines(FooterConstants.CHANGE_ID)).isNotEmpty();
    assertThat(c.getFooterLines(FooterConstants.REVIEWED_BY)).isNotEmpty();
    assertThat(c.getFooterLines(FooterConstants.REVIEWED_ON)).isNotEmpty();
  }

  private RevCommit getCurrentCommit(PushOneCommit.Result change) throws Exception {
    testRepo.git().fetch().setRemote("origin").call();
    ChangeInfo info = get(change.getChangeId(), CURRENT_REVISION);
    RevCommit c = testRepo.getRevWalk().parseCommit(ObjectId.fromString(info.currentRevision));
    testRepo.getRevWalk().parseBody(c);
    return c;
  }
}
