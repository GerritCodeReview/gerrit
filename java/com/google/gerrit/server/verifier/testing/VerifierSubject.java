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

package com.google.gerrit.server.verifier.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.gerrit.server.testing.ObjectIdSubject;
import com.google.gerrit.server.verifier.Verifier;
import com.google.gerrit.truth.OptionalSubject;
import java.sql.Timestamp;

public class VerifierSubject extends Subject<VerifierSubject, Verifier> {
  public static VerifierSubject assertThat(Verifier verifier) {
    return assertAbout(VerifierSubject::new).that(verifier);
  }

  private VerifierSubject(FailureMetadata metadata, Verifier actual) {
    super(metadata, actual);
  }

  public void hasUuid(String expectedUuid) {
    isNotNull();
    Verifier verifier = actual();
    Truth.assertThat(verifier.getUuid()).named("uuid").isEqualTo(expectedUuid);
  }

  public void hasName(String expectedName) {
    isNotNull();
    Verifier verifier = actual();
    Truth.assertThat(verifier.getName()).named("name").isEqualTo(expectedName);
  }

  public OptionalSubject<StringSubject, String> hasDescriptionThat() {
    isNotNull();
    Verifier verifier = actual();
    return OptionalSubject.assertThat(verifier.getDescription(), Truth::assertThat)
        .named("description");
  }

  public ComparableSubject<?, Timestamp> hasCreatedOnThat() {
    isNotNull();
    Verifier verifier = actual();
    return Truth.assertThat(verifier.getCreatedOn()).named("createdOn");
  }

  public ObjectIdSubject hasRefStateThat() {
    isNotNull();
    Verifier verifier = actual();
    return ObjectIdSubject.assertThat(verifier.getRefState()).named("refState");
  }
}
