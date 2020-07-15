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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ContextLine;
import com.google.gerrit.entities.Project;
import java.util.Collection;

/**
 * Caches the context lines of comments (source file content surrounding and including the lines
 * where the comment was written)
 */
public interface CommentContextCache {

  /**
   * Returns the context lines for a single comment.
   *
   * @param project The project that includes the change where the comment is written.
   * @param changeId ID of the change where the comment was written.
   * @param key a key representing a single comment through its patchset, path and ID.
   * @return {@code List} of {@code ContextLine} containing the line text and line number of the
   *     context.
   */
  ImmutableList<ContextLine> get(
      Project.NameKey project, Change.Id changeId, CommentContextKey key);

  /**
   * Returns the context line for multiple comments at once.
   *
   * @param project The project that includes the change where the comment is written.
   * @param changeId ID of the change where the comment was written.
   * @param keys list of keys, where each key represents a single comment through its patchset, path
   *     and ID
   * @return {@code Map} of {@code CommentInfo} to a {@code List} of {@code ContextLine}
   */
  ImmutableMap<CommentContextKey, ImmutableList<ContextLine>> getAll(
      Project.NameKey project, Change.Id changeId, Collection<CommentContextKey> keys);
}
