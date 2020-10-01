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
import static com.google.gerrit.truth.ListSubject.elements;

import com.google.common.truth.BooleanSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.MapSubject;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.truth.ListSubject;
import java.util.List;

public class RobotCommentInfoSubject extends Subject {

  public static ListSubject<RobotCommentInfoSubject, RobotCommentInfo> assertThatList(
      List<RobotCommentInfo> robotCommentInfos) {
    return ListSubject.assertThat(robotCommentInfos, robotComments());
  }

  public static RobotCommentInfoSubject assertThat(RobotCommentInfo robotCommentInfo) {
    return assertAbout(robotComments()).that(robotCommentInfo);
  }

  private static Factory<RobotCommentInfoSubject, RobotCommentInfo> robotComments() {
    return RobotCommentInfoSubject::new;
  }

  private final RobotCommentInfo robotCommentInfo;

  private RobotCommentInfoSubject(
      FailureMetadata failureMetadata, RobotCommentInfo robotCommentInfo) {
    super(failureMetadata, robotCommentInfo);
    this.robotCommentInfo = robotCommentInfo;
  }

  public ListSubject<FixSuggestionInfoSubject, FixSuggestionInfo> fixSuggestions() {
    return check("fixSuggestions")
        .about(elements())
        .thatCustom(robotCommentInfo.fixSuggestions, FixSuggestionInfoSubject.fixSuggestions());
  }

  public StringSubject path() {
    isNotNull();
    return check("path").that(robotCommentInfo.path);
  }

  public StringSubject robotId() {
    isNotNull();
    return check("robotId").that(robotCommentInfo.robotId);
  }

  public StringSubject robotRunId() {
    isNotNull();
    return check("robotRunId").that(robotCommentInfo.robotRunId);
  }

  public StringSubject url() {
    isNotNull();
    return check("url").that(robotCommentInfo.url);
  }

  public MapSubject properties() {
    isNotNull();
    return check("property").that(robotCommentInfo.properties);
  }

  public BooleanSubject unresolved() {
    isNotNull();
    return check("unresolved").that(robotCommentInfo.unresolved);
  }

  public FixSuggestionInfoSubject onlyFixSuggestion() {
    return fixSuggestions().onlyElement();
  }
}
