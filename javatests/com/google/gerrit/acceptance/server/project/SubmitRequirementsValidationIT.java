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

package com.google.gerrit.acceptance.server.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.acceptance.GitUtil.pushHead;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.project.ProjectConfig;
import java.util.Locale;
import java.util.function.Consumer;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Test;

/**
 * Tests validating submit requirements on upload of {@code project.config} to {@code
 * refs/meta/config}.
 */
public class SubmitRequirementsValidationIT extends AbstractDaemonTest {
  @Test
  public void validSubmitRequirementIsAccepted_optionalParametersNotSet() throws Exception {
    fetchRefsMetaConfig();

    String submitRequirementName = "Code-Review";
    updateProjectConfig(
        projectConfig ->
            projectConfig.setString(
                ProjectConfig.SUBMIT_REQUIREMENT,
                /* subsection= */ submitRequirementName,
                /* name= */ ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
                /* value= */ "label:\"Code-Review=+2\""));

    PushResult r = pushRefsMetaConfig();
    assertOkStatus(r);
  }

  @Test
  public void validSubmitRequirementIsAccepted_allParametersSet() throws Exception {
    fetchRefsMetaConfig();

    String submitRequirementName = "Code-Review";
    updateProjectConfig(
        projectConfig -> {
          projectConfig.setString(
              ProjectConfig.SUBMIT_REQUIREMENT,
              /* subsection= */ submitRequirementName,
              /* name= */ ProjectConfig.KEY_SR_DESCRIPTION,
              /* value= */ "foo bar description");
          projectConfig.setString(
              ProjectConfig.SUBMIT_REQUIREMENT,
              /* subsection= */ submitRequirementName,
              /* name= */ ProjectConfig.KEY_SR_APPLICABILITY_EXPRESSION,
              /* value= */ "branch:refs/heads/master");
          projectConfig.setString(
              ProjectConfig.SUBMIT_REQUIREMENT,
              /* subsection= */ submitRequirementName,
              /* name= */ ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
              /* value= */ "label:\"Code-Review=+2\"");
          projectConfig.setString(
              ProjectConfig.SUBMIT_REQUIREMENT,
              /* subsection= */ submitRequirementName,
              /* name= */ ProjectConfig.KEY_SR_OVERRIDE_EXPRESSION,
              /* value= */ "label:\"override=+1\"");
          projectConfig.setBoolean(
              ProjectConfig.SUBMIT_REQUIREMENT,
              /* subsection= */ submitRequirementName,
              /* name= */ ProjectConfig.KEY_SR_OVERRIDE_IN_CHILD_PROJECTS,
              /* value= */ false);
        });

    PushResult r = pushRefsMetaConfig();
    assertOkStatus(r);
  }

  @Test
  public void parametersDirectlyInSubmitRequirementsSectionAreRejected() throws Exception {
    fetchRefsMetaConfig();

    updateProjectConfig(
        projectConfig -> {
          projectConfig.setString(
              ProjectConfig.SUBMIT_REQUIREMENT,
              /* subsection= */ null,
              /* name= */ ProjectConfig.KEY_SR_DESCRIPTION,
              /* value= */ "foo bar description");
          projectConfig.setString(
              ProjectConfig.SUBMIT_REQUIREMENT,
              /* subsection= */ null,
              /* name= */ ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
              /* value= */ "label:\"Code-Review=+2\"");
        });

    PushResult r = pushRefsMetaConfig();
    assertErrorStatus(
        r,
        "Invalid project configuration",
        String.format(
            "project.config: Submit requirements must be defined in submit-requirement.<name>"
                + " subsections. Setting parameters directly in the submit-requirement section is"
                + " not allowed: [%s, %s]",
            ProjectConfig.KEY_SR_DESCRIPTION, ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION));
  }

  @Test
  public void unsupportedParameterDirectlyInSubmitRequirementsSectionIsRejected() throws Exception {
    fetchRefsMetaConfig();

    updateProjectConfig(
        projectConfig ->
            projectConfig.setString(
                ProjectConfig.SUBMIT_REQUIREMENT,
                /* subsection= */ null,
                /* name= */ "unknown",
                /* value= */ "value"));

    PushResult r = pushRefsMetaConfig();
    assertErrorStatus(
        r,
        "Invalid project configuration",
        "project.config: Submit requirements must be defined in submit-requirement.<name>"
            + " subsections. Setting parameters directly in the submit-requirement section is"
            + " not allowed: [unknown]");
  }

