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
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.common.changes.ListChangesOption;
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
import java.util.EnumSet;
import java.util.List;

class RelatedChanges extends Composite {
  interface Binder extends UiBinder<TabPanel, RelatedChanges> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String subject();
  }

  @UiField TabPanel tabPanel;
  @UiField Style style;

  private List<RelatedChangesTab> tabs;
  private RelatedChangesTab relatedChangesTab;
  private RelatedChangesTab conflictingChangesTab;
  private RelatedChangesTab cherryPicksTab;
  private int maxHeight;

  RelatedChanges() {
    initWidget(uiBinder.createAndBindUi(this));
    setVisible(false);

    tabs = new ArrayList<RelatedChangesTab>();
  }

  private RelatedChangesTab createTab(String title, String tooltip) {
    setVisible(true);
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
    if (index == 0) {
      tabPanel.selectTab(0);
    }
    return relatedChangesTab;
  }

  void setTabTitle(int index, String title) {
    tabPanel.getTabBar().setTabText(index, title);
  }

  Style getStyle() {
    return style;
  }

  void set(final ChangeInfo info, final String revision) {
    if (info.status().isOpen()) {
      setForOpenChange(info, revision);
    }

    StringBuilder cherryPicksQuery = new StringBuilder();
    cherryPicksQuery.append(" project:").append(info.project());
    cherryPicksQuery.append(" change:").append(info.change_id());
    cherryPicksQuery.append(" -change:").append(info._number());
    ChangeList.query(cherryPicksQuery.toString(),
        EnumSet.of(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT),
        new AsyncCallback<ChangeList>() {
          @Override
          public void onSuccess(ChangeList result) {
            if (result.length() > 0) {
              getTab().setTitle(Resources.M.cherryPicks(result.length()));
              getTab().setChanges(info.project(), revision,
                  convertChangeList(result));
            }
          }

          @Override
          public void onFailure(Throwable err) {
            getTab().setTitle(
                Resources.M.cherryPicks(Resources.C.notAvailable()));
            getTab().setError(err.getMessage());
          }

          private RelatedChangesTab getTab() {
            if (cherryPicksTab == null) {
              cherryPicksTab =
                  createTab(Resources.C.cherryPicks(),
                      Resources.C.cherryPicksTooltip());
              cherryPicksTab.registerKeys();
              cherryPicksTab.setShowBranches(true);
            }
            return cherryPicksTab;
          }
        });
  }

  private void setForOpenChange(final ChangeInfo info, final String revision) {
    relatedChangesTab = createTab(Resources.C.relatedChanges(),
        Resources.C.relatedChangesTooltip());

    ChangeApi.revision(info.legacy_id().get(), revision).view("related")
        .get(new AsyncCallback<RelatedInfo>() {
          @Override
          public void onSuccess(RelatedInfo result) {
            relatedChangesTab.setTitle(Resources.M.relatedChanges(result.changes().length()));
            relatedChangesTab.setChanges(info.project(), revision, result.changes());
          }

          @Override
          public void onFailure(Throwable err) {
            relatedChangesTab.setTitle(
                Resources.M.relatedChanges(Resources.C.notAvailable()));
            relatedChangesTab.setError(err.getMessage());
          }
        });

    StringBuilder conflictsQuery = new StringBuilder();
    conflictsQuery.append("status:open");
    conflictsQuery.append(" project:").append(info.project());
    conflictsQuery.append(" branch:").append(info.branch());
    conflictsQuery.append(" conflicts:").append(info.change_id());
    ChangeList.query(conflictsQuery.toString(),
        EnumSet.of(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT),
        new AsyncCallback<ChangeList>() {
          @Override
          public void onSuccess(ChangeList result) {
            if (result.length() > 0) {
              getTab().setTitle(Resources.M.conflictingChanges(result.length()));
              getTab().setChanges(info.project(), revision,
                  convertChangeList(result));
            }
          }

          @Override
          public void onFailure(Throwable err) {
            getTab().setTitle(
                Resources.M.conflictingChanges(Resources.C.notAvailable()));
            getTab().setError(err.getMessage());
          }

          private RelatedChangesTab getTab() {
            if (conflictingChangesTab == null) {
              conflictingChangesTab =
                  createTab(Resources.C.conflictingChanges(),
                      Resources.C.conflictingChangesTooltip());
              conflictingChangesTab.registerKeys();
            }
            return conflictingChangesTab;
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

  private JsArray<ChangeAndCommit> convertChangeList(ChangeList l) {
    JsArray<ChangeAndCommit> arr = JavaScriptObject.createArray().cast();
    for (ChangeInfo i : Natives.asList(l)) {
      RevisionInfo currentRevision = i.revision(i.current_revision());
      ChangeAndCommit c = ChangeAndCommit.create();
      c.set_id(i.id());
      c.set_commit(currentRevision.commit());
      c.set_change_number(i._number());
      c.set_revision_number(currentRevision._number());
      c.set_branch(i.branch());
      arr.push(c);
    }
    return arr;
  }

  private static class RelatedInfo extends JavaScriptObject {
    final native JsArray<ChangeAndCommit> changes() /*-{ return this.changes }-*/;
    protected RelatedInfo() {
    }
  }

  static class ChangeAndCommit extends JavaScriptObject {
    static ChangeAndCommit create() {
      return (ChangeAndCommit) createObject();
    }

    final native String id() /*-{ return this.change_id }-*/;
    final native CommitInfo commit() /*-{ return this.commit }-*/;
    final native String branch() /*-{ return this.branch }-*/;

    final native void set_id(String i)
    /*-{ if(i)this.change_id=i; }-*/;

    final native void set_commit(CommitInfo c)
    /*-{ if(c)this.commit=c; }-*/;

    final native void set_branch(String b)
    /*-{ if(b)this.branch=b; }-*/;

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

    final native void set_change_number(int n)
    /*-{ this._change_number=n; }-*/;

    final native void set_revision_number(int n)
    /*-{ this._revision_number=n; }-*/;

    protected ChangeAndCommit() {
    }
  }
}
