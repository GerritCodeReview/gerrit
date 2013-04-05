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

/** Info about a single commentlink section in a config. */
public class CommentLinkInfo {
  public final String match;
  public final String link;
  public final String html;

  public transient final String name;

  public CommentLinkInfo(String name, String match, String link, String html) {
    checkArgument(name != null, "invalid commentlink.name");
    checkArgument(!Strings.isNullOrEmpty(match),
        "invalid commentlink.%s.match", name);
    link = Strings.emptyToNull(link);
    html = Strings.emptyToNull(html);
    checkArgument(
        (link != null && html == null) || (link == null && html != null),
        "commentlink.%s must have either link or html", name);
    this.name = name;
    this.match = match;
    this.link = link;
    this.html = html;
  }
}
