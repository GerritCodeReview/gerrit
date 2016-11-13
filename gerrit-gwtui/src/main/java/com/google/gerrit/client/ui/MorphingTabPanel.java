// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.ui.TabPanel;
import com.google.gwt.user.client.ui.Widget;
import java.util.ArrayList;
import java.util.List;

/**
 * A TabPanel which allows entries to be hidden. This class is not yet designed to handle removes or
 * any other add methods than the one overridden here. It is also not designed to handle anything
 * other than text for the tab.
 */
public class MorphingTabPanel extends TabPanel {
  // Keep track of the order the widgets/texts should be in when not hidden.
  private List<Widget> widgets = new ArrayList<>();
  private List<String> texts = new ArrayList<>();

  // currently visible widgets
  private List<Widget> visibles = new ArrayList<>();

  private int selection;

  public MorphingTabPanel() {
    addSelectionHandler(
        new SelectionHandler<Integer>() {
          @Override
          public void onSelection(SelectionEvent<Integer> ev) {
            selection = ev.getSelectedItem();
          }
        });
  }

  public int getSelectedIndex() {
    return selection;
  }

  public Widget getSelectedWidget() {
    return getWidget(getSelectedIndex());
  }

  @Override
  public void clear() {
    super.clear();
    widgets.clear();
    texts.clear();
    visibles.clear();
  }

  @Override
  public void add(Widget w, String tabText) {
    addInvisible(w, tabText);
    visibles.add(w);
    super.add(w, tabText);
  }

  public void addInvisible(Widget w, String tabText) {
    widgets.add(w);
    texts.add(tabText);
  }

  public void setVisible(Widget w, boolean visible) {
    if (visible) {
      if (!visibles.contains(w)) {
        int origPos = widgets.indexOf(w);

        /* Re-insert the widget right after the first visible widget found
        when scanning backwards from the current widget */
        for (int pos = origPos - 1; pos >= 0; pos--) {
          int visiblePos = visibles.indexOf(widgets.get(pos));
          if (visiblePos != -1) {
            visibles.add(visiblePos + 1, w);
            insert(w, texts.get(origPos), visiblePos + 1);
            break;
          }
        }
      }
    } else {
      int i = visibles.indexOf(w);
      if (i != -1) {
        visibles.remove(i);
        remove(i);
      }
    }
  }
}
