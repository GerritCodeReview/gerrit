// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.common.data;

import com.google.gerrit.common.auth.SignInRequired;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountDiffPreference;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.Patch.Key;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.RemoteJsonService;
import com.google.gwtjsonrpc.client.RpcImpl;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtjsonrpc.client.RpcImpl.Version;

import java.util.List;
import java.util.Set;

@RpcImpl(version = Version.V2_0)
public interface PatchDetailService extends RemoteJsonService {
  void patchScript(Patch.Key key, PatchSet.Id a, PatchSet.Id b,
      AccountDiffPreference diffPrefs, AsyncCallback<PatchScript> callback);

  @SignInRequired
  void saveDraft(PatchLineComment comment,
      AsyncCallback<PatchLineComment> callback);

  @SignInRequired
  void deleteDraftComment(PatchLineComment.Key key, AsyncCallback<VoidResult> callback);

  @SignInRequired
  void deleteDraftPatchSet(PatchSet.Id psid, AsyncCallback<VoidResult> callback);

  @SignInRequired
  void publishComments(PatchSet.Id psid, String message,
      Set<ApprovalCategoryValue.Id> approvals,
      AsyncCallback<VoidResult> callback);

  @SignInRequired
  void addReviewers(Change.Id id, List<String> reviewers, boolean confirmed,
      AsyncCallback<ReviewerResult> callback);

  @SignInRequired
  void removeReviewer(Change.Id id, Account.Id reviewerId,
      AsyncCallback<ReviewerResult> callback);

  void userApprovals(Set<Change.Id> cids, Account.Id aid,
      AsyncCallback<ApprovalSummarySet> callback);

  void strongestApprovals(Set<Change.Id> cids,
      AsyncCallback<ApprovalSummarySet> callback);

  /**
   * Update the reviewed status for the patch.
   */
  @SignInRequired
  void setReviewedByCurrentUser(Key patchKey, boolean reviewed, AsyncCallback<VoidResult> callback);
}
