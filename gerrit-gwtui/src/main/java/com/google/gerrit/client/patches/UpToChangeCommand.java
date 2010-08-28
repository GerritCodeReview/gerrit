// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeScreen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.Change;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwtexpui.globalkey.client.KeyCommand;

class UpToChangeCommand extends KeyCommand {
  private final Change.Id changeId;

  UpToChangeCommand(Change.Id changeId, int mask, int key) {
    super(mask, key, PatchUtil.C.upToChange());
    this.changeId = changeId;
  }

  @Override
  public void onKeyPress(final KeyPressEvent event) {
    Gerrit.display(PageLinks.toChange(changeId), new ChangeScreen(changeId));
  }
}
