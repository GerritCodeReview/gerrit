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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.common.collect.Iterables;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ProjectConfigTest extends LocalDiskRepositoryTestCase {
  private static final String LABEL_SCORES_CONFIG =
      "  copyMinScore = "
          + !LabelType.DEF_COPY_MIN_SCORE
          + "\n" //
          + "  copyMaxScore = "
          + !LabelType.DEF_COPY_MAX_SCORE
          + "\n" //
          + "  copyAllScoresOnMergeFirstParentUpdate = "
          + !LabelType.DEF_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE
          + "\n" //
          + "  copyAllScoresOnTrivialRebase = "
          + !LabelType.DEF_COPY_ALL_SCORES_ON_TRIVIAL_REBASE
          + "\n" //
          + "  copyAllScoresIfNoCodeChange = "
          + !LabelType.DEF_COPY_ALL_SCORES_IF_NO_CODE_CHANGE
          + "\n" //
          + "  copyAllScoresIfNoChange = "
          + !LabelType.DEF_COPY_ALL_SCORES_IF_NO_CHANGE
          + "\n";

  private final GroupReference developers =
      new GroupReference(new AccountGroup.UUID("X"), "Developers");
  private final GroupReference staff = new GroupReference(new AccountGroup.UUID("Y"), "Staff");

  private Repository db;
  private TestRepository<Repository> util;

  @BeforeClass
  public static void setUpOnce() {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    db = createBareRepository();
    util = new TestRepository<>(db);
  }

  @Test
  public void readConfig() throws Exception {
    RevCommit rev =
        util.commit(
            util.tree( //
                util.file("groups", util.blob(group(developers))), //
                util.file(
                    "project.config",
                    util.blob(
                        "" //
                            + "[access \"refs/heads/*\"]\n" //
                            + "  exclusiveGroupPermissions = read submit create\n" //
                            + "  submit = group Developers\n" //
                            + "  push = group Developers\n" //
                            + "  read = group Developers\n" //
                            + "[accounts]\n" //
                            + "  sameGroupVisibility = deny group Developers\n" //
                            + "  sameGroupVisibility = block group Staff\n" //
                            + "[contributor-agreement \"Individual\"]\n" //
                            + "  description = A simple description\n" //
                            + "  accepted = group Developers\n" //
                            + "  accepted = group Staff\n" //
                            + "  autoVerify = group Developers\n" //
                            + "  agreementUrl = http://www.example.com/agree\n")) //
                ));

    ProjectConfig cfg = read(rev);
    assertThat(cfg.getAccountsSection().getSameGroupVisibility()).hasSize(2);
    ContributorAgreement ca = cfg.getContributorAgreement("Individual");
    assertThat(ca.getName()).isEqualTo("Individual");
    assertThat(ca.getDescription()).isEqualTo("A simple description");
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
        util.commit(
            util.tree( //
                util.file("groups", util.blob(group(developers))), //
                util.file(
                    "project.config",
                    util.blob(
                        "" //
                            + "[label \"CustomLabel\"]\n" //
                            + "  value = -1 Negative\n" //
                            + "  value =  0 No Score\n" //
                            + "  value =  1 Positive\n")) //
                ));

    ProjectConfig cfg = read(rev);
    Map<String, LabelType> labels = cfg.getLabelSections();
    Short dv = labels.entrySet().iterator().next().getValue().getDefaultValue();
    assertThat((int) dv).isEqualTo(0);
  }

  @Test
  public void readConfigLabelDefaultValueInRange() throws Exception {
    RevCommit rev =
        util.commit(
            util.tree( //
                util.file("groups", util.blob(group(developers))), //
                util.file(
                    "project.config",
                    util.blob(
                        "" //
                            + "[label \"CustomLabel\"]\n" //
                            + "  value = -1 Negative\n" //
                            + "  value =  0 No Score\n" //
                            + "  value =  1 Positive\n" //
                            + "  defaultValue = -1\n")) //
                ));

    ProjectConfig cfg = read(rev);
    Map<String, LabelType> labels = cfg.getLabelSections();
    Short dv = labels.entrySet().iterator().next().getValue().getDefaultValue();
    assertThat((int) dv).isEqualTo(-1);
  }

  @Test
  public void readConfigLabelDefaultValueNotInRange() throws Exception {
    RevCommit rev =
        util.commit(
            util.tree( //
                util.file("groups", util.blob(group(developers))), //
                util.file(
                    "project.config",
                    util.blob(
                        "" //
                            + "[label \"CustomLabel\"]\n" //
                            + "  value = -1 Negative\n" //
                            + "  value =  0 No Score\n" //
                            + "  value =  1 Positive\n" //
                            + "  defaultValue = -2\n")) //
                ));

    ProjectConfig cfg = read(rev);
    assertThat(cfg.getValidationErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(cfg.getValidationErrors()).getMessage())
        .isEqualTo("project.config: Invalid defaultValue \"-2\" for label \"CustomLabel\"");
  }

  @Test
  public void readConfigLabelScores() throws Exception {
    RevCommit rev =
        util.commit(
            util.tree( //
                util.file("groups", util.blob(group(developers))), //
                util.file(
                    "project.config",
                    util.blob(
                        "" //
                            + "[label \"CustomLabel\"]\n" //
                            + LABEL_SCORES_CONFIG)) //
                ));

    ProjectConfig cfg = read(rev);
    Map<String, LabelType> labels = cfg.getLabelSections();
    LabelType type = labels.entrySet().iterator().next().getValue();
    assertThat(type.isCopyMinScore()).isNotEqualTo(LabelType.DEF_COPY_MIN_SCORE);
    assertThat(type.isCopyMaxScore()).isNotEqualTo(LabelType.DEF_COPY_MAX_SCORE);
    assertThat(type.isCopyAllScoresOnMergeFirstParentUpdate())
        .isNotEqualTo(LabelType.DEF_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE);
    assertThat(type.isCopyAllScoresOnTrivialRebase())
        .isNotEqualTo(LabelType.DEF_COPY_ALL_SCORES_ON_TRIVIAL_REBASE);
    assertThat(type.isCopyAllScoresIfNoCodeChange())
        .isNotEqualTo(LabelType.DEF_COPY_ALL_SCORES_IF_NO_CODE_CHANGE);
    assertThat(type.isCopyAllScoresIfNoChange())
        .isNotEqualTo(LabelType.DEF_COPY_ALL_SCORES_IF_NO_CHANGE);
  }

  @Test
  public void editConfig() throws Exception {
    RevCommit rev =
        util.commit(
            util.tree( //
                util.file("groups", util.blob(group(developers))), //
                util.file(
                    "project.config",
                    util.blob(
                        "" //
                            + "[access \"refs/heads/*\"]\n" //
                            + "  exclusiveGroupPermissions = read submit\n" //
                            + "  submit = group Developers\n" //
                            + "  upload = group Developers\n" //
                            + "  read = group Developers\n" //
                            + "[accounts]\n" //
                            + "  sameGroupVisibility = deny group Developers\n" //
                            + "  sameGroupVisibility = block group Staff\n" //
                            + "[contributor-agreement \"Individual\"]\n" //
                            + "  description = A simple description\n" //
                            + "  accepted = group Developers\n" //
                            + "  autoVerify = group Developers\n" //
                            + "  agreementUrl = http://www.example.com/agree\n" //
                            + "[label \"CustomLabel\"]\n" //
                            + LABEL_SCORES_CONFIG)) //
                ));
    update(rev);

    ProjectConfig cfg = read(rev);
    AccessSection section = cfg.getAccessSection("refs/heads/*");
    cfg.getAccountsSection()
        .setSameGroupVisibility(Collections.singletonList(new PermissionRule(cfg.resolve(staff))));
    Permission submit = section.getPermission(Permission.SUBMIT);
    submit.add(new PermissionRule(cfg.resolve(staff)));
    ContributorAgreement ca = cfg.getContributorAgreement("Individual");
    ca.setAccepted(Collections.singletonList(new PermissionRule(cfg.resolve(staff))));
    ca.setAutoVerify(null);
    ca.setDescription("A new description");
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo(
            "" //
                + "[access \"refs/heads/*\"]\n" //
                + "  exclusiveGroupPermissions = read submit\n" //
                + "  submit = group Developers\n" //
                + "\tsubmit = group Staff\n" //
                + "  upload = group Developers\n" //
                + "  read = group Developers\n" //
                + "[accounts]\n" //
                + "  sameGroupVisibility = group Staff\n" //
                + "[contributor-agreement \"Individual\"]\n" //
                + "  description = A new description\n" //
                + "  accepted = group Staff\n" //
                + "  agreementUrl = http://www.example.com/agree\n"
                + "[label \"CustomLabel\"]\n" //
                + LABEL_SCORES_CONFIG
                + "\tfunction = MaxWithBlock\n" // label gets this function when it is created
                + "\tdefaultValue = 0\n"); //  label gets this value when it is created
  }

  @Test
  public void editConfigMissingGroupTableEntry() throws Exception {
    RevCommit rev =
        util.commit(
            util.tree( //
                util.file("groups", util.blob(group(developers))), //
                util.file(
                    "project.config",
                    util.blob(
                        "" //
                            + "[access \"refs/heads/*\"]\n" //
                            + "  exclusiveGroupPermissions = read submit\n" //
                            + "  submit = group People Who Can Submit\n" //
                            + "  upload = group Developers\n" //
                            + "  read = group Developers\n")) //
                ));
    update(rev);

    ProjectConfig cfg = read(rev);
    AccessSection section = cfg.getAccessSection("refs/heads/*");
    Permission submit = section.getPermission(Permission.SUBMIT);
    submit.add(new PermissionRule(cfg.resolve(staff)));
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo(
            "" //
                + "[access \"refs/heads/*\"]\n" //
                + "  exclusiveGroupPermissions = read submit\n" //
                + "  submit = group People Who Can Submit\n" //
                + "\tsubmit = group Staff\n" //
                + "  upload = group Developers\n" //
                + "  read = group Developers\n");
  }

  @Test
  public void readExistingPluginConfig() throws Exception {
    RevCommit rev =
        util.commit(
            util.tree( //
                util.file(
                    "project.config",
                    util.blob(
                        "" //
                            + "[plugin \"somePlugin\"]\n" //
                            + "key1 = value1\n" //
                            + "key2 = value2\n")) //
                ));
    update(rev);

    ProjectConfig cfg = read(rev);
    PluginConfig pluginCfg = cfg.getPluginConfig("somePlugin");
    assertThat(pluginCfg.getNames().size()).isEqualTo(2);
    assertThat(pluginCfg.getString("key1")).isEqualTo("value1");
    assertThat(pluginCfg.getString("key2")).isEqualTo("value2");
  }

  @Test
  public void readUnexistingPluginConfig() throws Exception {
    ProjectConfig cfg = new ProjectConfig(new Project.NameKey("test"));
    cfg.load(db);
    PluginConfig pluginCfg = cfg.getPluginConfig("somePlugin");
    assertThat(pluginCfg.getNames()).isEmpty();
  }

  @Test
  public void editPluginConfig() throws Exception {
    RevCommit rev =
        util.commit(
            util.tree( //
                util.file(
                    "project.config",
                    util.blob(
                        "" //
                            + "[plugin \"somePlugin\"]\n" //
                            + "  key1 = value1\n" //
                            + "  key2 = value2\n")) //
                ));
    update(rev);

    ProjectConfig cfg = read(rev);
    PluginConfig pluginCfg = cfg.getPluginConfig("somePlugin");
    pluginCfg.setString("key1", "updatedValue1");
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo(
            "" //
                + "[plugin \"somePlugin\"]\n" //
                + "\tkey1 = updatedValue1\n" //
                + "\tkey2 = value2\n");
  }

  @Test
  public void readPluginConfigGroupReference() throws Exception {
    RevCommit rev =
        util.commit(
            util.tree( //
                util.file("groups", util.blob(group(developers))), //
                util.file(
                    "project.config",
                    util.blob(
                        "" //
                            + "[plugin \"somePlugin\"]\n" //
                            + "key1 = "
                            + developers.toString()
                            + "\n")) //
                ));
    update(rev);

    ProjectConfig cfg = read(rev);
    PluginConfig pluginCfg = cfg.getPluginConfig("somePlugin");
    assertThat(pluginCfg.getNames().size()).isEqualTo(1);
    assertThat(GroupReference.fromString(pluginCfg.getString("key1"))).isEqualTo(developers);
  }

  @Test
  public void readPluginConfigGroupReferenceNotInGroupsFile() throws Exception {
    RevCommit rev =
        util.commit(
            util.tree( //
                util.file("groups", util.blob(group(developers))), //
                util.file(
                    "project.config",
                    util.blob(
                        "" //
                            + "[plugin \"somePlugin\"]\n" //
                            + "key1 = "
                            + staff.toString()
                            + "\n")) //
                ));
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
        util.commit(
            util.tree( //
                util.file("groups", util.blob(group(developers))), //
                util.file(
                    "project.config",
                    util.blob(
                        "" //
                            + "[plugin \"somePlugin\"]\n" //
                            + "key1 = "
                            + developers.toString()
                            + "\n")) //
                ));
    update(rev);

    ProjectConfig cfg = read(rev);
    PluginConfig pluginCfg = cfg.getPluginConfig("somePlugin");
    assertThat(pluginCfg.getNames().size()).isEqualTo(1);
    assertThat(GroupReference.fromString(pluginCfg.getString("key1"))).isEqualTo(developers);

    pluginCfg.setString("key1", staff.toString());
    rev = commit(cfg);
    assertThat(text(rev, "project.config"))
        .isEqualTo(
            "" //
                + "[plugin \"somePlugin\"]\n" //
                + "\tkey1 = "
                + staff.toString()
                + "\n");
  }

  private ProjectConfig read(RevCommit rev) throws IOException, ConfigInvalidException {
    ProjectConfig cfg = new ProjectConfig(new Project.NameKey("test"));
    cfg.load(db, rev);
    return cfg;
  }

  private RevCommit commit(ProjectConfig cfg)
      throws IOException, MissingObjectException, IncorrectObjectTypeException {
    try (MetaDataUpdate md =
        new MetaDataUpdate(GitReferenceUpdated.DISABLED, cfg.getProject().getNameKey(), db)) {
      util.tick(5);
      util.setAuthorAndCommitter(md.getCommitBuilder());
      md.setMessage("Edit\n");
      cfg.commit(md);

      Ref ref = db.exactRef(RefNames.REFS_CONFIG);
      return util.getRevWalk().parseCommit(ref.getObjectId());
    }
  }

  private void update(RevCommit rev) throws Exception {
    RefUpdate u = db.updateRef(RefNames.REFS_CONFIG);
    u.disableRefLog();
    u.setNewObjectId(rev);
    Result result = u.forceUpdate();
    assert_()
        .withFailureMessage("Cannot update ref for test: " + result)
        .that(result)
        .isAnyOf(Result.FAST_FORWARD, Result.FORCED, Result.NEW, Result.NO_CHANGE);
  }

  private String text(RevCommit rev, String path) throws Exception {
    RevObject blob = util.get(rev.getTree(), path);
    byte[] data = db.open(blob).getCachedBytes(Integer.MAX_VALUE);
    return RawParseUtils.decode(data);
  }

  private static String group(GroupReference g) {
    return g.getUUID().get() + "\t" + g.getName() + "\n";
  }
}