  @Test
  public void unsupportedParameterForSubmitRequirementIsRejected() throws Exception {
    fetchRefsMetaConfig();

    String submitRequirementName = "Code-Review";
    updateProjectConfig(
        projectConfig ->
            projectConfig.setString(
                ProjectConfig.SUBMIT_REQUIREMENT,
                /* subsection= */ submitRequirementName,
                /* name= */ "unknown",
                /* value= */ "value"));

    PushResult r = pushRefsMetaConfig();
    assertErrorStatus(
        r,
        "Invalid project configuration",
        String.format(
            "project.config: Unsupported parameters for submit requirement '%s': [unknown]",
            submitRequirementName));
  }

  @Test
  public void conflictingSubmitRequirementsAreRejected() throws Exception {
    fetchRefsMetaConfig();

    String submitRequirementName = "Code-Review";
    updateProjectConfig(
        projectConfig -> {
          projectConfig.setString(
              ProjectConfig.SUBMIT_REQUIREMENT,
              /* subsection= */ submitRequirementName,
              /* name= */ ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
              /* value= */ "label:\"Code-Review=+2\"");
          projectConfig.setString(
              ProjectConfig.SUBMIT_REQUIREMENT,
              /* subsection= */ submitRequirementName.toLowerCase(Locale.US),
              /* name= */ ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
              /* value= */ "label:\"Code-Review=+2\"");
        });

    PushResult r = pushRefsMetaConfig();
    assertErrorStatus(
        r,
        "Invalid project configuration",
        String.format(
            "project.config: Submit requirement '%s' conflicts with '%s'.",
            submitRequirementName.toLowerCase(Locale.US), submitRequirementName));
  }

  @Test
  public void conflictingSubmitRequirementIsRejected() throws Exception {
    fetchRefsMetaConfig();
    String submitRequirementName = "Code-Review";
    updateProjectConfig(
        projectConfig ->
            projectConfig.setString(
                ProjectConfig.SUBMIT_REQUIREMENT,
                /* subsection= */ submitRequirementName,
                /* name= */ ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
                /* value= */ "label:\"Code-Review=+2\""));
    PushResult r = pushRefsMetaConfig();
    assertOkStatus(r);

    updateProjectConfig(
        projectConfig ->
            projectConfig.setString(
                ProjectConfig.SUBMIT_REQUIREMENT,
                /* subsection= */ submitRequirementName.toLowerCase(Locale.US),
                /* name= */ ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
                /* value= */ "label:\"Code-Review=+2\""));
    r = pushRefsMetaConfig();
    assertErrorStatus(
        r,
        "Invalid project configuration",
        String.format(
            "project.config: Submit requirement '%s' conflicts with '%s'.",
            submitRequirementName.toLowerCase(Locale.US), submitRequirementName));
  }

  @Test
  public void submitRequirementWithoutSubmittabilityExpressionIsRejected() throws Exception {
    fetchRefsMetaConfig();

    String submitRequirementName = "Code-Review";
    updateProjectConfig(
        projectConfig ->
            projectConfig.setString(
                ProjectConfig.SUBMIT_REQUIREMENT,
                /* subsection= */ submitRequirementName,
                /* name= */ ProjectConfig.KEY_SR_DESCRIPTION,
                /* value= */ "foo bar description"));

    PushResult r = pushRefsMetaConfig();
    assertErrorStatus(
        r,
        "Invalid project configuration",
        String.format(
            "project.config: Setting a submittability expression for submit requirement '%s' is"
                + " required: Missing %s.%s.%s",
            submitRequirementName,
            ProjectConfig.SUBMIT_REQUIREMENT,
            submitRequirementName,
            ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION));
  }

