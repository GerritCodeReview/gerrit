// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.data.SideBySidePatchDetail;
import com.google.gerrit.client.data.UnifiedPatchDetail;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.rpc.SignInRequired;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.RemoteJsonService;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.List;
import java.util.Set;

public interface PatchDetailService extends RemoteJsonService {
  void sideBySidePatchDetail(Patch.Key key, List<PatchSet.Id> versions,
      AsyncCallback<SideBySidePatchDetail> callback);

  void unifiedPatchDetail(Patch.Key key,
      AsyncCallback<UnifiedPatchDetail> callback);

  @SignInRequired
  void myDrafts(Patch.Key key, AsyncCallback<List<PatchLineComment>> callback);

  @SignInRequired
  void saveDraft(PatchLineComment comment,
      AsyncCallback<PatchLineComment> callback);

  @SignInRequired
  void deleteDraft(PatchLineComment.Key key, AsyncCallback<VoidResult> callback);

  @SignInRequired
  void publishComments(PatchSet.Id psid, String message,
      Set<ApprovalCategoryValue.Id> approvals,
      AsyncCallback<VoidResult> callback);

  @SignInRequired
  void addReviewers(Change.Id id, List<String> reviewers,
      AsyncCallback<VoidResult> callback);

  @SignInRequired
  void abandonChange(String message, PatchSet.Id patchSetId,
      AsyncCallback<VoidResult> callback);
}
