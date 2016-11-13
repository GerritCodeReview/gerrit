// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.common.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EncodePathSeparatorTest {
  @Test
  public void testDefaultBehaviour() {
    assertEquals("a/b", new GitwebType().replacePathSeparator("a/b"));
  }

  @Test
  public void testExclamationMark() {
    GitwebType gitwebType = new GitwebType();
    gitwebType.setPathSeparator('!');
    assertEquals("a!b", gitwebType.replacePathSeparator("a/b"));
  }
}
