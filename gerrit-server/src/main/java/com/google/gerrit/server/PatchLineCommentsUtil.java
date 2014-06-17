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

package com.google.gerrit.server;

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.server.account.AccountCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Singleton;

import java.io.UnsupportedEncodingException;
import java.util.List;


/**
 * Utility functions to manipulate PatchLineComments.
 */
@Singleton
public class PatchLineCommentsUtil {
  public static String buildNote(AccountCache accountCache,
      List<PatchLineComment> comments) throws UnsupportedEncodingException,
      OrmException {
    if (comments.isEmpty()) {
      return null;
    }

    StringBuilder note = new StringBuilder();
    note.append("Patch set: ");
    note.append(comments.get(0).getKey().getParentKey().get());
    note.append("\n");

    String currentFilename = null;

    for (PatchLineComment c: comments) {
      String commentFilename = c.getKey().getParentKey().getFileName();
      if (!commentFilename.equals(currentFilename)) {
        note.append("File: ");
        note.append(commentFilename);
        note.append("\n\n");
      }
      CommentRange range = c.getRange();
      if (range != null) {
        note.append(range.getStartLine())
            .append(":")
            .append(range.getStartCharacter())
            .append("-")
            .append(range.getEndLine())
            .append(":")
            .append(range.getEndCharacter());
      } else {
        note.append(c.getLine());
      }
      note.append("\n");
      note.append(FormatUtil.mediumFormat(c.getWrittenOn()));

      byte[] messageBytes = c.getMessage().getBytes("UTF-8");
      note.append("Length(in bytes): ");
      note.append(messageBytes.length);
      note.append("\n");

      note.append("Author: ");
      Account account = accountCache.get(c.getAuthor()).getAccount();
      note.append(account.getFullName())
          .append(" <")
          .append(account.getPreferredEmail())
          .append(">");
      note.append("\n");

      String parent = c.getParentUuid();
      if (parent != null) {
        note.append("Parent: ");
        note.append(parent);
        note.append("\n");
      }

      note.append("UUID: ");
      note.append(c.getKey().get());
      note.append("\n");

      note.append(c.getMessage());
      note.append("\n\n");
    }

    return note.toString();
  }
}
