// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.server.mail.send.ChangeEmail.ChangeEmailDecorator;
import com.google.gerrit.server.util.LabelVote;
import java.util.List;

/** Send comments, after the author of them hit used Publish Comments in the UI. */
public interface CommentChangeEmailDecorator extends ChangeEmailDecorator {
  /** List of comments added as part of review iteration. */
  void setComments(List<? extends Comment> comments);

  /** Patchset-level comment attached to review iteration. */
  void setPatchSetComment(@Nullable String comment);

  /** List of votes set in the review iteration. */
  void setLabels(List<LabelVote> labels);
}
