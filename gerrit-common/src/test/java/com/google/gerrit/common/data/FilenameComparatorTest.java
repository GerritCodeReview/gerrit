// Copyright (C) 2016 The Android Open Source Project
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

public class FilenameComparatorTest {
  private FilenameComparator comparator = new FilenameComparator();

  @Test
  public void basicPaths() {
    assertTrue(comparator.compare(
        "abc/xyz/FileOne.java", "xyz/abc/FileTwo.java") < 0);
    assertTrue(comparator.compare(
        "abc/xyz/FileOne.java", "abc/xyz/FileOne.java") == 0);
    assertTrue(comparator.compare(
        "zzz/yyy/FileOne.java", "abc/xyz/FileOne.java") > 0);
  }

  @Test
  public void specialPaths() {
    assertTrue(comparator.compare(
        "ABC/xyz/FileOne.java", "/COMMIT_MSG") > 0);
    assertTrue(comparator.compare(
        "/COMMIT_MSG", "ABC/xyz/FileOne.java") < 0);

    assertTrue(comparator.compare(
        "ABC/xyz/FileOne.java", "/MERGE_LIST") > 0);
    assertTrue(comparator.compare(
        "/MERGE_LIST", "ABC/xyz/FileOne.java") < 0);

    assertTrue(comparator.compare(
        "/COMMIT_MSG", "/MERGE_LIST") < 0);
    assertTrue(comparator.compare(
        "/MERGE_LIST", "/COMMIT_MSG") > 0);
  }

  @Test
  public void cppExtensions() {
    assertTrue(comparator.compare(
        "abc/file.h", "abc/file.cc") < 0);
    assertTrue(comparator.compare(
        "abc/file.c", "abc/file.hpp") > 0);
    assertTrue(comparator.compare(
        "abc..xyz.file.h", "abc.xyz.file.cc") < 0);
  }
}