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
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;

/** Limits the size of comments to prevent large comments from causing issues. */
public class CommentLimitsValidator implements CommentValidator {
  private final int maxCommentLength;

  @Inject
  CommentLimitsValidator(@GerritServerConfig Config serverConfig) {
    maxCommentLength = serverConfig.getInt("change", null, "maxCommentLength", 16 << 10);
  }

  @Override
  public ImmutableList<CommentValidationFailure> validateComments(
      ImmutableList<CommentForValidation> comments) {
    return comments.stream()
        .filter(c -> c.getText().length() > maxCommentLength)
        .map(
            c ->
                c.failValidation(
                    String.format(
                        "Comment too large (%d > %d)", c.getText().length(), maxCommentLength)))
        .collect(ImmutableList.toImmutableList());
  }
}
