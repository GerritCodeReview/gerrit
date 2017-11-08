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
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.truth.ListSubject;
import java.util.List;

public class RobotCommentInfoSubject extends Subject<RobotCommentInfoSubject, RobotCommentInfo> {

  public static ListSubject<RobotCommentInfoSubject, RobotCommentInfo> assertThatList(
      List<RobotCommentInfo> robotCommentInfos) {
    return ListSubject.assertThat(robotCommentInfos, RobotCommentInfoSubject::assertThat)
        .named("robotCommentInfos");
  }

  public static RobotCommentInfoSubject assertThat(RobotCommentInfo robotCommentInfo) {
    return assertAbout(RobotCommentInfoSubject::new).that(robotCommentInfo);
  }

  private RobotCommentInfoSubject(
      FailureMetadata failureMetadata, RobotCommentInfo robotCommentInfo) {
    super(failureMetadata, robotCommentInfo);
  }

  public ListSubject<FixSuggestionInfoSubject, FixSuggestionInfo> fixSuggestions() {
    return ListSubject.assertThat(actual().fixSuggestions, FixSuggestionInfoSubject::assertThat)
        .named("fixSuggestions");
  }

  public FixSuggestionInfoSubject onlyFixSuggestion() {
    return fixSuggestions().onlyElement();
  }
}
