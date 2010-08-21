// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.Change;
import com.google.gwtorm.client.OrmException;

import junit.framework.TestCase;

import java.util.Arrays;

public class RegexFilePredicateTest extends TestCase {
  public void testPrefixOnlyOptimization() throws OrmException {
    RegexFilePredicate p = predicate("^a/b/.*");
    assertTrue(p.match(change("a/b/source.c")));
    assertFalse(p.match(change("source.c")));

    assertTrue(p.match(change("a/b/source.c", "a/c/test")));
    assertFalse(p.match(change("a/bb/source.c")));
  }

  public void testPrefixReducesSearchSpace() throws OrmException {
    RegexFilePredicate p = predicate("^a/b/.*\\.[ch]");
    assertTrue(p.match(change("a/b/source.c")));
    assertFalse(p.match(change("a/b/source.res")));
    assertFalse(p.match(change("source.res")));

    assertTrue(p.match(change("a/b/a.a", "a/b/a.d", "a/b/a.h")));
  }

  public void testFileExtension_Constant() throws OrmException {
    RegexFilePredicate p = predicate("^.*\\.res");
    assertTrue(p.match(change("test.res")));
    assertTrue(p.match(change("foo/bar/test.res")));
    assertFalse(p.match(change("test.res.bar")));
  }

  public void testFileExtension_CharacterGroup() throws OrmException {
    RegexFilePredicate p = predicate("^.*\\.[ch]");
    assertTrue(p.match(change("test.c")));
    assertTrue(p.match(change("test.h")));
    assertFalse(p.match(change("test.C")));
  }

  public void testEndOfString() throws OrmException {
    assertTrue(predicate("^a$").match(change("a")));
    assertFalse(predicate("^a$").match(change("a$")));

    assertFalse(predicate("^a\\$").match(change("a")));
    assertTrue(predicate("^a\\$").match(change("a$")));
  }

  public void testExactMatch() throws OrmException {
    RegexFilePredicate p = predicate("^foo.c");
    assertTrue(p.match(change("foo.c")));
    assertFalse(p.match(change("foo.cc")));
    assertFalse(p.match(change("bar.c")));
  }

  private static RegexFilePredicate predicate(String pattern) {
    return new RegexFilePredicate(null, null, pattern);
  }

  private static ChangeData change(String... files) {
    Arrays.sort(files);
    ChangeData cd = new ChangeData(new Change.Id(1));
    cd.setCurrentFilePaths(files);
    return cd;
  }
}
