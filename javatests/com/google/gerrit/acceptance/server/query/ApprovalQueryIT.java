// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.query;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.change.ChangeKindCreator;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.OperatorPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.approval.ApprovalContext;
import com.google.gerrit.server.query.approval.ApprovalQueryBuilder;
import com.google.gerrit.server.query.approval.ApprovalQueryBuilder.UserInOperandFactory;
import com.google.gerrit.server.query.approval.UserInPredicate;
import com.google.gerrit.server.update.RepoView;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

public class ApprovalQueryIT extends AbstractDaemonTest {
  @Inject private ApprovalQueryBuilder queryBuilder;
  @Inject private ChangeKindCreator changeKindCreator;
  @Inject private ChangeNotes.Factory changeNotesFactory;
  @Inject private ChangeKindCache changeKindCache;
  @Inject private ChangeOperations changeOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void magicValuePredicate() throws Exception {
    assertTrue(queryBuilder.parse("is:MAX").asMatchable().match(contextForCodeReviewLabel(2)));
    assertTrue(queryBuilder.parse("is:mAx").asMatchable().match(contextForCodeReviewLabel(2)));
    assertFalse(queryBuilder.parse("is:MAX").asMatchable().match(contextForCodeReviewLabel(-2)));
    assertFalse(queryBuilder.parse("is:MAX").asMatchable().match(contextForCodeReviewLabel(1)));
    assertFalse(queryBuilder.parse("is:MAX").asMatchable().match(contextForCodeReviewLabel(5000)));

    assertTrue(queryBuilder.parse("is:MIN").asMatchable().match(contextForCodeReviewLabel(-2)));
    assertTrue(queryBuilder.parse("is:mIn").asMatchable().match(contextForCodeReviewLabel(-2)));
    assertFalse(queryBuilder.parse("is:MIN").asMatchable().match(contextForCodeReviewLabel(2)));
    assertFalse(queryBuilder.parse("is:MIN").asMatchable().match(contextForCodeReviewLabel(-1)));
    assertFalse(queryBuilder.parse("is:MIN").asMatchable().match(contextForCodeReviewLabel(5000)));

    assertTrue(queryBuilder.parse("is:ANY").asMatchable().match(contextForCodeReviewLabel(-2)));
    assertTrue(queryBuilder.parse("is:ANY").asMatchable().match(contextForCodeReviewLabel(2)));
    assertTrue(queryBuilder.parse("is:aNy").asMatchable().match(contextForCodeReviewLabel(2)));

    assertTrue(queryBuilder.parse("is:POSITIVE").asMatchable().match(contextForCodeReviewLabel(1)));
    assertTrue(
        queryBuilder.parse("is:negative").asMatchable().match(contextForCodeReviewLabel(-2)));
    assertFalse(
        queryBuilder.parse("is:positive").asMatchable().match(contextForCodeReviewLabel(-1)));
    assertFalse(
        queryBuilder.parse("is:NEGATIVE").asMatchable().match(contextForCodeReviewLabel(2)));
  }

  @Test
  public void exactValuePredicate() throws Exception {
    ApprovalContext approvalContextCodeReviewPlusOne = contextForCodeReviewLabel(1);
    assertFalse(
        queryBuilder.parse("is:\"-2\"").asMatchable().match(approvalContextCodeReviewPlusOne));
    assertFalse(
        queryBuilder.parse("is:\"-1\"").asMatchable().match(approvalContextCodeReviewPlusOne));
    assertFalse(queryBuilder.parse("is:0").asMatchable().match(approvalContextCodeReviewPlusOne));
    assertTrue(queryBuilder.parse("is:1").asMatchable().match(approvalContextCodeReviewPlusOne));
    assertFalse(queryBuilder.parse("is:2").asMatchable().match(approvalContextCodeReviewPlusOne));
  }

