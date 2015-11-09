// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.inject.Inject;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

import java.util.Set;

public class SubmoduleSectionParserTest extends LocalDiskRepositoryTestCase {
  private static final String THIS_SERVER = "localhost";

  @Inject
  private SubmoduleSectionParser.Factory subSecParserFactory;

  @Test
  public void testSubmoduleParserFollowMasterBranch() throws Exception {
    Config cfg = new Config();
    String gitmodules = ""
        + "[submodule \"a\"]\n"
        + "path = localpath-to-a\n"
        + "url = ssh://localhost/a\n"
        + "branch = master\n";

    cfg.fromText(gitmodules);
    String thisServer = THIS_SERVER;
    Branch.NameKey targetBranch = new Branch.NameKey(
        new Project.NameKey("project"), "master");

    Set<SubmoduleSubscription> res = subSecParserFactory.create(
        cfg, thisServer, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected = Sets.newHashSet(
        new SubmoduleSubscription(targetBranch, new Branch.NameKey(
            new Project.NameKey("a"), "master"), "localpath-to-a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testSubmoduleParserFollowMatchingBranch() throws Exception {
    Config cfg = new Config();
    String gitmodules = ""
        + "[submodule \"a\"]\n"
        + "path = a\n"
        + "url = ssh://localhost/a\n"
        + "branch = .\n";
    cfg.fromText(gitmodules);
    String thisServer = THIS_SERVER;
    Branch.NameKey targetBranch = new Branch.NameKey(
        new Project.NameKey("project"), "master");
    Set<SubmoduleSubscription> res = subSecParserFactory.create(
        cfg, thisServer, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected = Sets.newHashSet(
        new SubmoduleSubscription(targetBranch, new Branch.NameKey(
            new Project.NameKey("a"), "master"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testSubmoduleParserFollowAnotherBranch() throws Exception {
    Config cfg = new Config();
    String gitmodules = ""
        + "[submodule \"a\"]\n"
        + "path = a\n"
        + "url = ssh://localhost/a\n"
        + "branch = anotherbranch\n";

    cfg.fromText(gitmodules);
    String thisServer = THIS_SERVER;
    Branch.NameKey targetBranch = new Branch.NameKey(
        new Project.NameKey("project"), "master");
    Set<SubmoduleSubscription> res = subSecParserFactory.create(
        cfg, thisServer, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected = Sets.newHashSet(
        new SubmoduleSubscription(targetBranch, new Branch.NameKey(
            new Project.NameKey("a"), "anotherbranch"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testSubmoduleParserWithAnotherURI() throws Exception {
    Config cfg = new Config();
    String gitmodules = ""
        + "[submodule \"a\"]\n"
        + "path = a\n"
        + "url = http://localhost:80/a\n"
        + "branch = master\n";

    cfg.fromText(gitmodules);
    String thisServer = THIS_SERVER;
    Branch.NameKey targetBranch = new Branch.NameKey(
        new Project.NameKey("project"), "master");
    Set<SubmoduleSubscription> res = subSecParserFactory.create(
        cfg, thisServer, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected = Sets.newHashSet(
        new SubmoduleSubscription(targetBranch, new Branch.NameKey(
            new Project.NameKey("a"), "master"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testSubmoduleParserWithSlashesInProjectName() throws Exception {
    Config cfg = new Config();
    String gitmodules = ""
        + "[submodule \"project/with/slashes/a\"]\n"
        + "path = a\n"
        + "url = http://localhost:80/project/with/slashes/a\n"
        + "branch = master\n";

    cfg.fromText(gitmodules);
    String thisServer = THIS_SERVER;
    Branch.NameKey targetBranch = new Branch.NameKey(
        new Project.NameKey("project"), "master");
    Set<SubmoduleSubscription> res = subSecParserFactory.create(
        cfg, thisServer, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected = Sets.newHashSet(
        new SubmoduleSubscription(targetBranch, new Branch.NameKey(
            new Project.NameKey("project/with/slashes/a"), "master"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testSubmoduleParserWithSlashesInPath() throws Exception {
    Config cfg = new Config();
    String gitmodules = ""
        + "[submodule \"a\"]\n"
        + "path = a/b/c/d/e\n"
        + "url = http://localhost:80/a\n"
        + "branch = master\n";

    cfg.fromText(gitmodules);
    String thisServer = THIS_SERVER;
    Branch.NameKey targetBranch = new Branch.NameKey(
        new Project.NameKey("project"), "master");
    Set<SubmoduleSubscription> res = subSecParserFactory.create(
        cfg, thisServer, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected = Sets.newHashSet(
        new SubmoduleSubscription(targetBranch, new Branch.NameKey(
            new Project.NameKey("a"), "master"), "a/b/c/d/e"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testSubmodulesParseWithMoreSections() throws Exception {
    Config cfg = new Config();
    String gitmodules = ""
        + "[submodule \"a\"]\n"
        + "path = a\n"
        + "url = ssh://localhost/a\n"
        + "branch = .\n" // follow any branch including the master branch
        + "[submodule \"b\"]\n"
        + "    path = b\n"
        + "    url = http://localhost:80/b\n"
        + "    branch = master\n";
    cfg.fromText(gitmodules);
    String thisServer = THIS_SERVER;
    Branch.NameKey targetBranch = new Branch.NameKey(
        new Project.NameKey("project"), "master");
    Set<SubmoduleSubscription> res = subSecParserFactory.create(
        cfg, thisServer, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected = Sets.newHashSet(
        new SubmoduleSubscription(targetBranch, new Branch.NameKey(
            new Project.NameKey("a"), "master"), "a"),
        new SubmoduleSubscription(targetBranch, new Branch.NameKey(
            new Project.NameKey("b"), "master"), "b"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testSubmodulesParseWithSubProjectFound() throws Exception {
    Config cfg = new Config();
    cfg.fromText("\n"
        + "[submodule \"a/b\"]\n"
        + "path = a/b\n"
        + "url = ssh://localhost/a/b\n"
        + "branch = .\n"
        + "[submodule \"b\"]\n"
        + "path = b\n"
        + "url = http://localhost/b\n"
        + "branch = .\n");

    String thisServer = THIS_SERVER;
    Branch.NameKey targetBranch = new Branch.NameKey(
        new Project.NameKey("project"), "master");
    Set<SubmoduleSubscription> res = subSecParserFactory.create(
        cfg, thisServer, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected = Sets.newHashSet(
        new SubmoduleSubscription(targetBranch, new Branch.NameKey(
            new Project.NameKey("b"), "master"), "b"),
        new SubmoduleSubscription(targetBranch, new Branch.NameKey(
            new Project.NameKey("a/b"), "master"), "a/b")
        );

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testSubmodulesParseWithAnInvalidSection() throws Exception {
    Config cfg = new Config();
    cfg.fromText("\n"
        + "[submodule \"a\"]\n"
        + "path = a\n"
        + "url = ssh://localhost/a\n"
        + "branch = .\n"
        + "[submodule \"b\"]\n"
        // path missing
        + "url = http://localhost:80/b\n"
        + "branch = master\n"
        + "[submodule \"c\"]\n"
        + "path = c\n"
        // url missing
        + "branch = .\n"
        + "[submodule \"d\"]\n"
        + "path = d-parent/the-d-folder\n"
        + "url = ssh://localhost/d\n"
        // branch missing
        + "[submodule \"e\"]\n"
        + "path = e\n"
        + "url = ssh://localhost/e\n"
        + "branch = refs/heads/master\n");
    String thisServer = THIS_SERVER;
    Branch.NameKey targetBranch = new Branch.NameKey(
        new Project.NameKey("project"), "master");
    Set<SubmoduleSubscription> res = subSecParserFactory.create(
        cfg, thisServer, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected = Sets.newHashSet(
        new SubmoduleSubscription(targetBranch, new Branch.NameKey(
            new Project.NameKey("a"), "master"), "a"),
        new SubmoduleSubscription(targetBranch, new Branch.NameKey(
            new Project.NameKey("e"), "master"), "e"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testSubmoduleSectionToOtherServer() throws Exception {
    Config cfg = new Config();
    cfg.fromText(""
        + "[submodule \"a\"]"
        + "path = a"
        + "url = ssh://non-localhost/a"
        + "branch = .");
    String thisServer = THIS_SERVER;
    Branch.NameKey targetBranch = new Branch.NameKey(
        new Project.NameKey("project"), "master");
    Set<SubmoduleSubscription> res = subSecParserFactory.create(
        cfg, thisServer, targetBranch).parseAllSections();

    assertThat(res).isEmpty();
  }
}
