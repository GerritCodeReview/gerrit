//Copyright (C) 2013 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.changes.ChangeFileApi;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;

class EditFileBox extends EditMessageBox {

  private PatchSet.Id id;
  private String file;

  EditFileBox(
      PatchSet.Id id,
      String content,
      String file) {
    super(null, null, content);
    this.id = id;
    this.file = file;
  }

  @Override
  @UiHandler("save")
  void onSave(ClickEvent e) {
    ChangeFileApi.putContent(id, file, message.getText().trim(),
        new AsyncCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            Gerrit.display(PageLinks.toChange(id.getParentKey()));
            hide();
          }

          @Override
          public void onFailure(Throwable caught) {
          }
        });
  }
}
