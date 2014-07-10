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

package com.google.gerrit.acceptance.ssh;

import static com.google.gerrit.acceptance.GitUtil.add;
import static com.google.gerrit.acceptance.GitUtil.createCommit;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil.Commit;

import com.jcraft.jsch.JSchException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.PushResult;
import org.junit.Test;

import java.io.IOException;
import java.util.Locale;

public class BanCommitIT extends AbstractDaemonTest {

  @Test
  public void banCommit() throws IOException, GitAPIException, JSchException {
    add(git, "a.txt", "some content");
    Commit c = createCommit(git, admin.getIdent(), "subject");

    String response =
        sshSession.exec("gerrit ban-commit " + project.get() + " "
            + c.getCommit().getName());
    assertFalse(sshSession.hasError());
    assertFalse(response, response.toLowerCase(Locale.US).contains("error"));

    PushResult pushResult = pushHead(git, "refs/heads/master", false);
    assertTrue(pushResult.getRemoteUpdate("refs/heads/master").getMessage()
        .startsWith("contains banned commit"));
  }
}
