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
package com.google.gerrit.test.pulsecheck;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gerrit.test.util.ProjectUtil;
import com.google.gerrit.test.util.RepositoryUtil;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;

public class CreateProjectPC extends AbstractPulseCheck {

  private Git git;

  @Test
  public void testSshProjectCreation() throws Exception {
    String projectName = ProjectUtil.calcTestProjectName(ssh);
    ssh.createProject(projectName, false);
    assertTrue(ssh.listProjects().contains(projectName));

    web.login(server.getAdminUser(), server.getAdminIdent().getName(), null);
    try {
      assertTrue(web.listProjects().contains(projectName));
    } finally {
      web.logout();
    }


    git = ssh.cloneProject(projectName);
    try {
      git.log().call();
      fail("should have failed");
    } catch (NoHeadException e) {
      // expected
    }
  }

  @After
  public void deleteRepository() throws IOException {
    RepositoryUtil.deleteRepository(git);
  }

}
