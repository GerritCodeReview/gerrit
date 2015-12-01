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

package com.google.gerrit.client.documentation;

import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.KeyUtil;

public class DocScreen extends Screen {
  private DocTable table;
  private final String query;

  public DocScreen(String query) {
    this.query = KeyUtil.decode(query);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    table = new DocTable();
    table.setSavePointerId(query);
    add(table);

    setWindowTitle(Util.M.docQueryWindowTitle(query));
    setPageTitle(Util.M.docQueryPageTitle(query));
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    doQuery();
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    table.setRegisterKeys(true);
  }

  private AsyncCallback<JsArray<DocInfo>> loadCallback() {
    return new GerritCallback<JsArray<DocInfo>>() {
      @Override
      public void onSuccess(JsArray<DocInfo> result) {
        displayResults(result);
        display();
      }
    };
  }

  private void displayResults(JsArray<DocInfo> result) {
    table.display(result);
    table.finishDisplay();
  }

  private void doQuery() {
    RestApi call = new RestApi("Documentation");
    call.addParameterRaw("q", KeyUtil.encode(query));
    call.get(loadCallback());
  }
}
