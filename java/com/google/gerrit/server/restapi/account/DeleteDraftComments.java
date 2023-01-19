// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.restapi.account;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.accounts.DeleteDraftCommentsInput;
import com.google.gerrit.extensions.api.accounts.DeletedDraftCommentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class DeleteDraftComments
    implements RestModifyView<AccountResource, DeleteDraftCommentsInput> {

  private final Provider<CurrentUser> userProvider;
  private final DeleteDraftCommentsUtil deleteDraftCommentsUtil;

  @Inject
  DeleteDraftComments(
      Provider<CurrentUser> userProvider, DeleteDraftCommentsUtil deleteDraftCommentsUtil) {
    this.userProvider = userProvider;
    this.deleteDraftCommentsUtil = deleteDraftCommentsUtil;
  }

  @Override
  public Response<ImmutableList<DeletedDraftCommentInfo>> apply(
      AccountResource rsrc, DeleteDraftCommentsInput input)
      throws RestApiException, UpdateException {
    CurrentUser user = userProvider.get();
    if (!user.isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    if (!rsrc.getUser().hasSameAccountId(user)) {
      // Disallow even for admins or users with Modify Account. Drafts are not like preferences or
      // other account info; there is no way even for admins to read or delete another user's drafts
      // using the normal draft endpoints under the change resource, so disallow it here as well.
      // (Admins may still call this endpoint with impersonation, but in that case it would pass the
      // hasSameAccountId check.)
      throw new AuthException("Cannot delete drafts of other user");
    }

    return Response.ok(deleteDraftCommentsUtil.deleteDraftComments(rsrc.getUser(), input.query));
  }
}
