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
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.changes.SubmitFailureDialog;
import com.google.gerrit.client.changes.SubmitInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.user.client.Window;

class SubmitAction {
  static void call(ChangeInfo changeInfo, RevisionInfo revisionInfo) {
    if (ChangeGlue.onSubmitChange(changeInfo, revisionInfo)) {
      final Change.Id changeId = changeInfo.legacy_id();

      if (revisionInfo.commit().otherBranchCommit().equalsIgnoreCase("different branch commit")) {
        boolean reply =
            Window
                .confirm("You are about to merge change from a different branch.\n "
                    + "Do you want to continue?");
        if (reply == true) {
          ChangeApi.submit(changeId.get(), revisionInfo.name(),
              new GerritCallback<SubmitInfo>() {
                public void onSuccess(SubmitInfo result) {
                  redisplay();
                }

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
        } else {
          Gerrit.display(PageLinks.toChange(changeId));
        }
      }

      else {
        ChangeApi.submit(changeId.get(), revisionInfo.name(),
            new GerritCallback<SubmitInfo>() {
              public void onSuccess(SubmitInfo result) {
                redisplay();
              }

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
}