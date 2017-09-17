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

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.truth.ListSubject;

public class FixSuggestionInfoSubject extends Subject<FixSuggestionInfoSubject, FixSuggestionInfo> {

  public static FixSuggestionInfoSubject assertThat(FixSuggestionInfo fixSuggestionInfo) {
    return assertAbout(FixSuggestionInfoSubject::new).that(fixSuggestionInfo);
  }

  private FixSuggestionInfoSubject(
      FailureMetadata failureMetadata, FixSuggestionInfo fixSuggestionInfo) {
    super(failureMetadata, fixSuggestionInfo);
  }

  public StringSubject fixId() {
    return Truth.assertThat(actual().fixId).named("fixId");
  }

  public ListSubject<FixReplacementInfoSubject, FixReplacementInfo> replacements() {
    return ListSubject.assertThat(actual().replacements, FixReplacementInfoSubject::assertThat)
        .named("replacements");
  }

  public FixReplacementInfoSubject onlyReplacement() {
    return replacements().onlyElement();
  }

  public StringSubject description() {
    return Truth.assertThat(actual().description).named("description");
  }
}
