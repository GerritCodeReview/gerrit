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

import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.gerrit.truth.ListSubject.elements;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.StandardSubjectBuilder;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.DiffInfo.ContentEntry;
import com.google.gerrit.truth.ListSubject;

public class ContentEntrySubject extends Subject {

  public static ContentEntrySubject assertThat(ContentEntry contentEntry) {
    return assertAbout(contentEntries()).that(contentEntry);
  }

  public static Subject.Factory<ContentEntrySubject, ContentEntry> contentEntries() {
    return ContentEntrySubject::new;
  }

  private final ContentEntry contentEntry;

  private ContentEntrySubject(FailureMetadata failureMetadata, ContentEntry contentEntry) {
    super(failureMetadata, contentEntry);
    this.contentEntry = contentEntry;
  }

  public void isDueToRebase() {
    isNotNull();
    if (contentEntry.dueToRebase == null || !contentEntry.dueToRebase) {
      failWithActual(simpleFact("expected entry to be marked 'dueToRebase'"));
    }
  }

  public void isNotDueToRebase() {
    isNotNull();
    if (contentEntry.dueToRebase != null && contentEntry.dueToRebase) {
      failWithActual(simpleFact("expected entry not to be marked 'dueToRebase'"));
    }
  }

  public ListSubject<StringSubject, String> commonLines() {
    isNotNull();
    return check("commonLines()")
        .about(elements())
        .that(contentEntry.ab, StandardSubjectBuilder::that);
  }

  public ListSubject<StringSubject, String> linesOfA() {
    isNotNull();
    return check("linesOfA()").about(elements()).that(contentEntry.a, StandardSubjectBuilder::that);
  }

  public ListSubject<StringSubject, String> linesOfB() {
    isNotNull();
    return check("linesOfB()").about(elements()).that(contentEntry.b, StandardSubjectBuilder::that);
  }

  public IterableSubject intralineEditsOfA() {
    isNotNull();
    return check("intralineEditsOfA()").that(contentEntry.editA);
  }

  public IterableSubject intralineEditsOfB() {
    isNotNull();
    return check("intralineEditsOfB()").that(contentEntry.editB);
  }

  public IntegerSubject numberOfSkippedLines() {
    isNotNull();
    return check("numberOfSkippedLines()").that(contentEntry.skip);
  }
}
