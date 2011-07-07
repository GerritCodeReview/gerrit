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

package com.google.gerrit.test.integrationtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gerrit.test.util.RepositoryUtil;

import com.jcraft.jsch.JSchException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.Iterator;

public class CreateProjectsIT extends AbstractIntegrationTest {

  private Git git;

  @Test
  public void testSshProjectCreation() throws JSchException {
    ssh.createProject("myProject", false);
    assertTrue(ssh.listProjects().contains("myProject"));
    assertTrue(web.listProjects().contains("myProject"));

    git = ssh.cloneProject("myProject");
    try {
      git.log().call();
      fail("should have failed");
    } catch (NoHeadException e) {
      // expected
    }
  }

  @Test
  public void testSshProjectCreationWithBlank() throws JSchException {
    ssh.createProject("My Project", false);
    assertTrue(ssh.listProjects().contains("My Project"));
    assertTrue(web.listProjects().contains("My Project"));
  }

  @Test
  public void testSshProjectCreationWithEmptyCommit() throws JSchException,
      NoHeadException {
    ssh.createProject("projectWithEmptyCommit", true);
    git = ssh.cloneProject("projectWithEmptyCommit");
    final LogCommand logCmd = git.log();
    final Iterator<RevCommit> it = logCmd.call().iterator();
    assertTrue(it.hasNext());
    assertEquals("Initial empty repository\n", it.next().getFullMessage());
    assertFalse(it.hasNext());
  }

  @After
  public void deleteRepository() throws IOException {
    RepositoryUtil.deleteRepository(git);
  }
}
