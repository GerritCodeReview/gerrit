// Copyright (C) 2019 The Android Open Source Project
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

import static com.google.gerrit.server.schema.AllProjectsInput.getDefaultCodeReviewLabel;
import static com.google.gerrit.server.schema.testing.AllProjectsCreatorTestUtil.assertSectionEquivalent;
import static com.google.gerrit.server.schema.testing.AllProjectsCreatorTestUtil.assertTwoConfigsEquivalent;
import static com.google.gerrit.server.schema.testing.AllProjectsCreatorTestUtil.getAllProjectsWithoutDefaultAcls;
import static com.google.gerrit.server.schema.testing.AllProjectsCreatorTestUtil.getDefaultAllProjectsWithAllDefaultSections;
import static com.google.gerrit.server.schema.testing.AllProjectsCreatorTestUtil.readAllProjectsConfig;
import static com.google.gerrit.truth.ConfigSubject.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.GroupUuid;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class AllProjectsCreatorTest {
  private static final LabelType TEST_LABEL =
      LabelType.create(
          "Test-Label",
          ImmutableList.of(
              LabelValue.create((short) 2, "Two"),
              LabelValue.create((short) 0, "Zero"),
              LabelValue.create((short) 1, "One")));

  private static final String TEST_LABEL_STRING =
      String.join(
          "\n",
          ImmutableList.of(
              "[label \"Test-Label\"]",
              "\tfunction = MaxWithBlock",
              "\tdefaultValue = 0",
              "\tvalue = 0 Zero",
              "\tvalue = +1 One",
              "\tvalue = +2 Two"));

  @Inject private AllProjectsName allProjectsName;

  @Inject @GerritPersonIdent private PersonIdent serverUser;

  @Inject private AllProjectsCreator allProjectsCreator;

  @Inject private GitRepositoryManager repoManager;

  @Before
  public void setUp() throws Exception {
    InMemoryModule inMemoryModule = new InMemoryModule();
    inMemoryModule.inject(this);

    // Creates an empty All-Projects.
    try (Repository repo = repoManager.createRepository(allProjectsName)) {
      // Intentionally empty.
    }
  }

  @Test
  public void createDefaultAllProjectsConfig() throws Exception {
    // Loads the expected configs.
    Config expectedConfig = new Config();
    expectedConfig.fromText(getDefaultAllProjectsWithAllDefaultSections());

    GroupReference adminsGroup = createGroupReference("Administrators");
    GroupReference batchUsersGroup = createGroupReference("Non-Interactive Users");
    AllProjectsInput allProjectsInput =
        AllProjectsInput.builder()
            .administratorsGroup(adminsGroup)
            .batchUsersGroup(batchUsersGroup)
            .build();
    allProjectsCreator.create(allProjectsInput);

    Config config = readAllProjectsConfig(repoManager, allProjectsName);
    assertTwoConfigsEquivalent(config, expectedConfig);
  }

  private GroupReference createGroupReference(String name) {
    AccountGroup.UUID groupUuid = GroupUuid.make(name, serverUser);
    return GroupReference.create(groupUuid, name);
  }

  @Test
  public void createAllProjectsWithNewCodeReviewLabel() throws Exception {
    Config expectedLabelConfig = new Config();
    expectedLabelConfig.fromText(TEST_LABEL_STRING);

    AllProjectsInput allProjectsInput =
        AllProjectsInput.builder().codeReviewLabel(TEST_LABEL).build();
    allProjectsCreator.create(allProjectsInput);

    Config config = readAllProjectsConfig(repoManager, allProjectsName);
    assertSectionEquivalent(config, expectedLabelConfig, "label");
  }

  @Test
  public void createAllProjectsWithProjectDescription() throws Exception {
    String testDescription = "test description";
    AllProjectsInput allProjectsInput =
        AllProjectsInput.builder().projectDescription(testDescription).build();
    allProjectsCreator.create(allProjectsInput);

    Config config = readAllProjectsConfig(repoManager, allProjectsName);
    assertThat(config).stringValue("project", null, "description").isEqualTo(testDescription);
  }

  @Test
  public void createAllProjectsWithBooleanConfigs() throws Exception {
    AllProjectsInput allProjectsInput =
        AllProjectsInput.builderWithNoDefault()
            .codeReviewLabel(getDefaultCodeReviewLabel())
            .firstChangeIdForNoteDb(Sequences.FIRST_CHANGE_ID)
            .addBooleanProjectConfig(
                BooleanProjectConfig.REJECT_EMPTY_COMMIT, InheritableBoolean.TRUE)
            .initDefaultAcls(true)
            .build();
    allProjectsCreator.create(allProjectsInput);

    Config config = readAllProjectsConfig(repoManager, allProjectsName);
    assertThat(config).booleanValue("submit", null, "rejectEmptyCommit", false).isTrue();
  }

  @Test
  public void createAllProjectsWithoutInitializingDefaultACLs() throws Exception {
    AllProjectsInput allProjectsInput = AllProjectsInput.builder().initDefaultAcls(false).build();
    allProjectsCreator.create(allProjectsInput);

    Config expectedConfig = new Config();
    expectedConfig.fromText(getAllProjectsWithoutDefaultAcls());
    Config config = readAllProjectsConfig(repoManager, allProjectsName);
    assertTwoConfigsEquivalent(config, expectedConfig);
  }

  @Test
  public void createAllProjectsOnlyInitializingProjectDescription() throws Exception {
    String description = "a project.config with just a project description";
    AllProjectsInput allProjectsInput =
        AllProjectsInput.builderWithNoDefault()
            .firstChangeIdForNoteDb(Sequences.FIRST_CHANGE_ID)
            .projectDescription(description)
            .initDefaultAcls(false)
            .build();
    allProjectsCreator.create(allProjectsInput);

    Config expectedConfig = new Config();
    expectedConfig.setString("project", null, "description", description);
    Config config = readAllProjectsConfig(repoManager, allProjectsName);
    assertTwoConfigsEquivalent(config, expectedConfig);
  }
}
