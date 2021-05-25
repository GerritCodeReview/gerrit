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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.change.ChangeKindCreator;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.query.approval.ApprovalContext;
import com.google.gerrit.server.query.approval.ApprovalQueryBuilder;
import com.google.inject.Inject;
import java.util.Date;
import org.junit.Test;

public class ApprovalQueryIT extends AbstractDaemonTest {
  @Inject private ApprovalQueryBuilder queryBuilder;
  @Inject private ChangeKindCreator changeKindCreator;

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
  }

  @Test
  public void changeKindPredicate_noCodeChange() throws Exception {
    String change = changeKindCreator.createChange(ChangeKind.NO_CODE_CHANGE, testRepo, admin);
    changeKindCreator.updateChange(change, ChangeKind.NO_CODE_CHANGE, testRepo, admin, project);
    PatchSet.Id ps1 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), 1);
    assertTrue(
        queryBuilder
            .parse("changekind:no-code-change")
            .asMatchable()
            .match(contextForCodeReviewLabel(-2, ps1)));

    changeKindCreator.updateChange(change, ChangeKind.TRIVIAL_REBASE, testRepo, admin, project);
    PatchSet.Id ps2 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), 2);
    assertFalse(
        queryBuilder
            .parse("changekind:no-code-change")
            .asMatchable()
            .match(contextForCodeReviewLabel(-2, ps2)));
  }

  @Test
  public void changeKindPredicate_trivialRebase() throws Exception {
    String change = changeKindCreator.createChange(ChangeKind.TRIVIAL_REBASE, testRepo, admin);
    changeKindCreator.updateChange(change, ChangeKind.TRIVIAL_REBASE, testRepo, admin, project);
    PatchSet.Id ps1 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), 1);
    assertTrue(
        queryBuilder
            .parse("changekind:trivial-rebase")
            .asMatchable()
            .match(contextForCodeReviewLabel(-2, ps1)));

    changeKindCreator.updateChange(change, ChangeKind.REWORK, testRepo, admin, project);
    PatchSet.Id ps2 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), 2);
    assertFalse(
        queryBuilder
            .parse("changekind:trivial-rebase")
            .asMatchable()
            .match(contextForCodeReviewLabel(-2, ps2)));
  }

  @Test
  public void changeKindPredicate_reworkAndNotRework() throws Exception {
    String change = changeKindCreator.createChange(ChangeKind.REWORK, testRepo, admin);
    changeKindCreator.updateChange(change, ChangeKind.REWORK, testRepo, admin, project);
    PatchSet.Id ps1 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), 1);
    assertTrue(
        queryBuilder
            .parse("changekind:rework")
            .asMatchable()
            .match(contextForCodeReviewLabel(-2, ps1)));

    changeKindCreator.updateChange(change, ChangeKind.REWORK, testRepo, admin, project);
    PatchSet.Id ps2 = PatchSet.id(Change.id(gApi.changes().id(change).get()._number), 2);
    assertFalse(
        queryBuilder
            .parse("-changekind:rework")
            .asMatchable()
            .match(contextForCodeReviewLabel(-2, ps2)));
  }

  private ApprovalContext contextForCodeReviewLabel(int value) {
    PatchSet.Id psId = PatchSet.id(Change.id(1), 1);
    return contextForCodeReviewLabel(value, psId);
  }

  private ApprovalContext contextForCodeReviewLabel(int value, PatchSet.Id psId) {
    PatchSetApproval approval =
        PatchSetApproval.builder()
            .postSubmit(false)
            .granted(new Date())
            .key(PatchSetApproval.key(psId, admin.id(), LabelId.create("Code-Review")))
            .value(value)
            .build();
    return ApprovalContext.create(project, approval, PatchSet.id(psId.changeId(), psId.get() + 1));
  }
}
