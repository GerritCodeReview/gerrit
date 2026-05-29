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
import static com.google.gerrit.extensions.common.testing.FixReplacementInfoSubject.fixReplacements;
import static com.google.gerrit.truth.ListSubject.elements;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.truth.ListSubject;

public class FixSuggestionInfoSubject extends Subject {

  public static FixSuggestionInfoSubject assertThat(FixSuggestionInfo fixSuggestionInfo) {
    return assertAbout(fixSuggestions()).that(fixSuggestionInfo);
  }

  public static Subject.Factory<FixSuggestionInfoSubject, FixSuggestionInfo> fixSuggestions() {
    return FixSuggestionInfoSubject::new;
  }

  private final FixSuggestionInfo fixSuggestionInfo;

  private FixSuggestionInfoSubject(
      FailureMetadata failureMetadata, FixSuggestionInfo fixSuggestionInfo) {
    super(failureMetadata, fixSuggestionInfo);
    this.fixSuggestionInfo = fixSuggestionInfo;
  }

  public StringSubject fixId() {
    return check("fixId").that(fixSuggestionInfo.fixId);
  }

  public ListSubject<FixReplacementInfoSubject, FixReplacementInfo> replacements() {
    isNotNull();
    return check("replacements")
        .about(elements())
        .thatCustom(fixSuggestionInfo.replacements, fixReplacements());
  }

  public FixReplacementInfoSubject onlyReplacement() {
    return replacements().onlyElement();
  }

  public StringSubject description() {
    isNotNull();
    return check("description").that(fixSuggestionInfo.description);
  }
}
