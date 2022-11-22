// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.approval.ApprovalCopier;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import org.junit.Test;

public class CopiedApprovalsInChangeMessageIT extends AbstractDaemonTest {
  @Inject private ApprovalsUtil approvalsUtil;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void cannotFormatWithNullApprovalCopierResult() throws Exception {
    LabelTypes labelTypes = projectCache.get(project).get().getLabelTypes();
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () ->
                approvalsUtil.formatApprovalCopierResult(
                    /* approvalCopierResult= */ null, labelTypes));
    assertThat(exception).hasMessageThat().isEqualTo("approvalCopierResult");
  }

  @Test
  public void cannotFormatWithNullLabelTypes() throws Exception {
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(), /* outdatedApprovals= */ ImmutableSet.of());
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () ->
                approvalsUtil.formatApprovalCopierResult(
                    approvalCopierResult, /* labelTypes= */ null));
    assertThat(exception).hasMessageThat().isEqualTo("labelTypes");
  }

  @Test
  public void format_noCopiedApprovals_noOutdatedApprovals() throws Exception {
    LabelTypes labelTypes = projectCache.get(project).get().getLabelTypes();
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(), /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .isEmpty();
  }

  @Test
  public void formatCopiedApproval_missingLabelType() throws Exception {
    LabelTypes labelTypes = new LabelTypes(ImmutableList.of());
    PatchSetApproval patchSetApproval = createPatchSetApproval(admin, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue("Copied Votes:\n* Code-Review+1 (label type is missing)\n");
  }

  @Test
  public void formatOutdatedApproval_missingLabelType() throws Exception {
    LabelTypes labelTypes = new LabelTypes(ImmutableList.of());
    PatchSetApproval patchSetApproval = createPatchSetApproval(admin, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(),
            /* outdatedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())));
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue("Outdated Votes:\n* Code-Review+1 (label type is missing)\n");
  }

  @Test
  public void formatCopiedApproval_noCopyCondition() throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(/* labelName= */ "Code-Review", /* copyCondition= */ null)));
    PatchSetApproval patchSetApproval = createPatchSetApproval(admin, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue("Copied Votes:\n* Code-Review+1\n");
  }

  @Test
  public void formatOutdatedApproval_noCopyCondition() throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(/* labelName= */ "Code-Review", /* copyCondition= */ null)));
    PatchSetApproval patchSetApproval = createPatchSetApproval(admin, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(),
            /* outdatedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())));
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue("Outdated Votes:\n* Code-Review+1\n");
  }

  @Test
  public void formatCopiedApproval_withCopyCondition_noUserInPredicate() throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review", /* copyCondition= */ "is:MIN OR is:MAX")));
    PatchSetApproval patchSetApproval = createPatchSetApproval(admin, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval,
                    /* passingAtoms= */ ImmutableSet.of("is:MIN"),
                    /* failingAtoms= */ ImmutableSet.of("is:MAX"))),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue("Copied Votes:\n* Code-Review+1 (copy condition: \"**is:MIN** OR is:MAX\")\n");
  }

  @Test
  public void formatOutdatedApproval_withCopyCondition_noUserInPredicate() throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ "changekind:TRIVIAL_REBASE is:MAX")));
    PatchSetApproval patchSetApproval = createPatchSetApproval(admin, "Code-Review", 2);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(),
            /* outdatedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval,
                    /* passingAtoms= */ ImmutableSet.of("changekind:TRIVIAL_REBASE"),
                    /* failingAtoms= */ ImmutableSet.of("is:MAX"))));
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            "Outdated Votes:\n* Code-Review+2 (copy condition:"
                + " \"**changekind:TRIVIAL_REBASE** is:MAX\")\n");
  }

  @Test
  public void formatCopiedApproval_withNonParseableCopyCondition_noUserInPredicate()
      throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review", /* copyCondition= */ "foo bar baz")));
    PatchSetApproval patchSetApproval = createPatchSetApproval(admin, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            "Copied Votes:\n* Code-Review+1 (non-parseable copy condition: \"foo bar baz\")\n");
  }

  @Test
  public void formatOutdatedApproval_withNonParseableCopyCondition_noUserInPredicate()
      throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review", /* copyCondition= */ "foo bar baz")));
    PatchSetApproval patchSetApproval = createPatchSetApproval(admin, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(),
            /* outdatedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())));
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            "Outdated Votes:\n* Code-Review+1 (non-parseable copy condition: \"foo bar baz\")\n");
  }

  @Test
  public void formatCopiedApproval_withCopyCondition_withUserInPredicate() throws Exception {
    String groupUuid =
        groupCache.get(AccountGroup.nameKey("Administrators")).get().getGroupUUID().get();
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:MAX approverin:%s)", groupUuid))));
    PatchSetApproval patchSetApproval = createPatchSetApproval(admin, "Code-Review", 2);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval,
                    /* passingAtoms= */ ImmutableSet.of(
                        "is:MAX", String.format("approverin:%s", groupUuid)),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN"))),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            String.format(
                "Copied Votes:\n"
                    + "* Code-Review+2 by %s"
                    + " (copy condition: \"is:MIN OR (**is:MAX** **approverin:%s**)\")\n",
                AccountTemplateUtil.getAccountTemplate(admin.id()), groupUuid));
  }

  @Test
  public void formatOutdatedpproval_withCopyCondition_withUserInPredicate() throws Exception {
    String groupUuid =
        groupCache.get(AccountGroup.nameKey("Administrators")).get().getGroupUUID().get();
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:MAX approverin:%s)", groupUuid))));
    PatchSetApproval patchSetApproval = createPatchSetApproval(admin, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(),
            /* outdatedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval,
                    /* passingAtoms= */ ImmutableSet.of(String.format("approverin:%s", groupUuid)),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN", "is:MAX"))));
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            String.format(
                "Outdated Votes:\n"
                    + "* Code-Review+1 by %s (copy condition: \"is:MIN"
                    + " OR (is:MAX **approverin:%s**)\")\n",
                AccountTemplateUtil.getAccountTemplate(admin.id()), groupUuid));
  }

  @Test
  public void formatCopiedApproval_withCopyCondition_withUserInPredicateThatContainNonVisibleGroup()
      throws Exception {
    String groupUuid =
        groupCache.get(AccountGroup.nameKey("Administrators")).get().getGroupUUID().get();
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:MAX approverin:%s)", groupUuid))));
    PatchSetApproval patchSetApproval = createPatchSetApproval(admin, "Code-Review", 2);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval,
                    /* passingAtoms= */ ImmutableSet.of(
                        "is:MAX", String.format("approverin:%s", groupUuid)),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN"))),
            /* outdatedApprovals= */ ImmutableSet.of());

    // Set 'user' as the current user in the request scope.
    // 'user' cannot see the Administrators group that is used in the copy condition.
    // Parsing the copy condition should still succeed since ApprovalsUtil should use the internal
    // user that can see everything when parsing the copy condition.
    requestScopeOperations.setApiUser(user.id());

    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            String.format(
                "Copied Votes:\n"
                    + "* Code-Review+2 by %s"
                    + " (copy condition: \"is:MIN OR (**is:MAX** **approverin:%s**)\")\n",
                AccountTemplateUtil.getAccountTemplate(admin.id()), groupUuid));
  }

  @Test
  public void
      formatOutdatedpproval_withCopyCondition_withUserInPredicateThatContainNonVisibleGroup()
          throws Exception {
    String groupUuid =
        groupCache.get(AccountGroup.nameKey("Administrators")).get().getGroupUUID().get();
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:MAX approverin:%s)", groupUuid))));
    PatchSetApproval patchSetApproval = createPatchSetApproval(admin, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(),
            /* outdatedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval,
                    /* passingAtoms= */ ImmutableSet.of(String.format("approverin:%s", groupUuid)),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN", "is:MAX"))));

    // Set 'user' as the current user in the request scope.
    // 'user' cannot see the Administrators group that is used in the copy condition.
    // Parsing the copy condition should still succeed since ApprovalsUtil should use the internal
    // user that can see everything when parsing the copy condition.
    requestScopeOperations.setApiUser(user.id());

    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            String.format(
                "Outdated Votes:\n"
                    + "* Code-Review+1 by %s (copy condition: \"is:MIN"
                    + " OR (is:MAX **approverin:%s**)\")\n",
                AccountTemplateUtil.getAccountTemplate(admin.id()), groupUuid));
  }

  @Test
  public void formatCopiedApproval_withNonParseableCopyCondition_withUserInPredicate()
      throws Exception {
    String groupUuid =
        groupCache.get(AccountGroup.nameKey("Administrators")).get().getGroupUUID().get();
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:MAX approverin:%s) OR foo bar baz", groupUuid))));
    PatchSetApproval patchSetApproval = createPatchSetApproval(admin, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            String.format(
                "Copied Votes:\n"
                    + "* Code-Review+1 (non-parseable copy condition: \"is:MIN"
                    + " OR (is:MAX approverin:%s) OR foo bar baz\")\n",
                groupUuid));
  }

  @Test
  public void formatOutdatedApproval_withNonParseableCopyCondition_withUserInPredicate()
      throws Exception {
    String groupUuid =
        groupCache.get(AccountGroup.nameKey("Administrators")).get().getGroupUUID().get();
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:MAX approverin:%s) OR foo bar baz", groupUuid))));
    PatchSetApproval patchSetApproval = createPatchSetApproval(admin, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(),
            /* outdatedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())));
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            String.format(
                "Outdated Votes:\n"
                    + "* Code-Review+1 (non-parseable copy condition: \"is:MIN"
                    + " OR (is:MAX approverin:%s) OR foo bar baz\")\n",
                groupUuid));
  }

  @Test
  public void formatMultipleApprovals_sameVote_missingLabelType() throws Exception {
    LabelTypes labelTypes = new LabelTypes(ImmutableList.of());
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 1);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of()),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue("Copied Votes:\n* Code-Review+1 (label type is missing)\n");
  }

  @Test
  public void formatMultipleApprovals_differentLabels_missingLabelType() throws Exception {
    LabelTypes labelTypes = new LabelTypes(ImmutableList.of());
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 1);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Verified", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of()),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            "Copied Votes:\n"
                + "* Code-Review+1 (label type is missing)\n"
                + "* Verified+1 (label type is missing)\n");
  }

  @Test
  public void formatMultipleApprovals_differentValues_missingLabelType() throws Exception {
    LabelTypes labelTypes = new LabelTypes(ImmutableList.of());
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 2);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of()),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue("Copied Votes:\n* Code-Review+1, Code-Review+2 (label type is missing)\n");
  }

  @Test
  public void formatMultipleApprovals_sameVote_noCopyCondition() throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(/* labelName= */ "Code-Review", /* copyCondition= */ null)));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 1);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of()),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue("Copied Votes:\n* Code-Review+1\n");
  }

  @Test
  public void formatMultipleApprovals_differentLabel_noCopyCondition() throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(/* labelName= */ "Code-Review", /* copyCondition= */ null),
                createLabelType(/* labelName= */ "Verified", /* copyCondition= */ null)));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 1);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Verified", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of()),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue("Copied Votes:\n* Code-Review+1\n* Verified+1\n");
  }

  @Test
  public void formatMultipleApprovals_differentValue_noCopyCondition() throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(/* labelName= */ "Code-Review", /* copyCondition= */ null)));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 2);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of()),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue("Copied Votes:\n* Code-Review+1, Code-Review+2\n");
  }

  @Test
  public void formatMultipleApprovals_sameVote_withCopyCondition_noUserInPredicate()
      throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review", /* copyCondition= */ "is:MIN OR is:MAX")));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 2);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Code-Review", 2);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of("is:MAX"),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN")),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of("is:MAX"),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN"))),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue("Copied Votes:\n* Code-Review+2 (copy condition: \"is:MIN OR **is:MAX**\")\n");
  }

  @Test
  public void formatMultipleApprovals_differentLabel_withCopyCondition_noUserInPredicate()
      throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review", /* copyCondition= */ "is:MIN OR is:MAX"),
                createLabelType(
                    /* labelName= */ "Verified", /* copyCondition= */ "is:MIN OR is:MAX")));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 2);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Verified", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of("is:MAX"),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN")),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of("is:MAX"),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN"))),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            "Copied Votes:\n"
                + "* Code-Review+2 (copy condition: \"is:MIN OR **is:MAX**\")\n"
                + "* Verified+1 (copy condition: \"is:MIN OR **is:MAX**\")\n");
  }

  @Test
  public void
      formatMultipleApprovals_differentValue_withCopyCondition_noUserInPredicate_samePassingAtoms()
          throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review", /* copyCondition= */ "changekind:REWORK")));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 2);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of("changekind:REWORK"),
                    /* failingAtoms= */ ImmutableSet.of()),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of("changekind:REWORK"),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            "Copied Votes:\n"
                + "* Code-Review+1, Code-Review+2 (copy condition: \"**changekind:REWORK**\")\n");
  }

  @Test
  public void
      formatMultipleApprovals_differentValue_withCopyCondition_noUserInPredicate_differentPassingAtoms()
          throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review", /* copyCondition= */ "is:1 OR is:2")));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 2);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of("is:2"),
                    /* failingAtoms= */ ImmutableSet.of("is:1")),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of("is:1"),
                    /* failingAtoms= */ ImmutableSet.of("is:2"))),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            "Copied Votes:\n"
                + "* Code-Review+1 (copy condition: \"**is:1** OR is:2\")\n"
                + "* Code-Review+2 (copy condition: \"is:1 OR **is:2**\")\n");
  }

  @Test
  public void formatMultipleApprovals_sameVote_withNonParseableCopyCondition_noUserInPredicate()
      throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review", /* copyCondition= */ "foo bar baz")));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 1);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of()),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            "Copied Votes:\n* Code-Review+1 (non-parseable copy condition: \"foo bar baz\")\n");
  }

  @Test
  public void
      formatMultipleApprovals_differentLabel_withNonParseableCopyCondition_noUserInPredicate()
          throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(/* labelName= */ "Code-Review", /* copyCondition= */ "foo bar baz"),
                createLabelType(/* labelName= */ "Verified", /* copyCondition= */ "foo bar baz")));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 1);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Verified", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of()),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            "Copied Votes:\n"
                + "* Code-Review+1 (non-parseable copy condition: \"foo bar baz\")\n"
                + "* Verified+1 (non-parseable copy condition: \"foo bar baz\")\n");
  }

  @Test
  public void
      formatMultipleApprovals_differentValue_withNonParseableCopyCondition_noUserInPredicate()
          throws Exception {
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review", /* copyCondition= */ "foo bar baz")));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 2);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of()),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            "Copied Votes:\n"
                + "* Code-Review+1, Code-Review+2"
                + " (non-parseable copy condition: \"foo bar baz\")\n");
  }

  @Test
  public void
      formatMultipleApprovals_sameVote_withCopyCondition_withUserInPredicate_samePassingAtoms()
          throws Exception {
    String groupUuid =
        groupCache.get(AccountGroup.nameKey("Administrators")).get().getGroupUUID().get();
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:MAX approverin:%s)", groupUuid))));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(user, "Code-Review", 2);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(admin, "Code-Review", 2);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(
                        "is:MAX", String.format("approverin:%s", groupUuid)),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN")),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(
                        "is:MAX", String.format("approverin:%s", groupUuid)),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN"))),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            String.format(
                "Copied Votes:\n"
                    + "* Code-Review+2 by %s, %s"
                    + " (copy condition: \"is:MIN OR (**is:MAX** **approverin:%s**)\")\n",
                AccountTemplateUtil.getAccountTemplate(admin.id()),
                AccountTemplateUtil.getAccountTemplate(user.id()),
                groupUuid));
  }

  @Test
  public void
      formatMultipleApprovals_sameVote_withCopyCondition_withUserInPredicate_differentPassingAtoms()
          throws Exception {
    String administratorsGroupUuid =
        groupCache.get(AccountGroup.nameKey("Administrators")).get().getGroupUUID().get();
    String registeredUsersGroupUuid = SystemGroupBackend.REGISTERED_USERS.get();
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:MAX approverin:%s) OR (is:MAX approverin:%s)",
                        administratorsGroupUuid, registeredUsersGroupUuid))));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(user, "Code-Review", 2);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(admin, "Code-Review", 2);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(
                        "is:MAX", String.format("approverin:%s", registeredUsersGroupUuid)),
                    /* failingAtoms= */ ImmutableSet.of(
                        "is:MIN", String.format("approverin:%s", administratorsGroupUuid))),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(
                        "is:MAX",
                        String.format("approverin:%s", administratorsGroupUuid),
                        String.format("approverin:%s", registeredUsersGroupUuid)),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN"))),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            String.format(
                "Copied Votes:\n"
                    + "* Code-Review+2 by %s"
                    + " (copy condition: \"is:MIN OR (**is:MAX** **approverin:%s**)"
                    + " OR (**is:MAX** **approverin:%s**)\")\n"
                    + "* Code-Review+2 by %s"
                    + " (copy condition: \"is:MIN OR (**is:MAX** approverin:%s)"
                    + " OR (**is:MAX** **approverin:%s**)\")\n",
                AccountTemplateUtil.getAccountTemplate(admin.id()),
                administratorsGroupUuid,
                registeredUsersGroupUuid,
                AccountTemplateUtil.getAccountTemplate(user.id()),
                administratorsGroupUuid,
                registeredUsersGroupUuid));
  }

  @Test
  public void formatMultipleApprovals_differentLabel_withCopyCondition_withUserInPredicate()
      throws Exception {
    String groupUuid =
        groupCache.get(AccountGroup.nameKey("Administrators")).get().getGroupUUID().get();
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:MAX approverin:%s)", groupUuid)),
                createLabelType(
                    /* labelName= */ "Verified",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:MAX approverin:%s)", groupUuid))));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(user, "Code-Review", -2);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(admin, "Verified", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of("is:MIN"),
                    /* failingAtoms= */ ImmutableSet.of(
                        "is:MAX", String.format("approverin:%s", groupUuid))),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(
                        "is:MAX", String.format("approverin:%s", groupUuid)),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN"))),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            String.format(
                "Copied Votes:\n"
                    + "* Code-Review-2 by %s (copy condition: \"**is:MIN**"
                    + " OR (is:MAX approverin:%s)\")\n"
                    + "* Verified+1 by %s (copy condition: \"is:MIN"
                    + " OR (**is:MAX** **approverin:%s**)\")\n",
                AccountTemplateUtil.getAccountTemplate(user.id()),
                groupUuid,
                AccountTemplateUtil.getAccountTemplate(admin.id()),
                groupUuid));
  }

  @Test
  public void
      formatMultipleApprovals_differentValue_withCopyCondition_withUserInPredicate_samePassingAtoms()
          throws Exception {
    String groupUuid =
        groupCache.get(AccountGroup.nameKey("Administrators")).get().getGroupUUID().get();
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:ANY approverin:%s)", groupUuid))));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 2);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(
                        "is:ANY", String.format("approverin:%s", groupUuid)),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN")),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(
                        "is:ANY", String.format("approverin:%s", groupUuid)),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN"))),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            String.format(
                "Copied Votes:\n"
                    + "* Code-Review+1 by %s, Code-Review+2 by %s"
                    + " (copy condition: \"is:MIN OR (**is:ANY** **approverin:%s**)\")\n",
                AccountTemplateUtil.getAccountTemplate(user.id()),
                AccountTemplateUtil.getAccountTemplate(admin.id()),
                groupUuid));
  }

  @Test
  public void
      formatMultipleApprovals_differentValue_withCopyCondition_withUserInPredicate_differentPassingAtoms()
          throws Exception {
    String groupUuid =
        groupCache.get(AccountGroup.nameKey("Administrators")).get().getGroupUUID().get();
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:1 approverin:%s) OR (is:2 approverin:%s)",
                        groupUuid, groupUuid))));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 2);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(
                        "is:2", String.format("approverin:%s", groupUuid)),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN", "is:1")),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(
                        "is:1", String.format("approverin:%s", groupUuid)),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN", "is:2"))),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            String.format(
                "Copied Votes:\n"
                    + "* Code-Review+1 by %s"
                    + " (copy condition: \"is:MIN OR (**is:1** **approverin:%s**)"
                    + " OR (is:2 **approverin:%s**)\")\n"
                    + "* Code-Review+2 by %s"
                    + " (copy condition: \"is:MIN OR (is:1 **approverin:%s**)"
                    + " OR (**is:2** **approverin:%s**)\")\n",
                AccountTemplateUtil.getAccountTemplate(user.id()),
                groupUuid,
                groupUuid,
                AccountTemplateUtil.getAccountTemplate(admin.id()),
                groupUuid,
                groupUuid));
  }

  @Test
  public void formatMultipleApprovals_differentAndSameValue_withCopyCondition_withUserInPredicate()
      throws Exception {
    TestAccount user2 = accountCreator.user2();
    String groupUuid = SystemGroupBackend.REGISTERED_USERS.get();
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:ANY approverin:%s)", groupUuid))));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 2);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user2, "Code-Review", 1);
    PatchSetApproval patchSetApproval3 = createPatchSetApproval(user, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(
                        "is:ANY", String.format("approverin:%s", groupUuid)),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN")),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(
                        "is:ANY", String.format("approverin:%s", groupUuid)),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN")),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval3,
                    /* passingAtoms= */ ImmutableSet.of(
                        "is:ANY", String.format("approverin:%s", groupUuid)),
                    /* failingAtoms= */ ImmutableSet.of("is:MIN"))),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            String.format(
                "Copied Votes:\n"
                    + "* Code-Review+1 by %s, %s, Code-Review+2 by %s"
                    + " (copy condition: \"is:MIN OR (**is:ANY** **approverin:%s**)\")\n",
                AccountTemplateUtil.getAccountTemplate(user.id()),
                AccountTemplateUtil.getAccountTemplate(user2.id()),
                AccountTemplateUtil.getAccountTemplate(admin.id()),
                groupUuid));
  }

  @Test
  public void formatMultipleApprovals_sameVote_withNonParseableCopyCondition_withUserInPredicate()
      throws Exception {
    String groupUuid =
        groupCache.get(AccountGroup.nameKey("Administrators")).get().getGroupUUID().get();
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:MAX approverin:%s) OR foo bar baz", groupUuid))));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 1);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of()),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            String.format(
                "Copied Votes:\n"
                    + "* Code-Review+1 (non-parseable copy condition: \"is:MIN"
                    + " OR (is:MAX approverin:%s) OR foo bar baz\")\n",
                groupUuid));
  }

  @Test
  public void
      formatMultipleApprovals_differentLabel_withNonParseableCopyCondition_withUserInPredicate()
          throws Exception {
    String groupUuid =
        groupCache.get(AccountGroup.nameKey("Administrators")).get().getGroupUUID().get();
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:MAX approverin:%s) OR foo bar baz", groupUuid)),
                createLabelType(
                    /* labelName= */ "Verified",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:MAX approverin:%s) OR foo bar baz", groupUuid))));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 1);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Verified", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of()),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            String.format(
                "Copied Votes:\n"
                    + "* Code-Review+1 (non-parseable copy condition: \"is:MIN"
                    + " OR (is:MAX approverin:%s) OR foo bar baz\")\n"
                    + "* Verified+1 (non-parseable copy condition: \"is:MIN"
                    + " OR (is:MAX approverin:%s) OR foo bar baz\")\n",
                groupUuid, groupUuid));
  }

  @Test
  public void
      formatMultipleApprovals_differentValue_withNonParseableCopyCondition_withUserInPredicate()
          throws Exception {
    String groupUuid =
        groupCache.get(AccountGroup.nameKey("Administrators")).get().getGroupUUID().get();
    LabelTypes labelTypes =
        new LabelTypes(
            ImmutableList.of(
                createLabelType(
                    /* labelName= */ "Code-Review",
                    /* copyCondition= */ String.format(
                        "is:MIN OR (is:MAX approverin:%s) OR foo bar baz", groupUuid))));
    PatchSetApproval patchSetApproval1 = createPatchSetApproval(admin, "Code-Review", 2);
    PatchSetApproval patchSetApproval2 = createPatchSetApproval(user, "Code-Review", 1);
    ApprovalCopier.Result approvalCopierResult =
        ApprovalCopier.Result.create(
            /* copiedApprovals= */ ImmutableSet.of(
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval1,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of()),
                ApprovalCopier.Result.ApprovalData.create(
                    patchSetApproval2,
                    /* passingAtoms= */ ImmutableSet.of(),
                    /* failingAtoms= */ ImmutableSet.of())),
            /* outdatedApprovals= */ ImmutableSet.of());
    assertThat(approvalsUtil.formatApprovalCopierResult(approvalCopierResult, labelTypes))
        .hasValue(
            String.format(
                "Copied Votes:\n"
                    + "* Code-Review+1, Code-Review+2 (non-parseable copy condition: \"is:MIN"
                    + " OR (is:MAX approverin:%s) OR foo bar baz\")\n",
                groupUuid));
  }

  @Test
  public void copiedAndOutdatedApprovalsAreIncludedInChangeMessageOnPatchSetCreationByPush()
      throws Exception {
    // Add Verified label without copy condition.
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder verified =
          labelBuilder(
              LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
      u.getConfig().upsertLabelType(verified.build());
      u.save();
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.verified().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    PushOneCommit.Result r = createChange();

    // Vote Code-Review-2 (sticky because it's a veto vote and the Code-Review label has "is:MIN" as
    // part of its copy condition)
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.reject());

    // Vote Verified+1 (not sticky because the Verified label has no copy condition)
    gApi.changes().id(r.getChangeId()).current().review(new ReviewInput().label("Verified", 1));

    amendChange(r.getChangeId()).assertOkStatus();

    ChangeInfo change = change(r).get();
    assertThat(Iterables.getLast(change.messages).message)
        .isEqualTo(
            "Uploaded patch set 2.\n"
                + "\n"
                + "Copied Votes:\n"
                + "* Code-Review-2 (copy condition: \"changekind:NO_CHANGE"
                + " OR changekind:TRIVIAL_REBASE OR **is:MIN**\")\n"
                + "\n"
                + "Outdated Votes:\n"
                + "* Verified+1\n");
  }

  @Test
  public void
      copiedAndOutdatedApprovalsAreIncludedInChangeMessageOnPatchSetCreationByPush_withReviewMessage()
          throws Exception {
    // Add Verified label without copy condition.
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder verified =
          labelBuilder(
              LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
      u.getConfig().upsertLabelType(verified.build());
      u.save();
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.verified().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    PushOneCommit.Result r = createChange();

    // Vote Code-Review-2 (sticky because it's a veto vote and the Code-Review label has "is:MIN" as
    // part of its copy condition)
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.reject());

    // Vote Verified+1 (not sticky because the Verified label has no copy condition)
    gApi.changes().id(r.getChangeId()).current().review(new ReviewInput().label("Verified", 1));

    String reviewMessage = "Foo-Bar-Baz";

    amendChange(r.getChangeId(), "refs/for/master%m=" + reviewMessage, admin, testRepo)
        .assertOkStatus();

    ChangeInfo change = change(r).get();
    assertThat(Iterables.getLast(change.messages).message)
        .isEqualTo(
            "Uploaded patch set 2.\n"
                + "\n"
                + "Foo-Bar-Baz\n"
                + "\n"
                + "Copied Votes:\n"
                + "* Code-Review-2 (copy condition: \"changekind:NO_CHANGE"
                + " OR changekind:TRIVIAL_REBASE OR **is:MIN**\")\n"
                + "\n"
                + "Outdated Votes:\n"
                + "* Verified+1\n");
  }

  @Test
  public void copiedAndOutdatedApprovalsAreIncludedInChangeMessageOnPatchSetCreationByApi()
      throws Exception {
    // Add Verified label without copy condition.
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder verified =
          labelBuilder(
              LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
      u.getConfig().upsertLabelType(verified.build());
      u.save();
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.verified().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    PushOneCommit.Result r = createChange();

    // Vote Code-Review-2 (sticky because it's a veto vote and the Code-Review label has "is:MIN" as
    // part of its copy condition)
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.reject());

    // Vote Verified+1 (not sticky because the Verified label has no copy condition)
    gApi.changes().id(r.getChangeId()).current().review(new ReviewInput().label("Verified", 1));

    gApi.changes().id(r.getChangeId()).edit().modifyFile("a.txt", RawInputUtil.create("content"));
    gApi.changes().id(r.getChangeId()).edit().publish();

    ChangeInfo change = change(r).get();
    assertThat(Iterables.getLast(change.messages).message)
        .isEqualTo(
            "Patch Set 2: Published edit on patch set 1.\n"
                + "\n"
                + "Copied Votes:\n"
                + "* Code-Review-2 (copy condition: \"changekind:NO_CHANGE"
                + " OR changekind:TRIVIAL_REBASE OR **is:MIN**\")\n"
                + "\n"
                + "Outdated Votes:\n"
                + "* Verified+1\n");
  }

  private PatchSetApproval createPatchSetApproval(
      TestAccount testAccount, String label, int value) {
    return PatchSetApproval.builder()
        .key(
            PatchSetApproval.key(
                PatchSet.id(Change.id(1), 1), testAccount.id(), LabelId.create(label)))
        .value(value)
        .granted(TimeUtil.now())
        .build();
  }

  private LabelType createLabelType(String labelName, @Nullable String copyCondition) {
    LabelType.Builder labelTypeBuilder =
        LabelType.builder(
            labelName,
            ImmutableList.of(
                LabelValue.create((short) -2, "Vetoed"),
                LabelValue.create((short) -1, "Disliked"),
                LabelValue.create((short) 0, "No Vote"),
                LabelValue.create((short) 1, "Liked"),
                LabelValue.create((short) 2, "Approved")));
    if (copyCondition != null) {
      labelTypeBuilder.setCopyCondition(copyCondition);
    }
    return labelTypeBuilder.build();
  }
}
