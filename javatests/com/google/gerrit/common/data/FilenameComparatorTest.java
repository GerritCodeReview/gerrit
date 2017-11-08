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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class FilenameComparatorTest {
  private FilenameComparator comparator = FilenameComparator.INSTANCE;

  @Test
  public void basicPaths() {
    assertThat(comparator.compare("abc/xyz/FileOne.java", "xyz/abc/FileTwo.java")).isLessThan(0);
    assertThat(comparator.compare("abc/xyz/FileOne.java", "abc/xyz/FileOne.java")).isEqualTo(0);
    assertThat(comparator.compare("zzz/yyy/FileOne.java", "abc/xyz/FileOne.java")).isGreaterThan(0);
  }

  @Test
  public void specialPaths() {
    assertThat(comparator.compare("ABC/xyz/FileOne.java", "/COMMIT_MSG")).isGreaterThan(0);
    assertThat(comparator.compare("/COMMIT_MSG", "ABC/xyz/FileOne.java")).isLessThan(0);

    assertThat(comparator.compare("ABC/xyz/FileOne.java", "/MERGE_LIST")).isGreaterThan(0);
    assertThat(comparator.compare("/MERGE_LIST", "ABC/xyz/FileOne.java")).isLessThan(0);

    assertThat(comparator.compare("/COMMIT_MSG", "/MERGE_LIST")).isLessThan(0);
    assertThat(comparator.compare("/MERGE_LIST", "/COMMIT_MSG")).isGreaterThan(0);

    assertThat(comparator.compare("/COMMIT_MSG", "/COMMIT_MSG")).isEqualTo(0);
    assertThat(comparator.compare("/MERGE_LIST", "/MERGE_LIST")).isEqualTo(0);
  }

  @Test
  public void cppExtensions() {
    assertThat(comparator.compare("abc/file.h", "abc/file.cc")).isLessThan(0);
    assertThat(comparator.compare("abc/file.c", "abc/file.hpp")).isGreaterThan(0);
    assertThat(comparator.compare("abc..xyz.file.h", "abc.xyz.file.cc")).isLessThan(0);
  }
}
