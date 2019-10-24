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
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.LabelFunction;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;

@NoHttpd
public class ListLabelsIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void notAllowed() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(AuthException.class, () -> gApi.projects().name(project.get()).labels().get());
    assertThat(thrown).hasMessageThat().contains("read refs/meta/config not permitted");
  }

  @Test
  public void noLabels() throws Exception {
    assertThat(gApi.projects().name(project.get()).labels().get()).isEmpty();
  }

  @Test
  public void allProjectsLabels() throws Exception {
    List<LabelDefinitionInfo> labels = gApi.projects().name(allProjects.get()).labels().get();
    assertThat(labelNames(labels)).containsExactly("Code-Review");

    LabelDefinitionInfo codeReviewLabel = Iterables.getOnlyElement(labels);
    assertThat(codeReviewLabel.name).isEqualTo("Code-Review");
    assertThat(codeReviewLabel.function).isEqualTo(LabelFunction.MAX_WITH_BLOCK.getFunctionName());
    assertThat(codeReviewLabel.values)
        .containsExactly(
            "+2",
            "Looks good to me, approved",
            "+1",
            "Looks good to me, but someone else must approve",
            " 0",
            "No score",
            "-1",
            "I would prefer this is not merged as is",
            "-2",
            "This shall not be merged");
    assertThat(codeReviewLabel.defaultValue).isEqualTo(0);
    assertThat(codeReviewLabel.branches).isNull();
    assertThat(codeReviewLabel.canOverride).isTrue();
    assertThat(codeReviewLabel.copyAnyScore).isNull();
    assertThat(codeReviewLabel.copyMinScore).isTrue();
    assertThat(codeReviewLabel.copyMaxScore).isNull();
    assertThat(codeReviewLabel.copyAllScoresIfNoChange).isTrue();
    assertThat(codeReviewLabel.copyAllScoresIfNoCodeChange).isNull();
    assertThat(codeReviewLabel.copyAllScoresOnTrivialRebase).isTrue();
    assertThat(codeReviewLabel.copyAllScoresOnMergeFirstParentUpdate).isNull();
    assertThat(codeReviewLabel.allowPostSubmit).isTrue();
    assertThat(codeReviewLabel.ignoreSelfApproval).isNull();
  }

  @Test
  public void labelsAreSortedByName() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);
    configLabel("bar", LabelFunction.NO_OP);
    configLabel("baz", LabelFunction.NO_OP);

    List<LabelDefinitionInfo> labels = gApi.projects().name(project.get()).labels().get();
    assertThat(labelNames(labels)).containsExactly("bar", "baz", "foo").inOrder();
  }

  @Test
  public void labelWithDefaultValue() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);

    // set default value
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType labelType = u.getConfig().getLabelSections().get("foo");
      labelType.setDefaultValue((short) 1);
      u.getConfig().getLabelSections().put(labelType.getName(), labelType);
      u.save();
    }

    List<LabelDefinitionInfo> labels = gApi.projects().name(project.get()).labels().get();
    assertThat(labelNames(labels)).containsExactly("foo");

    LabelDefinitionInfo fooLabel = Iterables.getOnlyElement(labels);
    assertThat(fooLabel.defaultValue).isEqualTo(1);
  }

  @Test
  public void labelLimitedToBranches() throws Exception {
    configLabel(
        "foo", LabelFunction.NO_OP, ImmutableList.of("refs/heads/master", "^refs/heads/stable-.*"));

    List<LabelDefinitionInfo> labels = gApi.projects().name(project.get()).labels().get();
    assertThat(labelNames(labels)).containsExactly("foo");

    LabelDefinitionInfo fooLabel = Iterables.getOnlyElement(labels);
    assertThat(fooLabel.branches).containsExactly("refs/heads/master", "^refs/heads/stable-.*");
  }

  @Test
  public void labelWithoutRules() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);

    // unset rules which are enabled by default
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType labelType = u.getConfig().getLabelSections().get("foo");
      labelType.setCanOverride(false);
      labelType.setCopyAllScoresIfNoChange(false);
      labelType.setAllowPostSubmit(false);
      u.getConfig().getLabelSections().put(labelType.getName(), labelType);
      u.save();
    }

    List<LabelDefinitionInfo> labels = gApi.projects().name(project.get()).labels().get();
    assertThat(labelNames(labels)).containsExactly("foo");

    LabelDefinitionInfo fooLabel = Iterables.getOnlyElement(labels);
    assertThat(fooLabel.canOverride).isNull();
    assertThat(fooLabel.copyAnyScore).isNull();
    assertThat(fooLabel.copyMinScore).isNull();
    assertThat(fooLabel.copyMaxScore).isNull();
    assertThat(fooLabel.copyAllScoresIfNoChange).isNull();
    assertThat(fooLabel.copyAllScoresIfNoCodeChange).isNull();
    assertThat(fooLabel.copyAllScoresOnTrivialRebase).isNull();
    assertThat(fooLabel.copyAllScoresOnMergeFirstParentUpdate).isNull();
    assertThat(fooLabel.allowPostSubmit).isNull();
    assertThat(fooLabel.ignoreSelfApproval).isNull();
  }

  @Test
  public void labelWithAllRules() throws Exception {
    configLabel("foo", LabelFunction.NO_OP);

    // set rules which are not enabled by default
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType labelType = u.getConfig().getLabelSections().get("foo");
      labelType.setCopyAnyScore(true);
      labelType.setCopyMinScore(true);
      labelType.setCopyMaxScore(true);
      labelType.setCopyAllScoresIfNoCodeChange(true);
      labelType.setCopyAllScoresOnTrivialRebase(true);
      labelType.setCopyAllScoresOnMergeFirstParentUpdate(true);
      labelType.setIgnoreSelfApproval(true);
      u.getConfig().getLabelSections().put(labelType.getName(), labelType);
      u.save();
    }

    List<LabelDefinitionInfo> labels = gApi.projects().name(project.get()).labels().get();
    assertThat(labelNames(labels)).containsExactly("foo");

    LabelDefinitionInfo fooLabel = Iterables.getOnlyElement(labels);
    assertThat(fooLabel.canOverride).isTrue();
    assertThat(fooLabel.copyAnyScore).isTrue();
    assertThat(fooLabel.copyMinScore).isTrue();
    assertThat(fooLabel.copyMaxScore).isTrue();
    assertThat(fooLabel.copyAllScoresIfNoChange).isTrue();
    assertThat(fooLabel.copyAllScoresIfNoCodeChange).isTrue();
    assertThat(fooLabel.copyAllScoresOnTrivialRebase).isTrue();
    assertThat(fooLabel.copyAllScoresOnMergeFirstParentUpdate).isTrue();
    assertThat(fooLabel.allowPostSubmit).isTrue();
    assertThat(fooLabel.ignoreSelfApproval).isTrue();
  }

  private static List<String> labelNames(List<LabelDefinitionInfo> labels) {
    return labels.stream().map(l -> l.name).collect(toList());
  }
}
