// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import java.io.IOException;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class GitRepositoryReferenceCountingManagerIT extends AbstractDaemonTest {

  private class CallerLeavingRepositoryOpen {

    Repository openRepository() throws IOException {
      return repoManager.openRepository(project);
    }

    void incrementOpenRepository(Repository repository) {
      repository.incrementOpen();
    }
  }

  @Test(expected = AssertionError.class)
  @SuppressWarnings("resource")
  public void shouldFailTestWhenRepositoryIsLeftOpen() throws Exception {
    Repository unused = repoManager.openRepository(project);
    afterTest();
  }

  @Test
  public void shouldNotFailTestWhenRepositoryIsClosed() throws IOException {
    try (Repository repository = repoManager.openRepository(project)) {
      repository.incrementOpen();
      repository.close();
    }
  }

  @Test
  @SuppressWarnings("resource")
  public void shouldFailMentioningTheRepositoryLeftOpen() throws IOException {
    Repository unused = repoManager.openRepository(project);
    AssertionError error = assertThrows(AssertionError.class, this::afterTest);
    assertThat(error.getLocalizedMessage()).contains(project.get());
  }

  @Test
  @SuppressWarnings("resource")
  public void shouldFailMentioningTheCallersLeavingTheRepositoryOpen() throws IOException {
    CallerLeavingRepositoryOpen caller = new CallerLeavingRepositoryOpen();
    Repository unused = caller.openRepository();
    AssertionError error = assertThrows(AssertionError.class, this::afterTest);
    assertThat(error.getLocalizedMessage()).contains(caller.getClass().getName());
    assertThat(error.getLocalizedMessage()).contains("openRepository");
  }

  @Test
  public void shouldFailMentioningTheCallersLeavingTheRepositoryWithReferenceCountingOpen()
      throws IOException {
    CallerLeavingRepositoryOpen caller = new CallerLeavingRepositoryOpen();
    String callerClassName = caller.getClass().getName();
    try (Repository repository = caller.openRepository()) {
      caller.incrementOpenRepository(repository);
    }
    AssertionError error = assertThrows(AssertionError.class, this::afterTest);
    assertThat(error.getLocalizedMessage()).contains(callerClassName);
    assertThat(error.getLocalizedMessage()).contains("incrementOpenRepository");
  }
}
