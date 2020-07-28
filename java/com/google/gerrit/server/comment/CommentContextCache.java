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
// limitations under the License

package com.google.gerrit.server.comment;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ContextLine;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.CommentInfo;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Caches the context lines of comments (source file content surrounding and including the lines
 * where the comment was written)
 */
public interface CommentContextCache<T extends CommentInfo> {

  /**
   * Returns the context line for a single comment.
   *
   * @param project The project that includes the change where the comment is written.
   * @param changeId ID of the change where the comment was written.
   * @param comment The commentInfo object where the context needs to be evaluated.
   * @return {@code List} of {@code ContextLine} containing the line text and line number of the
   *     context.
   */
  List<ContextLine> get(Project.NameKey project, Change.Id changeId, CommentInfo comment);

  /**
   * Returns the context line for multiple comments at once.
   *
   * @param project The project that includes the change where the comment is written.
   * @param changeId ID of the change where the comment was written.
   * @param comments {@code Collection} of comments for which the context should be retrieved
   * @return {@code Map} of {@code CommentInfo} to a {@code List} of {@code ContextLine}
   */
  Map<CommentInfo, List<ContextLine>> getAll(
      Project.NameKey project, Change.Id changeId, Collection<T> comments);
}
