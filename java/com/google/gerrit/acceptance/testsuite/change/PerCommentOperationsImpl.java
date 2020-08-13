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

package com.google.gerrit.acceptance.testsuite.change;

import static com.google.common.collect.MoreCollectors.onlyElement;

import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/**
 * The implementation of {@link PerCommentOperations}.
 *
 * <p>There is only one implementation of {@link PerCommentOperations}. Nevertheless, we keep the
 * separation between interface and implementation to enhance clarity.
 */
public class PerCommentOperationsImpl implements PerCommentOperations {
  private final CommentsUtil commentsUtil;

  private final ChangeNotes changeNotes;
  private final String commentUuid;

  public interface Factory {
    PerCommentOperationsImpl create(ChangeNotes changeNotes, String commentUuid);
  }

  @Inject
  public PerCommentOperationsImpl(
      CommentsUtil commentsUtil, @Assisted ChangeNotes changeNotes, @Assisted String commentUuid) {
    this.commentsUtil = commentsUtil;
    this.changeNotes = changeNotes;
    this.commentUuid = commentUuid;
  }

  @Override
  public TestHumanComment get() {
    HumanComment comment =
        commentsUtil.publishedHumanCommentsByChange(changeNotes).stream()
            .filter(foundComment -> foundComment.key.uuid.equals(commentUuid))
            .collect(onlyElement());
    return toTestComment(comment);
  }

  static TestHumanComment toTestComment(HumanComment comment) {
    return TestHumanComment.builder().uuid(comment.key.uuid).parentUuid(comment.parentUuid).build();
  }
}
