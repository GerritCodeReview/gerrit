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

package com.google.codereview;

import junit.framework.TestCase;

import java.io.File;

/**
 * JUnit TestCase with support for creating a temporary directory.
 */
public abstract class TrashTestCase extends TestCase {
  protected File tempRoot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    tempRoot = File.createTempFile("codereview", "test");
    tempRoot.delete();
    tempRoot.mkdir();
  }

  @Override
  protected void tearDown() throws Exception {
    if (tempRoot != null) {
      rm(tempRoot);
    }
    super.tearDown();
  }

  protected static void rm(final File dir) {
    final File[] list = dir.listFiles();
    for (int i = 0; list != null && i < list.length; i++) {
      final File f = list[i];
      if (f.getName().equals(".") || f.getName().equals("..")) {
        continue;
      }
      if (f.isDirectory()) {
        rm(f);
      } else {
        f.delete();
      }
    }
    dir.delete();
  }
}
