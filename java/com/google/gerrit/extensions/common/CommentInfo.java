// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.extensions.client.Comment;
import java.util.List;
import java.util.Objects;

public class CommentInfo extends Comment {
  public AccountInfo author;
  public String tag;
  public String changeMessageId;
  public Boolean unresolved;

  /**
   * A list of {@link ContextLineInfo}, that is, a list of pairs of {line_num, line_text} of the
   * actual source file content surrounding and including the lines where the comment was written.
   */
  public List<ContextLineInfo> contextLines;

  /** Mime type of the underlying source file. Only available if context lines are requested. */
  public String sourceContentType;

  @Override
  public boolean equals(Object o) {
    if (super.equals(o)) {
      CommentInfo ci = (CommentInfo) o;
      return Objects.equals(author, ci.author)
          && Objects.equals(tag, ci.tag)
          && Objects.equals(unresolved, ci.unresolved)
          && Objects.equals(fixSuggestions, ci.fixSuggestions);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), author, tag, unresolved, fixSuggestions);
  }
}
