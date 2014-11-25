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

import static org.junit.Assert.*;

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
    assertEquals(SubmitType.MERGE_IF_NECESSARY,
        repoCfg.getDefaultSubmitType(new NameKey("someProject")));
  }

  @Test
  public void testDefaultSubmitTypeForStarFilter() {
    configureDefaultSubmitType("*", SubmitType.CHERRY_PICK);
    assertEquals(SubmitType.CHERRY_PICK,
        repoCfg.getDefaultSubmitType(new NameKey("someProject")));

    configureDefaultSubmitType("*", SubmitType.FAST_FORWARD_ONLY);
    assertEquals(SubmitType.FAST_FORWARD_ONLY,
        repoCfg.getDefaultSubmitType(new NameKey("someProject")));

    configureDefaultSubmitType("*", SubmitType.REBASE_IF_NECESSARY);
    assertEquals(SubmitType.REBASE_IF_NECESSARY,
        repoCfg.getDefaultSubmitType(new NameKey("someProject")));
  }

  @Test
  public void testDefaultSubmitTypeForSpecificFilter() {
    configureDefaultSubmitType("someProject", SubmitType.CHERRY_PICK);
    assertEquals(SubmitType.MERGE_IF_NECESSARY,
        repoCfg.getDefaultSubmitType(new NameKey("someOtherProject")));
    assertEquals(SubmitType.CHERRY_PICK,
        repoCfg.getDefaultSubmitType(new NameKey("someProject")));

  }

  @Test
  public void testDefaultSubmitTypeForStartWithFilter() {
    configureDefaultSubmitType("somePath/somePath/*",
        SubmitType.REBASE_IF_NECESSARY);
    configureDefaultSubmitType("somePath/*", SubmitType.CHERRY_PICK);
    configureDefaultSubmitType("*", SubmitType.MERGE_ALWAYS);

    assertEquals(SubmitType.MERGE_ALWAYS,
        repoCfg.getDefaultSubmitType(new NameKey("someProject")));

    assertEquals(SubmitType.CHERRY_PICK,
        repoCfg.getDefaultSubmitType(new NameKey("somePath/someProject")));

    assertEquals(SubmitType.REBASE_IF_NECESSARY,
        repoCfg.getDefaultSubmitType(new NameKey(
            "somePath/somePath/someProject")));
  }

  private void configureDefaultSubmitType(String projectFilter,
      SubmitType submitType) {
    cfg.setString(RepositoryConfig.SECTION_NAME, projectFilter,
        RepositoryConfig.DEFAULT_SUBMIT_TYPE_NAME, submitType.toString());
  }

  @Test
  public void testOwnerGroupsWhenNotConfigured() {
    assertArrayEquals(new String[] {},
        repoCfg.getOwnerGroups(new NameKey("someProject")));
  }

  @Test
  public void testOwnerGroupsForStarFilter() {
    String[] ownerGroups = new String[] {"group1", "group2"};
    configureOwnerGroups("*", Lists.newArrayList(ownerGroups));
    assertArrayEquals(ownerGroups,
        repoCfg.getOwnerGroups(new NameKey("someProject")));
  }

  @Test
  public void testOwnerGroupsForSpecificFilter() {
    String[] ownerGroups = new String[] {"group1", "group2"};
    configureOwnerGroups("someProject", Lists.newArrayList(ownerGroups));
    assertArrayEquals(new String[] {},
        repoCfg.getOwnerGroups(new NameKey("someOtherProject")));
    assertArrayEquals(ownerGroups,
        repoCfg.getOwnerGroups(new NameKey("someProject")));
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

    assertArrayEquals(ownerGroups1,
        repoCfg.getOwnerGroups(new NameKey("someProject")));

    assertArrayEquals(ownerGroups2,
        repoCfg.getOwnerGroups(new NameKey("somePath/someProject")));

    assertArrayEquals(ownerGroups3,
        repoCfg.getOwnerGroups(new NameKey("somePath/somePath/someProject")));
  }

  private void configureOwnerGroups(String projectFilter,
      List<String> ownerGroups) {
    cfg.setStringList(RepositoryConfig.SECTION_NAME, projectFilter,
        RepositoryConfig.OWNER_GROUP_NAME, ownerGroups);
  }
}
