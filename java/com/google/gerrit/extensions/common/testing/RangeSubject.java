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

package com.google.gerrit.extensions.common.testing;

import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.gerrit.extensions.client.Comment;

public class RangeSubject extends Subject<RangeSubject, Comment.Range> {

  public static RangeSubject assertThat(Comment.Range range) {
    return assertAbout(RangeSubject::new).that(range);
  }

  private RangeSubject(FailureMetadata failureMetadata, Comment.Range range) {
    super(failureMetadata, range);
  }

  public IntegerSubject startLine() {
    return Truth.assertThat(actual().startLine).named("startLine");
  }

  public IntegerSubject startCharacter() {
    return Truth.assertThat(actual().startCharacter).named("startCharacter");
  }

  public IntegerSubject endLine() {
    return Truth.assertThat(actual().endLine).named("endLine");
  }

  public IntegerSubject endCharacter() {
    return Truth.assertThat(actual().endCharacter).named("endCharacter");
  }

  public void isValid() {
    isNotNull();
    if (!actual().isValid()) {
      failWithoutActual(fact("expected", "valid"));
    }
  }

  public void isInvalid() {
    isNotNull();
    if (actual().isValid()) {
      failWithoutActual(fact("expected", "not valid"));
    }
  }
}
