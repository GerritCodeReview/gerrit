// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.projects.CommentLinkInfo;

/** Info about a single commentlink section in a config. */
@AutoValue
public abstract class StoredCommentLinkInfo {
  public abstract String name();

  /** A regular expression to match for the commentlink to apply. */
  @Nullable
  public abstract String match();

  /** The link to replace the match with. This can only be set if html is {@code null}. */
  @Nullable
  public abstract String link();

  /** The html to replace the match with. This can only be set if link is {@code null}. */
  @Nullable
  public abstract String html();

  /** Weather this comment link is active. */
  @Nullable
  public abstract Boolean enabled(); // null means true

  /** If set, {@link StoredCommentLinkInfo} has to be overriden to take any effect. */
  public abstract boolean overrideOnly();

  /**
   * Creates an enabled {@link StoredCommentLinkInfo} that can be overriden but doesn't do anything
   * on it's own.
   */
  public static StoredCommentLinkInfo enabled(String name) {
    return create(name, null, null, null, null, true);
  }

  /**
   * Creates a disabled {@link StoredCommentLinkInfo} that can be overriden but doesn't do anything
   * on it's own.
   */
  public static StoredCommentLinkInfo disabled(String name) {
    return create(name, null, null, null, null, true);
  }

  /** Creates and returns a new {@link StoredCommentLinkInfo} instance. */
  public static StoredCommentLinkInfo create(
      String name, String match, String link, String html, Boolean enabled, boolean overrideOnly) {
    checkArgument(name != null, "invalid commentlink.name");
    link = Strings.emptyToNull(link);
    html = Strings.emptyToNull(html);
    if (!overrideOnly) {
      checkArgument(!Strings.isNullOrEmpty(match), "invalid commentlink.%s.match", name);
      checkArgument(
          (link != null && html == null) || (link == null && html != null),
          "commentlink.%s must have either link or html",
          name);
    }
    return new AutoValue_StoredCommentLinkInfo(name, match, link, html, enabled, overrideOnly);
  }

  /** Creates and returns a new {@link StoredCommentLinkInfo} instance with the same values. */
  static StoredCommentLinkInfo fromInfo(CommentLinkInfo src, boolean enabled) {
    return create(src.name, src.match, src.link, src.html, enabled, false);
  }

  /** Returns an {@link CommentLinkInfo} instance with the same values. */
  CommentLinkInfo toInfo() {
    CommentLinkInfo info = new CommentLinkInfo();
    info.name = name();
    info.match = match();
    info.link = link();
    info.html = html();
    info.enabled = enabled();
    return info;
  }
}
