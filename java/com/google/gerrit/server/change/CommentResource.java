// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.inject.TypeLiteral;

public class CommentResource implements RestResource {
  public static final TypeLiteral<RestView<CommentResource>> COMMENT_KIND =
      new TypeLiteral<RestView<CommentResource>>() {};

  private final RevisionResource rev;
  private final Comment comment;

  public CommentResource(RevisionResource rev, Comment c) {
    this.rev = rev;
    this.comment = c;
  }

  public PatchSet getPatchSet() {
    return rev.getPatchSet();
  }

  public Comment getComment() {
    return comment;
  }

  public String getId() {
    return comment.key.uuid;
  }

  public Account.Id getAuthorId() {
    return comment.author.getId();
  }

  public RevisionResource getRevisionResource() {
    return rev;
  }
}