  @Test
  public void submitRequirementWithInvalidSubmittabilityExpressionIsRejected() throws Exception {
    fetchRefsMetaConfig();

    String submitRequirementName = "Code-Review";
    String invalidExpression = "invalid_field:invalid_value";
    updateProjectConfig(
        projectConfig ->
            projectConfig.setString(
                ProjectConfig.SUBMIT_REQUIREMENT,
                /* subsection= */ submitRequirementName,
                /* name= */ ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
                /* value= */ invalidExpression));

    PushResult r = pushRefsMetaConfig();
    assertErrorStatus(
        r,
        "Invalid project configuration",
        String.format(
            "project.config: Expression '%s' of submit requirement '%s' (parameter %s.%s.%s) is"
                + " invalid: Unsupported operator %s",
            invalidExpression,
            submitRequirementName,
            ProjectConfig.SUBMIT_REQUIREMENT,
            submitRequirementName,
            ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
            invalidExpression));
  }

  @Test
  public void submitRequirementWithInvalidApplicabilityExpressionIsRejected() throws Exception {
    fetchRefsMetaConfig();

    String submitRequirementName = "Code-Review";
    String invalidExpression = "invalid_field:invalid_value";
    updateProjectConfig(
        projectConfig -> {
          projectConfig.setString(
              ProjectConfig.SUBMIT_REQUIREMENT,
              /* subsection= */ submitRequirementName,
              /* name= */ ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
              /* value= */ "label:\"Code-Review=+2\"");
          projectConfig.setString(
              ProjectConfig.SUBMIT_REQUIREMENT,
              /* subsection= */ submitRequirementName,
              /* name= */ ProjectConfig.KEY_SR_APPLICABILITY_EXPRESSION,
              /* value= */ invalidExpression);
        });

    PushResult r = pushRefsMetaConfig();
    assertErrorStatus(
        r,
        "Invalid project configuration",
        String.format(
            "project.config: Expression '%s' of submit requirement '%s' (parameter %s.%s.%s) is"
                + " invalid: Unsupported operator %s",
            invalidExpression,
            submitRequirementName,
            ProjectConfig.SUBMIT_REQUIREMENT,
            submitRequirementName,
            ProjectConfig.KEY_SR_APPLICABILITY_EXPRESSION,
            invalidExpression));
  }

  @Test
  public void submitRequirementWithInvalidOverrideExpressionIsRejected() throws Exception {
    fetchRefsMetaConfig();

    String submitRequirementName = "Code-Review";
    String invalidExpression = "invalid_field:invalid_value";
    updateProjectConfig(
        projectConfig -> {
          projectConfig.setString(
              ProjectConfig.SUBMIT_REQUIREMENT,
              /* subsection= */ submitRequirementName,
              /* name= */ ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
              /* value= */ "label:\"Code-Review=+2\"");
          projectConfig.setString(
              ProjectConfig.SUBMIT_REQUIREMENT,
              /* subsection= */ submitRequirementName,
              /* name= */ ProjectConfig.KEY_SR_OVERRIDE_EXPRESSION,
              /* value= */ invalidExpression);
        });

    PushResult r = pushRefsMetaConfig();
    assertErrorStatus(
        r,
        "Invalid project configuration",
        String.format(
            "project.config: Expression '%s' of submit requirement '%s' (parameter %s.%s.%s) is"
                + " invalid: Unsupported operator %s",
            invalidExpression,
            submitRequirementName,
            ProjectConfig.SUBMIT_REQUIREMENT,
            submitRequirementName,
            ProjectConfig.KEY_SR_OVERRIDE_EXPRESSION,
            invalidExpression));
  }

  @Test
  public void userDependentOperatorsAreRejected() throws Exception {
    for (String submitExpr :
        ImmutableList.of(
            "has:draft",
            "has:attention",
            "has:edit",
            "is:submittable",
            "is:attention",
            "is:owner",
            "is:uploader",
            "is:reviewer",
            "is:cc")) {
      fetchRefsMetaConfig();

      String submitRequirementName = "Code-Review";
      updateProjectConfig(
          projectConfig ->
              projectConfig.setString(
                  ProjectConfig.SUBMIT_REQUIREMENT,
                  /* subsection= */ submitRequirementName,
                  /* name= */ ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
                  /* value= */ submitExpr));

      PushResult r = pushRefsMetaConfig();
      assertErrorStatus(
          r,
          "Invalid project configuration",
          String.format(
              "project.config: Expression '%s' of submit requirement 'Code-Review' "
                  + "(parameter submit-requirement.Code-Review.submittableIf) is invalid: "
                  + "Operator '%s' cannot be used in submit requirement expressions.",
              submitExpr, submitExpr));
    }
  }