  @Test
  public void isPredicate_invalidValue() throws Exception {
    QueryParseException thrown =
        assertThrows(QueryParseException.class, () -> queryBuilder.parse("is:INVALID"));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "INVALID is not a valid value for operator 'is'. Valid values: ANY, MAX, MIN, NEGATIVE,"
                + " POSITIVE or integer");
  }

  @Test
  public void changeKindPredicate_noCodeChange() throws Exception {
    String change = changeKindCreator.createChange(ChangeKind.NO_CODE_CHANGE, testRepo, admin);
    changeKindCreator.updateChange(change, ChangeKind.NO_CODE_CHANGE, testRepo, admin, project);
    PatchSet.Id ps1 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), /* id= */ 1);
    assertTrue(
        queryBuilder
            .parse("changekind:no-code-change")
            .asMatchable()
            .match(contextForCodeReviewLabel(/* value= */ -2, ps1, admin.id())));

    changeKindCreator.updateChange(change, ChangeKind.TRIVIAL_REBASE, testRepo, admin, project);
    PatchSet.Id ps2 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), /* id= */ 2);
    assertFalse(
        queryBuilder
            .parse("changekind:no-code-change")
            .asMatchable()
            .match(contextForCodeReviewLabel(/* value= */ -2, ps2, admin.id())));
  }

  @Test
  public void changeKindPredicate_trivialRebase() throws Exception {
    String change = changeKindCreator.createChange(ChangeKind.TRIVIAL_REBASE, testRepo, admin);
    changeKindCreator.updateChange(change, ChangeKind.TRIVIAL_REBASE, testRepo, admin, project);
    PatchSet.Id ps1 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), /* id= */ 1);
    assertTrue(
        queryBuilder
            .parse("changekind:trivial-rebase")
            .asMatchable()
            .match(contextForCodeReviewLabel(/* value= */ -2, ps1, admin.id())));

    changeKindCreator.updateChange(change, ChangeKind.REWORK, testRepo, admin, project);
    PatchSet.Id ps2 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), /* id= */ 2);
    assertFalse(
        queryBuilder
            .parse("changekind:trivial-rebase")
            .asMatchable()
            .match(contextForCodeReviewLabel(/* value= */ -2, ps2, admin.id())));
  }

  @Test
  public void changeKindPredicate_reworkAndNotRework() throws Exception {
    String change = changeKindCreator.createChange(ChangeKind.REWORK, testRepo, admin);
    changeKindCreator.updateChange(change, ChangeKind.REWORK, testRepo, admin, project);
    PatchSet.Id ps1 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), /* id= */ 1);
    assertTrue(
        queryBuilder
            .parse("changekind:rework")
            .asMatchable()
            .match(contextForCodeReviewLabel(/* value= */ -2, ps1, admin.id())));

    changeKindCreator.updateChange(change, ChangeKind.REWORK, testRepo, admin, project);
    PatchSet.Id ps2 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), /* id= */ 2);
    assertFalse(
        queryBuilder
            .parse("-changekind:rework")
            .asMatchable()
            .match(contextForCodeReviewLabel(/* value= */ -2, ps2, admin.id())));
  }

  @Test
  public void changeKindPredicate_invalidValue() throws Exception {
    QueryParseException thrown =
        assertThrows(QueryParseException.class, () -> queryBuilder.parse("changekind:INVALID"));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "INVALID is not a valid value for operator 'changekind'. Valid values:"
                + " MERGE_FIRST_PARENT_UPDATE, NO_CHANGE, NO_CODE_CHANGE, REWORK, TRIVIAL_REBASE");
  }

  @Test
  public void changeIsPredicates_matches() throws Exception {
    ApprovalContext context = contextForCodeReviewLabel(2);

    assertTrue(queryBuilder.parse("changeis:open").asMatchable().match(context));
    assertFalse(queryBuilder.parse("changeis:wip").asMatchable().match(context));
  }

  @Test
  public void uploaderInPredicate() throws Exception {
    String administratorsUUID = gApi.groups().query("name:Administrators").get().get(0).id;

    PushOneCommit.Result pushResult = createChange();
    String changeCreatedByAdmin = pushResult.getChangeId();
    approve(changeCreatedByAdmin);
    // PS2 uploaded by admin
    amendChange(changeCreatedByAdmin);
    // PS3 uploaded by user
    amendChangeWithUploader(pushResult, project, user).assertOkStatus();

    // can copy approval from patchset 1 -> 2
    assertTrue(
        queryBuilder
            .parse("uploaderin:" + administratorsUUID)
            .asMatchable()
            .match(
                contextForCodeReviewLabel(
                    /* value= */ 2,
                    PatchSet.id(pushResult.getChange().getId(), /* id= */ 1),
                    admin.id())));
    // can not copy approval from patchset 2 -> 3
    assertFalse(
        queryBuilder
            .parse("uploaderin:" + administratorsUUID)
            .asMatchable()
            .match(
                contextForCodeReviewLabel(
                    /* value= */ 2,
                    PatchSet.id(pushResult.getChange().getId(), /* id= */ 2),
                    admin.id())));
  }

  @Test
  public void approverInPredicate() throws Exception {
    String administratorsUUID = gApi.groups().query("name:Administrators").get().get(0).id;

    PushOneCommit.Result pushResult = createChange();
    amendChange(pushResult.getChangeId());
    amendChange(pushResult.getChangeId());
    // can copy approval from patchset 1 -> 2
    assertTrue(
        queryBuilder
            .parse("approverin:" + administratorsUUID)
            .asMatchable()
            .match(
                contextForCodeReviewLabel(
                    /* value= */ 2,
                    PatchSet.id(pushResult.getChange().getId(), /* id= */ 1),
                    admin.id())));
    // can not copy approval from patchset 2 -> 3
    assertFalse(
        queryBuilder
            .parse("approverin:" + administratorsUUID)
            .asMatchable()
            .match(
                contextForCodeReviewLabel(
                    /* value= */ 2,
                    PatchSet.id(pushResult.getChange().getId(), /* id= */ 2),
                    user.id())));
  }

  @Test
  public void userInPredicate_groupNotFound() {
    QueryParseException thrown =
        assertThrows(
            QueryParseException.class,
            () ->
                queryBuilder
                    .parse("uploaderin:foobar")
                    .asMatchable()
                    .match(contextForCodeReviewLabel(/* value= */ 2)));
    assertThat(thrown).hasMessageThat().contains("Group foobar not found");
  }

  @Test
  public void hasChangedFilesPredicate() throws Exception {
    Change.Id changeId =
        changeOperations.newChange().project(project).file("file").content("content").create();
    changeOperations.change(changeId).newPatchset().file("file").content("new content").create();

    // can copy approval from patch-set 1 -> 2
    assertTrue(
        queryBuilder
            .parse("has:unchanged-files")
            .asMatchable()
            .match(
                contextForCodeReviewLabel(
                    /* value= */ 2, PatchSet.id(changeId, /* id= */ 1), admin.id())));
    changeOperations.change(changeId).newPatchset().file("file").delete().create();

    // can not copy approval from patch-set 2 -> 3
    assertFalse(
        queryBuilder
            .parse("has:unchanged-files")
            .asMatchable()
            .match(
                contextForCodeReviewLabel(
                    /* value= */ 2, PatchSet.id(changeId, /* id= */ 2), admin.id())));
  }

  @Test
  public void hasChangedFilesPredicate_unsupportedOperator() {
    QueryParseException thrown =
        assertThrows(
            QueryParseException.class,
            () ->
                queryBuilder
                    .parse("has:invalid")
                    .asMatchable()
                    .match(contextForCodeReviewLabel(/* value= */ 2)));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "'invalid' is not a valid value for operator 'has'."
                + " The only valid value is 'unchanged-files'.");
  }

  @Test
  public void invalidQuery() throws Exception {
    QueryParseException thrown =
        assertThrows(QueryParseException.class, () -> queryBuilder.parse("INVALID"));
    assertThat(thrown).hasMessageThat().contains("Unsupported query: INVALID");
  }

  private static class CustomAlwaysTruePredicate extends OperatorPredicate<ApprovalContext>
      implements UserInOperandFactory, Matchable<ApprovalContext> {

    public static final String OPERAND = "true-predicate";

    public CustomAlwaysTruePredicate() {
      super("uploaderin", OPERAND);
    }

    @Override
    public boolean match(ApprovalContext object) {
      return true;
    }

    @Override
    public int getCost() {
      return 0;
    }

    @Override
    public Predicate<ApprovalContext> create(UserInPredicate.Field field)
        throws QueryParseException {
      return this;
    }
  }

  @Test
  public void pluginUploaderInQuery() throws Exception {
    try (Registration registration =
        extensionRegistry
            .newRegistration()
            .add(new CustomAlwaysTruePredicate(), CustomAlwaysTruePredicate.OPERAND)) {
      assertTrue(
          queryBuilder
              .parse(
                  String.format(
                      "uploaderin:%s_%s",
                      CustomAlwaysTruePredicate.OPERAND, ExtensionRegistry.PLUGIN_NAME))
              .asMatchable()
              .match(contextForCodeReviewLabel(2)));
    }
  }

  private ApprovalContext contextForCodeReviewLabel(int value) throws Exception {
    PushOneCommit.Result result = createChange();
    amendChange(result.getChangeId());
    PatchSet.Id psId = PatchSet.id(result.getChange().getId(), 1);
    return contextForCodeReviewLabel(value, psId, admin.id());
  }

  private ApprovalContext contextForCodeReviewLabel(
      int value, PatchSet.Id psId, Account.Id approver) throws Exception {
    ChangeNotes changeNotes = changeNotesFactory.create(project, psId.changeId());
    PatchSet.Id newPsId = PatchSet.id(psId.changeId(), psId.get() + 1);
    ChangeKind changeKind =
        changeKindCache.getChangeKind(
            changeNotes.getChange(), changeNotes.getPatchSets().get(newPsId));
    try (Repository repo = repoManager.openRepository(project);
        ObjectInserter ins = repo.newObjectInserter();
        ObjectReader reader = ins.newReader();
        RevWalk rw = new RevWalk(reader);
        RepoView repoView = new RepoView(repo, rw, ins)) {
      return ApprovalContext.create(
          changeDataFactory.create(changeNotes),
          psId,
          approver,
          projectCache.get(project).get().getLabelTypes().byLabel("Code-Review").get(),
          (short) value,
          changeNotes.getPatchSets().get(newPsId),
          changeKind,
          /* isMerge= */ false,
          repoView);
    }
  }
}
