// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.server.util.SubmoduleSectionParser;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class SubmoduleSectionParserIT extends AbstractDaemonTest {
  private static final String THIS_SERVER = "http://localhost/";

  @Test
  public void testFollowMasterBranch() throws Exception {
    Project.NameKey p = createProject("a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = localpath-to-a\n"
            + "url = ssh://localhost/"
            + p.get()
            + "\n"
            + "branch = master\n");
    Branch.NameKey targetBranch = new Branch.NameKey(new Project.NameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(
                targetBranch, new Branch.NameKey(p, "master"), "localpath-to-a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testFollowMatchingBranch() throws Exception {
    Project.NameKey p = createProject("a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = a\n"
            + "url = ssh://localhost/"
            + p.get()
            + "\n"
            + "branch = .\n");

    Branch.NameKey targetBranch1 = new Branch.NameKey(new Project.NameKey("project"), "master");

    Set<SubmoduleSubscription> res1 =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch1).parseAllSections();

    Set<SubmoduleSubscription> expected1 =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch1, new Branch.NameKey(p, "master"), "a"));

    assertThat(res1).containsExactlyElementsIn(expected1);

    Branch.NameKey targetBranch2 = new Branch.NameKey(new Project.NameKey("project"), "somebranch");

    Set<SubmoduleSubscription> res2 =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch2).parseAllSections();

    Set<SubmoduleSubscription> expected2 =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch2, new Branch.NameKey(p, "somebranch"), "a"));

    assertThat(res2).containsExactlyElementsIn(expected2);
  }

  @Test
  public void testFollowAnotherBranch() throws Exception {
    Project.NameKey p = createProject("a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = a\n"
            + "url = ssh://localhost/"
            + p.get()
            + "\n"
            + "branch = anotherbranch\n");

    Branch.NameKey targetBranch = new Branch.NameKey(new Project.NameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, new Branch.NameKey(p, "anotherbranch"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testWithAnotherURI() throws Exception {
    Project.NameKey p = createProject("a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = a\n"
            + "url = http://localhost:80/"
            + p.get()
            + "\n"
            + "branch = master\n");

    Branch.NameKey targetBranch = new Branch.NameKey(new Project.NameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, new Branch.NameKey(p, "master"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testWithSlashesInProjectName() throws Exception {
    Project.NameKey p = createProject("project/with/slashes/a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"project/with/slashes/a\"]\n"
            + "path = a\n"
            + "url = http://localhost:80/"
            + p.get()
            + "\n"
            + "branch = master\n");

    Branch.NameKey targetBranch = new Branch.NameKey(new Project.NameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, new Branch.NameKey(p, "master"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testWithSlashesInPath() throws Exception {
    Project.NameKey p = createProject("a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = a/b/c/d/e\n"
            + "url = http://localhost:80/"
            + p.get()
            + "\n"
            + "branch = master\n");

    Branch.NameKey targetBranch = new Branch.NameKey(new Project.NameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, new Branch.NameKey(p, "master"), "a/b/c/d/e"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testWithMoreSections() throws Exception {
    Project.NameKey p1 = createProject("a");
    Project.NameKey p2 = createProject("b");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "     path = a\n"
            + "     url = ssh://localhost/"
            + p1.get()
            + "\n"
            + "     branch = .\n"
            + "[submodule \"b\"]\n"
            + "		path = b\n"
            + "		url = http://localhost:80/"
            + p2.get()
            + "\n"
            + "		branch = master\n");

    Branch.NameKey targetBranch = new Branch.NameKey(new Project.NameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, new Branch.NameKey(p1, "master"), "a"),
            new SubmoduleSubscription(targetBranch, new Branch.NameKey(p2, "master"), "b"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testWithSubProjectFound() throws Exception {
    Project.NameKey p1 = createProject("a/b");
    Project.NameKey p2 = createProject("b");
    Config cfg = new Config();
    cfg.fromText(
        "\n"
            + "[submodule \"a/b\"]\n"
            + "path = a/b\n"
            + "url = ssh://localhost/"
            + p1.get()
            + "\n"
            + "branch = .\n"
            + "[submodule \"b\"]\n"
            + "path = b\n"
            + "url = http://localhost/"
            + p2.get()
            + "\n"
            + "branch = .\n");

    Branch.NameKey targetBranch = new Branch.NameKey(new Project.NameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, new Branch.NameKey(p2, "master"), "b"),
            new SubmoduleSubscription(targetBranch, new Branch.NameKey(p1, "master"), "a/b"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testWithAnInvalidSection() throws Exception {
    Project.NameKey p1 = createProject("a");
    Project.NameKey p2 = createProject("b");
    Project.NameKey p3 = createProject("d");
    Project.NameKey p4 = createProject("e");
    Config cfg = new Config();
    cfg.fromText(
        "\n"
            + "[submodule \"a\"]\n"
            + "    path = a\n"
            + "    url = ssh://localhost/"
            + p1.get()
            + "\n"
            + "    branch = .\n"
            + "[submodule \"b\"]\n"
            // path missing
            + "    url = http://localhost:80/"
            + p2.get()
            + "\n"
            + "    branch = master\n"
            + "[submodule \"c\"]\n"
            + "    path = c\n"
            // url missing
            + "    branch = .\n"
            + "[submodule \"d\"]\n"
            + "    path = d-parent/the-d-folder\n"
            + "    url = ssh://localhost/"
            + p3.get()
            + "\n"
            // branch missing
            + "[submodule \"e\"]\n"
            + "    path = e\n"
            + "    url = ssh://localhost/"
            + p4.get()
            + "\n"
            + "    branch = refs/heads/master\n");

    Branch.NameKey targetBranch = new Branch.NameKey(new Project.NameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, new Branch.NameKey(p1, "master"), "a"),
            new SubmoduleSubscription(targetBranch, new Branch.NameKey(p4, "master"), "e"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testWithSectionOfNonexistingProject() throws Exception {
    Config cfg = new Config();
    cfg.fromText(
        "\n"
            + "[submodule \"a\"]\n"
            + "path = a\n"
            + "url = ssh://non-localhost/a\n"
            // Project "a" doesn't exist
            + "branch = .\\n");

    Branch.NameKey targetBranch = new Branch.NameKey(new Project.NameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    assertThat(res).isEmpty();
  }

  @Test
  public void testWithSectionToOtherServer() throws Exception {
    Project.NameKey p1 = createProject("a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]"
            + "path = a"
            + "url = ssh://non-localhost/"
            + p1.get()
            + "\n"
            + "branch = .");

    Branch.NameKey targetBranch = new Branch.NameKey(new Project.NameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    assertThat(res).isEmpty();
  }

  @Test
  public void testWithRelativeURI() throws Exception {
    Project.NameKey p1 = createProject("a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = a\n"
            + "url = ../"
            + p1.get()
            + "\n"
            + "branch = master\n");

    Branch.NameKey targetBranch = new Branch.NameKey(new Project.NameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, new Branch.NameKey(p1, "master"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testWithDeepRelativeURI() throws Exception {
    Project.NameKey p1 = createProject("a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = a\n"
            + "url = ../../"
            + p1.get()
            + "\n"
            + "branch = master\n");

    Branch.NameKey targetBranch =
        new Branch.NameKey(new Project.NameKey("nested/project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, new Branch.NameKey(p1, "master"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void testWithOverlyDeepRelativeURI() throws Exception {
    Project.NameKey p1 = createProject("nested/a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = a\n"
            + "url = ../../"
            + p1.get()
            + "\n"
            + "branch = master\n");

    Branch.NameKey targetBranch =
        new Branch.NameKey(new Project.NameKey("nested/project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, new Branch.NameKey(p1, "master"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }
}
