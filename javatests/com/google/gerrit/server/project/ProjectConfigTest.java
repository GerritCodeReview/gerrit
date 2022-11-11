// Copyright (C) 2011 The Android Open Source Project
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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.entities.BooleanProjectConfig.REQUIRE_CHANGE_ID;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.RuntimeVersion;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.AccountsSection;
import com.google.gerrit.entities.BranchOrderSection;
import com.google.gerrit.entities.ContributorAgreement;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.StoredCommentLinkInfo;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.FileBasedAllProjectsConfigProvider;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.testing.TestLabels;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProjectConfigTest {
  private static final String COPY_CONDITION = "is:MIN OR is:MAX";
  private static final String LABEL_SCORES_CONFIG = "  copyCondition = " + COPY_CONDITION + "\n";

  private static final AllProjectsName ALL_PROJECTS = new AllProjectsName("All-The-Projects");

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final GroupReference developers =
      GroupReference.create(AccountGroup.uuid("X"), "Developers");
  private final GroupReference staff = GroupReference.create(AccountGroup.uuid("Y"), "Staff");

  private SitePaths sitePaths;
  private ProjectConfig.Factory factory;
  private Repository db;
  private TestRepository<?> tr;

  @Before
  public void setUp() throws Exception {
    sitePaths = new SitePaths(temporaryFolder.newFolder().toPath());
    Files.createDirectories(sitePaths.etc_dir);
    factory =
        new ProjectConfig.Factory(ALL_PROJECTS, new FileBasedAllProjectsConfigProvider(sitePaths));
    db = new InMemoryRepository(new DfsRepositoryDescription("repo"));
    tr = new TestRepository<>(db);
  }

  @Test
  public void readConfig() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add(
                "project.config",
                "[access \"refs/heads/*\"]\n"
                    + "  exclusiveGroupPermissions = read submit create\n"
                    + "  submit = group Developers\n"
                    + "  push = group Developers\n"
                    + "  read = group Developers\n"
                    + "[accounts]\n"
                    + "  sameGroupVisibility = deny group Developers\n"
                    + "  sameGroupVisibility = block group Staff\n"
                    + "[contributor-agreement \"Individual\"]\n"
                    + "  description = A simple description\n"
                    + "  matchProjects = ^/ourproject\n"
                    + "  matchProjects = ^/ourotherproject\n"
                    + "  matchProjects = ^/someotherroot/ourproject\n"
                    + "  excludeProjects = ^/theirproject\n"
                    + "  excludeProjects = ^/theirotherproject\n"
                    + "  excludeProjects = ^/someotherroot/theirproject\n"
                    + "  excludeProjects = ^/someotherroot/theirotherproject\n"
                    + "  accepted = group Developers\n"
                    + "  accepted = group Staff\n"
                    + "  autoVerify = group Developers\n"
                    + "  agreementUrl = http://www.example.com/agree\n")
            .create();

    ProjectConfig cfg = read(rev);
    assertThat(cfg.getAccountsSection().getSameGroupVisibility()).hasSize(2);
    ContributorAgreement ca = cfg.getContributorAgreement("Individual");
    assertThat(ca.getName()).isEqualTo("Individual");
    assertThat(ca.getDescription()).isEqualTo("A simple description");
    assertThat(ca.getMatchProjectsRegexes())
        .containsExactly("^/ourproject", "^/ourotherproject", "^/someotherroot/ourproject");
    assertThat(ca.getExcludeProjectsRegexes())
        .containsExactly(
            "^/theirproject",
            "^/theirotherproject",
            "^/someotherroot/theirproject",
            "^/someotherroot/theirotherproject");
    assertThat(ca.getAgreementUrl()).isEqualTo("http://www.example.com/agree");
    assertThat(ca.getAccepted()).hasSize(2);
    assertThat(ca.getAccepted().get(0).getGroup()).isEqualTo(developers);
    assertThat(ca.getAccepted().get(1).getGroup().getName()).isEqualTo("Staff");
    assertThat(ca.getAutoVerify().getName()).isEqualTo("Developers");

    AccessSection section = cfg.getAccessSection("refs/heads/*");
    assertThat(section).isNotNull();
    assertThat(cfg.getAccessSection("refs/*")).isNull();

    Permission create = section.getPermission(Permission.CREATE);
    Permission submit = section.getPermission(Permission.SUBMIT);
    Permission read = section.getPermission(Permission.READ);
    Permission push = section.getPermission(Permission.PUSH);

    assertThat(create.getExclusiveGroup()).isTrue();
    assertThat(submit.getExclusiveGroup()).isTrue();
    assertThat(read.getExclusiveGroup()).isTrue();
    assertThat(push.getExclusiveGroup()).isFalse();
  }

  @Test
  public void readConfigLabelDefaultValue() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add(
                "project.config",
                "[label \"CustomLabel\"]\n"
                    + "  value = -1 Negative\n"
                    // No leading space before 0.
                    + "  value = 0 No Score\n"
                    + "  value =  1 Positive\n")
            .create();

    ProjectConfig cfg = read(rev);
    Map<String, LabelType> labels = cfg.getLabelSections();
    Short dv = labels.entrySet().iterator().next().getValue().getDefaultValue();
    assertThat((int) dv).isEqualTo(0);
  }

  @Test
  public void readSubmitRequirements() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add(
                "project.config",
                "[submit-requirement \"Code-review\"]\n"
                    + "  description =  At least one Code Review +2\n"
                    + "  applicableIf =branch(refs/heads/master)\n"
                    + "  submittableIf =  label(Code-Review, +2)\n"
                    + "[submit-requirement \"api-review\"]\n"
                    + "  description =  Additional review required for API modifications\n"
                    + "  applicableIf =commit_filepath_contains(\\\"/api/.*\\\")\n"
                    + "  submittableIf =  label(api-review, +2)\n"
                    + "  overrideIf =  label(build-cop-override, +1)\n"
                    + "  canOverrideInChildProjects = true\n")
            .create();

    ProjectConfig cfg = read(rev);
    Map<String, SubmitRequirement> submitRequirements = cfg.getSubmitRequirementSections();
    assertThat(submitRequirements)
        .containsExactly(
            "Code-review", // Capitalization preserved
            SubmitRequirement.builder()
                .setName("Code-review") // Capitalization preserved
                .setDescription(Optional.of("At least one Code Review +2"))
                .setApplicabilityExpression(
                    SubmitRequirementExpression.of("branch(refs/heads/master)"))
                .setSubmittabilityExpression(
                    SubmitRequirementExpression.create("label(Code-Review, +2)"))
                .setOverrideExpression(Optional.empty())
                .setAllowOverrideInChildProjects(false)
                .build(),
            "api-review",
            SubmitRequirement.builder()
                .setName("api-review")
                .setDescription(Optional.of("Additional review required for API modifications"))
                .setApplicabilityExpression(
                    SubmitRequirementExpression.of("commit_filepath_contains(\"/api/.*\")"))
                .setSubmittabilityExpression(
                    SubmitRequirementExpression.create("label(api-review, +2)"))
                .setOverrideExpression(
                    SubmitRequirementExpression.of("label(build-cop-override, +1)"))
                .setAllowOverrideInChildProjects(true)
                .build());
  }

  @Test
  public void readSubmitRequirementEmpty() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add(
                "project.config",
                "[submit-requirement \"Code-Review\"]\n"
                    + "  submittableIf =  label(Code-Review, +2)\n")
            .create();

    ProjectConfig cfg = read(rev);
    Map<String, SubmitRequirement> submitRequirements = cfg.getSubmitRequirementSections();
    assertThat(submitRequirements)
        .containsExactly(
            "Code-Review",
            SubmitRequirement.builder()
                .setName("Code-Review")
                .setSubmittabilityExpression(
                    SubmitRequirementExpression.create("label(Code-Review, +2)"))
                .setAllowOverrideInChildProjects(false)
                .build());
  }

  @Test
  public void readSubmitRequirementsIdentical_WithCapitalizationDifference() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add(
                "project.config",
                "[submit-requirement \"code-review\"]\n"
                    + "  description = At least one Code Review +2\n"
                    + "  submittableIf =  label(code-review, +2)\n"
                    + "[submit-requirement \"Code-Review\"]\n"
                    + "  description = Another code review label\n"
                    + "  submittableIf =  label(code-review, +2)\n"
                    + "  canOverrideInChildProjects = true\n")
            .create();

    ProjectConfig cfg = read(rev);
    Map<String, SubmitRequirement> submitRequirements = cfg.getSubmitRequirementSections();
    assertThat(submitRequirements)
        .containsExactly(
            "code-review",
            SubmitRequirement.builder()
                .setName("code-review")
                .setDescription(Optional.of("At least one Code Review +2"))
                .setSubmittabilityExpression(
                    SubmitRequirementExpression.create("label(code-review, +2)"))
                .setAllowOverrideInChildProjects(false)
                .build());
    assertThat(cfg.getValidationErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(cfg.getValidationErrors()).getMessage())
        .isEqualTo(
            "project.config: Submit requirement 'Code-Review' conflicts with 'code-review'.");
  }

  @Test
  public void readSubmitRequirementNoSubmittabilityExpression() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add(
                "project.config",
                "[submit-requirement \"Code-Review\"]\n"
                    + "  applicableIf =label(Code-Review, +2)\n")
            .create();

    ProjectConfig cfg = read(rev);
    Map<String, SubmitRequirement> submitRequirements = cfg.getSubmitRequirementSections();
    assertThat(submitRequirements).isEmpty();
    assertThat(cfg.getValidationErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(cfg.getValidationErrors()).getMessage())
        .isEqualTo(
            "project.config: Setting a submittability expression for submit requirement"
                + " 'Code-Review' is required: Missing"
                + " submit-requirement.Code-Review.submittableIf");
  }

  @Test
  public void readConfigLabelOldStyleWithLeadingSpace() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add(
                "project.config",
                "[label \"CustomLabel\"]\n"
                    + "  value = -1 Negative\n"
                    // Leading space before 0.
                    + "  value =  0 No Score\n"
                    + "  value =  1 Positive\n")
            .create();

    ProjectConfig cfg = read(rev);
    Map<String, LabelType> labels = cfg.getLabelSections();
    Short dv = labels.entrySet().iterator().next().getValue().getDefaultValue();
    assertThat((int) dv).isEqualTo(0);
  }

  @Test
  public void readConfigLabelDefaultValueInRange() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add(
                "project.config",
                "[label \"CustomLabel\"]\n"
                    + "  value = -1 Negative\n"
                    + "  value = 0 No Score\n"
                    + "  value =  1 Positive\n"
                    + "  defaultValue = -1\n")
            .create();

    ProjectConfig cfg = read(rev);
    Map<String, LabelType> labels = cfg.getLabelSections();
    Short dv = labels.entrySet().iterator().next().getValue().getDefaultValue();
    assertThat((int) dv).isEqualTo(-1);
  }

  @Test
  public void readConfigLabelDefaultValueNotInRange() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add(
                "project.config",
                "[label \"CustomLabel\"]\n"
                    + "  value = -1 Negative\n"
                    + "  value = 0 No Score\n"
                    + "  value =  1 Positive\n"
                    + "  defaultValue = -2\n")
            .create();

    ProjectConfig cfg = read(rev);
    assertThat(cfg.getValidationErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(cfg.getValidationErrors()).getMessage())
        .isEqualTo("project.config: Invalid defaultValue \"-2\" for label \"CustomLabel\"");
  }

  @Test
  public void readConfigLabelInvalidBranchPattern() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add(
                "project.config",
                "[label \"CustomLabel\"]\n"
                    + "  value = -1 Negative\n"
                    + "  value = 0 No Score\n"
                    + "  value =  1 Positive\n"
                    + "  branch = ^***\n"
                    + "  defaultValue = 0\n")
            .create();

    ProjectConfig cfg = read(rev);
    assertThat(cfg.getValidationErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(cfg.getValidationErrors()).getMessage())
        .isEqualTo(
            "project.config: Invalid ref pattern \"^***\""
                + " in label.CustomLabel.branch: Dangling meta character '*' near index 2\n"
                + "^***\n"
                + "  ^");
  }

  @Test
  public void readConfigCondition() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add("project.config", "[label \"CustomLabel\"]\n" + LABEL_SCORES_CONFIG)
            .create();

    ProjectConfig cfg = read(rev);
    Map<String, LabelType> labels = cfg.getLabelSections();
    LabelType type = labels.entrySet().iterator().next().getValue();
    assertThat(type.getCopyCondition()).hasValue(COPY_CONDITION);
  }

  @Test
  public void editConfig() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add(
                "project.config",
                "[access \"refs/heads/*\"]\n"
                    + "  exclusiveGroupPermissions = read submit\n"
                    + "  submit = group Developers\n"
                    + "  upload = group Developers\n"
                    + "  read = group Developers\n"
                    + "[accounts]\n"
                    + "  sameGroupVisibility = deny group Developers\n"
                    + "  sameGroupVisibility = block group Staff\n"
                    + "[contributor-agreement \"Individual\"]\n"
                    + "  description = A simple description\n"
                    + "  matchProjects = ^/ourproject\n"
                    + "  accepted = group Developers\n"
                    + "  autoVerify = group Developers\n"
                    + "  agreementUrl = http://www.example.com/agree\n"
                    + "[label \"CustomLabel\"]\n"
                    + LABEL_SCORES_CONFIG)
            .create();
    update(rev);

    ProjectConfig cfg = read(rev);
    cfg.upsertAccessSection(
        "refs/heads/*",
        section -> {
          Permission.Builder submit = section.upsertPermission(Permission.SUBMIT);
          submit.add(PermissionRule.builder(cfg.resolve(staff)));
        });
    cfg.setAccountsSection(
        AccountsSection.create(
            Collections.singletonList(PermissionRule.create(cfg.resolve(staff)))));
    ContributorAgreement.Builder ca = cfg.getContributorAgreement("Individual").toBuilder();
    ca.setAccepted(ImmutableList.of(PermissionRule.create(cfg.resolve(staff))));
    ca.setAutoVerify(null);
    ca.setMatchProjectsRegexes(ImmutableList.of());
    ca.setExcludeProjectsRegexes(ImmutableList.of("^/theirproject"));
    ca.setDescription("A new description");
    cfg.upsertContributorAgreement(ca.build());
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo(
            "[access \"refs/heads/*\"]\n"
                + "  exclusiveGroupPermissions = read submit\n"
                + "  submit = group Developers\n"
                + "\tsubmit = group Staff\n"
                + "  upload = group Developers\n"
                + "  read = group Developers\n"
                + "[label \"CustomLabel\"]\n"
                + LABEL_SCORES_CONFIG
                + "\tfunction = MaxWithBlock\n" // label gets this function when it is created
                + "\tdefaultValue = 0\n" //  label gets this value when it is created
                + "[accounts]\n"
                + "\tsameGroupVisibility = group Staff\n"
                + "[contributor-agreement \"Individual\"]\n"
                + "\tdescription = A new description\n"
                + "\tagreementUrl = http://www.example.com/agree\n"
                + "\taccepted = group Staff\n"
                + "\texcludeProjects = ^/theirproject\n");
  }

  @Test
  public void editConfigLabelValues() throws Exception {
    RevCommit rev = tr.commit().create();
    update(rev);

    ProjectConfig cfg = read(rev);
    cfg.getLabelSections()
        .put(
            "My-Label",
            TestLabels.label(
                "My-Label",
                TestLabels.value(-1, "Negative"),
                TestLabels.value(0, "No score"),
                TestLabels.value(1, "Positive")));
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo(
            "[label \"My-Label\"]\n"
                + "\tfunction = MaxWithBlock\n"
                + "\tdefaultValue = 0\n"
                + "\tvalue = -1 Negative\n"
                + "\tvalue = 0 No score\n"
                + "\tvalue = +1 Positive\n");
  }

  @Test
  public void readExistingBranchOrder() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("project.config", "[branchOrder]\n" + "\tbranch = foo\n" + "\tbranch = bar\n")
            .create();
    update(rev);

    ProjectConfig cfg = read(rev);
    assertThat(cfg.getBranchOrderSection())
        .isEqualTo(BranchOrderSection.create(ImmutableList.of("foo", "bar")));
  }

  @Test
  public void editBranchOrder() throws Exception {
    RevCommit rev = tr.commit().create();
    update(rev);

    ProjectConfig cfg = read(rev);
    cfg.setBranchOrderSection(BranchOrderSection.create(ImmutableList.of("foo", "bar")));
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo("[branchOrder]\n" + "\tbranch = foo\n" + "\tbranch = bar\n");
  }

  @Test
  public void addCommentLink() throws Exception {
    RevCommit rev = tr.commit().create();
    update(rev);

    ProjectConfig cfg = read(rev);
    StoredCommentLinkInfo cm =
        StoredCommentLinkInfo.builder("Test")
            .setMatch("abc.*")
            .setLink("link")
            .setEnabled(true)
            .setOverrideOnly(false)
            .build();
    cfg.addCommentLinkSection(cm);
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo("[commentlink \"Test\"]\n\tmatch = abc.*\n\tlink = link\n");
  }

  @Test
  public void editConfigMissingGroupTableEntry() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add(
                "project.config",
                "[access \"refs/heads/*\"]\n"
                    + "  exclusiveGroupPermissions = read submit\n"
                    + "  submit = group People Who Can Submit\n"
                    + "  upload = group Developers\n"
                    + "  read = group Developers\n")
            .create();
    update(rev);

    ProjectConfig cfg = read(rev);
    cfg.upsertAccessSection(
        "refs/heads/*",
        section -> {
          Permission.Builder submit = section.upsertPermission(Permission.SUBMIT);
          submit.add(PermissionRule.builder(cfg.resolve(staff)));
        });
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo(
            "[access \"refs/heads/*\"]\n"
                + "  exclusiveGroupPermissions = read submit\n"
                + "  submit = group People Who Can Submit\n"
                + "\tsubmit = group Staff\n"
                + "  upload = group Developers\n"
                + "  read = group Developers\n");
  }

  @Test
  public void readExistingPluginConfig() throws Exception {
    RevCommit rev =
        tr.commit()
            .add(
                "project.config",
                "[plugin \"somePlugin\"]\n"
                    + "  key1 = value1\n"
                    + "  key2 = value2a\n"
                    + "  key2 = value2b\n")
            .create();
    update(rev);

    ProjectConfig cfg = read(rev);
    PluginConfig pluginCfg = cfg.getPluginConfig("somePlugin");
    assertThat(pluginCfg.getNames()).hasSize(2);
    assertThat(pluginCfg.getString("key1")).isEqualTo("value1");
    assertThat(pluginCfg.getStringList("key2")).isEqualTo(new String[] {"value2a", "value2b"});
  }

  @Test
  public void readUnexistingPluginConfig() throws Exception {
    ProjectConfig cfg = factory.create(Project.nameKey("test"));
    cfg.load(db);
    PluginConfig pluginCfg = cfg.getPluginConfig("somePlugin");
    assertThat(pluginCfg.getNames()).isEmpty();
  }

  @Test
  public void editPluginConfig() throws Exception {
    RevCommit rev =
        tr.commit()
            .add(
                "project.config",
                "[plugin \"somePlugin\"]\n"
                    + "  key1 = value1\n"
                    + "  key2 = value2a\n"
                    + "  key2 = value2b\n")
            .create();
    update(rev);

    ProjectConfig cfg = read(rev);
    cfg.updatePluginConfig(
        "somePlugin",
        pluginCfg -> {
          pluginCfg.setString("key1", "updatedValue1");
          pluginCfg.setStringList("key2", Arrays.asList("updatedValue2a", "updatedValue2b"));
        });
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo(
            "[plugin \"somePlugin\"]\n"
                + "\tkey1 = updatedValue1\n"
                + "\tkey2 = updatedValue2a\n"
                + "\tkey2 = updatedValue2b\n");
  }

  @Test
  public void readPluginConfigGroupReference() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add("project.config", "[plugin \"somePlugin\"]\nkey1 = " + developers.toConfigValue())
            .create();
    update(rev);

    ProjectConfig cfg = read(rev);
    PluginConfig pluginCfg = cfg.getPluginConfig("somePlugin");
    assertThat(pluginCfg.getNames()).hasSize(1);
    assertThat(pluginCfg.getGroupReference("key1").get()).isEqualTo(developers);
  }

  @Test
  public void readPluginConfigGroupReferenceNotInGroupsFile() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add("project.config", "[plugin \"somePlugin\"]\nkey1 = " + staff.toConfigValue())
            .create();
    update(rev);

    ProjectConfig cfg = read(rev);
    assertThat(cfg.getValidationErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(cfg.getValidationErrors()).getMessage())
        .isEqualTo(
            "project.config: group \"" + staff.getName() + "\" not in " + GroupList.FILE_NAME);
  }

  @Test
  public void editPluginConfigGroupReference() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("groups", group(developers))
            .add("project.config", "[plugin \"somePlugin\"]\nkey1 = " + developers.toConfigValue())
            .create();
    update(rev);

    ProjectConfig cfg = read(rev);
    assertThat(cfg.getPluginConfig("somePlugin").getNames()).hasSize(1);
    assertThat(cfg.getPluginConfig("somePlugin").getGroupReference("key1").get())
        .isEqualTo(developers);
    cfg.updatePluginConfig("somePlugin", pluginCfg -> pluginCfg.setGroupReference("key1", staff));
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo("[plugin \"somePlugin\"]\n\tkey1 = " + staff.toConfigValue() + "\n");
    assertThat(text(rev, "groups"))
        .isEqualTo(
            "# UUID\tGroup Name\n"
                + "#\n"
                + staff.getUUID().get()
                + "     \t"
                + staff.getName()
                + "\n");
  }

  @Test
  public void readCommentLinks() throws Exception {
    RevCommit rev =
        tr.commit()
            .add(
                "project.config",
                "[commentlink \"bugzilla\"]\n"
                    + "\tmatch = \"(^|\\\\s)(bug\\\\s+#?)(\\\\d+)($|\\\\s)\"\n"
                    + "\tlink = http://bugs.example.com/show_bug.cgi?id=$3\n"
                    + "\tprefix = $1\n"
                    + "\ttext = $2$3\n"
                    + "\tsuffix = $4\n")
            .create();
    ProjectConfig cfg = read(rev);
    assertThat(cfg.getCommentLinkSections())
        .containsExactly(
            StoredCommentLinkInfo.builder("bugzilla")
                .setMatch("(^|\\s)(bug\\s+#?)(\\d+)($|\\s)")
                .setLink("http://bugs.example.com/show_bug.cgi?id=$3")
                .setPrefix("$1")
                .setSuffix("$4")
                .setText("$2$3")
                .setOverrideOnly(false)
                .build());
  }

  @Test
  public void readCommentLinksNoLinkButEnabled() throws Exception {
    RevCommit rev =
        tr.commit().add("project.config", "[commentlink \"bugzilla\"]\n \tenabled = true").create();
    ProjectConfig cfg = read(rev);
    assertThat(cfg.getCommentLinkSections())
        .containsExactly(StoredCommentLinkInfo.enabled("bugzilla"));
    assertThat(Iterables.getOnlyElement(cfg.getCommentLinkSections()).getEnabled()).isNull();
  }

  @Test
  public void readCommentLinksNoLinkAndDisabled() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("project.config", "[commentlink \"bugzilla\"]\n \tenabled = false")
            .create();
    ProjectConfig cfg = read(rev);
    assertThat(cfg.getCommentLinkSections())
        .containsExactly(StoredCommentLinkInfo.disabled("bugzilla"));
    assertThat(Iterables.getOnlyElement(cfg.getCommentLinkSections()).getEnabled()).isFalse();
  }

  @Test
  public void readCommentLinksMissingEnabled() throws Exception {
    RevCommit rev =
        tr.commit()
            .add(
                "project.config",
                "[commentlink \"bugzilla\"]\n \tlink = http://bugs.example.com/show_bug.cgi?id=$2"
                    + "\n \tmatch = \"(bug\\\\s+#?)(\\\\d+)\"\n")
            .create();
    ProjectConfig cfg = read(rev);
    assertThat(cfg.getCommentLinkSections())
        .containsExactly(
            StoredCommentLinkInfo.builder("bugzilla")
                .setMatch("(bug\\s+#?)(\\d+)")
                .setLink("http://bugs.example.com/show_bug.cgi?id=$2")
                .build());
    StoredCommentLinkInfo stored = Iterables.getOnlyElement(cfg.getCommentLinkSections());
    assertThat(StoredCommentLinkInfo.fromInfo(stored.toInfo(), stored.getEnabled()))
        .isEqualTo(stored);
  }

  @Test
  public void readCommentLinkInvalidPattern() throws Exception {
    RevCommit rev =
        tr.commit()
            .add(
                "project.config",
                "[commentlink \"bugzilla\"]\n"
                    + "\tmatch = \"(bugs{+#?)(d+)\"\n"
                    + "\tlink = http://bugs.example.com/show_bug.cgi?id=$2")
            .create();
    ProjectConfig cfg = read(rev);
    assertThat(cfg.getCommentLinkSections()).isEmpty();

    boolean atLeastJava17 = RuntimeVersion.isAtLeast17();
    assertThat(cfg.getValidationErrors())
        .containsExactly(
            ValidationError.create(
                "project.config: Invalid pattern \"(bugs{+#?)(d+)\" in commentlink.bugzilla.match: "
                    + "Illegal repetition near index "
                    + (atLeastJava17 ? "6" : "4")
                    + "\n"
                    + "(bugs{+#?)(d+)\n"
                    + "    "
                    + (atLeastJava17 ? "  " : "")
                    + "^"));
  }

  @Test
  public void readCommentLinkMatchButNoLink() throws Exception {
    RevCommit rev =
        tr.commit()
            .add("project.config", "[commentlink \"bugzilla\"]\n" + "\tmatch = \"(bugs#?)(d+)\"\n")
            .create();
    ProjectConfig cfg = read(rev);
    assertThat(cfg.getCommentLinkSections()).isEmpty();
    assertThat(cfg.getValidationErrors())
        .containsExactly(
            ValidationError.create(
                "project.config: Error in pattern \"(bugs#?)(d+)\" in commentlink.bugzilla.match: "
                    + "commentlink.bugzilla must have link specified"));
  }

  @Test
  public void readAllProjectsBaseConfigFromSitePaths() throws Exception {
    ProjectConfig cfg = factory.create(ALL_PROJECTS);
    cfg.load(db);
    assertThat(cfg.getProject().getBooleanConfig(REQUIRE_CHANGE_ID))
        .isEqualTo(InheritableBoolean.INHERIT);

    writeDefaultAllProjectsConfig("[receive]", "requireChangeId = false");

    cfg.load(db);
    assertThat(cfg.getProject().getBooleanConfig(REQUIRE_CHANGE_ID))
        .isEqualTo(InheritableBoolean.FALSE);
  }

  @Test
  public void readOtherProjectIgnoresAllProjectsBaseConfig() throws Exception {
    ProjectConfig cfg = factory.create(Project.nameKey("test"));
    cfg.load(db);
    assertThat(cfg.getProject().getBooleanConfig(REQUIRE_CHANGE_ID))
        .isEqualTo(InheritableBoolean.INHERIT);

    writeDefaultAllProjectsConfig("[receive]", "requireChangeId = false");

    cfg.load(db);
    // If we went through ProjectState, then this would return FALSE, since project.config for
    // All-Projects would inherit from all_projects.config, and this project would inherit from
    // All-Projects. But in ProjectConfig itself, there is no inheritance from All-Projects, so this
    // continues to return the default.
    assertThat(cfg.getProject().getBooleanConfig(REQUIRE_CHANGE_ID))
        .isEqualTo(InheritableBoolean.INHERIT);
  }

  @Test
  public void accountsSectionIsUnsetIfNoSameGroupVisibilityIsSet() throws Exception {
    RevCommit rev =
        tr.commit()
            .add(
                "project.config",
                "[commentlink \"bugzilla\"]\n"
                    + "\tmatch = \"(bug\\\\s+#?)(\\\\d+)\"\n"
                    + "\tlink = http://bugs.example.com/show_bug.cgi?id=$2\n"
                    + "[accounts]\n"
                    + "  sameGroupVisibility = group Staff\n")
            .create();
    update(rev);

    ProjectConfig cfg = read(rev);
    cfg.setAccountsSection(AccountsSection.create(ImmutableList.of()));
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo(
            "[commentlink \"bugzilla\"]\n"
                + "\tmatch = \"(bug\\\\s+#?)(\\\\d+)\"\n"
                + "\tlink = http://bugs.example.com/show_bug.cgi?id=$2\n");
  }

  @Test
  public void contributorSectionIsUnsetIfNoContributorAgreementIsSet() throws Exception {
    RevCommit rev =
        tr.commit()
            .add(
                "project.config",
                "[commentlink \"bugzilla\"]\n"
                    + "\tmatch = \"(bug\\\\s+#?)(\\\\d+)\"\n"
                    + "\tlink = http://bugs.example.com/show_bug.cgi?id=$2\n"
                    + "[contributor-agreement \"Individual\"]\n"
                    + "  accepted = group Developers\n"
                    + "  accepted = group Staff\n")
            .create();
    update(rev);

    ProjectConfig cfg = read(rev);
    ContributorAgreement.Builder section = cfg.getContributorAgreement("Individual").toBuilder();
    section.setAccepted(ImmutableList.of());
    cfg.upsertContributorAgreement(section.build());
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo(
            "[commentlink \"bugzilla\"]\n"
                + "\tmatch = \"(bug\\\\s+#?)(\\\\d+)\"\n"
                + "\tlink = http://bugs.example.com/show_bug.cgi?id=$2\n");
  }

  @Test
  public void notifySectionIsUnsetIfNoNotificationsAreSet() throws Exception {
    RevCommit rev =
        tr.commit()
            .add(
                "project.config",
                "[commentlink \"bugzilla\"]\n"
                    + "\tmatch = \"(bug\\\\s+#?)(\\\\d+)\"\n"
                    + "\tlink = http://bugs.example.com/show_bug.cgi?id=$2\n"
                    + "[notify \"name\"]\n"
                    + "  email = example@example.com\n")
            .create();
    update(rev);

    ProjectConfig cfg = read(rev);
    cfg.getNotifyConfigs().clear();
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo(
            "[commentlink \"bugzilla\"]\n"
                + "\tmatch = \"(bug\\\\s+#?)(\\\\d+)\"\n"
                + "\tlink = http://bugs.example.com/show_bug.cgi?id=$2\n");
  }

  @Test
  public void commentLinkSectionIsUnsetIfNoCommentLinksAreSet() throws Exception {
    RevCommit rev =
        tr.commit()
            .add(
                "project.config",
                "[commentlink \"bugzilla\"]\n"
                    + "\tmatch = \"(bug\\\\s+#?)(\\\\d+)\"\n"
                    + "\tlink = http://bugs.example.com/show_bug.cgi?id=$2\n"
                    + "[notify \"name\"]\n"
                    + "  email = example@example.com\n")
            .create();
    update(rev);

    ProjectConfig cfg = read(rev);
    cfg.getCommentLinkSections().clear();
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo("[notify \"name\"]\n\temail = example@example.com\n");
  }

  @Test
  public void submitRequirementSectionIsUnsetIfNoSubmitRequirementsAreSet() throws Exception {
    RevCommit rev =
        tr.commit()
            .add(
                "project.config",
                "[submit-requirement \"Code-Review\"]\n"
                    + "  description =  At least one Code Review +2\n"
                    + "  applicableIf =branch(refs/heads/master)\n"
                    + "  submittableIf =  label(Code-Review, +2)\n"
                    + "[notify \"name\"]\n"
                    + "  email = example@example.com\n")
            .create();
    update(rev);

    ProjectConfig cfg = read(rev);
    cfg.getSubmitRequirementSections().clear();
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo("[notify \"name\"]\n\temail = example@example.com\n");
  }

  @Test
  public void pluginSectionIsUnsetIfAllPluginConfigsAreEmpty() throws Exception {
    RevCommit rev =
        tr.commit()
            .add(
                "project.config",
                "[commentlink \"bugzilla\"]\n"
                    + "\tmatch = \"(bug\\\\s+#?)(\\\\d+)\"\n"
                    + "\tlink = http://bugs.example.com/show_bug.cgi?id=$2\n"
                    + "[plugin \"somePlugin\"]\n"
                    + "  key = value\n")
            .create();
    update(rev);

    ProjectConfig cfg = read(rev);
    cfg.updatePluginConfig("somePlugin", pluginCfg -> pluginCfg.unset("key"));
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo(
            "[commentlink \"bugzilla\"]\n"
                + "\tmatch = \"(bug\\\\s+#?)(\\\\d+)\"\n"
                + "\tlink = http://bugs.example.com/show_bug.cgi?id=$2\n");
  }

  @Test
  public void allProjectsProjectConfigInSitePaths_hashForKeyChangesWithnFileChanges()
      throws Exception {
    Path tmp = Files.createTempFile("gerrit_test_", "_site");
    Files.deleteIfExists(tmp);
    SitePaths sitePaths = new SitePaths(tmp);
    FileBasedAllProjectsConfigProvider allProjectsConfigProvider =
        new FileBasedAllProjectsConfigProvider(sitePaths);
    byte[] hashedContents =
        ProjectCacheImpl.allProjectsFileProjectConfigHash(
            allProjectsConfigProvider.get(ALL_PROJECTS));
    assertThat(hashedContents).isEqualTo(new byte[16]); // Empty/absent config
    FileBasedConfig fileBasedConfig =
        new FileBasedConfig(
            sitePaths
                .etc_dir
                .resolve(ALL_PROJECTS.get())
                .resolve(ProjectConfig.PROJECT_CONFIG)
                .toFile(),
            FS.DETECTED);
    fileBasedConfig.setString("plugin", "my-plugin", "key", "value");
    fileBasedConfig.save();
    hashedContents =
        ProjectCacheImpl.allProjectsFileProjectConfigHash(
            allProjectsConfigProvider.get(ALL_PROJECTS));
    assertThat(hashedContents)
        .isEqualTo(
            new byte[] {
              -53, -97, -1, 52, -119, 104, -13, -41, -7, 82, -90, 126, -32, -13, -91, 49
            });
  }

  private Path writeDefaultAllProjectsConfig(String... lines) throws IOException {
    Path dir = sitePaths.etc_dir.resolve(ALL_PROJECTS.get());
    Files.createDirectories(dir);
    return Files.write(dir.resolve("project.config"), ImmutableList.copyOf(lines));
  }

  private ProjectConfig read(RevCommit rev) throws IOException, ConfigInvalidException {
    ProjectConfig cfg = factory.create(Project.nameKey("test"));
    cfg.load(db, rev);
    return cfg;
  }

  private RevCommit commit(ProjectConfig cfg)
      throws IOException, MissingObjectException, IncorrectObjectTypeException {
    try (MetaDataUpdate md =
        new MetaDataUpdate(GitReferenceUpdated.DISABLED, cfg.getProject().getNameKey(), db)) {
      tr.tick(5);
      tr.setAuthorAndCommitter(md.getCommitBuilder());
      md.setMessage("Edit\n");
      cfg.commit(md);

      Ref ref = db.exactRef(RefNames.REFS_CONFIG);
      return tr.getRevWalk().parseCommit(ref.getObjectId());
    }
  }

  private void update(RevCommit rev) throws Exception {
    RefUpdate u = db.updateRef(RefNames.REFS_CONFIG);
    u.disableRefLog();
    u.setNewObjectId(rev);
    Result result = u.forceUpdate();
    assertWithMessage("Cannot update ref for test: " + result)
        .that(result)
        .isAnyOf(Result.FAST_FORWARD, Result.FORCED, Result.NEW, Result.NO_CHANGE);
  }

  private String text(RevCommit rev, String path) throws Exception {
    RevObject blob = tr.get(rev.getTree(), path);
    byte[] data = db.open(blob).getCachedBytes(Integer.MAX_VALUE);
    return RawParseUtils.decode(data);
  }

  private static String group(GroupReference g) {
    return g.getUUID().get() + "\t" + g.getName() + "\n";
  }
}
