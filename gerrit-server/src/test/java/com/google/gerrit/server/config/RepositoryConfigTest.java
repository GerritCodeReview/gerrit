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

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.SubmitType;
import com.google.gerrit.reviewdb.client.Project.NameKey;

import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class RepositoryConfigTest {

  private Config cfg;
  private RepositoryConfig repoCfg;

  @Before
  public void setUp() throws Exception {
    cfg = new Config();
    repoCfg = new RepositoryConfig(cfg);
  }

  @Test
  public void testDefaultSubmitTypeWhenNotConfigured() {
    assertThat(repoCfg.getDefaultSubmitType(new NameKey("someProject")))
        .isEqualTo(SubmitType.MERGE_IF_NECESSARY);
  }

  @Test
  public void testDefaultSubmitTypeForStarFilter() {
    configureDefaultSubmitType("*", SubmitType.CHERRY_PICK);
    assertThat(repoCfg.getDefaultSubmitType(new NameKey("someProject")))
        .isEqualTo(SubmitType.CHERRY_PICK);

    configureDefaultSubmitType("*", SubmitType.FAST_FORWARD_ONLY);
    assertThat(repoCfg.getDefaultSubmitType(new NameKey("someProject")))
        .isEqualTo(SubmitType.FAST_FORWARD_ONLY);

    configureDefaultSubmitType("*", SubmitType.REBASE_IF_NECESSARY);
    assertThat(repoCfg.getDefaultSubmitType(new NameKey("someProject")))
        .isEqualTo(SubmitType.REBASE_IF_NECESSARY);
  }

  @Test
  public void testDefaultSubmitTypeForSpecificFilter() {
    configureDefaultSubmitType("someProject", SubmitType.CHERRY_PICK);
    assertThat(repoCfg.getDefaultSubmitType(new NameKey("someOtherProject")))
        .isEqualTo(SubmitType.MERGE_IF_NECESSARY);
    assertThat(repoCfg.getDefaultSubmitType(new NameKey("someProject")))
        .isEqualTo(SubmitType.CHERRY_PICK);
  }

  @Test
  public void testDefaultSubmitTypeForStartWithFilter() {
    configureDefaultSubmitType("somePath/somePath/*",
        SubmitType.REBASE_IF_NECESSARY);
    configureDefaultSubmitType("somePath/*", SubmitType.CHERRY_PICK);
    configureDefaultSubmitType("*", SubmitType.MERGE_ALWAYS);

    assertThat(repoCfg.getDefaultSubmitType(new NameKey("someProject")))
        .isEqualTo(SubmitType.MERGE_ALWAYS);

    assertThat(
        repoCfg.getDefaultSubmitType(new NameKey("somePath/someProject")))
        .isEqualTo(SubmitType.CHERRY_PICK);

    assertThat(
        repoCfg.getDefaultSubmitType(new NameKey(
            "somePath/somePath/someProject"))).isEqualTo(
        SubmitType.REBASE_IF_NECESSARY);
  }

  private void configureDefaultSubmitType(String projectFilter,
      SubmitType submitType) {
    cfg.setString(RepositoryConfig.SECTION_NAME, projectFilter,
        RepositoryConfig.DEFAULT_SUBMIT_TYPE_NAME, submitType.toString());
  }

  @Test
  public void testOwnerGroupsWhenNotConfigured() {
    assertThat(repoCfg.getOwnerGroups(new NameKey("someProject"))).isEqualTo(
        new String[] {});
  }

  @Test
  public void testOwnerGroupsForStarFilter() {
    String[] ownerGroups = new String[] {"group1", "group2"};
    configureOwnerGroups("*", Lists.newArrayList(ownerGroups));
    assertThat(repoCfg.getOwnerGroups(new NameKey("someProject"))).isEqualTo(
        ownerGroups);
  }

  @Test
  public void testOwnerGroupsForSpecificFilter() {
    String[] ownerGroups = new String[] {"group1", "group2"};
    configureOwnerGroups("someProject", Lists.newArrayList(ownerGroups));
    assertThat(repoCfg.getOwnerGroups(new NameKey("someOtherProject")))
        .isEqualTo(new String[] {});
    assertThat(repoCfg.getOwnerGroups(new NameKey("someProject"))).isEqualTo(
        ownerGroups);
  }

  @Test
  public void testOwnerGroupsForStartWithFilter() {
    String[] ownerGroups1 = new String[] {"group1"};
    String[] ownerGroups2 = new String[] {"group2"};
    String[] ownerGroups3 = new String[] {"group3"};

    configureOwnerGroups("*", Lists.newArrayList(ownerGroups1));
    configureOwnerGroups("somePath/*", Lists.newArrayList(ownerGroups2));
    configureOwnerGroups("somePath/somePath/*",
        Lists.newArrayList(ownerGroups3));

    assertThat(repoCfg.getOwnerGroups(new NameKey("someProject"))).isEqualTo(
        ownerGroups1);

    assertThat(repoCfg.getOwnerGroups(new NameKey("somePath/someProject")))
        .isEqualTo(ownerGroups2);

    assertThat(
        repoCfg.getOwnerGroups(new NameKey("somePath/somePath/someProject")))
        .isEqualTo(ownerGroups3);
  }

  private void configureOwnerGroups(String projectFilter,
      List<String> ownerGroups) {
    cfg.setStringList(RepositoryConfig.SECTION_NAME, projectFilter,
        RepositoryConfig.OWNER_GROUP_NAME, ownerGroups);
  }

  @Test
  public void testBasePathWhenNotConfigured() {
    assertThat(repoCfg.getBasePath(new NameKey("someProject"))).isNull();
  }

  @Test
  public void testBasePathForStarFilter() {
    String basePath = "/someAbsolutePath/someDirectory";
    configureBasePath("*", basePath);
    assertThat(repoCfg.getBasePath(new NameKey("someProject"))).isEqualTo(
        basePath);
  }


  @Test
  public void testBasePathForSpecificFilter() {
    String basePath = "/someAbsolutePath/someDirectory";
    configureBasePath("someProject", basePath);
    assertThat(repoCfg.getBasePath(new NameKey("someOtherProject"))).isNull();
    assertThat(repoCfg.getBasePath(new NameKey("someProject"))).isEqualTo(
        basePath);
  }

  @Test
  public void testBasePathForStartWithFilter() {
    String basePath1 = "/someAbsolutePath1/someDirectory";
    String basePath2 = "someRelativeDirectory2";
    String basePath3 = "/someAbsolutePath3/someDirectory";


    configureBasePath("project/project/*", basePath1);
    configureBasePath("project/*", basePath2);
    configureBasePath("*", basePath3);

    assertThat(repoCfg.getBasePath(new NameKey("project/project/someProject")))
        .isEqualTo(basePath1);
    assertThat(repoCfg.getBasePath(new NameKey("project/someProject")))
        .isEqualTo(basePath2);
    assertThat(repoCfg.getBasePath(new NameKey("someProject"))).isEqualTo(
        basePath3);
  }

  @Test
  public void testAllBasePath() {
    String[] allBasePaths =
        new String[] {"/someBasePath1", "/someBasePath2", "/someBasePath3"};

    configureBasePath("*", allBasePaths[0]);
    configureBasePath("project/*", allBasePaths[1]);
    configureBasePath("project/project/*", allBasePaths[2]);

    assertThat(repoCfg.getAllBasePaths()).isEqualTo(allBasePaths);
  }

  private void configureBasePath(String projectFilter, String basePath) {
    cfg.setString(RepositoryConfig.SECTION_NAME, projectFilter,
        RepositoryConfig.BASE_PATH_NAME, basePath);
  }
}
