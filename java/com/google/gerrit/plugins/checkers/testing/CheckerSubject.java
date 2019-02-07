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

package com.google.gerrit.plugins.checkers.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.gerrit.plugins.checkers.Checker;
import com.google.gerrit.server.testing.ObjectIdSubject;
import com.google.gerrit.truth.OptionalSubject;
import java.sql.Timestamp;

public class CheckerSubject extends Subject<CheckerSubject, Checker> {
  public static CheckerSubject assertThat(Checker checker) {
    return assertAbout(CheckerSubject::new).that(checker);
  }

  private CheckerSubject(FailureMetadata metadata, Checker actual) {
    super(metadata, actual);
  }

  public void hasUuid(String expectedUuid) {
    isNotNull();
    Checker checker = actual();
    Truth.assertThat(checker.getUuid()).named("uuid").isEqualTo(expectedUuid);
  }

  public void hasName(String expectedName) {
    isNotNull();
    Checker checker = actual();
    Truth.assertThat(checker.getName()).named("name").isEqualTo(expectedName);
  }

  public OptionalSubject<StringSubject, String> hasDescriptionThat() {
    isNotNull();
    Checker checker = actual();
    return OptionalSubject.assertThat(checker.getDescription(), Truth::assertThat)
        .named("description");
  }

  public OptionalSubject<StringSubject, String> hasUrlThat() {
    isNotNull();
    Checker checker = actual();
    return OptionalSubject.assertThat(checker.getUrl(), Truth::assertThat).named("url");
  }

  public ComparableSubject<?, Timestamp> hasCreatedOnThat() {
    isNotNull();
    Checker checker = actual();
    return Truth.assertThat(checker.getCreatedOn()).named("createdOn");
  }

  public ObjectIdSubject hasRefStateThat() {
    isNotNull();
    Checker checker = actual();
    return ObjectIdSubject.assertThat(checker.getRefState()).named("refState");
  }
}
