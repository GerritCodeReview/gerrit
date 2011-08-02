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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SubmoduleSubscriptionAccess;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Provider;

import junit.framework.Assert;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.SortedSet;
import java.util.TreeSet;

public class SubmoduleSectionParserTest extends LocalDiskRepositoryTestCase {
  private SchemaFactory<ReviewDb> schemaFactory;
  private SubmoduleSubscriptionAccess subscriptions;
  private ReviewDb schema;
  private Provider<String> urlProvider;
  private GitRepositoryManager repoManager;
  private ReplicationQueue replication;
  private BlobBasedConfig bbc;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    schemaFactory = createStrictMock(SchemaFactory.class);
    schema = createStrictMock(ReviewDb.class);
    subscriptions = createStrictMock(SubmoduleSubscriptionAccess.class);
    urlProvider = createStrictMock(Provider.class);
    repoManager = createStrictMock(GitRepositoryManager.class);
    replication = createStrictMock(ReplicationQueue.class);
    bbc = createStrictMock(BlobBasedConfig.class);
  }

  private void doReplay() {
    replay(schemaFactory, schema, subscriptions, urlProvider, repoManager,
        replication, bbc);
  }

  private void doVerify() {
    verify(schemaFactory, schema, subscriptions, urlProvider, repoManager,
        replication, bbc);
  }

  @Test
  public void testSimpleHttpUrl() {
    final SortedSet<Project.NameKey> projects = new TreeSet<Project.NameKey>();
    projects.add(new Project.NameKey("a"));

    doAssertParseTrue("http://localhost/a", "a", ".", new Branch.NameKey(
        new Project.NameKey("super"), "refs/heads/master"), projects,
        new Branch.NameKey(new Project.NameKey("a"), "refs/heads/master"));
  }

  @Test
  public void testSimpleSshUrl() {
    final SortedSet<Project.NameKey> projects = new TreeSet<Project.NameKey>();
    projects.add(new Project.NameKey("a"));

    doAssertParseTrue("ssh://localhost/a", "a", ".", new Branch.NameKey(
        new Project.NameKey("super"), "refs/heads/master"), projects,
        new Branch.NameKey(new Project.NameKey("a"), "refs/heads/master"));
  }

  @Test
  public void testRevisionDiffOfSuper() {
    final String revision = "refs/heads/dev";

    final SortedSet<Project.NameKey> projects = new TreeSet<Project.NameKey>();
    projects.add(new Project.NameKey("a"));

    doAssertParseTrue("ssh://localhost/a", "a", revision, new Branch.NameKey(
        new Project.NameKey("super"), "refs/heads/master"), projects,
        new Branch.NameKey(new Project.NameKey("a"), revision));
  }

  @Test
  public void testProjNameWithSlash() {
    final String projectName = "tools/gerrit";

    final SortedSet<Project.NameKey> projects = new TreeSet<Project.NameKey>();
    projects.add(new Project.NameKey(projectName));

    doAssertParseTrue("ssh://localhost/" + projectName, "gerrit", ".",
        new Branch.NameKey(new Project.NameKey("super"), "refs/heads/master"),
        projects, new Branch.NameKey(new Project.NameKey(projectName),
            "refs/heads/master"));
  }

  @Test
  public void testFalseOtherServer() {
    doAssertParseFalse("ssh://review.source.com/a", "a", ".",
        new Branch.NameKey(new Project.NameKey("super"), "refs/heads/master"),
        null, 0);
  }

  @Test
  public void testFalseProjectNotFound() {
    final SortedSet<Project.NameKey> projects = new TreeSet<Project.NameKey>();
    projects.add(new Project.NameKey("project-one"));
    projects.add(new Project.NameKey("project-two"));

    doAssertParseFalse("ssh://localhost/a", "a", ".", new Branch.NameKey(
        new Project.NameKey("super"), "refs/heads/master"), projects, 1);
  }

  @Test
  public void testFalseProjectWithSlashesNotFound() {
    final SortedSet<Project.NameKey> projects = new TreeSet<Project.NameKey>();
    projects.add(new Project.NameKey("project-one"));
    projects.add(new Project.NameKey("project-two"));

    doAssertParseFalse("ssh://localhost/company/tools/project", "project", ".",
        new Branch.NameKey(new Project.NameKey("super"), "refs/heads/master"),
        projects, 3);
  }

  private void doAssertParseTrue(final String url, final String path,
      final String revision, final Branch.NameKey subscriber,
      final SortedSet<Project.NameKey> repoManagerList,
      final Branch.NameKey expectedSubmoduleBranch) {
    expect(bbc.getString("submodule", "id", "url")).andReturn(url);
    expect(bbc.getString("submodule", "id", "path")).andReturn(path);
    expect(bbc.getString("submodule", "id", "revision")).andReturn(revision);

    for (int index = 1; index <= expectedSubmoduleBranch.getParentKey().get()
        .split("/").length; index++) {
      expect(repoManager.list()).andReturn(repoManagerList);
    }

    doReplay();

    final SubmoduleSectionParser ssp =
        new SubmoduleSectionParser("id", bbc, "localhost", subscriber,
            repoManager);

    assertTrue(ssp.parse());

    doVerify();

    assertEquals(path, ssp.getPath());
    assertEquals(expectedSubmoduleBranch, ssp.getSubmoduleBranch());
  }

  private void doAssertParseFalse(final String url, final String path,
      final String revision, final Branch.NameKey subscriber,
      final SortedSet<Project.NameKey> repoManagerList, final int listCalls) {
    expect(bbc.getString("submodule", "id", "url")).andReturn(url);
    expect(bbc.getString("submodule", "id", "path")).andReturn(path);
    expect(bbc.getString("submodule", "id", "revision")).andReturn(revision);

    for (int index = 0; index < listCalls; index++) {
      expect(repoManager.list()).andReturn(repoManagerList);
    }

    doReplay();

    final SubmoduleSectionParser ssp =
        new SubmoduleSectionParser("id", bbc, "localhost", subscriber,
            repoManager);

    assertFalse(ssp.parse());

    doVerify();
  }

}
