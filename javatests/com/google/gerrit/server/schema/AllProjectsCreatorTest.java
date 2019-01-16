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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.schema.AllProjectsInput.getDefaultCodeReviewLabel;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class AllProjectsCreatorTest extends GerritBaseTests {
  private static final ImmutableList<String> DEFAULT_ALL_PROJECTS_PROJECT_SECTION =
      ImmutableList.of("[project]", "  description = Access inherited by all other projects.");
  private static final ImmutableList<String> DEFAULT_ALL_PROJECTS_RECEIVE_SECTION =
      ImmutableList.of(
          "[receive]",
          "  requireContributorAgreement = false",
          "  requireSignedOffBy = false",
          "  requireChangeId = true",
          "  enableSignedPush = false");
  private static final ImmutableList<String> DEFAULT_ALL_PROJECTS_SUBMIT_SECTION =
      ImmutableList.of("[submit]", "  mergeContent = true");
  private static final ImmutableList<String> DEFAULT_ALL_PROJECTS_CAPABILITY_SECTION =
      ImmutableList.of(
          "[capability]",
          "  administrateServer = group Administrators",
          "  priority = batch group Non-Interactive Users",
          "  streamEvents = group Non-Interactive Users");
  private static final ImmutableList<String> DEFAULT_ALL_PROJECTS_ACCESS_SECTION =
      ImmutableList.of(
          "[access \"refs/*\"]",
          "  read = group Administrators",
          "  read = group Anonymous Users",
          "[access \"refs/for/*\"]",
          "  addPatchSet = group Registered Users",
          "[access \"refs/for/refs/*\"]",
          "  push = group Registered Users",
          "  pushMerge = group Registered Users",
          "[access \"refs/heads/*\"]",
          "  create = group Administrators",
          "  create = group Project Owners",
          "  editTopicName = +force group Administrators",
          "  editTopicName = +force group Project Owners",
          "  forgeAuthor = group Registered Users",
          "  forgeCommitter = group Administrators",
          "  forgeCommitter = group Project Owners",
          "  label-Code-Review = -2..+2 group Administrators",
          "  label-Code-Review = -2..+2 group Project Owners",
          "  label-Code-Review = -1..+1 group Registered Users",
          "  push = group Administrators",
          "  push = group Project Owners",
          "  submit = group Administrators",
          "  submit = group Project Owners",
          "[access \"refs/meta/config\"]",
          "  exclusiveGroupPermissions = read",
          "  create = group Administrators",
          "  create = group Project Owners",
          "  label-Code-Review = -2..+2 group Administrators",
          "  label-Code-Review = -2..+2 group Project Owners",
          "  push = group Administrators",
          "  push = group Project Owners",
          "  read = group Administrators",
          "  read = group Project Owners",
          "  submit = group Administrators",
          "  submit = group Project Owners",
          "[access \"refs/tags/*\"]",
          "  create = group Administrators",
          "  create = group Project Owners",
          "  createSignedTag = group Administrators",
          "  createSignedTag = group Project Owners",
          "  createTag = group Administrators",
          "  createTag = group Project Owners");
  private static final ImmutableList<String> DEFAULT_ALL_PROJECTS_LABEL_SECTION =
      ImmutableList.of(
          "[label \"Code-Review\"]",
          "  function = MaxWithBlock",
          "  defaultValue = 0",
          "  copyMinScore = true",
          "  copyAllScoresOnTrivialRebase = true",
          "  value = -2 This shall not be merged",
          "  value = -1 I would prefer this is not merged as is",
          "  value = 0 No score",
          "  value = +1 Looks good to me, but someone else must approve",
          "  value = +2 Looks good to me, approved");

  private static final LabelType TEST_LABEL =
      new LabelType(
          "Test-Label",
          ImmutableList.of(
              new LabelValue((short) 2, "Two"),
              new LabelValue((short) 0, "Zero"),
              new LabelValue((short) 1, "One")));

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
    Repository repo = repoManager.createRepository(allProjectsName);
    repo.close();
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

    Config config = readAllProjectsConfig();
    assertTwoConfigsEquivalent(config, expectedConfig);
  }

  private static String getDefaultAllProjectsWithAllDefaultSections() {
    return Streams.stream(
            Iterables.concat(
                DEFAULT_ALL_PROJECTS_PROJECT_SECTION,
                DEFAULT_ALL_PROJECTS_RECEIVE_SECTION,
                DEFAULT_ALL_PROJECTS_SUBMIT_SECTION,
                DEFAULT_ALL_PROJECTS_CAPABILITY_SECTION,
                DEFAULT_ALL_PROJECTS_ACCESS_SECTION,
                DEFAULT_ALL_PROJECTS_LABEL_SECTION))
        .collect(Collectors.joining("\n"));
  }

  private GroupReference createGroupReference(String name) {
    AccountGroup.UUID groupUuid = GroupUUID.make(name, serverUser);
    return new GroupReference(groupUuid, name);
  }

  @Test
  public void createAllProjectsWithNewCodeReviewLabel() throws Exception {
    Config expectedLabelConfig = new Config();
    expectedLabelConfig.fromText(TEST_LABEL_STRING);

    AllProjectsInput allProjectsInput =
        AllProjectsInput.builder().codeReviewLabel(TEST_LABEL).build();
    allProjectsCreator.create(allProjectsInput);

    Config config = readAllProjectsConfig();
    assertSectionEquivalent(config, expectedLabelConfig, "label");
  }

  @Test
  public void createAllProjectsWithProjectDescription() throws Exception {
    String testDescription = "test description";
    AllProjectsInput allProjectsInput =
        AllProjectsInput.builder().projectDescription(testDescription).build();
    allProjectsCreator.create(allProjectsInput);

    Config config = readAllProjectsConfig();
    assertThat(config.getString("project", null, "description")).isEqualTo(testDescription);
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

    Config config = readAllProjectsConfig();
    assertThat(config.getBoolean("submit", null, "rejectEmptyCommit", false)).isTrue();
  }

  @Test
  public void createAllProjectsWithoutInitializingDefaultACLs() throws Exception {
    AllProjectsInput allProjectsInput = AllProjectsInput.builder().initDefaultAcls(false).build();
    allProjectsCreator.create(allProjectsInput);

    Config expectedConfig = new Config();
    expectedConfig.fromText(getAllProjectsWithoutDefaultAcls());
    Config config = readAllProjectsConfig();
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
    Config config = readAllProjectsConfig();
    assertTwoConfigsEquivalent(config, expectedConfig);
  }

  // Loads the "project.config" from the All-Projects repo.
  private Config readAllProjectsConfig() throws IOException, ConfigInvalidException {
    try (Repository repo = repoManager.openRepository(allProjectsName)) {
      Ref configRef = repo.findRef(RefNames.REFS_CONFIG);
      return new BlobBasedConfig(null, repo, configRef.getObjectId(), "project.config");
    }
  }

  private static String getAllProjectsWithoutDefaultAcls() {
    return Streams.stream(
            Iterables.concat(
                DEFAULT_ALL_PROJECTS_PROJECT_SECTION,
                DEFAULT_ALL_PROJECTS_RECEIVE_SECTION,
                DEFAULT_ALL_PROJECTS_SUBMIT_SECTION,
                DEFAULT_ALL_PROJECTS_LABEL_SECTION))
        .collect(Collectors.joining("\n"));
  }

  private static void assertTwoConfigsEquivalent(Config config1, Config config2) {
    Set<String> sections1 = config1.getSections();
    Set<String> sections2 = config2.getSections();
    assertThat(sections1).containsExactlyElementsIn(sections2);

    sections1.forEach(s -> assertSectionEquivalent(config1, config2, s));
  }

  private static void assertSectionEquivalent(Config config1, Config config2, String section) {
    assertSubsectionEquivalent(config1, config2, section, null);

    Set<String> subsections1 = config1.getSubsections(section);
    Set<String> subsections2 = config2.getSubsections(section);
    assertThat(subsections1)
        .named("section \"%s\"", section)
        .containsExactlyElementsIn(subsections2);

    subsections1.forEach(s -> assertSubsectionEquivalent(config1, config2, section, s));
  }

  private static void assertSubsectionEquivalent(
      Config config1, Config config2, String section, String subsection) {
    Set<String> subsectionNames1 = config1.getNames(section, subsection);
    Set<String> subsectionNames2 = config2.getNames(section, subsection);
    String name = String.format("subsection \"%s\" of section \"%s\"", subsection, section);
    assertThat(subsectionNames1).named(name).containsExactlyElementsIn(subsectionNames2);

    subsectionNames1.forEach(
        n ->
            assertThat(config1.getStringList(section, subsection, n))
                .named(name)
                .asList()
                .containsExactlyElementsIn(config2.getStringList(section, subsection, n)));
  }
}
