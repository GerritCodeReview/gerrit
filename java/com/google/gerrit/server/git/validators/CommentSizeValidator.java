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
// limitations under the License.

package com.google.gerrit.server.git.validators;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;

/** Limits the size of comments to prevent space/time complexity issues. */
public class CommentSizeValidator implements CommentValidator {
  private final int commentSizeLimit;
  private final int robotCommentSizeLimit;

  @Inject
  CommentSizeValidator(@GerritServerConfig Config serverConfig) {
    commentSizeLimit = serverConfig.getInt("change", "commentSizeLimit", 16 << 10);
    robotCommentSizeLimit = serverConfig.getInt("change", "robotCommentSizeLimit", 1 << 20);
  }

  @Override
  public ImmutableList<CommentValidationFailure> validateComments(
      CommentValidationContext ctx, ImmutableList<CommentForValidation> comments) {
    return comments.stream()
        .filter(this::exceedsSizeLimit)
        .map(c -> c.failValidation(buildErrorMessage(c)))
        .collect(ImmutableList.toImmutableList());
  }

  private boolean exceedsSizeLimit(CommentForValidation comment) {
    switch (comment.getSource()) {
      case HUMAN:
        return comment.getApproximateSize() > commentSizeLimit;
      case ROBOT:
        return robotCommentSizeLimit > 0 && comment.getApproximateSize() > robotCommentSizeLimit;
    }
    throw new RuntimeException(
        "Unknown comment source (should not have compiled): " + comment.getSource());
  }

  private String buildErrorMessage(CommentForValidation comment) {
    switch (comment.getSource()) {
      case HUMAN:
        return String.format(
            "Comment size exceeds limit (%d > %d)", comment.getApproximateSize(), commentSizeLimit);

      case ROBOT:
        return String.format(
            "Size %d (bytes) of robot comment is greater than limit %d (bytes)",
            comment.getApproximateSize(), robotCommentSizeLimit);
    }
    throw new RuntimeException(
        "Unknown comment source (should not have compiled): " + comment.getSource());
  }
}
