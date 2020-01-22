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

package com.google.gerrit.server.schema.testing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class AllProjectsCreatorTestUtil {
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
          "  revert = group Registered Users",
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
          "  revert = group Administrators",
          "  revert = group Project Owners",
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

  public static String getDefaultAllProjectsWithAllDefaultSections() {
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

  public static String getAllProjectsWithoutDefaultAcls() {
    return Streams.stream(
            Iterables.concat(
                DEFAULT_ALL_PROJECTS_PROJECT_SECTION,
                DEFAULT_ALL_PROJECTS_RECEIVE_SECTION,
                DEFAULT_ALL_PROJECTS_SUBMIT_SECTION,
                DEFAULT_ALL_PROJECTS_LABEL_SECTION))
        .collect(Collectors.joining("\n"));
  }

  // Loads the "project.config" from the All-Projects repo.
  public static Config readAllProjectsConfig(
      GitRepositoryManager repoManager, AllProjectsName allProjectsName)
      throws IOException, ConfigInvalidException {
    try (Repository repo = repoManager.openRepository(allProjectsName)) {
      Ref configRef = repo.exactRef(RefNames.REFS_CONFIG);
      return new BlobBasedConfig(null, repo, configRef.getObjectId(), "project.config");
    }
  }

  public static void assertTwoConfigsEquivalent(Config config1, Config config2) {
    Set<String> sections1 = config1.getSections();
    Set<String> sections2 = config2.getSections();
    assertThat(sections1).containsExactlyElementsIn(sections2);

    sections1.forEach(s -> assertSectionEquivalent(config1, config2, s));
  }

  public static void assertSectionEquivalent(Config config1, Config config2, String section) {
    assertSubsectionEquivalent(config1, config2, section, null);

    Set<String> subsections1 = config1.getSubsections(section);
    Set<String> subsections2 = config2.getSubsections(section);
    assertWithMessage("section \"%s\"", section)
        .that(subsections1)
        .containsExactlyElementsIn(subsections2);

    subsections1.forEach(s -> assertSubsectionEquivalent(config1, config2, section, s));
  }

  private static void assertSubsectionEquivalent(
      Config config1, Config config2, String section, String subsection) {
    Set<String> subsectionNames1 = config1.getNames(section, subsection);
    Set<String> subsectionNames2 = config2.getNames(section, subsection);
    String name = String.format("subsection \"%s\" of section \"%s\"", subsection, section);
    assertWithMessage(name).that(subsectionNames1).containsExactlyElementsIn(subsectionNames2);

    subsectionNames1.forEach(
        n ->
            assertWithMessage(name)
                .that(config1.getStringList(section, subsection, n))
                .asList()
                .containsExactlyElementsIn(config2.getStringList(section, subsection, n)));
  }

  private AllProjectsCreatorTestUtil() {}
}
