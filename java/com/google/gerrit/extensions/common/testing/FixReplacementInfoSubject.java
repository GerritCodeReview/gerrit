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
import static com.google.gerrit.extensions.common.testing.RangeSubject.ranges;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.FixReplacementInfo;

public class FixReplacementInfoSubject extends Subject {

  public static FixReplacementInfoSubject assertThat(FixReplacementInfo fixReplacementInfo) {
    return assertAbout(fixReplacements()).that(fixReplacementInfo);
  }

  public static Subject.Factory<FixReplacementInfoSubject, FixReplacementInfo> fixReplacements() {
    return FixReplacementInfoSubject::new;
  }

  private final FixReplacementInfo fixReplacementInfo;

  private FixReplacementInfoSubject(
      FailureMetadata failureMetadata, FixReplacementInfo fixReplacementInfo) {
    super(failureMetadata, fixReplacementInfo);
    this.fixReplacementInfo = fixReplacementInfo;
  }

  public StringSubject path() {
    isNotNull();
    return check("path").that(fixReplacementInfo.path);
  }

  public RangeSubject range() {
    isNotNull();
    return check("range").about(ranges()).that(fixReplacementInfo.range);
  }

  public StringSubject replacement() {
    isNotNull();
    return check("replacement").that(fixReplacementInfo.replacement);
  }
}
