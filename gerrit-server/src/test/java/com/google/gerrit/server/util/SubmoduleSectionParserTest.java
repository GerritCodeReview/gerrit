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

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Constants;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class SubmoduleSectionParserTest extends LocalDiskRepositoryTestCase {
  private static final String THIS_SERVER = "localhost";
  private ProjectCache projectCache;
  private BlobBasedConfig bbc;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    projectCache = createStrictMock(ProjectCache.class);
    bbc = createStrictMock(BlobBasedConfig.class);
  }

  private void doReplay() {
    replay(projectCache, bbc);
  }

  private void doVerify() {
    verify(projectCache, bbc);
  }

  @Test
  public void testSubmodulesParseWithCorrectSections() throws Exception {
    final Map<String, SubmoduleSection> sectionsToReturn = new TreeMap<>();
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

    Map<String, String> reposToBeFound = new HashMap<>();
    reposToBeFound.put("a", "a");
    reposToBeFound.put("b", "b");
    reposToBeFound.put("c", "test/c");
    reposToBeFound.put("d", "d");
    reposToBeFound.put("e", "e");

    final Branch.NameKey superBranchNameKey =
        new Branch.NameKey(new Project.NameKey("super-project"),
            "refs/heads/master");

    Set<SubmoduleSubscription> expectedSubscriptions = Sets.newHashSet();
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
    final Map<String, SubmoduleSection> sectionsToReturn = new TreeMap<>();
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
    Map<String, String> reposToBeFound = new HashMap<>();
    reposToBeFound.put("a", "a");
    reposToBeFound.put("c", "test/c");
    reposToBeFound.put("d", "d");
    reposToBeFound.put("e", "e");

    final Branch.NameKey superBranchNameKey =
        new Branch.NameKey(new Project.NameKey("super-project"),
            "refs/heads/master");

    Set<SubmoduleSubscription> expectedSubscriptions = Sets.newHashSet();
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
    Map<String, SubmoduleSection> sectionsToReturn = new HashMap<>();
    // The url is not to this server.
    sectionsToReturn.put("a", new SubmoduleSection("ssh://review.source.com/a",
        "a", "."));

    Set<SubmoduleSubscription> expectedSubscriptions = Collections.emptySet();
    execute(new Branch.NameKey(new Project.NameKey("super-project"),
        "refs/heads/master"), sectionsToReturn, new HashMap<String, String>(),
        expectedSubscriptions);
  }

  @Test
  public void testProjectNotFound() throws Exception {
    Map<String, SubmoduleSection> sectionsToReturn = new HashMap<>();
    sectionsToReturn.put("a", new SubmoduleSection("ssh://localhost/a", "a",
        "."));

    Set<SubmoduleSubscription> expectedSubscriptions = Collections.emptySet();
    execute(new Branch.NameKey(new Project.NameKey("super-project"),
        "refs/heads/master"), sectionsToReturn, new HashMap<String, String>(),
        expectedSubscriptions);
  }

  @Test
  public void testProjectWithSlashesNotFound() throws Exception {
    Map<String, SubmoduleSection> sectionsToReturn = new HashMap<>();
    sectionsToReturn.put("project", new SubmoduleSection(
        "ssh://localhost/company/tools/project", "project", "."));

    Set<SubmoduleSubscription> expectedSubscriptions = Collections.emptySet();
    execute(new Branch.NameKey(new Project.NameKey("super-project"),
        "refs/heads/master"), sectionsToReturn, new HashMap<String, String>(),
        expectedSubscriptions);
  }

  private void execute(final Branch.NameKey superProjectBranch,
      final Map<String, SubmoduleSection> sectionsToReturn,
      final Map<String, String> reposToBeFound,
      final Set<SubmoduleSubscription> expectedSubscriptions) throws Exception {
    expect(bbc.getSubsections("submodule"))
        .andReturn(sectionsToReturn.keySet());

    for (Map.Entry<String, SubmoduleSection> entry : sectionsToReturn.entrySet()) {
      String id = entry.getKey();
      final SubmoduleSection section = entry.getValue();
      expect(bbc.getString("submodule", id, "url")).andReturn(section.getUrl());
      expect(bbc.getString("submodule", id, "path")).andReturn(
          section.getPath());
      expect(bbc.getString("submodule", id, "branch")).andReturn(
          section.getBranch());

      if (THIS_SERVER.equals(new URI(section.getUrl()).getHost())) {
        String projectNameCandidate;
        final String urlExtractedPath = new URI(section.getUrl()).getPath();
        int fromIndex = urlExtractedPath.length() - 1;
        while (fromIndex > 0) {
          fromIndex = urlExtractedPath.lastIndexOf('/', fromIndex - 1);
          projectNameCandidate = urlExtractedPath.substring(fromIndex + 1);
          if (projectNameCandidate.endsWith(Constants.DOT_GIT_EXT)) {
            projectNameCandidate = projectNameCandidate.substring(0, //
                projectNameCandidate.length() - Constants.DOT_GIT_EXT.length());
          }
          if (projectNameCandidate.equals(reposToBeFound.get(id))) {
            expect(projectCache.get(new Project.NameKey(projectNameCandidate)))
                .andReturn(createNiceMock(ProjectState.class));
            break;
          } else {
            expect(projectCache.get(new Project.NameKey(projectNameCandidate)))
                .andReturn(null);
          }
        }
      }
    }

    doReplay();

    final SubmoduleSectionParser ssp =
        new SubmoduleSectionParser(projectCache, bbc, THIS_SERVER,
            superProjectBranch);

    Set<SubmoduleSubscription> returnedSubscriptions = ssp.parseAllSections();

    doVerify();

    assertEquals(expectedSubscriptions, returnedSubscriptions);
  }

  private static final class SubmoduleSection {
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
