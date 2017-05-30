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

package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.ui.Button;

public class EditActions {

  static void deleteEdit(@Nullable Project.NameKey project, Change.Id id, Button... editButtons) {
    ChangeApi.deleteEdit(
        id.get(), Project.NameKey.asStringOrNull(project), cs(project, id, editButtons));
  }

  static void publishEdit(@Nullable Project.NameKey project, Change.Id id, Button... editButtons) {
    ChangeApi.publishEdit(
        id.get(), Project.NameKey.asStringOrNull(project), cs(project, id, editButtons));
  }

  static void rebaseEdit(@Nullable Project.NameKey project, Change.Id id, Button... editButtons) {
    ChangeApi.rebaseEdit(
        id.get(), Project.NameKey.asStringOrNull(project), cs(project, id, editButtons));
  }

  public static GerritCallback<JavaScriptObject> cs(
      @Nullable Project.NameKey project, final Change.Id id, final Button... editButtons) {
    setEnabled(false, editButtons);
    return new GerritCallback<JavaScriptObject>() {
      @Override
      public void onSuccess(JavaScriptObject result) {
        Gerrit.display(PageLinks.toChange(project, id));
      }

      @Override
      public void onFailure(Throwable err) {
        setEnabled(true, editButtons);
        if (SubmitFailureDialog.isConflict(err)) {
          new SubmitFailureDialog(err.getMessage()).center();
          Gerrit.display(PageLinks.toChange(project, id));
        } else {
          super.onFailure(err);
        }
      }
    };
  }

  private static void setEnabled(boolean enabled, Button... editButtons) {
    if (editButtons != null) {
      for (Button b : editButtons) {
        b.setEnabled(enabled);
      }
    }
  }
}
