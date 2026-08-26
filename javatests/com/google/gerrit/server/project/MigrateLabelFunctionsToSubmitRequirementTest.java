// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.server.restapi.project.MigrateLabelFunctionsToSubmitRequirement;
import com.google.gerrit.server.restapi.project.MigrateLabelFunctionsToSubmitRequirement.Status;
import com.google.gerrit.server.schema.UpdateUI;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.TestUpdateUI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/** Tests full migration behavior of {@link MigrateLabelFunctionsToSubmitRequirement}. */
public class MigrateLabelFunctionsToSubmitRequirementTest {
  private static final String LABEL_NAME = "Foo";

  private static final ImmutableList<LabelValue> STANDARD_VALUES =
      ImmutableList.of(
          LabelValue.create((short) -1, "Looks Bad"),
          LabelValue.create((short) 0, "No Score"),
          LabelValue.create((short) 1, "Looks Good"));

  private InMemoryRepositoryManager repoManager;
  private MigrateLabelFunctionsToSubmitRequirement migrator;

  @Before
  public void setUp() {
    repoManager = new InMemoryRepositoryManager();
    migrator = new MigrateLabelFunctionsToSubmitRequirement(null, repoManager);
  }

  private record TestProjectConfig(
      ProjectConfig config,
      Map<String, LabelType> labels,
      Map<String, SubmitRequirement> submitRequirements) {}

  private TestProjectConfig newConfig() {
    ProjectConfig config = mock(ProjectConfig.class);
    Map<String, LabelType> labels = new LinkedHashMap<>();
    Map<String, SubmitRequirement> submitRequirements = new LinkedHashMap<>();
    when(config.getLabelSections()).thenReturn(labels);
    when(config.getSubmitRequirementSections()).thenReturn(submitRequirements);
    return new TestProjectConfig(config, labels, submitRequirements);
  }

  private LabelType.Builder labelBuilder(LabelFunction function) {
    return LabelType.builder(LABEL_NAME, STANDARD_VALUES).setFunction(function);
  }

  private void createRepository(Project.NameKey project) throws Exception {
    var repo = repoManager.createRepository(project);
    assertThat(repo).isNotNull();
  }

  private SubmitRequirement requirementFor(ProjectConfig config) {
    Map<String, SubmitRequirement> srs = config.getSubmitRequirementSections();
    assertThat(srs).containsKey(LABEL_NAME);
    return srs.get(LABEL_NAME);
  }

  @Test
  public void maxWithBlock_createsSr_andResetsLabelFunction() throws Exception {
    Project.NameKey project = Project.nameKey("p-max");
    createRepository(project);
    TestProjectConfig c = newConfig();
    c.labels().put(LABEL_NAME, labelBuilder(LabelFunction.MAX_WITH_BLOCK).build());
    UpdateUI ui = mock(UpdateUI.class);

    Status status = migrator.updateConfig(project, c.config(), ui);

    assertThat(status).isEqualTo(Status.MIGRATED);
    assertThat(requirementFor(c.config()).submittabilityExpression().expressionString())
        .isEqualTo("label:Foo=MAX AND -label:Foo=MIN");
    assertThat(c.labels().get(LABEL_NAME).getFunction()).isEqualTo(LabelFunction.NO_BLOCK);
  }

  @Test
  public void noBlock_doesNotCreateSr_andReturnsNoChange() throws Exception {
    Project.NameKey project = Project.nameKey("p-noblock");
    createRepository(project);
    TestProjectConfig c = newConfig();
    c.labels().put(LABEL_NAME, labelBuilder(LabelFunction.NO_BLOCK).build());
    UpdateUI ui = mock(UpdateUI.class);

    Status status = migrator.updateConfig(project, c.config(), ui);

    assertThat(status).isEqualTo(Status.NO_CHANGE);
    assertThat(c.submitRequirements()).isEmpty();
    verifyNoInteractions(ui);
  }

  @Test
  public void noOp_resetsToNoBlock_withoutSr() throws Exception {
    Project.NameKey project = Project.nameKey("p-noop");
    createRepository(project);
    TestProjectConfig c = newConfig();
    c.labels().put(LABEL_NAME, labelBuilder(LabelFunction.NO_OP).build());
    UpdateUI ui = mock(UpdateUI.class);

    Status status = migrator.updateConfig(project, c.config(), ui);

    assertThat(status).isEqualTo(Status.MIGRATED);
    assertThat(c.submitRequirements()).isEmpty();
    assertThat(c.labels().get(LABEL_NAME).getFunction()).isEqualTo(LabelFunction.NO_BLOCK);
  }

