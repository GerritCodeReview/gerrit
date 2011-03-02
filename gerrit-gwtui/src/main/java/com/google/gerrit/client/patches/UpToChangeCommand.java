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
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwtexpui.globalkey.client.KeyCommand;

class UpToChangeCommand extends KeyCommand {
  private final PatchSet.Id patchSetId;

  UpToChangeCommand(PatchSet.Id patchSetId, int mask, int key) {
    super(mask, key, PatchUtil.C.upToChange());
    this.patchSetId = patchSetId;
  }

  @Override
  public void onKeyPress(final KeyPressEvent event) {
    Gerrit.display(PageLinks.toChange(patchSetId), new ChangeScreen(patchSetId));
  }
}
