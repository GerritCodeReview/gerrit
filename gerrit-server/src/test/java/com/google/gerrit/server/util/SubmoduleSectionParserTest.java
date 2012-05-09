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

import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.server.git.GitRepositoryManager;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class SubmoduleSectionParserTest extends LocalDiskRepositoryTestCase {
  private final static String THIS_SERVER = "localhost";
  private GitRepositoryManager repoManager;
  private BlobBasedConfig bbc;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    repoManager = createStrictMock(GitRepositoryManager.class);
    bbc = createStrictMock(BlobBasedConfig.class);
  }

  private void doReplay() {
    replay(repoManager, bbc);
  }

  private void doVerify() {
    verify(repoManager, bbc);
  }

  @Test
  public void testSubmodulesParseWithCorrectSections() throws Exception {
    final Map<String, SubmoduleSection> sectionsToReturn =
        new TreeMap<String, SubmoduleSection>();
    sectionsToReturn.put("a", new SubmoduleSection("ssh://localhost/a", "a",
        "."));
    sectionsToReturn.put("b", new SubmoduleSection("ssh://localhost/b", "b",
        "."));
    sectionsToReturn.put("c", new SubmoduleSection("ssh://localhost/test/c",
        "c-path", "refs/heads/master"));
    sectionsToReturn.put("d", new SubmoduleSection("ssh://localhost/d",
        "d-parent/the-d-folder", "refs/heads/test"));
    sectionsToReturn.put("e", new SubmoduleSection("ssh://localhost/e.git", "e",
        "."));

    final Map<String, String> reposToBeFound = new HashMap<String, String>();
    reposToBeFound.put("a", "a");
    reposToBeFound.put("b", "b");
    reposToBeFound.put("c", "test/c");
    reposToBeFound.put("d", "d");
    reposToBeFound.put("e", "e");

    final Branch.NameKey superBranchNameKey =
        new Branch.NameKey(new Project.NameKey("super-project"),
            "refs/heads/master");

    final List<SubmoduleSubscription> expectedSubscriptions =
        new ArrayList<SubmoduleSubscription>();
    expectedSubscriptions
        .add(new SubmoduleSubscription(superBranchNameKey, new Branch.NameKey(
            new Project.NameKey("a"), "refs/heads/master"), "a"));
    expectedSubscriptions
        .add(new SubmoduleSubscription(superBranchNameKey, new Branch.NameKey(
            new Project.NameKey("b"), "refs/heads/master"), "b"));
    expectedSubscriptions.add(new SubmoduleSubscription(superBranchNameKey,
        new Branch.NameKey(new Project.NameKey("test/c"), "refs/heads/master"),
        "c-path"));
    expectedSubscriptions.add(new SubmoduleSubscription(superBranchNameKey,
        new Branch.NameKey(new Project.NameKey("d"), "refs/heads/test"),
        "d-parent/the-d-folder"));
    expectedSubscriptions
        .add(new SubmoduleSubscription(superBranchNameKey, new Branch.NameKey(
            new Project.NameKey("e"), "refs/heads/master"), "e"));

    execute(superBranchNameKey, sectionsToReturn, reposToBeFound,
        expectedSubscriptions);
  }

  @Test
  public void testSubmodulesParseWithAnInvalidSection() throws Exception {
    final Map<String, SubmoduleSection> sectionsToReturn =
        new TreeMap<String, SubmoduleSection>();
    sectionsToReturn.put("a", new SubmoduleSection("ssh://localhost/a", "a",
        "."));
    // This one is invalid since "b" is not a recognized project
    sectionsToReturn.put("b", new SubmoduleSection("ssh://localhost/b", "b",
        "."));
    sectionsToReturn.put("c", new SubmoduleSection("ssh://localhost/test/c",
        "c-path", "refs/heads/master"));
    sectionsToReturn.put("d", new SubmoduleSection("ssh://localhost/d",
        "d-parent/the-d-folder", "refs/heads/test"));
    sectionsToReturn.put("e", new SubmoduleSection("ssh://localhost/e.git", "e",
        "."));

    // "b" will not be in this list
    final Map<String, String> reposToBeFound = new HashMap<String, String>();
    reposToBeFound.put("a", "a");
    reposToBeFound.put("c", "test/c");
    reposToBeFound.put("d", "d");
    reposToBeFound.put("e", "e");

    final Branch.NameKey superBranchNameKey =
        new Branch.NameKey(new Project.NameKey("super-project"),
            "refs/heads/master");

    final List<SubmoduleSubscription> expectedSubscriptions =
        new ArrayList<SubmoduleSubscription>();
    expectedSubscriptions
        .add(new SubmoduleSubscription(superBranchNameKey, new Branch.NameKey(
            new Project.NameKey("a"), "refs/heads/master"), "a"));
    expectedSubscriptions.add(new SubmoduleSubscription(superBranchNameKey,
        new Branch.NameKey(new Project.NameKey("test/c"), "refs/heads/master"),
        "c-path"));
    expectedSubscriptions.add(new SubmoduleSubscription(superBranchNameKey,
        new Branch.NameKey(new Project.NameKey("d"), "refs/heads/test"),
        "d-parent/the-d-folder"));
    expectedSubscriptions
        .add(new SubmoduleSubscription(superBranchNameKey, new Branch.NameKey(
            new Project.NameKey("e"), "refs/heads/master"), "e"));

    execute(superBranchNameKey, sectionsToReturn, reposToBeFound,
        expectedSubscriptions);
  }

  @Test
  public void testSubmoduleSectionToOtherServer() throws Exception {
    Map<String, SubmoduleSection> sectionsToReturn =
        new HashMap<String, SubmoduleSection>();
    // The url is not to this server.
    sectionsToReturn.put("a", new SubmoduleSection("ssh://review.source.com/a",
        "a", "."));

    execute(new Branch.NameKey(new Project.NameKey("super-project"),
        "refs/heads/master"), sectionsToReturn, new HashMap<String, String>(),
        new ArrayList<SubmoduleSubscription>());
  }

  @Test
  public void testProjectNotFound() throws Exception {
    Map<String, SubmoduleSection> sectionsToReturn =
        new HashMap<String, SubmoduleSection>();
    sectionsToReturn.put("a", new SubmoduleSection("ssh://localhost/a", "a",
        "."));

    execute(new Branch.NameKey(new Project.NameKey("super-project"),
        "refs/heads/master"), sectionsToReturn, new HashMap<String, String>(),
        new ArrayList<SubmoduleSubscription>());
  }

  @Test
  public void testProjectWithSlashesNotFound() throws Exception {
    Map<String, SubmoduleSection> sectionsToReturn =
        new HashMap<String, SubmoduleSection>();
    sectionsToReturn.put("project", new SubmoduleSection(
        "ssh://localhost/company/tools/project", "project", "."));

    execute(new Branch.NameKey(new Project.NameKey("super-project"),
        "refs/heads/master"), sectionsToReturn, new HashMap<String, String>(),
        new ArrayList<SubmoduleSubscription>());
  }

  private void execute(final Branch.NameKey superProjectBranch,
      final Map<String, SubmoduleSection> sectionsToReturn,
      final Map<String, String> reposToBeFound,
      final List<SubmoduleSubscription> expectedSubscriptions) throws Exception {
    expect(bbc.getSubsections("submodule"))
        .andReturn(sectionsToReturn.keySet());

    for (final String id : sectionsToReturn.keySet()) {
      final SubmoduleSection section = sectionsToReturn.get(id);
      expect(bbc.getString("submodule", id, "url")).andReturn(section.getUrl());
      expect(bbc.getString("submodule", id, "path")).andReturn(
          section.getPath());
      expect(bbc.getString("submodule", id, "branch")).andReturn(
          section.getBranch());

      if (THIS_SERVER.equals(new URI(section.getUrl()).getHost())) {
        String projectNameCandidate = null;
        final String urlExtractedPath = new URI(section.getUrl()).getPath();
        int fromIndex = urlExtractedPath.length() - 1;
        while (fromIndex > 0) {
          fromIndex = urlExtractedPath.lastIndexOf('/', fromIndex - 1);
          projectNameCandidate = urlExtractedPath.substring(fromIndex + 1);
          if (projectNameCandidate.endsWith(".git")) {
            projectNameCandidate = projectNameCandidate.substring(0, projectNameCandidate.length() - 4);
          }
          if (projectNameCandidate.equals(reposToBeFound.get(id))) {
            expect(repoManager.list()).andReturn(
                new TreeSet<Project.NameKey>(Collections
                    .singletonList(new Project.NameKey(projectNameCandidate))));
            break;
          } else {
            expect(repoManager.list()).andReturn(
                new TreeSet<Project.NameKey>(Collections.<Project.NameKey> emptyList()));
          }
        }
      }
    }

    doReplay();

    final SubmoduleSectionParser ssp =
        new SubmoduleSectionParser(bbc, THIS_SERVER, superProjectBranch,
            repoManager);

    List<SubmoduleSubscription> returnedSubscriptions = ssp.parseAllSections();

    doVerify();

    assertEquals(expectedSubscriptions, returnedSubscriptions);
  }

  private final static class SubmoduleSection {
    private final String url;
    private final String path;
    private final String branch;

    public SubmoduleSection(final String url, final String path,
        final String branch) {
      this.url = url;
      this.path = path;
      this.branch = branch;
    }

    public String getUrl() {
      return url;
    }

    public String getPath() {
      return path;
    }

    public String getBranch() {
      return branch;
    }
  }
}
