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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.config.UrlFormatter;
import com.google.gerrit.server.git.ChangeMessageModifier;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class SubmitByRebaseAlwaysIT extends AbstractSubmitByRebase {
  @Inject private DynamicItem<UrlFormatter> urlFormatter;
  @Inject private ProjectOperations projectOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.REBASE_ALWAYS;
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithPossibleFastForward() throws Throwable {
    RevCommit oldHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());

    RevCommit head = projectOperations.project(project).getHead("master");
    assertThat(head.getId()).isNotEqualTo(change.getCommit());
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertApproved(change.getChangeId());
    assertCurrentRevision(change.getChangeId(), 2, head);
    assertSubmitter(change.getChangeId(), 1);
    assertSubmitter(change.getChangeId(), 2);
    assertPersonEquals(admin.newIdent(), head.getAuthorIdent());
    assertPersonEquals(admin.newIdent(), head.getCommitterIdent());
    assertRefUpdatedEvents(oldHead, head);
    assertChangeMergedEvents(change.getChangeId(), head.name());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void alwaysAddFooters() throws Throwable {
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
  public void rebaseInvokesChangeMessageModifiers() throws Throwable {
    ChangeMessageModifier modifier1 =
        (msg, orig, tip, dest) -> msg + "This-change-before-rebase: " + orig.name() + "\n";
    ChangeMessageModifier modifier2 =
        (msg, orig, tip, dest) -> msg + "Previous-step-tip: " + tip.name() + "\n";
    ChangeMessageModifier modifier3 =
        (msg, orig, tip, dest) -> msg + "Dest: " + dest.shortName() + "\n";

    try (Registration registration =
        extensionRegistry.newRegistration().add(modifier1).add(modifier2).add(modifier3)) {
      ImmutableList<PushOneCommit.Result> changes = submitWithRebase(admin);
      ChangeData cd1 = changes.get(0).getChange();
      ChangeData cd2 = changes.get(1).getChange();
      assertThat(cd2.patchSets()).hasSize(2);
      String change1CurrentCommit = cd1.currentPatchSet().commitId().name();
      String change2Ps1Commit = cd2.patchSet(PatchSet.id(cd2.getId(), 1)).commitId().name();

      assertThat(gApi.changes().id(cd2.getId().get()).revision(2).commit(false).message)
          .isEqualTo(
              "Change 2\n\n"
                  + ("Change-Id: " + cd2.change().getKey() + "\n")
                  + ("Reviewed-on: "
                      + urlFormatter.get().getChangeViewUrl(project, cd2.getId()).get()
                      + "\n")
                  + "Reviewed-by: Administrator <admin@example.com>\n"
                  + ("This-change-before-rebase: " + change2Ps1Commit + "\n")
                  + ("Previous-step-tip: " + change1CurrentCommit + "\n")
                  + "Dest: master\n");
    }
  }

  @Test
  public void failingChangeMessageModifierShortCircuits() throws Throwable {
    ChangeMessageModifier modifier1 =
        (msg, orig, tip, dest) -> {
          throw new IllegalStateException("boom");
        };
    ChangeMessageModifier modifier2 = (msg, orig, tip, dest) -> msg + "A-footer: value\n";
    try (Registration registration =
        extensionRegistry.newRegistration().add(modifier1).add(modifier2)) {
      ResourceConflictException thrown =
          assertThrows(ResourceConflictException.class, () -> submitWithRebase());
      Throwable cause = Throwables.getRootCause(thrown);
      assertThat(cause).isInstanceOf(RuntimeException.class);
      assertThat(cause).hasMessageThat().isEqualTo("boom");
    }
  }

  @Test
  public void changeMessageModifierReturningNullShortCircuits() throws Throwable {
    ChangeMessageModifier modifier1 = (msg, orig, tip, dest) -> null;
    ChangeMessageModifier modifier2 = (msg, orig, tip, dest) -> msg + "A-footer: value\n";
    try (Registration registration =
        extensionRegistry
            .newRegistration()
            .add(modifier1, "modifier-1")
            .add(modifier2, "modifier-2")) {
      ResourceConflictException thrown =
          assertThrows(ResourceConflictException.class, () -> submitWithRebase());
      Throwable cause = Throwables.getRootCause(thrown);
      assertThat(cause).isInstanceOf(RuntimeException.class);
      assertThat(cause)
          .hasMessageThat()
          .isEqualTo(
              modifier1.getClass().getName()
                  + ".onSubmit from plugin modifier-1 returned null instead of new commit"
                  + " message");
    }
  }

  private void assertLatestRevisionHasFooters(PushOneCommit.Result change) throws Throwable {
    RevCommit c = getCurrentCommit(change);
    assertThat(c.getFooterLines(FooterConstants.CHANGE_ID)).isNotEmpty();
    assertThat(c.getFooterLines(FooterConstants.REVIEWED_BY)).isNotEmpty();
    assertThat(c.getFooterLines(FooterConstants.REVIEWED_ON)).isNotEmpty();
  }

  private RevCommit getCurrentCommit(PushOneCommit.Result change) throws Throwable {
    testRepo.git().fetch().setRemote("origin").call();
    ChangeInfo info = get(change.getChangeId(), CURRENT_REVISION);
    RevCommit c = testRepo.getRevWalk().parseCommit(ObjectId.fromString(info.currentRevision));
    testRepo.getRevWalk().parseBody(c);
    return c;
  }
}
