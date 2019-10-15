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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import java.util.Arrays;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class RegexPathPredicateTest {
  @Test
  public void prefixOnlyOptimization() {
    RegexPathPredicate p = predicate("^a/b/.*");
    assertTrue(p.match(change("a/b/source.c")));
    assertFalse(p.match(change("source.c")));

    assertTrue(p.match(change("a/b/source.c", "a/c/test")));
    assertFalse(p.match(change("a/bb/source.c")));
  }

  @Test
  public void prefixReducesSearchSpace() {
    RegexPathPredicate p = predicate("^a/b/.*\\.[ch]");
    assertTrue(p.match(change("a/b/source.c")));
    assertFalse(p.match(change("a/b/source.res")));
    assertFalse(p.match(change("source.res")));

    assertTrue(p.match(change("a/b/a.a", "a/b/a.d", "a/b/a.h")));
  }

  @Test
  public void fileExtension_Constant() {
    RegexPathPredicate p = predicate("^.*\\.res");
    assertTrue(p.match(change("test.res")));
    assertTrue(p.match(change("foo/bar/test.res")));
    assertFalse(p.match(change("test.res.bar")));
  }

  @Test
  public void fileExtension_CharacterGroup() {
    RegexPathPredicate p = predicate("^.*\\.[ch]");
    assertTrue(p.match(change("test.c")));
    assertTrue(p.match(change("test.h")));
    assertFalse(p.match(change("test.C")));
  }

  @Test
  public void endOfString() {
    assertTrue(predicate("^a$").match(change("a")));
    assertFalse(predicate("^a$").match(change("a$")));

    assertFalse(predicate("^a\\$").match(change("a")));
    assertTrue(predicate("^a\\$").match(change("a$")));
  }

  @Test
  public void exactMatch() {
    RegexPathPredicate p = predicate("^foo.c");
    assertTrue(p.match(change("foo.c")));
    assertFalse(p.match(change("foo.cc")));
    assertFalse(p.match(change("bar.c")));
  }

  private static RegexPathPredicate predicate(String pattern) {
    return new RegexPathPredicate(pattern);
  }

  private static ChangeData change(String... files) {
    Arrays.sort(files);
    ChangeData cd =
        ChangeData.createForTest(Project.nameKey("project"), Change.id(1), 1, ObjectId.zeroId());
    cd.setCurrentFilePaths(Arrays.asList(files));
    return cd;
  }
}
