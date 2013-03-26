// Copyright (C) 2013 The Android Open Source Project
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
// limitations under the License.package com.google.gerrit.server.change;

package com.google.gerrit.server.change;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.server.account.AccountInfo;

import java.sql.Timestamp;

public class CommentInfo {
  static enum Side {
    PARENT, REVISION;
  }

  final String kind = "gerritcodereview#comment";
  String id;
  String path;
  Side side;
  Integer line;
  String inReplyTo;
  String message;
  Timestamp updated;
  AccountInfo author;

  CommentInfo(PatchLineComment c, AccountInfo.Loader accountLoader) {
    id = Url.encode(c.getKey().get());
    path = c.getKey().getParentKey().getFileName();
    if (c.getSide() == 0) {
      side = Side.PARENT;
    }
    if (c.getLine() > 0) {
      line = c.getLine();
    }
    inReplyTo = Url.encode(c.getParentUuid());
    message = Strings.emptyToNull(c.getMessage());
    updated = c.getWrittenOn();
    if (accountLoader != null) {
      author = accountLoader.get(c.getAuthor());
    }
  }
}
