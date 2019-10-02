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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.fetch;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.util.Arrays;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class ProjectLevelConfigIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  @Before
  public void setUp() throws Exception {
    fetch(testRepo, RefNames.REFS_CONFIG + ":refs/heads/config");
    testRepo.reset("refs/heads/config");
  }

  @Test
  public void accessProjectSpecificConfig() throws Exception {
    String configName = "test.config";
    Config cfg = new Config();
    cfg.setString("s1", null, "k1", "v1");
    cfg.setString("s2", "ss", "k2", "v2");
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(), testRepo, "Create Project Level Config", configName, cfg.toText());
    push.to(RefNames.REFS_CONFIG);

    ProjectState state = projectCache.get(project);
    assertThat(state.getConfig(configName).get().toText()).isEqualTo(cfg.toText());
  }

  @Test
  public void nonExistingConfig() {
    ProjectState state = projectCache.get(project);
    assertThat(state.getConfig("test.config").get().toText()).isEqualTo("");
  }

  @Test
  public void withInheritance() throws Exception {
    String configName = "test.config";

    Config parentCfg = new Config();
    parentCfg.setString("s1", null, "k1", "parentValue1");
    parentCfg.setString("s1", null, "k2", "parentValue2");
    parentCfg.setString("s2", "ss", "k3", "parentValue3");
    parentCfg.setString("s2", "ss", "k4", "parentValue4");

    pushFactory
        .create(
            admin.newIdent(),
            testRepo,
            "Create Project Level Config",
            configName,
            parentCfg.toText())
        .to(RefNames.REFS_CONFIG)
        .assertOkStatus();

    Project.NameKey childProject = projectOperations.newProject().parent(project).create();
    TestRepository<?> childTestRepo = cloneProject(childProject);
    fetch(childTestRepo, RefNames.REFS_CONFIG + ":refs/heads/config");
    childTestRepo.reset("refs/heads/config");

    Config cfg = new Config();
    cfg.setString("s1", null, "k1", "childValue1");
    cfg.setString("s2", "ss", "k3", "childValue2");

    pushFactory
        .create(
            admin.newIdent(),
            childTestRepo,
            "Create Project Level Config",
            configName,
            cfg.toText())
        .to(RefNames.REFS_CONFIG)
        .assertOkStatus();

    ProjectState state = projectCache.get(childProject);

    Config expectedCfg = new Config();
    expectedCfg.setString("s1", null, "k1", "childValue1");
    expectedCfg.setString("s1", null, "k2", "parentValue2");
    expectedCfg.setString("s2", "ss", "k3", "childValue2");
    expectedCfg.setString("s2", "ss", "k4", "parentValue4");

    assertThat(state.getConfig(configName).getWithInheritance().toText())
        .isEqualTo(expectedCfg.toText());

    assertThat(state.getConfig(configName).get().toText()).isEqualTo(cfg.toText());
  }

  @Test
  public void withMergedInheritance() throws Exception {
    String configName = "test.config";

    Config parentCfg = new Config();
    parentCfg.setString("s1", null, "k1", "parentValue1");
    parentCfg.setString("s1", null, "k2", "parentValue2");
    parentCfg.setString("s2", "ss", "k3", "parentValue3");
    parentCfg.setString("s2", "ss", "k4", "parentValue4");

    pushFactory
        .create(
            admin.newIdent(),
            testRepo,
            "Create Project Level Config",
            configName,
            parentCfg.toText())
        .to(RefNames.REFS_CONFIG)
        .assertOkStatus();

    Project.NameKey childProject = projectOperations.newProject().parent(project).create();
    TestRepository<?> childTestRepo = cloneProject(childProject);
    fetch(childTestRepo, RefNames.REFS_CONFIG + ":refs/heads/config");
    childTestRepo.reset("refs/heads/config");

    Config cfg = new Config();
    cfg.setString("s1", null, "k1", "parentValue1");
    cfg.setString("s1", null, "k2", "parentValue2");
    cfg.setString("s2", "ss", "k3", "parentValue3");
    cfg.setString("s2", "ss", "k4", "parentValue4");
    cfg.setString("s1", null, "k1", "childValue1");
    cfg.setString("s2", "ss", "k3", "childValue2");
    cfg.setString("s3", null, "k5", "childValue3");
    cfg.setString("s3", "ss", "k6", "childValue4");

    pushFactory
        .create(
            admin.newIdent(),
            childTestRepo,
            "Create Project Level Config",
            configName,
            cfg.toText())
        .to(RefNames.REFS_CONFIG)
        .assertOkStatus();

    ProjectState state = projectCache.get(childProject);

    Config expectedCfg = new Config();
    expectedCfg.setStringList("s1", null, "k1", Arrays.asList("childValue1", "parentValue1"));
    expectedCfg.setString("s1", null, "k2", "parentValue2");
    expectedCfg.setStringList("s2", "ss", "k3", Arrays.asList("childValue2", "parentValue3"));
    expectedCfg.setString("s2", "ss", "k4", "parentValue4");
    expectedCfg.setString("s3", null, "k5", "childValue3");
    expectedCfg.setString("s3", "ss", "k6", "childValue4");

    assertThat(state.getConfig(configName).getWithInheritance(true).toText())
        .isEqualTo(expectedCfg.toText());

    assertThat(state.getConfig(configName).get().toText()).isEqualTo(cfg.toText());
  }
}
