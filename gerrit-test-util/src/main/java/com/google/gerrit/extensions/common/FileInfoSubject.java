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

package com.google.gerrit.extensions.common;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;

public class FileInfoSubject extends Subject<FileInfoSubject, FileInfo> {

  private static final SubjectFactory<FileInfoSubject, FileInfo> FILE_INFO_SUBJECT_FACTORY =
      new SubjectFactory<FileInfoSubject, FileInfo>() {
        @Override
        public FileInfoSubject getSubject(FailureStrategy failureStrategy, FileInfo fileInfo) {
          return new FileInfoSubject(failureStrategy, fileInfo);
        }
      };

  public static FileInfoSubject assertThat(FileInfo fileInfo) {
    return assertAbout(FILE_INFO_SUBJECT_FACTORY).that(fileInfo);
  }

  private FileInfoSubject(FailureStrategy failureStrategy, FileInfo fileInfo) {
    super(failureStrategy, fileInfo);
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
}
