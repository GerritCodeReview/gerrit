// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.testing.GerritBaseTests;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class RepositoryConfigTest extends GerritBaseTests {

  private Config cfg;
  private RepositoryConfig repoCfg;

  @Before
  public void setUp() throws Exception {
    cfg = new Config();
    repoCfg = new RepositoryConfig(cfg);
  }

  @Test
  public void defaultSubmitTypeWhenNotConfigured() {
    // Check expected value explicitly rather than depending on constant.
    assertThat(repoCfg.getDefaultSubmitType(Project.nameKey("someProject")))
        .isEqualTo(SubmitType.INHERIT);
  }

  @Test
  public void defaultSubmitTypeForStarFilter() {
    configureDefaultSubmitType("*", SubmitType.CHERRY_PICK);
    assertThat(repoCfg.getDefaultSubmitType(Project.nameKey("someProject")))
        .isEqualTo(SubmitType.CHERRY_PICK);

    configureDefaultSubmitType("*", SubmitType.FAST_FORWARD_ONLY);
    assertThat(repoCfg.getDefaultSubmitType(Project.nameKey("someProject")))
        .isEqualTo(SubmitType.FAST_FORWARD_ONLY);

    configureDefaultSubmitType("*", SubmitType.REBASE_IF_NECESSARY);
    assertThat(repoCfg.getDefaultSubmitType(Project.nameKey("someProject")))
        .isEqualTo(SubmitType.REBASE_IF_NECESSARY);

    configureDefaultSubmitType("*", SubmitType.REBASE_ALWAYS);
    assertThat(repoCfg.getDefaultSubmitType(Project.nameKey("someProject")))
        .isEqualTo(SubmitType.REBASE_ALWAYS);
  }

  @Test
  public void defaultSubmitTypeForSpecificFilter() {
    configureDefaultSubmitType("someProject", SubmitType.CHERRY_PICK);
    assertThat(repoCfg.getDefaultSubmitType(Project.nameKey("someOtherProject")))
        .isEqualTo(RepositoryConfig.DEFAULT_SUBMIT_TYPE);
    assertThat(repoCfg.getDefaultSubmitType(Project.nameKey("someProject")))
        .isEqualTo(SubmitType.CHERRY_PICK);
  }

  @Test
  public void defaultSubmitTypeForStartWithFilter() {
    configureDefaultSubmitType("somePath/somePath/*", SubmitType.REBASE_IF_NECESSARY);
    configureDefaultSubmitType("somePath/*", SubmitType.CHERRY_PICK);
    configureDefaultSubmitType("*", SubmitType.MERGE_ALWAYS);

    assertThat(repoCfg.getDefaultSubmitType(Project.nameKey("someProject")))
        .isEqualTo(SubmitType.MERGE_ALWAYS);

    assertThat(repoCfg.getDefaultSubmitType(Project.nameKey("somePath/someProject")))
        .isEqualTo(SubmitType.CHERRY_PICK);

    assertThat(repoCfg.getDefaultSubmitType(Project.nameKey("somePath/somePath/someProject")))
        .isEqualTo(SubmitType.REBASE_IF_NECESSARY);
  }

  private void configureDefaultSubmitType(String projectFilter, SubmitType submitType) {
    cfg.setString(
        RepositoryConfig.SECTION_NAME,
        projectFilter,
        RepositoryConfig.DEFAULT_SUBMIT_TYPE_NAME,
        submitType.toString());
  }

  @Test
  public void ownerGroupsWhenNotConfigured() {
    assertThat(repoCfg.getOwnerGroups(Project.nameKey("someProject"))).isEmpty();
  }

  @Test
  public void ownerGroupsForStarFilter() {
    ImmutableList<String> ownerGroups = ImmutableList.of("group1", "group2");
    configureOwnerGroups("*", ownerGroups);
    assertThat(repoCfg.getOwnerGroups(Project.nameKey("someProject")))
        .containsExactlyElementsIn(ownerGroups);
  }

  @Test
  public void ownerGroupsForSpecificFilter() {
    ImmutableList<String> ownerGroups = ImmutableList.of("group1", "group2");
    configureOwnerGroups("someProject", ownerGroups);
    assertThat(repoCfg.getOwnerGroups(Project.nameKey("someOtherProject"))).isEmpty();
    assertThat(repoCfg.getOwnerGroups(Project.nameKey("someProject")))
        .containsExactlyElementsIn(ownerGroups);
  }

  @Test
  public void ownerGroupsForStartWithFilter() {
    ImmutableList<String> ownerGroups1 = ImmutableList.of("group1");
    ImmutableList<String> ownerGroups2 = ImmutableList.of("group2");
    ImmutableList<String> ownerGroups3 = ImmutableList.of("group3");

    configureOwnerGroups("*", ownerGroups1);
    configureOwnerGroups("somePath/*", ownerGroups2);
    configureOwnerGroups("somePath/somePath/*", ownerGroups3);

    assertThat(repoCfg.getOwnerGroups(Project.nameKey("someProject")))
        .containsExactlyElementsIn(ownerGroups1);

    assertThat(repoCfg.getOwnerGroups(Project.nameKey("somePath/someProject")))
        .containsExactlyElementsIn(ownerGroups2);

    assertThat(repoCfg.getOwnerGroups(Project.nameKey("somePath/somePath/someProject")))
        .containsExactlyElementsIn(ownerGroups3);
  }

  private void configureOwnerGroups(String projectFilter, List<String> ownerGroups) {
    cfg.setStringList(
        RepositoryConfig.SECTION_NAME,
        projectFilter,
        RepositoryConfig.OWNER_GROUP_NAME,
        ownerGroups);
  }

  @Test
  public void basePathWhenNotConfigured() {
    assertThat(repoCfg.getBasePath(Project.nameKey("someProject"))).isNull();
  }

  @Test
  public void basePathForStarFilter() {
    String basePath = "/someAbsolutePath/someDirectory";
    configureBasePath("*", basePath);
    assertThat(repoCfg.getBasePath(Project.nameKey("someProject")).toString()).isEqualTo(basePath);
  }

  @Test
  public void basePathForSpecificFilter() {
    String basePath = "/someAbsolutePath/someDirectory";
    configureBasePath("someProject", basePath);
    assertThat(repoCfg.getBasePath(Project.nameKey("someOtherProject"))).isNull();
    assertThat(repoCfg.getBasePath(Project.nameKey("someProject")).toString()).isEqualTo(basePath);
  }

  @Test
  public void basePathForStartWithFilter() {
    String basePath1 = "/someAbsolutePath1/someDirectory";
    String basePath2 = "someRelativeDirectory2";
    String basePath3 = "/someAbsolutePath3/someDirectory";
    String basePath4 = "/someAbsolutePath4/someDirectory";

    configureBasePath("pro*", basePath1);
    configureBasePath("project/project/*", basePath2);
    configureBasePath("project/*", basePath3);
    configureBasePath("*", basePath4);

    assertThat(repoCfg.getBasePath(Project.nameKey("project1")).toString()).isEqualTo(basePath1);
    assertThat(repoCfg.getBasePath(Project.nameKey("project/project/someProject")).toString())
        .isEqualTo(basePath2);
    assertThat(repoCfg.getBasePath(Project.nameKey("project/someProject")).toString())
        .isEqualTo(basePath3);
    assertThat(repoCfg.getBasePath(Project.nameKey("someProject")).toString()).isEqualTo(basePath4);
  }

  @Test
  public void allBasePath() {
    ImmutableList<Path> allBasePaths =
        ImmutableList.of(
            Paths.get("/someBasePath1"), Paths.get("/someBasePath2"), Paths.get("/someBasePath2"));

    configureBasePath("*", allBasePaths.get(0).toString());
    configureBasePath("project/*", allBasePaths.get(1).toString());
    configureBasePath("project/project/*", allBasePaths.get(2).toString());

    assertThat(repoCfg.getAllBasePaths()).isEqualTo(allBasePaths);
  }

  private void configureBasePath(String projectFilter, String basePath) {
    cfg.setString(
        RepositoryConfig.SECTION_NAME, projectFilter, RepositoryConfig.BASE_PATH_NAME, basePath);
  }
}
