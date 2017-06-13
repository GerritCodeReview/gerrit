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

package com.google.gerrit.server.mail.receive;

import com.google.gerrit.reviewdb.client.Comment;

/** A comment parsed from inbound email */
public class MailComment {
  enum CommentType {
    CHANGE_MESSAGE,
    FILE_COMMENT,
    INLINE_COMMENT
  }

  public CommentType type;
  public Comment inReplyTo;
  public String fileName;
  public String message;

  public MailComment() {}

  public MailComment(String message, String fileName, Comment inReplyTo, CommentType type) {
    this.message = message;
    this.fileName = fileName;
    this.inReplyTo = inReplyTo;
    this.type = type;
  }
}
