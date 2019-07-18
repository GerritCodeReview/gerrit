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

package com.google.gerrit.server.api.changes;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.changes.DeleteCommentInput;
import com.google.gerrit.extensions.api.changes.DraftApi;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.DraftCommentResource;
import com.google.gerrit.server.restapi.change.DeleteDraftComment;
import com.google.gerrit.server.restapi.change.GetDraftComment;
import com.google.gerrit.server.restapi.change.PutDraftComment;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

class DraftApiImpl implements DraftApi {
  interface Factory {
    DraftApiImpl create(DraftCommentResource d);
  }

  private final DeleteDraftComment deleteDraft;
  private final GetDraftComment getDraft;
  private final PutDraftComment putDraft;
  private final DraftCommentResource draft;

  @Inject
  DraftApiImpl(
      DeleteDraftComment deleteDraft,
      GetDraftComment getDraft,
      PutDraftComment putDraft,
      @Assisted DraftCommentResource draft) {
    this.deleteDraft = deleteDraft;
    this.getDraft = getDraft;
    this.putDraft = putDraft;
    this.draft = draft;
  }

  @Override
  public CommentInfo get() throws RestApiException {
    try {
      return getDraft.apply(draft).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve draft", e);
    }
  }

  @Override
  public CommentInfo update(DraftInput in) throws RestApiException {
    try {
      return putDraft.apply(draft, in).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot update draft", e);
    }
  }

  @Override
  public void delete() throws RestApiException {
    try {
      deleteDraft.apply(draft, null);
    } catch (Exception e) {
      throw asRestApiException("Cannot delete draft", e);
    }
  }

  @Override
  public CommentInfo delete(DeleteCommentInput input) {
    throw new NotImplementedException();
  }
}
