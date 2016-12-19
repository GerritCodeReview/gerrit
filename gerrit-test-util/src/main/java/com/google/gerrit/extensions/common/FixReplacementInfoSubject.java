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

package com.google.gerrit.extensions.common;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.gerrit.extensions.client.RangeSubject;

public class FixReplacementInfoSubject
    extends Subject<FixReplacementInfoSubject, FixReplacementInfo> {

  private static final SubjectFactory<FixReplacementInfoSubject,
      FixReplacementInfo> FIX_REPLACEMENT_INFO_SUBJECT_FACTORY =
      new SubjectFactory<FixReplacementInfoSubject, FixReplacementInfo>() {
        @Override
        public FixReplacementInfoSubject getSubject(
            FailureStrategy failureStrategy,
            FixReplacementInfo fixReplacementInfo) {
          return new FixReplacementInfoSubject(failureStrategy,
              fixReplacementInfo);
        }
      };

  public static FixReplacementInfoSubject assertThat(
      FixReplacementInfo fixReplacementInfo) {
    return assertAbout(FIX_REPLACEMENT_INFO_SUBJECT_FACTORY)
        .that(fixReplacementInfo);
  }

  private FixReplacementInfoSubject(FailureStrategy failureStrategy,
      FixReplacementInfo fixReplacementInfo) {
    super(failureStrategy, fixReplacementInfo);
  }

  public StringSubject path() {
    return Truth.assertThat(actual().path).named("path");
  }

  public RangeSubject range() {
    return RangeSubject.assertThat(actual().range).named("range");
  }

  public StringSubject replacement() {
    return Truth.assertThat(actual().replacement).named("replacement");
  }
}
