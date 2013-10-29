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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtorm.client.KeyUtil;

public class DocScreen extends Screen {
  private DocTable table;
  private DocTable.Section section;
  private DocList docs;
  private final String query;

  public DocScreen(final String query) {
    this.query = KeyUtil.decode(query);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    table = new DocTable() {
      {
        keysNavigation.add(new KeyCommand(0, 'R', Util.C.keyReloadSearch()) {
          @Override
          public void onKeyPress(final KeyPressEvent event) {
            Gerrit.display(getToken());
          }
        });
      }
    };
    section = new DocTable.Section();
    table.addSection(section);
    table.setSavePointerId(query);
    add(table);

    setWindowTitle(Util.M.docQueryWindowTitle(query));
    setPageTitle(Util.M.docQueryPageTitle(query));
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    docs.query(loadCallback(), query);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    table.setRegisterKeys(true);
  }

  protected AsyncCallback<DocList> loadCallback() {
    return new GerritCallback<DocList>() {
      @Override
      public final void onSuccess(DocList result) {
        display(result);
        DocScreen.this.display();
      }
    };
  }

  protected void display(final DocList result) {
    docs = result;
    section.display(result);
    table.finishDisplay();
  }

  @Override
  public void onShowView() {
    super.onShowView();
    Gerrit.setQueryString(query);
  }
}
