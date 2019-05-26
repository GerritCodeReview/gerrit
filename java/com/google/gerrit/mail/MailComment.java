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

package com.google.gerrit.mail;

import com.google.gerrit.entities.Comment;
import java.util.Objects;

/** A comment parsed from inbound email */
public class MailComment {
  public enum CommentType {
    CHANGE_MESSAGE,
    FILE_COMMENT,
    INLINE_COMMENT
  }

  CommentType type;
  Comment inReplyTo;
  String fileName;
  String message;
  boolean isLink;

  public MailComment() {}

  public MailComment(
      String message, String fileName, Comment inReplyTo, CommentType type, boolean isLink) {
    this.message = message;
    this.fileName = fileName;
    this.inReplyTo = inReplyTo;
    this.type = type;
    this.isLink = isLink;
  }

  public CommentType getType() {
    return type;
  }

  public Comment getInReplyTo() {
    return inReplyTo;
  }

  public String getFileName() {
    return fileName;
  }

  public String getMessage() {
    return message;
  }

  /**
   * Checks if the provided comment concerns the same exact spot in the change. This is basically an
   * equals method except that the message is not checked.
   */
  public boolean isSameCommentPath(MailComment c) {
    return Objects.equals(fileName, c.fileName)
        && Objects.equals(inReplyTo, c.inReplyTo)
        && Objects.equals(type, c.type);
  }
}
