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
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.SubmitFailureDialog;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

class DraftActions {

  static void publish(final Change.Id id, String revision) {
    ChangeApi.publish(id.get(), revision, cs(id));
  }

  static void drop(final Change.Id id, String revision) {
    ChangeApi.deleteDraftPatchSet(id.get(), revision, cs(id));
  }

  static void drop(final Change.Id id) {
    ChangeApi.deleteDraftChange(id.get(), mine());
  }

  private static GerritCallback<JavaScriptObject> cs(
      final Change.Id id) {
    return new GerritCallback<JavaScriptObject>() {
      public void onSuccess(JavaScriptObject result) {
        redisplay();
      }

      public void onFailure(Throwable err) {
        if (SubmitFailureDialog.isConflict(err)) {
          new SubmitFailureDialog(err.getMessage()).center();
          redisplay();
        } else {
          super.onFailure(err);
        }
      }

      private void redisplay() {
        Gerrit.display(PageLinks.toChange2(id));
      }
    };
  }

  private static AsyncCallback<JavaScriptObject> mine() {
    return new GerritCallback<JavaScriptObject>() {
      public void onSuccess(JavaScriptObject result) {
        redisplay();
      }

      public void onFailure(Throwable err) {
        if (SubmitFailureDialog.isConflict(err)) {
          new SubmitFailureDialog(err.getMessage()).center();
          redisplay();
        } else {
          super.onFailure(err);
        }
      }

      private void redisplay() {
        Gerrit.display(PageLinks.MINE);
      }
    };
  }

}
