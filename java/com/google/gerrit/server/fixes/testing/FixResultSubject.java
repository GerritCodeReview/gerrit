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

package com.google.gerrit.server.fixes.testing;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.gerrit.server.fixes.testing.GitEditSubject.gitEdits;
import static com.google.gerrit.truth.ListSubject.elements;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.server.fixes.FixCalculator.FixResult;
import com.google.gerrit.truth.ListSubject;
import org.eclipse.jgit.diff.Edit;

public class FixResultSubject extends Subject {
  public static FixResultSubject assertThat(FixResult fixResult) {
    return assertAbout(FixResultSubject::new).that(fixResult);
  }

  private final FixResult fixResult;

  private FixResultSubject(FailureMetadata failureMetadata, FixResult fixResult) {
    super(failureMetadata, fixResult);
    this.fixResult = fixResult;
  }

  public StringSubject text() {
    isNotNull();
    return check("text").that(fixResult.text.getString(0, fixResult.text.size(), false));
  }

  public ListSubject<GitEditSubject, Edit> edits() {
    isNotNull();
    return check("edits").about(elements()).thatCustom(fixResult.edits, gitEdits());
  }
}
