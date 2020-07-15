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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.ContextLines;
import java.util.Collection;

/**
 * Caches the context lines of comments (source file content surrounding and including the lines
 * where the comment was written)
 */
public interface CommentContextCache {

  /**
   * Returns the context lines for a single comment.
   *
   * @param key a key representing a subset of fields for a comment that serves as an identifier.
   * @return a {@link ContextLines} object containing all line numbers and text of the context.
   */
  ContextLines get(CommentContextKey key);

  /**
   * Returns the context lines for multiple comments - identified by their {@code keys}.
   *
   * @param keys list of keys, where each key represents a single comment through its project,
   *     change ID, patchset, path and ID.
   * @return {@code Map} of {@code ContextLines} containing the context for all comments.
   */
  ImmutableMap<CommentContextKey, ContextLines> getAll(Collection<CommentContextKey> keys);
}
