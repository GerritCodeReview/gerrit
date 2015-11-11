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

package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.api.ChangeGlue;
import com.google.gerrit.client.change.RelatedChanges.ChangeAndCommit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.SubmitInfo;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.SubmitWholeTopic;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.JsArray;

class SubmitAction {
  static void call(JsArray<ChangeAndCommit> sameTopicChanges,
      JsArray<ChangeAndCommit> sameBranchChanges,
      final ChangeInfo changeInfo,
      final RevisionInfo revisionInfo) {
    if (Gerrit.info().change().submitWholeTopicMode() == SubmitWholeTopic.Mode.AUTO) {
      call(changeInfo, revisionInfo, true);
      return;
    }

    new SubmitDialog(changeInfo, sameTopicChanges, sameBranchChanges,
        new SubmitDialog.Handler() {
          @Override
          public void onBranchSubmit() {
            call(changeInfo, revisionInfo, false);
          }

          @Override
          public void onTopicSubmit() {
            call(changeInfo, revisionInfo, true);
          }
        }).center();
  }

  private static void call(ChangeInfo changeInfo, RevisionInfo revisionInfo,
      boolean submitWholeTopic) {
    if (ChangeGlue.onSubmitChange(changeInfo, revisionInfo)) {
      final Change.Id changeId = changeInfo.legacyId();
      ChangeApi.submit(
        changeId.get(), revisionInfo.name(), submitWholeTopic,
        new GerritCallback<SubmitInfo>() {
          @Override
          public void onSuccess(SubmitInfo result) {
            redisplay();
          }

          @Override
          public void onFailure(Throwable err) {
            if (SubmitFailureDialog.isConflict(err)) {
              new SubmitFailureDialog(err.getMessage()).center();
            } else {
              super.onFailure(err);
            }
            redisplay();
          }

          private void redisplay() {
            Gerrit.display(PageLinks.toChange(changeId));
          }
        });
    }
  }
}
