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

import com.google.gerrit.test.Change;
import com.google.gerrit.test.util.RepositoryUtil;

import com.jcraft.jsch.JSchException;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class PushIT extends AbstractIntegrationTest {

  private Git git;

  @Test
  public void testSshPushForReview() throws JSchException,
      NoFilepatternException, IOException, NoHeadException, NoMessageException,
      ConcurrentRefUpdateException, JGitInternalException,
      WrongRepositoryStateException, InvalidRemoteException {
    ssh.createProject("myProject", true);
    git = ssh.cloneProject("myProject");

    FileUtils.write(new File(
        git.getRepository().getDirectory().getParentFile(), "test.txt"),
        "my test file");

    final AddCommand addCmd = git.add();
    addCmd.addFilepattern("test.txt");
    addCmd.call();

    final CommitCommand commitCmd = git.commit();
    commitCmd.setAuthor(server.getAdminIdent());
    commitCmd.setCommitter(server.getAdminIdent());
    final String commitMessage = "test commit";
    commitCmd.setMessage(commitMessage);
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

    final List<Change> changes = web.listAllOpenChanges();
    assertEquals(1, changes.size());
    final Change change = changes.get(0);
    assertEquals(commitMessage, change.subject);
    assertEquals(server.getAdminIdent().getName(), change.owner);
    assertEquals("master", change.branch);
    assertEquals("myProject", change.projectName);
  }

  @After
  public void deleteRepository() throws IOException {
    RepositoryUtil.deleteRepository(git);
  }
}
