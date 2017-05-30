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

import static com.google.gerrit.common.PageLinks.op;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.CommitInfo;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.TabBar;
import com.google.gwt.user.client.ui.TabPanel;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class RelatedChanges extends TabPanel {
  static final RelatedChangesResources R = GWT.create(RelatedChangesResources.class);

  interface RelatedChangesResources extends ClientBundle {
    @Source("related_changes.css")
    RelatedChangesCss css();
  }

  interface RelatedChangesCss extends CssResource {
    String activeRow();

    String current();

    String gitweb();

    String indirect();

    String notCurrent();

    String pointer();

    String row();

    String subject();

    String strikedSubject();

    String submittable();

    String tabPanel();
  }

  enum Tab {
    RELATED_CHANGES(Resources.C.relatedChanges(), Resources.C.relatedChangesTooltip()) {
      @Override
      String getTitle(int count) {
        return Resources.M.relatedChanges(count);
      }

      @Override
      String getTitle(String count) {
        return Resources.M.relatedChanges(count);
      }
    },

    SUBMITTED_TOGETHER(Resources.C.submittedTogether(), Resources.C.submittedTogether()) {
      @Override
      String getTitle(int count) {
        return Resources.M.submittedTogether(count);
      }

      @Override
      String getTitle(String count) {
        return Resources.M.submittedTogether(count);
      }
    },

    SAME_TOPIC(Resources.C.sameTopic(), Resources.C.sameTopicTooltip()) {
      @Override
      String getTitle(int count) {
        return Resources.M.sameTopic(count);
      }

      @Override
      String getTitle(String count) {
        return Resources.M.sameTopic(count);
      }
    },

    CONFLICTING_CHANGES(Resources.C.conflictingChanges(), Resources.C.conflictingChangesTooltip()) {
      @Override
      String getTitle(int count) {
        return Resources.M.conflictingChanges(count);
      }

      @Override
      String getTitle(String count) {
        return Resources.M.conflictingChanges(count);
      }
    },

    CHERRY_PICKS(Resources.C.cherryPicks(), Resources.C.cherryPicksTooltip()) {
      @Override
      String getTitle(int count) {
        return Resources.M.cherryPicks(count);
      }

      @Override
      String getTitle(String count) {
        return Resources.M.cherryPicks(count);
      }
    };

    final String defaultTitle;
    final String tooltip;

    abstract String getTitle(int count);

    abstract String getTitle(String count);

    Tab(String defaultTitle, String tooltip) {
      this.defaultTitle = defaultTitle;
      this.tooltip = tooltip;
    }
  }

  private static Tab savedTab;

  private final List<RelatedChangesTab> tabs;
  private int maxHeightWithHeader;
  private int selectedTab;
  private int outstandingCallbacks;

  RelatedChanges() {
    tabs = new ArrayList<>(Tab.values().length);
    selectedTab = -1;

    setVisible(false);
    addStyleName(R.css().tabPanel());
    initTabBar();
  }

  private void initTabBar() {
    TabBar tabBar = getTabBar();
    tabBar.addSelectionHandler(
        new SelectionHandler<Integer>() {
          @Override
          public void onSelection(SelectionEvent<Integer> event) {
            if (selectedTab >= 0) {
              tabs.get(selectedTab).registerKeys(false);
            }
            selectedTab = event.getSelectedItem();
            tabs.get(selectedTab).registerKeys(true);
          }
        });

    for (Tab tabInfo : Tab.values()) {
      RelatedChangesTab panel = new RelatedChangesTab(tabInfo);
      add(panel, tabInfo.defaultTitle);
      tabs.add(panel);

      TabBar.Tab tab = tabBar.getTab(tabInfo.ordinal());
      tab.setWordWrap(false);
      ((Composite) tab).setTitle(tabInfo.tooltip);

      setTabEnabled(tabInfo, false);
    }
    getTab(Tab.RELATED_CHANGES).setShowIndirectAncestors(true);
    getTab(Tab.CHERRY_PICKS).setShowBranches(true);
    getTab(Tab.SAME_TOPIC).setShowBranches(true);
    getTab(Tab.SAME_TOPIC).setShowProjects(true);
    getTab(Tab.SAME_TOPIC).setShowSubmittable(true);
    getTab(Tab.SUBMITTED_TOGETHER).setShowBranches(true);
    getTab(Tab.SUBMITTED_TOGETHER).setShowProjects(true);
    getTab(Tab.SUBMITTED_TOGETHER).setShowSubmittable(true);
  }

  void set(final ChangeInfo info, final String revision) {
    if (info.status().isOpen()) {
      setForOpenChange(info, revision);
    }

    ChangeApi.revision(info.project(), info.legacyId().get(), revision)
        .view("related")
        .get(
            new TabCallback<RelatedInfo>(Tab.RELATED_CHANGES, info.project(), revision) {
              @Override
              public JsArray<ChangeAndCommit> convert(RelatedInfo result) {
                return result.changes();
              }
            });

    StringBuilder cherryPicksQuery = new StringBuilder();
    cherryPicksQuery.append(op("project", info.project()));
    cherryPicksQuery.append(" ").append(op("change", info.changeId()));
    cherryPicksQuery.append(" ").append(op("-change", info.legacyId().get()));
    cherryPicksQuery.append(" -is:abandoned");
    ChangeList.query(
        cherryPicksQuery.toString(),
        EnumSet.of(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT),
        new TabChangeListCallback(Tab.CHERRY_PICKS, info.project(), revision));

    if (info.currentRevision() != null && info.currentRevision().equals(revision)) {
      ChangeApi.change(info.project(), info.legacyId().get())
          .view("submitted_together")
          .get(new TabChangeListCallback(Tab.SUBMITTED_TOGETHER, info.project(), revision));
    }

    if (!Gerrit.info().change().isSubmitWholeTopicEnabled()
        && info.topic() != null
        && !"".equals(info.topic())) {
      StringBuilder topicQuery = new StringBuilder();
      topicQuery.append("status:open");
      topicQuery.append(" ").append(op("topic", info.topic()));
      ChangeList.query(
          topicQuery.toString(),
          EnumSet.of(
              ListChangesOption.CURRENT_REVISION,
              ListChangesOption.CURRENT_COMMIT,
              ListChangesOption.DETAILED_LABELS,
              ListChangesOption.LABELS),
          new TabChangeListCallback(Tab.SAME_TOPIC, info.project(), revision));
    }
  }

  private void setForOpenChange(final ChangeInfo info, final String revision) {
    if (info.mergeable()) {
      StringBuilder conflictsQuery = new StringBuilder();
      conflictsQuery.append("status:open");
      conflictsQuery.append(" is:mergeable");
      conflictsQuery.append(" ").append(op("conflicts", info.legacyId().get()));
      ChangeList.query(
          conflictsQuery.toString(),
          EnumSet.of(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT),
          new TabChangeListCallback(Tab.CONFLICTING_CHANGES, info.project(), revision));
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    R.css().ensureInjected();
  }

  static void setSavedTab(Tab subject) {
    savedTab = subject;
  }

  private RelatedChangesTab getTab(Tab tabInfo) {
    return tabs.get(tabInfo.ordinal());
  }

  private void setTabTitle(Tab tabInfo, String title) {
    getTabBar().setTabText(tabInfo.ordinal(), title);
  }

  private void setTabEnabled(Tab tabInfo, boolean enabled) {
    getTabBar().setTabEnabled(tabInfo.ordinal(), enabled);
  }

  void setMaxHeight(int height) {
    maxHeightWithHeader = height;
    if (isVisible()) {
      applyMaxHeight();
    }
  }

  private void applyMaxHeight() {
    int header = getTabBar().getOffsetHeight() + 2 /* padding */;
    for (int i = 0; i < getTabBar().getTabCount(); i++) {
      tabs.get(i).setMaxHeight(maxHeightWithHeader - header);
    }
  }

  private abstract class TabCallback<T> implements AsyncCallback<T> {
    private final Tab tabInfo;
    private final String project;
    private final String revision;

    TabCallback(Tab tabInfo, String project, String revision) {
      this.tabInfo = tabInfo;
      this.project = project;
      this.revision = revision;
      outstandingCallbacks++;
    }

    protected abstract JsArray<ChangeAndCommit> convert(T result);

    @Override
    public void onSuccess(T result) {
      if (isAttached()) {
        JsArray<ChangeAndCommit> changes = convert(result);
        if (changes.length() > 0) {
          setTabTitle(tabInfo, tabInfo.getTitle(changes.length()));
          getTab(tabInfo).setChanges(project, revision, changes);
        }
        onDone(changes.length() > 0);
      }
    }

    @Override
    public void onFailure(Throwable err) {
      if (isAttached()) {
        setTabTitle(tabInfo, tabInfo.getTitle(Resources.C.notAvailable()));
        getTab(tabInfo).setError(err.getMessage());
        onDone(true);
      }
    }

    private void onDone(boolean enabled) {
      setTabEnabled(tabInfo, enabled);
      outstandingCallbacks--;
      if (outstandingCallbacks == 0 || (enabled && tabInfo == Tab.RELATED_CHANGES)) {
        outstandingCallbacks = 0; // Only execute this block once
        for (int i = 0; i < getTabBar().getTabCount(); i++) {
          if (getTabBar().isTabEnabled(i)) {
            selectTab(i);
            setVisible(true);
            applyMaxHeight();
            break;
          }
        }
      }

      if (tabInfo == savedTab && enabled) {
        selectTab(savedTab.ordinal());
      }
    }
  }

  private class TabChangeListCallback extends TabCallback<ChangeList> {
    TabChangeListCallback(Tab tabInfo, String project, String revision) {
      super(tabInfo, project, revision);
    }

    @Override
    protected JsArray<ChangeAndCommit> convert(ChangeList l) {
      JsArray<ChangeAndCommit> arr = JavaScriptObject.createArray().cast();
      for (ChangeInfo i : Natives.asList(l)) {
        if (i.currentRevision() != null && i.revisions().containsKey(i.currentRevision())) {
          RevisionInfo currentRevision = i.revision(i.currentRevision());
          ChangeAndCommit c = ChangeAndCommit.create();
          c.setId(i.id());
          c.setCommit(currentRevision.commit());
          c.setChangeNumber(i.legacyId().get());
          c.setRevisionNumber(currentRevision._number());
          c.setBranch(i.branch());
          c.setProject(i.project());
          c.setSubmittable(i.submittable() && i.mergeable());
          c.setStatus(i.status().asChangeStatus().toString());
          arr.push(c);
        }
      }
      return arr;
    }
  }

  public static class RelatedInfo extends JavaScriptObject {
    public final native JsArray<ChangeAndCommit> changes() /*-{ return this.changes }-*/;

    protected RelatedInfo() {}
  }

  public static class ChangeAndCommit extends JavaScriptObject {
    static ChangeAndCommit create() {
      return (ChangeAndCommit) createObject();
    }

    public final native String id() /*-{ return this.change_id }-*/;

    public final native CommitInfo commit() /*-{ return this.commit }-*/;

    final native String branch() /*-{ return this.branch }-*/;

    final native String project() /*-{ return this.project }-*/;

    final native boolean submittable() /*-{ return this._submittable ? true : false; }-*/;

    final Change.Status status() {
      String s = statusRaw();
      return s != null ? Change.Status.valueOf(s) : null;
    }

    private native String statusRaw() /*-{ return this.status; }-*/;

    final native void setId(String i) /*-{ if(i)this.change_id=i; }-*/;

    final native void setCommit(CommitInfo c) /*-{ if(c)this.commit=c; }-*/;

    final native void setBranch(String b) /*-{ if(b)this.branch=b; }-*/;

    final native void setProject(String b) /*-{ if(b)this.project=b; }-*/;

    public final Change.Id legacyId() {
      return hasChangeNumber() ? new Change.Id(_changeNumber()) : null;
    }

    public final PatchSet.Id patchSetId() {
      return hasChangeNumber() && hasRevisionNumber()
          ? new PatchSet.Id(legacyId(), _revisionNumber())
          : null;
    }

    public final native boolean hasChangeNumber()
        /*-{ return this.hasOwnProperty('_change_number') }-*/ ;

    final native boolean hasRevisionNumber()
        /*-{ return this.hasOwnProperty('_revision_number') }-*/ ;

    final native boolean hasCurrentRevisionNumber()
        /*-{ return this.hasOwnProperty('_current_revision_number') }-*/ ;

    final native int _changeNumber() /*-{ return this._change_number }-*/;

    final native int _revisionNumber() /*-{ return this._revision_number }-*/;

    final native int _currentRevisionNumber() /*-{ return this._current_revision_number }-*/;

    final native void setChangeNumber(int n) /*-{ this._change_number=n; }-*/;

    final native void setRevisionNumber(int n) /*-{ this._revision_number=n; }-*/;

    final native void setCurrentRevisionNumber(int n) /*-{ this._current_revision_number=n; }-*/;

    final native void setSubmittable(boolean s) /*-{ this._submittable=s; }-*/;

    final native void setStatus(String s) /*-{ if(s)this.status=s; }-*/;

    protected ChangeAndCommit() {}
  }
}
