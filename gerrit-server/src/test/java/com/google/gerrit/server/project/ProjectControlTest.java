// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.gerrit.common.data.Permission.READ;
import static com.google.gerrit.server.project.Util.DEVS;
import static com.google.gerrit.server.project.Util.allow;
import static com.google.gerrit.server.project.Util.deny;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.ProjectConfig;

import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link ProjectControl}. */
public class ProjectControlTest {
  private TestRepository<InMemoryRepository> repo;
  private Util util;
  private ProjectConfig project;

  @Before
  public void setUp() throws Exception {
    util = new Util();
    project = new ProjectConfig(new Project.NameKey("project"));
    InMemoryRepository inMemoryRepo = util.add(project);
    repo = new TestRepository<InMemoryRepository>(inMemoryRepo);
  }

  @Test
  public void canReadCommitWhenAllRefsVisible() throws Exception {
    allow(project, READ, DEVS, "refs/*");
    ObjectId id = repo.branch("master").commit().create();
    ProjectControl pc = util.user(project, DEVS);
    RevWalk rw = repo.getRevWalk();
    assertTrue(pc.canReadCommit(rw, rw.parseCommit(id)));
  }

  @Test
  public void canReadCommitIfRefVisible() throws Exception {
    allow(project, READ, DEVS, "refs/heads/branch1");
    deny(project, READ, DEVS, "refs/heads/branch2");

    ObjectId id1 = repo.branch("branch1").commit().create();
    ObjectId id2 = repo.branch("branch2").commit().create();

    ProjectControl pc = util.user(project, DEVS);
    RevWalk rw = repo.getRevWalk();
    assertTrue(pc.canReadCommit(rw, rw.parseCommit(id1)));
    assertFalse(pc.canReadCommit(rw, rw.parseCommit(id2)));
  }

  @Test
  public void canReadCommitIfReachableFromVisibleRef() throws Exception {
    allow(project, READ, DEVS, "refs/heads/branch1");
    deny(project, READ, DEVS, "refs/heads/branch2");

    RevCommit parent1 = repo.commit().create();
    repo.branch("branch1").commit().parent(parent1).create();

    RevCommit parent2 = repo.commit().create();
    repo.branch("branch2").commit().parent(parent2).create();

    ProjectControl pc = util.user(project, DEVS);
    RevWalk rw = repo.getRevWalk();
    assertTrue(pc.canReadCommit(rw, rw.parseCommit(parent1)));
    assertFalse(pc.canReadCommit(rw, rw.parseCommit(parent2)));
  }

  @Test
  public void cannotReadAfterRollbackWithRestrictedRead() throws Exception {
    allow(project, READ, DEVS, "refs/heads/branch1");

    RevCommit parent1 = repo.commit().create();
    ObjectId id1 = repo.branch("branch1").commit().parent(parent1).create();

    ProjectControl pc = util.user(project, DEVS);
    RevWalk rw = repo.getRevWalk();
    assertTrue(pc.canReadCommit(rw, rw.parseCommit(parent1)));
    assertTrue(pc.canReadCommit(rw, rw.parseCommit(id1)));

    repo.branch("branch1").update(parent1);
    assertTrue(pc.canReadCommit(rw, rw.parseCommit(parent1)));
    assertFalse(pc.canReadCommit(rw, rw.parseCommit(id1)));
  }

  @Test
  public void canReadAfterRollbackWithAllRefsVisible() throws Exception {
    allow(project, READ, DEVS, "refs/*");

    RevCommit parent1 = repo.commit().create();
    ObjectId id1 = repo.branch("branch1").commit().parent(parent1).create();

    ProjectControl pc = util.user(project, DEVS);
    RevWalk rw = repo.getRevWalk();
    assertTrue(pc.canReadCommit(rw, rw.parseCommit(parent1)));
    assertTrue(pc.canReadCommit(rw, rw.parseCommit(id1)));

    repo.branch("branch1").update(parent1);
    assertTrue(pc.canReadCommit(rw, rw.parseCommit(parent1)));
    assertFalse(pc.canReadCommit(rw, rw.parseCommit(id1)));
  }
}
