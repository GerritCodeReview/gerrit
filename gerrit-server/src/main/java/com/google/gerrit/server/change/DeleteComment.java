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

package com.google.gerrit.server.change;


import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.DeleteComment.Input;
import com.google.gerrit.server.notedb.ChangeNotesCommentUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class DeleteComment implements
    RestModifyView<CommentResource, Input> {
  public static class Input {
  }

  private ChangeNotesCommentUpdate commentUpdate;
  private final Provider<CurrentUser> currentUserProvider;

  @Inject
  public DeleteComment(ChangeNotesCommentUpdate commentUpdate,
      Provider<CurrentUser> currentUserProvider) {
    this.commentUpdate = commentUpdate;
    this.currentUserProvider = currentUserProvider;
  }

  @Override
  public Response<?> apply(CommentResource rsrc, Input input)
      throws Exception {
    if (!currentUserProvider.get().getCapabilities().canAdministrateServer()) {
      throw new Exception("NonAdmins are not allow to delete comments");
    }

    commentUpdate.init(rsrc.getRevisionResource().getProject(),
        rsrc.getPatchSet().getId().changeId);
    commentUpdate.deleteComment(rsrc.getComment());
    return Response.none();
  }

}

