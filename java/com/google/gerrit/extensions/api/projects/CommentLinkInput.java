// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.extensions.api.projects;

/**
 * Input for a commentlink configuration on a project.
 *
 * <p>See {@link com.google.gerrit.entities.StoredCommentLinkInfo} for additional details.
 */
public class CommentLinkInput {
  /** A JavaScript regular expression to match positions to be replaced with a hyperlink. */
  public String match;

  /** The URL to direct the user to whenever the regular expression is matched. */
  public String link;

  /** Text inserted before the link if the regular expression is matched. */
  public String prefix;

  /** Text inserted after the link if the regular expression is matched. */
  public String suffix;

  /** Text of the link. */
  public String text;

  /** Whether the commentlink is enabled. */
  public Boolean enabled;
}
