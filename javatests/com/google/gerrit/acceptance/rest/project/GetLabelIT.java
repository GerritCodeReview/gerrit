// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.inject.Inject;
import org.junit.Test;

@NoHttpd
public class GetLabelIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void anonymous() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get());
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void notAllowed() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get());
    assertThat(thrown).hasMessageThat().contains("read refs/meta/config not permitted");
  }

  @Test
  public void notFound() throws Exception {
    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(project.get()).label("Foo-Review").get());
    assertThat(thrown).hasMessageThat().contains("Not found: Foo-Review");
  }

  @Test
  public void allProjectsCodeReviewLabel() throws Exception {
    LabelDefinitionInfo codeReviewLabel =
        gApi.projects().name(allProjects.get()).label(LabelId.CODE_REVIEW).get();
    LabelAssert.assertCodeReviewLabel(codeReviewLabel);
  }

  @Test
  public void labelWithDefaultValue() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);

    // set default value
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType("foo", labelType -> labelType.setDefaultValue((short) 1));
      u.save();
    }

    LabelDefinitionInfo fooLabel = gApi.projects().name(project.get()).label("foo").get();
    assertThat(fooLabel.defaultValue).isEqualTo(1);
  }

  @Test
  public void labelLimitedToBranches() throws Exception {
    configLabel(
        "foo", LabelFunction.NO_OP, ImmutableList.of("refs/heads/master", "^refs/heads/stable-.*"));

    LabelDefinitionInfo fooLabel = gApi.projects().name(project.get()).label("foo").get();
    assertThat(fooLabel.branches).containsExactly("refs/heads/master", "^refs/heads/stable-.*");
  }

  @Test
  public void labelWithoutRules() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);

    // unset rules which are enabled by default
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              "foo",
              labelType -> {
                labelType.setCanOverride(false);
                labelType.setAllowPostSubmit(false);
              });
      u.save();
    }

    LabelDefinitionInfo fooLabel = gApi.projects().name(project.get()).label("foo").get();
    assertThat(fooLabel.canOverride).isNull();
    assertThat(fooLabel.allowPostSubmit).isNull();
    assertThat(fooLabel.ignoreSelfApproval).isNull();
  }

  @Test
  public void labelWithAllRules() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);

    // set rules which are not enabled by default
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateLabelType(
              "foo",
              labelType -> {
                labelType.setCopyCondition("is:MIN OR is:MAX");
                labelType.setIgnoreSelfApproval(true);
              });
      u.save();
    }

    LabelDefinitionInfo fooLabel = gApi.projects().name(project.get()).label("foo").get();
    assertThat(fooLabel.canOverride).isTrue();
    assertThat(fooLabel.allowPostSubmit).isTrue();
    assertThat(fooLabel.copyCondition).isEqualTo("is:MIN OR is:MAX");
    assertThat(fooLabel.ignoreSelfApproval).isTrue();
  }
}
