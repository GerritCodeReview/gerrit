//Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.diff;

import com.google.gerrit.client.ui.ListenableAccountDiffPreference;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;

public abstract class DiffScreen extends Screen {

  /**
   * What should be displayed in the top of the screen
   */
  public static enum TopViewType {
    MAIN, COMMIT, PREFERENCES, PATCH_SETS, FILES
  }

  static int LARGE_FILE_CONTEXT = 25;

  @UiField
  FlowPanel topPanel;

  private DiffPreferencesPanel settingsPanel;
  private TopViewType topViewType;
  private ListenableAccountDiffPreference prefs;
  private HandlerRegistration prefsHandler;
  private int context;

  DiffScreen() {
    prefs = new ListenableAccountDiffPreference();
    context = prefs.get().getContext();
    settingsPanel = new DiffPreferencesPanel(prefs);
  }

  @Override
  public void onShowView() {
    super.onShowView();

    if (prefsHandler == null) {
      prefsHandler = prefs.addValueChangeHandler(
          new ValueChangeHandler<AccountDiffPreference>() {
            @Override
            public void onValueChange(
                ValueChangeEvent<AccountDiffPreference> event) {
              updateDiffPrefs(event.getValue());
              topPanel.clear();
            }
          });
    }
  }

  @Override
  protected void onUnload() {
    super.onUnload();

    if (prefsHandler != null) {
      prefsHandler.removeHandler();
      prefsHandler = null;
    }
  }

  abstract void resizeCodeMirror();

  abstract void updateDiffPrefs(AccountDiffPreference value);

  ListenableAccountDiffPreference getPrefs() {
    return prefs;
  }

  int getContext() {
    return context;
  }

  void setContext(int c) {
    context = c;
  }

  public TopViewType getTopViewType() {
    return topViewType;
  }

  public void setTopViewType(TopViewType tv) {
    topViewType = tv;
    topPanel.clear();
    /**
     * TODO: As of now, the only top view implemented is the diff preferences.
     * The rest of the views need to be implemented and links need to be fixed.
     */
    switch(tv) {
      case COMMIT:
        break;
      case PREFERENCES: topPanel.add(settingsPanel);
        break;
      case PATCH_SETS:
        break;
      case FILES:
        break;
      case MAIN:
        break;
    }
    resizeCodeMirror();
  }

  FlowPanel getTopPanel() {
    return topPanel;
  }
}