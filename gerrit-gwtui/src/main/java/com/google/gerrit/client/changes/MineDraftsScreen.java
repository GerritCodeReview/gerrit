// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.common.PageLinks;


public class MineDraftsScreen extends MineSingleListScreen {
  public MineDraftsScreen() {
    super(PageLinks.MINE_DRAFTS);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setWindowTitle(Gerrit.C.menuMyDrafts());
    setPageTitle(Util.C.draftsHeading());
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.LIST_SVC.myDraftChanges(loadCallback());
  }
}
