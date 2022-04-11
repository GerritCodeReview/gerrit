// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.testing.GerritBaseTests;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

public class RepoRefCacheTest extends GerritBaseTests {

  @SuppressWarnings("resource")
  @Test
  public void usingClosedPerThreadCacheFails() throws Exception {
    RefCache cache;
    FS fs = FS.detect();
    fs.setUserHome(null);
    try (RefCache refCache = new RepoRefCache(newRepostory())) {
      cache = refCache;
    }

    exception.expect(IllegalStateException.class);
    exception.expectMessage("already closed");
    cache.get("foobar");
    cache.close();
  }

  private Repository newRepostory() throws Exception {
    DfsRepositoryDescription desc = new DfsRepositoryDescription("foo");
    FS fs = FS.detect();
    fs.setUserHome(null);
    return new InMemoryRepository.Builder().setRepositoryDescription(desc).setFS(fs).build();
  }
}
