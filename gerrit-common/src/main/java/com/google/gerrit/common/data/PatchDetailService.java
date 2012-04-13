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

import com.google.gerrit.common.audit.Audit;
import com.google.gerrit.common.auth.SignInRequired;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Patch.Key;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.RemoteJsonService;
import com.google.gwtjsonrpc.common.RpcImpl;
import com.google.gwtjsonrpc.common.VoidResult;
import com.google.gwtjsonrpc.common.RpcImpl.Version;

import java.util.List;
import java.util.Set;

@RpcImpl(version = Version.V2_0)
public interface PatchDetailService extends RemoteJsonService {
  @Audit
  void patchScript(Patch.Key key, PatchSet.Id a, PatchSet.Id b,
      AccountDiffPreference diffPrefs, AsyncCallback<PatchScript> callback);

  @Audit
  @SignInRequired
  void saveDraft(PatchLineComment comment,
      AsyncCallback<PatchLineComment> callback);

  @Audit
  @SignInRequired
  void deleteDraft(PatchLineComment.Key key, AsyncCallback<VoidResult> callback);

  /**
   * Deletes the specified draft patch set. If the draft patch set is the only
   * patch set of the change, then also the change gets deleted.
   *
   * @param psid ID of the draft patch set that should be deleted
   * @param callback callback to report the result of the draft patch set
   *        deletion operation; if the draft patch set was successfully deleted
   *        {@link AsyncCallback#onSuccess(Object)} is invoked and the change
   *        details are passed as parameter; if the change gets deleted because
   *        the draft patch set that was deleted was the only patch set in the
   *        change, then <code>null</code> is passed as result to
   *        {@link AsyncCallback#onSuccess(Object)}
   */
  @Audit
  @SignInRequired
  void deleteDraftPatchSet(PatchSet.Id psid, AsyncCallback<ChangeDetail> callback);

  @Audit
  @SignInRequired
  void publishComments(PatchSet.Id psid, String message,
      Set<ApprovalCategoryValue.Id> approvals,
      AsyncCallback<VoidResult> callback);

  @Audit
  @SignInRequired
  void addReviewers(Change.Id id, List<String> reviewers, boolean confirmed,
      AsyncCallback<ReviewerResult> callback);

  @Audit
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
  @Audit
  @SignInRequired
  void setReviewedByCurrentUser(Key patchKey, boolean reviewed, AsyncCallback<VoidResult> callback);
}