  @Test
  public void submitRequirementWithInvalidAllowOverrideInChildProjectsIsRejected()
      throws Exception {
    fetchRefsMetaConfig();

    String submitRequirementName = "Code-Review";
    String invalidValue = "invalid";
    updateProjectConfig(
        projectConfig ->
            projectConfig.setString(
                ProjectConfig.SUBMIT_REQUIREMENT,
                /* subsection= */ submitRequirementName,
                /* name= */ ProjectConfig.KEY_SR_OVERRIDE_IN_CHILD_PROJECTS,
                /* value= */ invalidValue));

    PushResult r = pushRefsMetaConfig();
    assertErrorStatus(
        r,
        "Invalid project configuration",
        String.format(
            "project.config: Invalid value %s.%s.%s for submit requirement '%s': %s",
            ProjectConfig.SUBMIT_REQUIREMENT,
            submitRequirementName,
            ProjectConfig.KEY_SR_OVERRIDE_IN_CHILD_PROJECTS,
            submitRequirementName,
            invalidValue));
  }

  @Test
  public void validSubmitRequirementCanBePushedForReview_optionalParametersNotSet()
      throws Exception {
    fetchRefsMetaConfig();

    String submitRequirementName = "Code-Review";
    RevCommit head = getHead(testRepo.getRepository(), RefNames.REFS_CONFIG);
    Config projectConfig = readProjectConfig(head);
    projectConfig.setString(
        ProjectConfig.SUBMIT_REQUIREMENT,
        /* subsection= */ submitRequirementName,
        /* name= */ ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
        /* value= */ "label:\"Code-Review=+2\"");

    PushOneCommit.Result r =
        createChange(
            testRepo,
            RefNames.REFS_CONFIG,
            "Add submit requirement",
            ProjectConfig.PROJECT_CONFIG,
            projectConfig.toText(),
            /* topic= */ null);
    r.assertOkStatus();
  }

  @Test
  public void validSubmitRequirementCanBePushedForReview_allParametersSet() throws Exception {
    fetchRefsMetaConfig();

    String submitRequirementName = "Code-Review";
    RevCommit head = getHead(testRepo.getRepository(), RefNames.REFS_CONFIG);
    Config projectConfig = readProjectConfig(head);
    projectConfig.setString(
        ProjectConfig.SUBMIT_REQUIREMENT,
        /* subsection= */ submitRequirementName,
        /* name= */ ProjectConfig.KEY_SR_DESCRIPTION,
        /* value= */ "foo bar description");
    projectConfig.setString(
        ProjectConfig.SUBMIT_REQUIREMENT,
        /* subsection= */ submitRequirementName,
        /* name= */ ProjectConfig.KEY_SR_APPLICABILITY_EXPRESSION,
        /* value= */ "branch:refs/heads/master");
    projectConfig.setString(
        ProjectConfig.SUBMIT_REQUIREMENT,
        /* subsection= */ submitRequirementName,
        /* name= */ ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
        /* value= */ "label:\"Code-Review=+2\"");
    projectConfig.setString(
        ProjectConfig.SUBMIT_REQUIREMENT,
        /* subsection= */ submitRequirementName,
        /* name= */ ProjectConfig.KEY_SR_OVERRIDE_EXPRESSION,
        /* value= */ "label:\"override=+1\"");
    projectConfig.setBoolean(
        ProjectConfig.SUBMIT_REQUIREMENT,
        /* subsection= */ submitRequirementName,
        /* name= */ ProjectConfig.KEY_SR_OVERRIDE_IN_CHILD_PROJECTS,
        /* value= */ false);

    PushOneCommit.Result r =
        createChange(
            testRepo,
            RefNames.REFS_CONFIG,
            "Add submit requirement",
            ProjectConfig.PROJECT_CONFIG,
            projectConfig.toText(),
            /* topic= */ null);
    r.assertOkStatus();
  }

