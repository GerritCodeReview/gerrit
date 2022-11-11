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

package com.google.gerrit.entities;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.projects.CommentLinkInfo;

/** Info about a single commentlink section in a config. */
@AutoValue
public abstract class StoredCommentLinkInfo {
  public abstract String getName();

  /** A regular expression to match for the commentlink to apply. */
  @Nullable
  public abstract String getMatch();

  /**
   * The link to replace the match with.
   *
   * <p>The constructed link is using {@link #getLink()} {@link #getPrefix()} {@link #getSuffix()}
   * and {@link #getText()}, and has the shape of
   *
   * <p>{@code PREFIX<a href="LINK">TEXT</a>SUFFIX}
   */
  @Nullable
  public abstract String getLink();

  /**
   * The optional text before the link tag that the match is replaced with.
   */
  @Nullable
  public abstract String getPrefix();

  /**
   * The optional text after the link tag that the match is replaced with.
   */
  @Nullable
  public abstract String getSuffix();

  /**
   * The content of the link tag that the match is replaced with. If not set full match is used.
   */
  @Nullable
  public abstract String getText();

  /** Weather this comment link is active. {@code null} means true. */
  @Nullable
  public abstract Boolean getEnabled();

  /** If set, {@link StoredCommentLinkInfo} has to be overridden to take any effect. */
  public abstract boolean getOverrideOnly();

  /**
   * Creates an enabled {@link StoredCommentLinkInfo} that can be overridden but doesn't do anything
   * on its own.
   */
  public static StoredCommentLinkInfo enabled(String name) {
    return builder(name).setOverrideOnly(true).build();
  }

  /**
   * Creates a disabled {@link StoredCommentLinkInfo} that can be overridden but doesn't do anything
   * on it's own.
   */
  public static StoredCommentLinkInfo disabled(String name) {
    return builder(name).setOverrideOnly(true).setEnabled(false).build();
  }

  /** Creates and returns a new {@link StoredCommentLinkInfo.Builder} instance. */
  public static Builder builder(String name) {
    checkArgument(name != null, "invalid commentlink.name");
    return new AutoValue_StoredCommentLinkInfo.Builder().setName(name).setOverrideOnly(false);
  }

  /** Creates and returns a new {@link StoredCommentLinkInfo} instance with the same values. */
  public static StoredCommentLinkInfo fromInfo(CommentLinkInfo src, Boolean enabled) {
    return builder(src.name)
        .setMatch(src.match)
        .setLink(src.link)
        .setPrefix(src.prefix)
        .setSuffix(src.suffix)
        .setText(src.text)
        .setEnabled(enabled)
        .setOverrideOnly(false)
        .build();
  }

  /** Returns an {@link CommentLinkInfo} instance with the same values. */
  public CommentLinkInfo toInfo() {
    CommentLinkInfo info = new CommentLinkInfo();
    info.name = getName();
    info.match = getMatch();
    info.link = getLink();
    info.prefix = getPrefix();
    info.suffix = getSuffix();
    info.text = getText();
    info.enabled = getEnabled();
    return info;
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String value);

    public abstract Builder setMatch(@Nullable String value);

    public abstract Builder setLink(@Nullable String value);

    public abstract Builder setPrefix(@Nullable String value);

    public abstract Builder setSuffix(@Nullable String value);

    public abstract Builder setText(@Nullable String value);

    public abstract Builder setEnabled(@Nullable Boolean value);

    public abstract Builder setOverrideOnly(boolean value);

    public StoredCommentLinkInfo build() {
      checkArgument(getName() != null, "invalid commentlink.name");
      setPrefix(Strings.emptyToNull(getPrefix()));
      setSuffix(Strings.emptyToNull(getSuffix()));
      setText(Strings.emptyToNull(getText()));
      if (!getOverrideOnly()) {
        checkArgument(
            !Strings.isNullOrEmpty(getMatch()), "invalid commentlink.%s.match", getName());
        checkArgument(
          !Strings.isNullOrEmpty(getLink()), "commentlink.%s must have link specified", getName());
      }
      return autoBuild();
    }

    protected abstract StoredCommentLinkInfo autoBuild();

    protected abstract String getName();

    protected abstract String getMatch();

    protected abstract String getLink();

    protected abstract String getPrefix();

    protected abstract String getSuffix();

    protected abstract String getText();

    protected abstract boolean getOverrideOnly();
  }
}
