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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

public class GitmodulesIT extends AbstractDaemonTest {
  @Test
  public void invalidSubmoduleURLIsRejected() throws Exception {
    pushGitmodules("name", "-invalid-url", "path", "Invalid submodule URL");
  }

  @Test
  public void invalidSubmodulePathIsRejected() throws Exception {
    pushGitmodules("name", "http://somewhere", "-invalid-path", "Invalid submodule path");
  }

  @Test
  public void invalidSubmoduleNameIsRejected() throws Exception {
    pushGitmodules("-invalid-name", "http://somewhere", "path", "Invalid submodule name");
  }

  @Test
  public void notSetSubmoduleURLIsRejected() throws Exception {
    // TODO(dpursehouse) change this error message when
    // SubmoduleValidator#assertValidGitModulesFile
    // is fixed to not throw NPE on unset URL.
    pushGitmodules("name", null, "path", "null");
  }

  @Test
  public void notSetSubmodulePathIsRejected() throws Exception {
    Config config = new Config();
    config.setString("submodule", "name", "url", "http://somewhere");
    // TODO(dpursehouse) change this error message when
    // SubmoduleValidator#assertValidGitModulesFile
    // is fixed to not throw NPE on unset path.
    pushGitmodules("name", "http://somewhere", null, "null");
  }

  private void pushGitmodules(String name, String url, String path, String expectedErrorMessage)
      throws Exception {
    Config config = new Config();
    if (url != null) {
      config.setString("submodule", name, "url", url);
    }
    if (path != null) {
      config.setString("submodule", name, "path", path);
    }
    TestRepository<?> repo = cloneProject(project);
    repo.branch("HEAD")
        .commit()
        .insertChangeId()
        .message("subject: adding new subscription")
        .add(".gitmodules", config.toText().toString())
        .create();

    exception.expectMessage(expectedErrorMessage);
    exception.expect(TransportException.class);
    repo.git().push().setRemote("origin").setRefSpecs(new RefSpec("HEAD:refs/for/master")).call();
  }
}