  @Test
  public void invalidSubmitRequirementIsRejectedWhenPushingForReview() throws Exception {
    fetchRefsMetaConfig();

    String submitRequirementName = "Code-Review";
    String invalidExpression = "invalid_field:invalid_value";

    RevCommit head = getHead(testRepo.getRepository(), RefNames.REFS_CONFIG);
    Config projectConfig = readProjectConfig(head);
    projectConfig.setString(
        ProjectConfig.SUBMIT_REQUIREMENT,
        /* subsection= */ submitRequirementName,
        /* name= */ ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
        /* value= */ invalidExpression);
    PushOneCommit.Result r =
        createChange(
            testRepo,
            RefNames.REFS_CONFIG,
            "Add submit requirement",
            ProjectConfig.PROJECT_CONFIG,
            projectConfig.toText(),
            /* topic= */ null);
    r.assertErrorStatus(
        String.format(
            "invalid submit requirement expressions in project.config (revision = %s)",
            r.getCommit().name()));
    assertThat(r.getMessage()).contains("Invalid project configuration");
    assertThat(r.getMessage())
        .contains(
            String.format(
                "project.config: Expression '%s' of submit requirement '%s' (parameter %s.%s.%s) is"
                    + " invalid: Unsupported operator %s",
                invalidExpression,
                submitRequirementName,
                ProjectConfig.SUBMIT_REQUIREMENT,
                submitRequirementName,
                ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
                invalidExpression));
  }

  private void fetchRefsMetaConfig() throws Exception {
    fetch(testRepo, RefNames.REFS_CONFIG + ":" + RefNames.REFS_CONFIG);
    testRepo.reset(RefNames.REFS_CONFIG);
  }

  private PushResult pushRefsMetaConfig() throws Exception {
    return pushHead(testRepo, RefNames.REFS_CONFIG);
  }

  private void updateProjectConfig(Consumer<Config> configUpdater) throws Exception {
    RevCommit head = getHead(testRepo.getRepository(), RefNames.REFS_CONFIG);
    Config projectConfig = readProjectConfig(head);
    configUpdater.accept(projectConfig);
    RevCommit commit =
        testRepo.update(
            RefNames.REFS_CONFIG,
            testRepo
                .commit()
                .parent(head)
                .message("Update project config")
                .author(admin.newIdent())
                .committer(admin.newIdent())
                .add(ProjectConfig.PROJECT_CONFIG, projectConfig.toText()));

    testRepo.reset(commit);
  }

  private Config readProjectConfig(RevCommit commit) throws Exception {
    try (TreeWalk tw =
        TreeWalk.forPath(
            testRepo.getRevWalk().getObjectReader(),
            ProjectConfig.PROJECT_CONFIG,
            commit.getTree())) {
      if (tw == null) {
        throw new IllegalStateException(
            String.format("%s does not exist", ProjectConfig.PROJECT_CONFIG));
      }
    }
    RevObject blob = testRepo.get(commit.getTree(), ProjectConfig.PROJECT_CONFIG);
    byte[] data = testRepo.getRepository().open(blob).getCachedBytes(Integer.MAX_VALUE);
    String content = RawParseUtils.decode(data);

    Config projectConfig = new Config();
    projectConfig.fromText(content);
    return projectConfig;
  }

  public void assertOkStatus(PushResult result) {
    RemoteRefUpdate refUpdate = result.getRemoteUpdate(RefNames.REFS_CONFIG);
    assertThat(refUpdate).isNotNull();
    assertWithMessage(getMessage(result, refUpdate))
        .that(refUpdate.getStatus())
        .isEqualTo(Status.OK);
  }

  public void assertErrorStatus(PushResult result, String... expectedMessages) {
    RemoteRefUpdate refUpdate = result.getRemoteUpdate(RefNames.REFS_CONFIG);
    assertThat(refUpdate).isNotNull();
    assertWithMessage(getMessage(result, refUpdate))
        .that(refUpdate.getStatus())
        .isEqualTo(Status.REJECTED_OTHER_REASON);
    for (String expectedMessage : expectedMessages) {
      assertThat(result.getMessages()).contains(expectedMessage);
    }
  }

  private String getMessage(PushResult result, RemoteRefUpdate refUpdate) {
    StringBuilder b = new StringBuilder();
    if (refUpdate.getMessage() != null) {
      b.append(refUpdate.getMessage());
      b.append("\n");
    }
    b.append(result.getMessages());
    return b.toString();
  }
}
