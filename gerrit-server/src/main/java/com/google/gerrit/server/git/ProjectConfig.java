// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Project.SubmitType;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;

public class ProjectConfig extends VersionedMetaData {
  private static final String PROJECT_CONFIG = "project.config";

  private static final String PROJECT = "project";
  private static final String KEY_DESCRIPTION = "description";

  private static final String ACCESS = "access";
  private static final String KEY_INHERIT_FROM = "inheritFrom";

  private static final String RECEIVE = "receive";
  private static final String KEY_REQUIRE_SIGNED_OFF_BY = "requireSignedOffBy";
  private static final String KEY_REQUIRE_CHANGE_ID = "requireChangeId";
  private static final String KEY_REQUIRE_CONTRIBUTOR_AGREEMENT =
      "requireContributorAgreement";

  private static final String SUBMIT = "submit";
  private static final String KEY_ACTION = "action";
  private static final String KEY_MERGE_CONTENT = "mergeContent";

  private static final SubmitType defaultSubmitAction =
      SubmitType.MERGE_IF_NECESSARY;

  private Project.NameKey projectName;
  private Project project;

  public static ProjectConfig read(MetaDataUpdate update) throws IOException,
      ConfigInvalidException {
    ProjectConfig r = new ProjectConfig(update.getProjectName());
    r.load(update);
    return r;
  }

  public static ProjectConfig read(MetaDataUpdate update, ObjectId id)
      throws IOException, ConfigInvalidException {
    ProjectConfig r = new ProjectConfig(update.getProjectName());
    r.load(update, id);
    return r;
  }

  public ProjectConfig(Project.NameKey projectName) {
    this.projectName = projectName;
  }

  public Project getProject() {
    return project;
  }

  @Override
  protected String getRefName() {
    return GitRepositoryManager.REF_CONFIG;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    Config rc = readConfig(PROJECT_CONFIG);
    project = new Project(projectName);

    Project p = project;
    p.setDescription(rc.getString(PROJECT, null, KEY_DESCRIPTION));
    if (p.getDescription() == null) {
      p.setDescription("");
    }
    p.setParentName(rc.getString(ACCESS, null, KEY_INHERIT_FROM));

    p.setUseContributorAgreements(rc.getBoolean(RECEIVE, KEY_REQUIRE_CONTRIBUTOR_AGREEMENT, false));
    p.setUseSignedOffBy(rc.getBoolean(RECEIVE, KEY_REQUIRE_SIGNED_OFF_BY, false));
    p.setRequireChangeID(rc.getBoolean(RECEIVE, KEY_REQUIRE_CHANGE_ID, false));

    p.setSubmitType(rc.getEnum(SUBMIT, null, KEY_ACTION, defaultSubmitAction));
    p.setUseContentMerge(rc.getBoolean(SUBMIT, null, KEY_MERGE_CONTENT, false));
  }

  @Override
  protected void onSave(CommitBuilder commit) throws IOException,
      ConfigInvalidException {
    if (commit.getMessage() == null || "".equals(commit.getMessage())) {
      commit.setMessage("Updated project configuration\n");
    }

    Config rc = readConfig(PROJECT_CONFIG);
    Project p = project;

    if (p.getDescription() != null && !p.getDescription().isEmpty()) {
      rc.setString(PROJECT, null, KEY_DESCRIPTION, p.getDescription());
    } else {
      rc.unset(PROJECT, null, KEY_DESCRIPTION);
    }
    set(rc, ACCESS, null, KEY_INHERIT_FROM, p.getParentName());

    set(rc, RECEIVE, null, KEY_REQUIRE_CONTRIBUTOR_AGREEMENT, p.isUseContributorAgreements());
    set(rc, RECEIVE, null, KEY_REQUIRE_SIGNED_OFF_BY, p.isUseSignedOffBy());
    set(rc, RECEIVE, null, KEY_REQUIRE_CHANGE_ID, p.isRequireChangeID());

    set(rc, SUBMIT, null, KEY_ACTION, p.getSubmitType(), defaultSubmitAction);
    set(rc, SUBMIT, null, KEY_MERGE_CONTENT, p.isUseContentMerge());

    saveConfig(PROJECT_CONFIG, rc);
  }
}
