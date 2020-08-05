// Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.truth.BooleanSubject;
import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.truth.ListSubject;
import java.util.List;

public class CommentInfoSubject extends Subject {

  public static ListSubject<CommentInfoSubject, CommentInfo> assertThatList(
      List<CommentInfo> commentInfos) {
    return ListSubject.assertThat(commentInfos, comments());
  }

  public static CommentInfoSubject assertThat(CommentInfo commentInfo) {
    return assertAbout(comments()).that(commentInfo);
  }

  private static Factory<CommentInfoSubject, CommentInfo> comments() {
    return CommentInfoSubject::new;
  }

  private final CommentInfo commentInfo;

  private CommentInfoSubject(FailureMetadata failureMetadata, CommentInfo commentInfo) {
    super(failureMetadata, commentInfo);
    this.commentInfo = commentInfo;
  }

  public IntegerSubject patchSet() {
    return check("patchSet").that(commentInfo().patchSet);
  }

  public StringSubject path() {
    return check("path").that(commentInfo().path);
  }

  public IntegerSubject line() {
    return check("line").that(commentInfo().line);
  }

  public RangeSubject range() {
    return check("range").about(ranges()).that(commentInfo().range);
  }

  public StringSubject message() {
    return check("message").that(commentInfo().message);
  }

  public ComparableSubject<Side> side() {
    return check("side").that(commentInfo().side);
  }

  public IntegerSubject parent() {
    return check("parent").that(commentInfo().parent);
  }

  public BooleanSubject unresolved() {
    return check("unresolved").that(commentInfo().unresolved);
  }

  public StringSubject inReplyTo() {
    return check("inReplyTo").that(commentInfo().inReplyTo);
  }

  private CommentInfo commentInfo() {
    isNotNull();
    return commentInfo;
  }
}
