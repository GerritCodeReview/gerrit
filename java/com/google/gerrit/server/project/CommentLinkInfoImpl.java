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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.projects.CommentLinkInfo;

/** Info about a single commentlink section in a config. */
public class CommentLinkInfoImpl extends CommentLinkInfo {
  public static class Enabled extends CommentLinkInfoImpl {
    public Enabled(String name) {
      super(name, true);
    }

    @Override
    boolean isOverrideOnly() {
      return true;
    }
  }

  public static class Disabled extends CommentLinkInfoImpl {
    public Disabled(String name) {
      super(name, false);
    }

    @Override
    boolean isOverrideOnly() {
      return true;
    }
  }

  public CommentLinkInfoImpl(String name, String match, String link, String html, Boolean enabled) {
    checkArgument(name != null, "invalid commentlink.name");
    checkArgument(!Strings.isNullOrEmpty(match), "invalid commentlink.%s.match", name);
    link = Strings.emptyToNull(link);
    html = Strings.emptyToNull(html);
    checkArgument(
        (link != null && html == null) || (link == null && html != null),
        "commentlink.%s must have either link or html",
        name);
    this.name = name;
    this.match = match;
    this.link = link;
    this.html = html;
    this.enabled = enabled;
  }

  private CommentLinkInfoImpl(CommentLinkInfo src, boolean enabled) {
    this.name = src.name;
    this.match = src.match;
    this.link = src.link;
    this.html = src.html;
    this.enabled = enabled;
  }

  private CommentLinkInfoImpl(String name, boolean enabled) {
    this.name = name;
    this.match = null;
    this.link = null;
    this.html = null;
    this.enabled = enabled;
  }

  boolean isOverrideOnly() {
    return false;
  }

  CommentLinkInfo inherit(CommentLinkInfo src) {
    return new CommentLinkInfoImpl(src, enabled);
  }
}
