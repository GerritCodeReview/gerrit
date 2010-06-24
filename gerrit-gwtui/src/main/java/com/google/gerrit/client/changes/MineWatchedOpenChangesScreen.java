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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.Gerrit;

public class MineWatchedOpenChangesScreen extends PagedSingleListScreen {
  public MineWatchedOpenChangesScreen(final String positionToken) {
    super("mine,watched", positionToken);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setWindowTitle(Gerrit.C.menuMyWatchedChanges());
    setPageTitle(Util.C.watchedHeading());
    setRequiresSignIn(true);
  }

  @Override
  protected void loadPrev() {
    Util.LIST_SVC.myWatchedOpenPrev(pos, pageSize, loadCallback());
  }

  @Override
  protected void loadNext() {
    Util.LIST_SVC.myWatchedOpenNext(pos, pageSize, loadCallback());
  }
}
