// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.git;

import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.reviewdb.client.Project;
import com.jcraft.jsch.JSchException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.After;

@NoHttpd
@UseSsh
public class SshPushReplicationEventIT extends AbstractPushReplicationEventIT {
  private SshSession currentUserSession;

  @After
  public void closeSession() {
    if (currentUserSession != null) {
      currentUserSession.close();
      currentUserSession = null;
    }
  }

  @Override
  protected void setProtocolUser(AcceptanceTestRequestScope.Context context, TestAccount user) {
    closeSession();
    GitUtil.initSsh(user);
    currentUserSession = context.getSession();
    try {
      currentUserSession.open();
    } catch (JSchException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  protected TestRepository<InMemoryRepository> createTestRepository(Project.NameKey project)
      throws Exception {
    String url = currentUserSession.getUrl();
    return GitUtil.cloneProject(project, url + "/" + project.get());
  }
}
