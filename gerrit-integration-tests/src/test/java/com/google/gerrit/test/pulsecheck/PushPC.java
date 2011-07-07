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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.test.util.RepositoryUtil;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

public class PushPC extends AbstractPulseCheck {

  private static final String TEST_PROJECT = "TestProject";
  private Git git;

  @Test
  public void testSshPushForReview() throws Exception {
    if (!ssh.projectExists(TEST_PROJECT)) {
      ssh.createProject(TEST_PROJECT, true);
    }
    git = ssh.cloneProject(TEST_PROJECT);

    String fileName = createTestFile();

    final AddCommand addCmd = git.add();
    addCmd.addFilepattern(fileName);
    addCmd.call();

    final CommitCommand commitCmd = git.commit();
    commitCmd.setAuthor(server.getAdminIdent());
    commitCmd.setCommitter(server.getAdminIdent());
    commitCmd.setMessage("test commit");
    commitCmd.call();

    final PushCommand pushCmd = git.push();
    pushCmd.setRefSpecs(new RefSpec("HEAD:refs/for/master"));
    final Iterator<PushResult> it = pushCmd.call().iterator();
    assertTrue(it.hasNext());
    final PushResult pushResult = it.next();
    final RemoteRefUpdate refUpdate =
        pushResult.getRemoteUpdate("refs/for/master");
    assertEquals(
        refUpdate.getMessage() + "\nMessage from Gerrit:"
            + pushResult.getMessages(), Status.OK, refUpdate.getStatus());
    assertFalse(it.hasNext());
  }

  @After
  public void deleteRepository() throws IOException {
    RepositoryUtil.deleteRepository(git);
  }

  private String createTestFile() throws Exception {
    String fileName;
    int index = 1;
    File file;
    File parentFile = git.getRepository().getDirectory().getParentFile();
    do {
      fileName = "test" + index + ".txt";
      file = new File(parentFile, fileName);
    } while (file.exists());
    FileUtils.write(file, "my test file");
    return fileName;
  }
}
