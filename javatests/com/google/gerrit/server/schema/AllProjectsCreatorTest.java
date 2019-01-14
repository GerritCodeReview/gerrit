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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.reviewdb.client.RefNames;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Test;

public class AllProjectsCreatorTest extends AbstractDaemonTest {
  private static final String DEFAULT_ALL_PROJECTS_CONFIG =
      "[project]\n"
          + "\tdescription = Access inherited by all other projects.\n"
          + "[receive]\n"
          + "\trequireContributorAgreement = false\n"
          + "\trequireSignedOffBy = false\n"
          + "\trequireChangeId = true\n"
          + "\tenableSignedPush = false\n"
          + "[submit]\n"
          + "\tmergeContent = true\n"
          + "[capability]\n"
          + "\tadministrateServer = group Administrators\n"
          + "\tpriority = batch group Non-Interactive Users\n"
          + "\tstreamEvents = group Non-Interactive Users\n"
          + "[access \"refs/*\"]\n"
          + "\tread = group Administrators\n"
          + "\tread = group Anonymous Users\n"
          + "[access \"refs/for/*\"]\n"
          + "\taddPatchSet = group Registered Users\n"
          + "[access \"refs/for/refs/*\"]\n"
          + "\tpush = group Registered Users\n"
          + "\tpushMerge = group Registered Users\n"
          + "[access \"refs/heads/*\"]\n"
          + "\tcreate = group Administrators\n"
          + "\tcreate = group Project Owners\n"
          + "\teditTopicName = +force group Administrators\n"
          + "\teditTopicName = +force group Project Owners\n"
          + "\tforgeAuthor = group Registered Users\n"
          + "\tforgeCommitter = group Administrators\n"
          + "\tforgeCommitter = group Project Owners\n"
          + "\tlabel-Code-Review = -2..+2 group Administrators\n"
          + "\tlabel-Code-Review = -2..+2 group Project Owners\n"
          + "\tlabel-Code-Review = -1..+1 group Registered Users\n"
          + "\tpush = group Administrators\n"
          + "\tpush = group Project Owners\n"
          + "\tsubmit = group Administrators\n"
          + "\tsubmit = group Project Owners\n"
          + "[access \"refs/meta/config\"]\n"
          + "\texclusiveGroupPermissions = read\n"
          + "\tcreate = group Administrators\n"
          + "\tcreate = group Project Owners\n"
          + "\tlabel-Code-Review = -2..+2 group Administrators\n"
          + "\tlabel-Code-Review = -2..+2 group Project Owners\n"
          + "\tpush = group Administrators\n"
          + "\tpush = group Project Owners\n"
          + "\tread = group Administrators\n"
          + "\tread = group Project Owners\n"
          + "\tsubmit = group Administrators\n"
          + "\tsubmit = group Project Owners\n"
          + "[access \"refs/tags/*\"]\n"
          + "\tcreate = group Administrators\n"
          + "\tcreate = group Project Owners\n"
          + "\tcreateSignedTag = group Administrators\n"
          + "\tcreateSignedTag = group Project Owners\n"
          + "\tcreateTag = group Administrators\n"
          + "\tcreateTag = group Project Owners\n"
          + "[label \"Code-Review\"]\n"
          + "\tfunction = MaxWithBlock\n"
          + "\tdefaultValue = 0\n"
          + "\tcopyMinScore = true\n"
          + "\tcopyAllScoresOnTrivialRebase = true\n"
          + "\tvalue = -2 This shall not be merged\n"
          + "\tvalue = -1 I would prefer this is not merged as is\n"
          + "\tvalue = 0 No score\n"
          + "\tvalue = +1 Looks good to me, but someone else must approve\n"
          + "\tvalue = +2 Looks good to me, approved"
          + "\n";

  @Test
  public void defaultAllProjectsConfig() throws Exception {
    // Loads the "project.config" from All-Projects of the current site.
    Config config = new Config();
    config.fromText(readAllProjectsConfigAsString());
    // Loads the expected configs.
    Config expectedConfig = new Config();
    expectedConfig.fromText(DEFAULT_ALL_PROJECTS_CONFIG);

    assertTwoConfigsEquivalent(config, expectedConfig);
  }

  private String readAllProjectsConfigAsString() throws IOException {
    try (Repository repo = repoManager.openRepository(allProjects);
        RevWalk revWalk = new RevWalk(repo);
        ObjectReader reader = repo.newObjectReader()) {
      Ref configRef = repo.findRef(RefNames.REFS_CONFIG);
      RevCommit commit = revWalk.parseCommit(configRef.getObjectId());
      try (TreeWalk tw = TreeWalk.forPath(reader, "project.config", commit.getTree())) {
        ObjectLoader obj = reader.open(tw.getObjectId(0), Constants.OBJ_BLOB);
        return RawParseUtils.decode(obj.getCachedBytes(Integer.MAX_VALUE));
      }
    }
  }

  private void assertTwoConfigsEquivalent(Config config1, Config config2) {
    Set<String> sections1 = config1.getSections();
    Set<String> sections2 = config2.getSections();
    assertThat(sections1).containsExactlyElementsIn(sections2);

    sections1.forEach(s -> assertSectionEquivalent(config1, config2, s));
  }

  private void assertSectionEquivalent(Config config1, Config config2, String section) {
    assertSubsectionEquivalent(config1, config2, section, null);

    Set<String> subsections1 = config1.getSubsections(section);
    Set<String> subsections2 = config2.getSubsections(section);
    assertThat(subsections1).containsExactlyElementsIn(subsections2);
    for (String subsection : subsections1) {
      assertSubsectionEquivalent(config1, config2, section, subsection);
    }
  }

  private void assertSubsectionEquivalent(
      Config config1, Config config2, String section, String subsection) {
    Set<String> subsectionNames1 = config1.getNames(section, subsection);
    Set<String> subsectionNames2 = config2.getNames(section, subsection);
    assertThat(subsectionNames1).containsExactlyElementsIn(subsectionNames2);

    subsectionNames1.forEach(
        name ->
            assertThat(Arrays.asList(config1.getStringList(section, subsection, name)))
                .containsExactlyElementsIn(
                    Arrays.asList(config2.getStringList(section, subsection, name))));
  }
}
