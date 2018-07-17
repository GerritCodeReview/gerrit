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
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DiffInfo.ContentEntry;
import com.google.gerrit.truth.ListSubject;

public class DiffInfoSubject extends Subject<DiffInfoSubject, DiffInfo> {

  public static DiffInfoSubject assertThat(DiffInfo diffInfo) {
    return assertAbout(DiffInfoSubject::new).that(diffInfo);
  }

  private DiffInfoSubject(FailureMetadata failureMetadata, DiffInfo diffInfo) {
    super(failureMetadata, diffInfo);
  }

  public ListSubject<ContentEntrySubject, ContentEntry> content() {
    isNotNull();
    DiffInfo diffInfo = actual();
    return ListSubject.assertThat(diffInfo.content, ContentEntrySubject::assertThat)
        .named("content");
  }

  public ComparableSubject<?, ChangeType> changeType() {
    isNotNull();
    DiffInfo diffInfo = actual();
    return Truth.assertThat(diffInfo.changeType).named("changeType");
  }

  public FileMetaSubject metaA() {
    isNotNull();
    DiffInfo diffInfo = actual();
    return FileMetaSubject.assertThat(diffInfo.metaA).named("metaA");
  }

  public FileMetaSubject metaB() {
    isNotNull();
    DiffInfo diffInfo = actual();
    return FileMetaSubject.assertThat(diffInfo.metaB).named("metaB");
  }
}
