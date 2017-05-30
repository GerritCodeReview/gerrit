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
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;

public class ChangeActions {

  static void publish(
      @Nullable Project.NameKey project, Change.Id id, String revision, Button... draftButtons) {
    ChangeApi.publish(
        Project.NameKey.asStringOrNull(project), id.get(), revision, cs(project, id, draftButtons));
  }

  static void delete(
      @Nullable Project.NameKey project, Change.Id id, String revision, Button... draftButtons) {
    ChangeApi.deleteRevision(
        Project.NameKey.asStringOrNull(project), id.get(), revision, cs(project, id, draftButtons));
  }

  static void delete(@Nullable Project.NameKey project, Change.Id id, Button... draftButtons) {
    ChangeApi.deleteChange(Project.NameKey.asStringOrNull(project), id.get(), mine(draftButtons));
  }

  static void markPrivate(@Nullable Project.NameKey project, Change.Id id, Button... draftButtons) {
    ChangeApi.markPrivate(
        Project.NameKey.asStringOrNull(project), id.get(), cs(project, id, draftButtons));
  }

  static void unmarkPrivate(
      @Nullable Project.NameKey project, Change.Id id, Button... draftButtons) {
    ChangeApi.unmarkPrivate(
        Project.NameKey.asStringOrNull(project), id.get(), cs(project, id, draftButtons));
  }

  public static GerritCallback<JavaScriptObject> cs(
      @Nullable Project.NameKey project, final Change.Id id, final Button... draftButtons) {
    setEnabled(false, draftButtons);
    return new GerritCallback<JavaScriptObject>() {
      @Override
      public void onSuccess(JavaScriptObject result) {
        Gerrit.display(PageLinks.toChange(project, id));
      }

      @Override
      public void onFailure(Throwable err) {
        setEnabled(true, draftButtons);
        if (SubmitFailureDialog.isConflict(err)) {
          new SubmitFailureDialog(err.getMessage()).center();
          Gerrit.display(PageLinks.toChange(project, id));
        } else {
          super.onFailure(err);
        }
      }
    };
  }

  private static AsyncCallback<JavaScriptObject> mine(final Button... draftButtons) {
    setEnabled(false, draftButtons);
    return new GerritCallback<JavaScriptObject>() {
      @Override
      public void onSuccess(JavaScriptObject result) {
        Gerrit.display(PageLinks.MINE);
      }

      @Override
      public void onFailure(Throwable err) {
        setEnabled(true, draftButtons);
        if (SubmitFailureDialog.isConflict(err)) {
          new SubmitFailureDialog(err.getMessage()).center();
          Gerrit.display(PageLinks.MINE);
        } else {
          super.onFailure(err);
        }
      }
    };
  }

  private static void setEnabled(boolean enabled, Button... draftButtons) {
    if (draftButtons != null) {
      for (Button b : draftButtons) {
        b.setEnabled(enabled);
      }
    }
  }
}
