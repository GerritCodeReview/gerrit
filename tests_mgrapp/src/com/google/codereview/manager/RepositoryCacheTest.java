// Copyright 2008 Google Inc.
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

package com.google.codereview.manager;

import com.google.codereview.TrashTestCase;

import org.spearce.jgit.lib.Repository;

import java.io.File;

public class RepositoryCacheTest extends TrashTestCase {
  public void testCreateCache() {
    final RepositoryCache rc = new RepositoryCache(tempRoot);
  }

  public void testLookupInvalidNames() {
    final RepositoryCache rc = new RepositoryCache(tempRoot);
    assertInvalidRepository(rc, "");
    assertInvalidRepository(rc, "^");
    assertInvalidRepository(rc, ".");
    assertInvalidRepository(rc, "..");
    assertInvalidRepository(rc, "../foo");
    assertInvalidRepository(rc, "/foo");
    assertInvalidRepository(rc, "/foo/bar");
    assertInvalidRepository(rc, "bar/../foo");
    assertInvalidRepository(rc, "bar\\..\\foo");
  }

  private void assertInvalidRepository(final RepositoryCache rc, final String n) {
    try {
      rc.get(n);
      fail("Cache accepted name " + n);
    } catch (InvalidRepositoryException err) {
      assertEquals(n, err.getMessage());
    }
  }

  public void testLookupNotCreatedRepository() {
    final String name = "test.git";
    final RepositoryCache rc = new RepositoryCache(tempRoot);
    try {
      rc.get(name);
    } catch (InvalidRepositoryException err) {
      assertEquals(name, err.getMessage());
    }
  }

  public void testLookupExistingEmptyRepository() throws Exception {
    final RepositoryCache rc = new RepositoryCache(tempRoot);

    // Create after the cache is built, to test creation-on-the-fly.
    //
    final String[] names = {"test.git", "foo/bar/test.git"};
    final File[] gitdir = new File[names.length];
    for (int i = 0; i < names.length; i++) {
      gitdir[i] = new File(tempRoot, names[i]);
      final Repository r = new Repository(gitdir[i]);
      r.create();
      assertTrue(gitdir[i].isDirectory());
    }

    final Repository[] cached = new Repository[names.length];
    for (int i = 0; i < names.length; i++) {
      cached[i] = rc.get(names[i]);
      assertNotNull(cached[i]);
      assertEquals(gitdir[i], cached[i].getDirectory());
    }
    for (int i = 0; i < names.length; i++) {
      assertSame(cached[i], rc.get(names[i]));
    }
  }
}
