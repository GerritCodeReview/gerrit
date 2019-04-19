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

package com.google.gerrit.server.util.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.testing.GerritBaseTests;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class SubmoduleSectionParserTest extends GerritBaseTests {
  private static final String THIS_SERVER = "http://localhost/";

  @Test
  public void followMasterBranch() throws Exception {
    Project.NameKey p = Project.nameKey("proj");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = localpath-to-a\n"
            + "url = ssh://localhost/"
            + p.get()
            + "\n"
            + "branch = master\n");
    Branch.NameKey targetBranch = Branch.nameKey(Project.nameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, Branch.nameKey(p, "master"), "localpath-to-a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void followMatchingBranch() throws Exception {
    Project.NameKey p = Project.nameKey("a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = a\n"
            + "url = ssh://localhost/"
            + p.get()
            + "\n"
            + "branch = .\n");

    Branch.NameKey targetBranch1 = Branch.nameKey(Project.nameKey("project"), "master");

    Set<SubmoduleSubscription> res1 =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch1).parseAllSections();

    Set<SubmoduleSubscription> expected1 =
        Sets.newHashSet(new SubmoduleSubscription(targetBranch1, Branch.nameKey(p, "master"), "a"));

    assertThat(res1).containsExactlyElementsIn(expected1);

    Branch.NameKey targetBranch2 = Branch.nameKey(Project.nameKey("project"), "somebranch");

    Set<SubmoduleSubscription> res2 =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch2).parseAllSections();

    Set<SubmoduleSubscription> expected2 =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch2, Branch.nameKey(p, "somebranch"), "a"));

    assertThat(res2).containsExactlyElementsIn(expected2);
  }

  @Test
  public void followAnotherBranch() throws Exception {
    Project.NameKey p = Project.nameKey("a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = a\n"
            + "url = ssh://localhost/"
            + p.get()
            + "\n"
            + "branch = anotherbranch\n");

    Branch.NameKey targetBranch = Branch.nameKey(Project.nameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, Branch.nameKey(p, "anotherbranch"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void withAnotherURI() throws Exception {
    Project.NameKey p = Project.nameKey("a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = a\n"
            + "url = http://localhost:80/"
            + p.get()
            + "\n"
            + "branch = master\n");

    Branch.NameKey targetBranch = Branch.nameKey(Project.nameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(new SubmoduleSubscription(targetBranch, Branch.nameKey(p, "master"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void withSlashesInProjectName() throws Exception {
    Project.NameKey p = Project.nameKey("project/with/slashes/a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"project/with/slashes/a\"]\n"
            + "path = a\n"
            + "url = http://localhost:80/"
            + p.get()
            + "\n"
            + "branch = master\n");

    Branch.NameKey targetBranch = Branch.nameKey(Project.nameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(new SubmoduleSubscription(targetBranch, Branch.nameKey(p, "master"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void withSlashesInPath() throws Exception {
    Project.NameKey p = Project.nameKey("a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = a/b/c/d/e\n"
            + "url = http://localhost:80/"
            + p.get()
            + "\n"
            + "branch = master\n");

    Branch.NameKey targetBranch = Branch.nameKey(Project.nameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, Branch.nameKey(p, "master"), "a/b/c/d/e"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void withMoreSections() throws Exception {
    Project.NameKey p1 = Project.nameKey("a");
    Project.NameKey p2 = Project.nameKey("b");
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

    Branch.NameKey targetBranch = Branch.nameKey(Project.nameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, Branch.nameKey(p1, "master"), "a"),
            new SubmoduleSubscription(targetBranch, Branch.nameKey(p2, "master"), "b"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void withSubProjectFound() throws Exception {
    Project.NameKey p1 = Project.nameKey("a/b");
    Project.NameKey p2 = Project.nameKey("b");
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

    Branch.NameKey targetBranch = Branch.nameKey(Project.nameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, Branch.nameKey(p2, "master"), "b"),
            new SubmoduleSubscription(targetBranch, Branch.nameKey(p1, "master"), "a/b"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void withAnInvalidSection() throws Exception {
    Project.NameKey p1 = Project.nameKey("a");
    Project.NameKey p2 = Project.nameKey("b");
    Project.NameKey p3 = Project.nameKey("d");
    Project.NameKey p4 = Project.nameKey("e");
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

    Branch.NameKey targetBranch = Branch.nameKey(Project.nameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(
            new SubmoduleSubscription(targetBranch, Branch.nameKey(p1, "master"), "a"),
            new SubmoduleSubscription(targetBranch, Branch.nameKey(p4, "master"), "e"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void withSectionOfNonexistingProject() throws Exception {
    Config cfg = new Config();
    cfg.fromText(
        "\n"
            + "[submodule \"a\"]\n"
            + "path = a\n"
            + "url = ssh://non-localhost/a\n"
            // Project "a" doesn't exist
            + "branch = .\\n");

    Branch.NameKey targetBranch = Branch.nameKey(Project.nameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    assertThat(res).isEmpty();
  }

  @Test
  public void withSectionToOtherServer() throws Exception {
    Project.NameKey p1 = Project.nameKey("a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]"
            + "path = a"
            + "url = ssh://non-localhost/"
            + p1.get()
            + "\n"
            + "branch = .");

    Branch.NameKey targetBranch = Branch.nameKey(Project.nameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    assertThat(res).isEmpty();
  }

  @Test
  public void withRelativeURI() throws Exception {
    Project.NameKey p1 = Project.nameKey("a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = a\n"
            + "url = ../"
            + p1.get()
            + "\n"
            + "branch = master\n");

    Branch.NameKey targetBranch = Branch.nameKey(Project.nameKey("project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(new SubmoduleSubscription(targetBranch, Branch.nameKey(p1, "master"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void withDeepRelativeURI() throws Exception {
    Project.NameKey p1 = Project.nameKey("a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = a\n"
            + "url = ../../"
            + p1.get()
            + "\n"
            + "branch = master\n");

    Branch.NameKey targetBranch = Branch.nameKey(Project.nameKey("nested/project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(new SubmoduleSubscription(targetBranch, Branch.nameKey(p1, "master"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }

  @Test
  public void withOverlyDeepRelativeURI() throws Exception {
    Project.NameKey p1 = Project.nameKey("nested/a");
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[submodule \"a\"]\n"
            + "path = a\n"
            + "url = ../../"
            + p1.get()
            + "\n"
            + "branch = master\n");

    Branch.NameKey targetBranch = Branch.nameKey(Project.nameKey("nested/project"), "master");

    Set<SubmoduleSubscription> res =
        new SubmoduleSectionParser(cfg, THIS_SERVER, targetBranch).parseAllSections();

    Set<SubmoduleSubscription> expected =
        Sets.newHashSet(new SubmoduleSubscription(targetBranch, Branch.nameKey(p1, "master"), "a"));

    assertThat(res).containsExactlyElementsIn(expected);
  }
}
