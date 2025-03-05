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
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;
import com.google.common.base.Strings;
import com.google.errorprone.annotations.InlineMe;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.projects.CommentLinkInfo;

/**
 * Info about a single commentlink section in a config.
 *
 * @param match A regular expression to match for the commentlink to apply.
 * @param link The link to replace the match with.
 *     <p>The constructed link is using {@link #getLink()} {@link #getPrefix()} {@link #getSuffix()}
 *     and {@link #getText()}, and has the shape of
 *     <p>{@code PREFIX<a href="LINK">TEXT</a>SUFFIX}
 * @param prefix The optional text before the link tag that the match is replaced with.
 * @param suffix The optional text after the link tag that the match is replaced with.
 * @param text The content of the link tag that the match is replaced with. If not set full match is
 *     used.
 * @param enabled Weather this comment link is active. {@code null} means true.
 * @param overrideOnly If set, {@link StoredCommentLinkInfo} has to be overridden to take any
 *     effect.
 */
public record StoredCommentLinkInfo(
    String name,
    @Nullable String match,
    @Nullable String link,
    @Nullable String prefix,
    @Nullable String suffix,
    @Nullable String text,
    @Nullable Boolean enabled,
    boolean overrideOnly) {
  public StoredCommentLinkInfo {
    requireNonNull(name, "name");
  }

  @InlineMe(replacement = "this.name()")
  public String getName() {
    return name();
  }

  @InlineMe(replacement = "this.match()")
  public @Nullable String getMatch() {
    return match();
  }

  @InlineMe(replacement = "this.link()")
  public @Nullable String getLink() {
    return link();
  }

  @InlineMe(replacement = "this.prefix()")
  public @Nullable String getPrefix() {
    return prefix();
  }

  @InlineMe(replacement = "this.suffix()")
  public @Nullable String getSuffix() {
    return suffix();
  }

  @InlineMe(replacement = "this.text()")
  public @Nullable String getText() {
    return text();
  }

  @InlineMe(replacement = "this.enabled()")
  public @Nullable Boolean getEnabled() {
    return enabled();
  }

  @InlineMe(replacement = "this.overrideOnly()")
  public boolean getOverrideOnly() {
    return overrideOnly();
  }

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
    return new AutoBuilder_StoredCommentLinkInfo_Builder().setName(name).setOverrideOnly(false);
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

  @AutoBuilder
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
            !Strings.isNullOrEmpty(getLink()),
            "commentlink.%s must have link specified",
            getName());
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
