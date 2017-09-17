// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.extensions.common.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.gerrit.extensions.common.FileInfo;

public class FileInfoSubject extends Subject<FileInfoSubject, FileInfo> {

  public static FileInfoSubject assertThat(FileInfo fileInfo) {
    return assertAbout(FileInfoSubject::new).that(fileInfo);
  }

  private FileInfoSubject(FailureMetadata failureMetadata, FileInfo fileInfo) {
    super(failureMetadata, fileInfo);
  }

  public IntegerSubject linesInserted() {
    isNotNull();
    FileInfo fileInfo = actual();
    return Truth.assertThat(fileInfo.linesInserted).named("linesInserted");
  }

  public IntegerSubject linesDeleted() {
    isNotNull();
    FileInfo fileInfo = actual();
    return Truth.assertThat(fileInfo.linesDeleted).named("linesDeleted");
  }

  public ComparableSubject<?, Character> status() {
    isNotNull();
    FileInfo fileInfo = actual();
    return Truth.assertThat(fileInfo.status).named("status");
  }
}
