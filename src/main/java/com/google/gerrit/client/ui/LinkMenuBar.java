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

package com.google.gerrit.client.ui;

import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;

/** A GWT MenuBar, rendering its items as through they were normal links. */
public class LinkMenuBar extends MenuBar {
  public LinkMenuBar() {
    setStyleName("gerrit-LinkMenuBar");
  }

  @Override
  public MenuItem addItem(final MenuItem item) {
    item.addStyleDependentName("NormalItem");
    return super.addItem(item);
  }

  /**
   * Add a cell to fill the screen width.
   * <p>
   * The glue has 100% width, forcing the browser to align out the next element
   * as far right as possible. If there is exactly 1 glue in the menu bar, the
   * bar is split into a left and right section. If there are 2 glues, the bar
   * will be split into thirds.
   */
  public void addGlue() {
    addSeparator().setStyleName("gerrit-FillMenuCenter");
  }

  /**
   * Mark this item as the last in its group, so it has no border.
   * <p>
   * Usually this is used just before {@link #addGlue()} and after the last item
   * has been added.
   */
  public void lastInGroup() {
    if (!getItems().isEmpty()) {
      final MenuItem i = getItems().get(getItems().size() - 1);
      i.removeStyleDependentName("NormalItem");
      i.addStyleDependentName("LastItem");
    }
  }
}
