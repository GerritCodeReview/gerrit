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

import com.google.gerrit.client.Link;

/**
 * A screen that displays all the changes created by a user.
 */
public class ReviewerScreen extends MineSingleListScreen {
  private String ownerName;

  public ReviewerScreen(String userName) {
    super(Link.REVIEWER);
    this.ownerName = userName;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setWindowTitle(Util.M.changesReviewedByTitle(ownerName));
    setPageTitle(Util.M.changesReviewedByTitle(ownerName));
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.LIST_SVC.changesReviewedBy(ownerName, loadCallback());
  }

}
