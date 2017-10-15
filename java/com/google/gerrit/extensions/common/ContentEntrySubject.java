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

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.gerrit.extensions.common.DiffInfo.ContentEntry;
import com.google.gerrit.truth.ListSubject;

public class ContentEntrySubject extends Subject<ContentEntrySubject, ContentEntry> {

  public static ContentEntrySubject assertThat(ContentEntry contentEntry) {
    return assertAbout(ContentEntrySubject::new).that(contentEntry);
  }

  private ContentEntrySubject(FailureMetadata failureMetadata, ContentEntry contentEntry) {
    super(failureMetadata, contentEntry);
  }

  public void isDueToRebase() {
    isNotNull();
    ContentEntry contentEntry = actual();
    Truth.assertWithMessage("Entry should be marked 'dueToRebase'")
        .that(contentEntry.dueToRebase)
        .named("dueToRebase")
        .isTrue();
  }

  public void isNotDueToRebase() {
    isNotNull();
    ContentEntry contentEntry = actual();
    Truth.assertWithMessage("Entry should not be marked 'dueToRebase'")
        .that(contentEntry.dueToRebase)
        .named("dueToRebase")
        .isNull();
  }

  public ListSubject<StringSubject, String> commonLines() {
    isNotNull();
    ContentEntry contentEntry = actual();
    return ListSubject.assertThat(contentEntry.ab, Truth::assertThat).named("common lines");
  }

  public ListSubject<StringSubject, String> linesOfA() {
    isNotNull();
    ContentEntry contentEntry = actual();
    return ListSubject.assertThat(contentEntry.a, Truth::assertThat).named("lines of 'a'");
  }

  public ListSubject<StringSubject, String> linesOfB() {
    isNotNull();
    ContentEntry contentEntry = actual();
    return ListSubject.assertThat(contentEntry.b, Truth::assertThat).named("lines of 'b'");
  }
}
