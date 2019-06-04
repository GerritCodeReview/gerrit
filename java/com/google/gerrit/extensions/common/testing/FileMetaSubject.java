// Copyright (C) 2018 The Android Open Source Project
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

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.DiffInfo.FileMeta;

public class FileMetaSubject extends Subject {

  public static FileMetaSubject assertThat(FileMeta fileMeta) {
    return assertAbout(fileMetas()).that(fileMeta);
  }

  public static Subject.Factory<FileMetaSubject, FileMeta> fileMetas() {
    return FileMetaSubject::new;
  }

  private final FileMeta fileMeta;

  private FileMetaSubject(FailureMetadata failureMetadata, FileMeta fileMeta) {
    super(failureMetadata, fileMeta);
    this.fileMeta = fileMeta;
  }

  public IntegerSubject totalLineCount() {
    isNotNull();
    return check("totalLineCount()").that(fileMeta.lines);
  }
}
