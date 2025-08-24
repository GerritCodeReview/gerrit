// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import static com.google.gerrit.entities.LabelId.VERIFIED;
import static com.google.gerrit.server.schema.AclUtil.grant;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.pgm.init.api.AllProjectsConfig;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class InitLabels implements InitStep {
  private static final String KEY_LABEL = "label";

  private final ConsoleUI ui;
  private final AllProjectsConfig allProjectsConfig;
  private AllProjectsName allProjectsName;
  private PersonIdent serverUser;
  private ProjectConfig.Factory projectConfigFactory;
  private SystemGroupBackend systemGroupBackend;

  private boolean installVerified;

  @Inject
  InitLabels(ConsoleUI ui, AllProjectsConfig allProjectsConfig) {
    this.ui = ui;
    this.allProjectsConfig = allProjectsConfig;
  }

  @Inject(optional = true)
  void setAllProjectsName(AllProjectsName allProjectsName) {
    this.allProjectsName = allProjectsName;
  }

  @Inject(optional = true)
  void setProjectConfigFactory(ProjectConfig.Factory projectConfigFactory) {
    this.projectConfigFactory = projectConfigFactory;
  }

  @Inject(optional = true)
  void setGerritPersonIdent(@GerritPersonIdent PersonIdent serverUser) {
    this.serverUser = serverUser;
  }

  @Inject(optional = true)
  void setSystemGroupBackend(SystemGroupBackend systemGroupBackend) {
    this.systemGroupBackend = systemGroupBackend;
  }

  @Override
  public void run() throws Exception {
    Config cfg = allProjectsConfig.load().getConfig();
    if (cfg == null || !cfg.getSubsections(KEY_LABEL).contains(VERIFIED)) {
      ui.header("Review Labels");
      installVerified = ui.yesno(false, "Install Verified label");
    }
  }

  @Override
  public void postRun() throws Exception {
    if (installVerified) {
      installVerified();
    }
  }

  private void installVerified() throws IOException, ConfigInvalidException {
    try (Repository git = allProjectsConfig.openGitRepository();
        MetaDataUpdate md =
            new MetaDataUpdate(GitReferenceUpdated.DISABLED, allProjectsName, git)) {
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);
      md.setMessage("Configured 'Verified' submit requirement");
      ProjectConfig config = projectConfigFactory.read(md);
      LabelType verifiedLabel = getDefaultVerifiedReviewLabel();
      config.upsertLabelType(verifiedLabel);
      config.upsertSubmitRequirement(getDefaultVerifiedSubmitRequirement());
      GroupReference owners = systemGroupBackend.getGroup(SystemGroupBackend.PROJECT_OWNERS);
      GroupReference admins = GroupReference.create("Administrators");
      config.upsertAccessSection(
          AccessSection.HEADS,
          heads -> {
            grant(config, heads, verifiedLabel, -1, 1, admins, owners);
          });
      config.upsertAccessSection(
          RefNames.REFS_CONFIG,
          meta -> {
            grant(config, meta, verifiedLabel, -1, 1, admins, owners);
          });
      config.commit(md);
    }
  }

  private static LabelType getDefaultVerifiedReviewLabel() {
    return LabelType.builder(
            VERIFIED,
            ImmutableList.of(
                LabelValue.create((short) 1, "Verified"),
                LabelValue.create((short) 0, "No score"),
                LabelValue.create((short) -1, "Fails")))
        .setCopyCondition("changekind:NO_CHANGE OR changekind:NO_CODE_CHANGE")
        .build();
  }

  private static SubmitRequirement getDefaultVerifiedSubmitRequirement() {
    return SubmitRequirement.builder()
        .setName(VERIFIED)
        .setDescription(
            Optional.of(
                String.format("At least one maximum vote for label '%s' is required", VERIFIED)))
        .setSubmittabilityExpression(
            SubmitRequirementExpression.create(
                String.format("label:%s=MAX AND -label:%s=MIN", VERIFIED, VERIFIED)))
        .setAllowOverrideInChildProjects(true)
        .build();
  }
}
