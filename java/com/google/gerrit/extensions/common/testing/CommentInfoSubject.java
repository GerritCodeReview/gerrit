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
import static com.google.gerrit.extensions.common.testing.AccountInfoSubject.accounts;
import static com.google.gerrit.extensions.common.testing.RangeSubject.ranges;
import static com.google.gerrit.truth.ListSubject.elements;

import com.google.common.truth.BooleanSubject;
import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.truth.ListSubject;
import java.sql.Timestamp;
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

  public ListSubject<FixSuggestionInfoSubject, FixSuggestionInfo> fixSuggestions() {
    return check("fixSuggestions")
        .about(elements())
        .thatCustom(commentInfo.fixSuggestions, FixSuggestionInfoSubject.fixSuggestions());
  }

  public StringSubject uuid() {
    return check("id").that(commentInfo().id);
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

  public StringSubject commitId() {
    return check("commitId").that(commentInfo().commitId);
  }

  public AccountInfoSubject author() {
    return check("author").about(accounts()).that(commentInfo().author);
  }

  public StringSubject tag() {
    return check("tag").that(commentInfo().tag);
  }

  public ComparableSubject<Timestamp> updated() {
    return check("updated").that(commentInfo().updated);
  }

  public StringSubject changeMessageId() {
    return check("changeMessageId").that(commentInfo().changeMessageId);
  }

  private CommentInfo commentInfo() {
    isNotNull();
    return commentInfo;
  }

  public FixSuggestionInfoSubject onlyFixSuggestion() {
    return fixSuggestions().onlyElement();
  }
}
