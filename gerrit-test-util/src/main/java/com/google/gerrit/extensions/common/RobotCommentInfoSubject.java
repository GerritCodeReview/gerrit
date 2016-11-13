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
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.gerrit.truth.ListSubject;
import java.util.List;

public class RobotCommentInfoSubject extends Subject<RobotCommentInfoSubject, RobotCommentInfo> {

  private static final SubjectFactory<RobotCommentInfoSubject, RobotCommentInfo>
      ROBOT_COMMENT_INFO_SUBJECT_FACTORY =
          new SubjectFactory<RobotCommentInfoSubject, RobotCommentInfo>() {
            @Override
            public RobotCommentInfoSubject getSubject(
                FailureStrategy failureStrategy, RobotCommentInfo robotCommentInfo) {
              return new RobotCommentInfoSubject(failureStrategy, robotCommentInfo);
            }
          };

  public static ListSubject<RobotCommentInfoSubject, RobotCommentInfo> assertThatList(
      List<RobotCommentInfo> robotCommentInfos) {
    return ListSubject.assertThat(robotCommentInfos, RobotCommentInfoSubject::assertThat)
        .named("robotCommentInfos");
  }

  public static RobotCommentInfoSubject assertThat(RobotCommentInfo robotCommentInfo) {
    return assertAbout(ROBOT_COMMENT_INFO_SUBJECT_FACTORY).that(robotCommentInfo);
  }

  private RobotCommentInfoSubject(
      FailureStrategy failureStrategy, RobotCommentInfo robotCommentInfo) {
    super(failureStrategy, robotCommentInfo);
  }

  public ListSubject<FixSuggestionInfoSubject, FixSuggestionInfo> fixSuggestions() {
    return ListSubject.assertThat(actual().fixSuggestions, FixSuggestionInfoSubject::assertThat)
        .named("fixSuggestions");
  }

  public FixSuggestionInfoSubject onlyFixSuggestion() {
    return fixSuggestions().onlyElement();
  }
}
