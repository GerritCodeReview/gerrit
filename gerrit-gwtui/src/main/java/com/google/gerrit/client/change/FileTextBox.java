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

import com.google.gerrit.client.changes.ChangeFileApi;
import com.google.gerrit.client.changes.ChangeFileApi.FileContent;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtexpui.globalkey.client.NpTextBox;

class FileTextBox extends NpTextBox {
  private HandlerRegistration blurHandler;
  private NpTextArea textArea;
  private PatchSet.Id id;

  @Override
  protected void onLoad() {
    blurHandler = addBlurHandler(new BlurHandler() {
      @Override
      public void onBlur(BlurEvent event) {
        loadFileContent();
      }
    });
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    blurHandler.removeHandler();
  }

  void set(PatchSet.Id id, NpTextArea content) {
    this.id = id;
    this.textArea = content;
  }

  private void loadFileContent() {
    ChangeFileApi.getContent(id, getText(), new GerritCallback<FileContent>() {
      @Override
      public void onSuccess(FileContent result) {
        textArea.setText(result.text());
      }

      @Override
      public void onFailure(Throwable caught) {
        if (RestApi.isNotFound(caught)) {
          // that means that the file doesn't exist in the repository
        } else {
          super.onFailure(caught);
        }
      }
    });
  }
}
