// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.prettify.common.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.MapSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.prettify.common.SparseFileContent;
import java.util.HashMap;
import java.util.Map;

public class SparseFileContentSubject extends Subject {
  public static SparseFileContentSubject assertThat(SparseFileContent sparseFileContent) {
    return assertAbout(sparseFileContent()).that(sparseFileContent);
  }

  private final SparseFileContent sparseFileContent;

  private SparseFileContentSubject(FailureMetadata metadata, SparseFileContent actual) {
    super(metadata, actual);
    this.sparseFileContent = actual;
  }

  private static Subject.Factory<SparseFileContentSubject, SparseFileContent> sparseFileContent() {
    return SparseFileContentSubject::new;
  }

  public IntegerSubject getSize() {
    isNotNull();
    return check("size()").that(sparseFileContent.getSize());
  }

  public IntegerSubject getRangesCount() {
    isNotNull();
    return check("rangesCount()").that(sparseFileContent.getRangesCount());
  }

  public MapSubject lines() {
    isNotNull();
    Map<Integer, String> lines = new HashMap<>();
    SparseFileContent.Accessor accessor = sparseFileContent.createAccessor();
    int size = accessor.getSize();
    int idx = accessor.first();
    while (idx < size) {
      lines.put(idx, accessor.get(idx));
      idx = accessor.next(idx);
    }
    return check("lines()").that(lines);
  }
}
