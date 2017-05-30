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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;

class IncludedInAction extends RightSidePopdownAction {
  private final IncludedInBox includedInBox;

  IncludedInAction(
      @Nullable Project.NameKey project,
      Change.Id changeId,
      ChangeScreen.Style style,
      UIObject relativeTo,
      Widget includedInButton) {
    super(style, relativeTo, includedInButton);
    this.includedInBox = new IncludedInBox(project, changeId);
  }

  @Override
  Widget getWidget() {
    return includedInBox;
  }
}