  @Test
  public void existingSrWithSameName_isNotOverwritten_andWarningEmitted() throws Exception {
    Project.NameKey project = Project.nameKey("p-existing");
    createRepository(project);
    TestProjectConfig c = newConfig();
    c.labels().put(LABEL_NAME, labelBuilder(LabelFunction.MAX_WITH_BLOCK).build());
    c.submitRequirements()
        .put(
            LABEL_NAME,
            SubmitRequirement.builder()
                .setName(LABEL_NAME)
                .setSubmittabilityExpression(SubmitRequirementExpression.create("project:foo"))
                .setAllowOverrideInChildProjects(false)
                .build());

    TestUpdateUI ui = new TestUpdateUI();
    Status status = migrator.updateConfig(project, c.config(), ui);

    assertThat(status).isEqualTo(Status.MIGRATED);
    assertThat(c.labels().get(LABEL_NAME).getFunction()).isEqualTo(LabelFunction.NO_BLOCK);
    assertThat(c.submitRequirements().get(LABEL_NAME).submittabilityExpression().expressionString())
        .isEqualTo("project:foo");
    assertThat(ui.getOutput()).contains("Warning");
  }

  @Test
  public void branchPattern_regex_usedAsIs() throws Exception {
    Project.NameKey project = Project.nameKey("p-regex");
    createRepository(project);
    TestProjectConfig c = newConfig();
    c.labels()
        .put(
            LABEL_NAME,
            labelBuilder(LabelFunction.MAX_WITH_BLOCK)
                .setRefPatterns(ImmutableList.of("^refs/heads/main-.*"))
                .build());
    UpdateUI ui = mock(UpdateUI.class);

    Status status = migrator.updateConfig(project, c.config(), ui);

    assertThat(status).isEqualTo(Status.MIGRATED);
    assertThat(
            requirementFor(c.config()).applicabilityExpression().orElseThrow().expressionString())
        .isEqualTo("branch:^refs/heads/main-.*");
  }

  @Test
  public void branchPattern_wildcard_convertedToRegex() throws Exception {
    Project.NameKey project = Project.nameKey("p-wildcard");
    createRepository(project);
    TestProjectConfig c = newConfig();
    c.labels()
        .put(
            LABEL_NAME,
            labelBuilder(LabelFunction.MAX_WITH_BLOCK)
                .setRefPatterns(ImmutableList.of("refs/heads/release/*"))
                .build());
    UpdateUI ui = mock(UpdateUI.class);

    Status status = migrator.updateConfig(project, c.config(), ui);

    assertThat(status).isEqualTo(Status.MIGRATED);
    assertThat(
            requirementFor(c.config()).applicabilityExpression().orElseThrow().expressionString())
        .isEqualTo("branch:^\\Qrefs/heads/release/\\E.*");
  }

  @Test
  public void branchPattern_plain_withQuote_isEscapedAndQuoted() throws Exception {
    Project.NameKey project = Project.nameKey("p-quote");
    createRepository(project);
    TestProjectConfig c = newConfig();
    c.labels()
        .put(
            LABEL_NAME,
            labelBuilder(LabelFunction.MAX_WITH_BLOCK)
                .setRefPatterns(ImmutableList.of("refs/heads/gerr\"it"))
                .build());
    UpdateUI ui = mock(UpdateUI.class);

    Status status = migrator.updateConfig(project, c.config(), ui);

    assertThat(status).isEqualTo(Status.MIGRATED);
    assertThat(
            requirementFor(c.config()).applicabilityExpression().orElseThrow().expressionString())
        .isEqualTo("branch:\"refs/heads/gerr\\\"it\"");
  }

  @Test
  public void branchPattern_multiple_joinedWithOr() throws Exception {
    Project.NameKey project = Project.nameKey("p-multi");
    createRepository(project);
    TestProjectConfig c = newConfig();
    c.labels()
        .put(
            LABEL_NAME,
            labelBuilder(LabelFunction.MAX_WITH_BLOCK)
                .setRefPatterns(ImmutableList.of("refs/heads/master", "^refs/heads/main-.*"))
                .build());
    UpdateUI ui = mock(UpdateUI.class);

    Status status = migrator.updateConfig(project, c.config(), ui);

    assertThat(status).isEqualTo(Status.MIGRATED);
    assertThat(
            requirementFor(c.config()).applicabilityExpression().orElseThrow().expressionString())
        .isEqualTo("branch:\"refs/heads/master\" OR branch:^refs/heads/main-.*");
  }
}
