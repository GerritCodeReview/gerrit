// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.PatchLineComment;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Timestamp;

class GetDraft implements RestReadView<DraftResource> {
  @Override
  public Object apply(DraftResource rsrc) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    return new Comment(rsrc.getComment());
  }

  static enum Side {
    PARENT, REVISION;
  }

  static class Comment {
    final String kind = "gerritcodereview#comment";
    String id;
    String path;
    Side side;
    Integer line;
    String inReplyTo;
    String message;
    Timestamp updated;

    Comment(PatchLineComment c) {
      try {
        id = URLEncoder.encode(c.getKey().get(), "UTF-8");
        if (c.getParentUuid() != null) {
          inReplyTo = URLEncoder.encode(c.getParentUuid(), "UTF-8");
        }
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException("UTF-8 encoding not supported", e);
      }
      path = c.getKey().getParentKey().getFileName();
      if (c.getSide() == 0) {
        side = Side.PARENT;
      }
      if (c.getLine() > 0) {
        line = c.getLine();
      }
      message = Strings.emptyToNull(c.getMessage());
      updated = c.getWrittenOn();
    }
  }
}
