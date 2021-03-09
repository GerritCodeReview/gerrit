// Copyright (C) 2021 The Android Open Source Project
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
package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.Project.NameKey;
import java.util.SortedSet;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class GitRepositoryManagerTest {

  private GitRepositoryManager objectUnderTest;

  @Before
  public void setUp() throws Exception {
    objectUnderTest = new TestGitRepositoryManager();
  }

  @Test
  public void shouldReturnFalseWhenDefaultCanPerformGC() {
    assertThat(objectUnderTest.canPerformGC()).isFalse();
  }

  private static class TestGitRepositoryManager implements GitRepositoryManager {
    @Override
    public Repository openRepository(NameKey name) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Repository createRepository(NameKey name) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public SortedSet<NameKey> list() {
      throw new UnsupportedOperationException("Not implemented");
    }
  }
}
