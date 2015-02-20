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
// limitations under the License.

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.inject.TypeLiteral;

public class CommentResource implements RestResource {
  public static final TypeLiteral<RestView<CommentResource>> COMMENT_KIND =
      new TypeLiteral<RestView<CommentResource>>() {};

  private final RevisionResource rev;
  private final PatchLineComment comment;

  public CommentResource(RevisionResource rev, PatchLineComment c) {
    this.rev = rev;
    this.comment = c;
  }

  public PatchSet getPatchSet() {
    return rev.getPatchSet();
  }

  PatchLineComment getComment() {
    return comment;
  }

  String getId() {
    return comment.getKey().get();
  }

  Account.Id getAuthorId() {
    return comment.getAuthor();
  }
}
