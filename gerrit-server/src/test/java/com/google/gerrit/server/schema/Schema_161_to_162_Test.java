// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.schema.Schema_162.RawConfig;
import com.google.gerrit.testutil.SchemaUpgradeTestEnvironment;
import com.google.gerrit.testutil.TestUpdateUI;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class Schema_161_to_162_Test {
  @Rule public SchemaUpgradeTestEnvironment testEnv = new SchemaUpgradeTestEnvironment();

  @Inject private AllProjectsName allProjects;
  @Inject private GerritApi gApi;
  @Inject private GitRepositoryManager repoManager;
  @Inject private Schema_162 schema162;

  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    testEnv.getInjector().injectMembers(this);
    db = testEnv.getDb();
  }

  @Test
  public void setExplicitSubmitAction() throws Exception {
    assertThat(readRawSubmitAction(allProjects)).isNull();

    Project.NameKey p1 = new Project.NameKey("p1");
    createProject(p1, null);
    assertThat(readRawSubmitAction(p1)).isNull();

    Project.NameKey p2 = new Project.NameKey("p2");
    createProject(p2, SubmitType.CHERRY_PICK);
    assertThat(readRawSubmitAction(p2)).isEqualTo("cherry pick");

    schema162.migrateData(db, new TestUpdateUI());

    assertThat(readRawSubmitAction(allProjects)).isEqualTo("merge if necessary");
    assertThat(readRawSubmitAction(p1)).isEqualTo("merge if necessary");
    assertThat(readRawSubmitAction(p2)).isEqualTo("cherry pick");
  }

  private void createProject(Project.NameKey p, @Nullable SubmitType submitType) throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = p.get();
    in.submitType = submitType;
    gApi.projects().create(in);
  }

  @Nullable
  private String readRawSubmitAction(Project.NameKey p) throws Exception {
    try (Repository repo = repoManager.openRepository(p)) {
      RawConfig rc = new RawConfig(p);
      rc.load(repo);
      return rc.rawConfig.getString("submit", null, "action");
    }
  }
}
