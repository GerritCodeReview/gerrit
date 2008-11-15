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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.Screen;
import com.google.gerrit.client.data.MineResult;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;


public class MineScreen extends Screen {
  private ChangeTable table;
  private ChangeTable.Section byMe;
  private ChangeTable.Section forReview;
  private ChangeTable.Section closed;

  public MineScreen() {
    super(Util.C.mineHeading());

    table = new ChangeTable();
    byMe = new ChangeTable.Section(Util.C.mineByMe());
    forReview = new ChangeTable.Section(Util.C.mineForReview());
    closed = new ChangeTable.Section(Util.C.mineClosed());
    
    Util.LIST_SVC.mine(new AsyncCallback<MineResult>() {
      public void onSuccess(final MineResult r) {
        byMe.display(r.byMe);
        forReview.display(r.forReview);
        closed.display(r.closed);
      }

      public void onFailure(final Throwable caught) {
        GWT.log("Fail", caught);
      }
    });

    table.addSection(byMe);
    table.addSection(forReview);
    table.addSection(closed);

    add(table);
  }
}
