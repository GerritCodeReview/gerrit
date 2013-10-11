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

import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.CommitInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.TabBar;
import com.google.gwt.user.client.ui.TabBar.Tab;
import com.google.gwt.user.client.ui.TabPanel;
import com.google.gwt.user.client.ui.VerticalPanel;

import java.util.ArrayList;
import java.util.List;

class RelatedChanges extends Composite {
  interface Binder extends UiBinder<TabPanel, RelatedChanges> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  private static final int RELATED_CHANGES = 0;

  interface Style extends CssResource {
    String subject();
  }

  @UiField TabPanel tabPanel;
  @UiField Style style;

  private List<RelatedChangesTab> tabs;
  private int maxHeight;

  RelatedChanges() {
    initWidget(uiBinder.createAndBindUi(this));

    tabs = new ArrayList<RelatedChangesTab>();
  }

  private RelatedChangesTab createTab(String title, String tooltip) {
    VerticalPanel panel = new VerticalPanel();
    tabPanel.add(panel, title);
    TabBar tabBar = tabPanel.getTabBar();
    int index = tabBar.getTabCount() - 1;
    Tab tab = tabBar.getTab(index);
    tab.setWordWrap(false);
    ((Composite) tab).setTitle(tooltip);
    RelatedChangesTab relatedChangesTab =
        new RelatedChangesTab(this, index, panel);
    tabs.add(relatedChangesTab);
    relatedChangesTab.setMaxHeight(maxHeight);
    return relatedChangesTab;
  }

  void setTabTitle(int index, String title) {
    tabPanel.getTabBar().setTabText(index, title);
  }

  Style getStyle() {
    return style;
  }

  void set(final ChangeInfo info, final String revision) {
    if (info.status().isClosed()) {
      setVisible(false);
      return;
    }

    createTab(Resources.C.relatedChanges(),
        Resources.C.relatedChangesTooltip());
    tabPanel.selectTab(RELATED_CHANGES);

    ChangeApi.revision(info.legacy_id().get(), revision).view("related")
        .get(new AsyncCallback<RelatedInfo>() {
          @Override
          public void onSuccess(RelatedInfo result) {
            RelatedChangesTab tab = tabs.get(RELATED_CHANGES);
            tab.setTitle(Resources.M.relatedChanges(result.changes().length()));
            tab.setChanges(info.project(), revision, result.changes());
          }

          @Override
          public void onFailure(Throwable err) {
            RelatedChangesTab tab = tabs.get(RELATED_CHANGES);
            tab.setTitle(Resources.M.relatedChanges(Resources.C.notAvailable()));
            tab.setError(err.getMessage());
          }
        });
  }

  void setMaxHeight(int height) {
    this.maxHeight = height;
    for (int i = 0; i < tabPanel.getTabBar().getTabCount(); i++) {
      tabs.get(i).setMaxHeight(height);
    }
  }

  void registerKeys() {
    for (int i = 0; i < tabPanel.getTabBar().getTabCount(); i++) {
      tabs.get(i).registerKeys();
    }
  }

  private static class RelatedInfo extends JavaScriptObject {
    final native JsArray<ChangeAndCommit> changes() /*-{ return this.changes }-*/;
    protected RelatedInfo() {
    }
  }

  static class ChangeAndCommit extends JavaScriptObject {
    final native String id() /*-{ return this.change_id }-*/;
    final native CommitInfo commit() /*-{ return this.commit }-*/;

    final Change.Id legacy_id() {
      return has_change_number() ? new Change.Id(_change_number()) : null;
    }

    final PatchSet.Id patch_set_id() {
      return has_change_number() && has_revision_number()
          ? new PatchSet.Id(legacy_id(), _revision_number())
          : null;
    }

    final native boolean has_change_number()
    /*-{ return this.hasOwnProperty('_change_number') }-*/;

    final native boolean has_revision_number()
    /*-{ return this.hasOwnProperty('_revision_number') }-*/;

    final native int _change_number()
    /*-{ return this._change_number }-*/;

    final native int _revision_number()
    /*-{ return this._revision_number }-*/;

    protected ChangeAndCommit() {
    }
  }
}
