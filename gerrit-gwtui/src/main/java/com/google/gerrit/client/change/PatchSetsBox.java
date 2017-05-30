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

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.CommitInfo;
import com.google.gerrit.client.info.ChangeInfo.EditInfo;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.ui.FancyFlexTableImpl;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.impl.HyperlinkImpl;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import java.util.Collections;
import java.util.EnumSet;

class PatchSetsBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, PatchSetsBox> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  private static final String OPEN;
  private static final HyperlinkImpl link = GWT.create(HyperlinkImpl.class);

  static {
    OPEN = DOM.createUniqueId().replace('-', '_');
    init(OPEN);
  }

  private static native void init(String o) /*-{
    $wnd[o] = $entry(function(e,i) {
      return @com.google.gerrit.client.change.PatchSetsBox::onOpen(Lcom/google/gwt/dom/client/NativeEvent;I)(e,i);
    });
  }-*/;

  private static boolean onOpen(NativeEvent e, int idx) {
    if (link.handleAsClick(e.<Event>cast())) {
      PatchSetsBox t = getRevisionBox(e);
      if (t != null) {
        t.onOpenRow(idx);
        e.preventDefault();
        return false;
      }
    }
    return true;
  }

  private static PatchSetsBox getRevisionBox(NativeEvent event) {
    Element e = event.getEventTarget().cast();
    for (e = DOM.getParent(e); e != null; e = DOM.getParent(e)) {
      EventListener l = DOM.getEventListener(e);
      if (l instanceof PatchSetsBox) {
        return (PatchSetsBox) l;
      }
    }
    return null;
  }

  interface Style extends CssResource {
    String current();

    String legacy_id();

    String commit();

    String draft_comment();
  }

  private final Change.Id changeId;
  private final Project.NameKey project;
  private final String revision;
  private final EditInfo edit;
  private boolean loaded;
  private JsArray<RevisionInfo> revisions;

  @UiField FlexTable table;
  @UiField Style style;

  PatchSetsBox(
      @Nullable Project.NameKey project, Change.Id changeId, String revision, EditInfo edit) {
    this.project = project;
    this.changeId = changeId;
    this.revision = revision;
    this.edit = edit;
    initWidget(uiBinder.createAndBindUi(this));
  }

  @Override
  protected void onLoad() {
    if (!loaded) {
      RestApi call = ChangeApi.detail(changeId.get(), Project.NameKey.asStringOrNull(project));
      ChangeList.addOptions(
          call, EnumSet.of(ListChangesOption.ALL_COMMITS, ListChangesOption.ALL_REVISIONS));
      call.get(
          new AsyncCallback<ChangeInfo>() {
            @Override
            public void onSuccess(ChangeInfo result) {
              if (edit != null) {
                edit.setName(edit.commit().commit());
                result.revisions().put(edit.name(), RevisionInfo.fromEdit(edit));
              }
              render(result.revisions());
              loaded = true;
            }

            @Override
            public void onFailure(Throwable caught) {}
          });
    }
  }

  private void onOpenRow(int idx) {
    closeParent();
    Gerrit.display(url(revisions.get(idx)));
  }

  private void render(NativeMap<RevisionInfo> map) {
    map.copyKeysIntoChildren("name");

    revisions = map.values();
    RevisionInfo.sortRevisionInfoByNumber(revisions);
    Collections.reverse(Natives.asList(revisions));

    SafeHtmlBuilder sb = new SafeHtmlBuilder();
    header(sb);
    for (int i = 0; i < revisions.length(); i++) {
      revision(sb, i, revisions.get(i));
    }

    GWT.<FancyFlexTableImpl>create(FancyFlexTableImpl.class).resetHtml(table, sb);
  }

  private void header(SafeHtmlBuilder sb) {
    sb.openTr()
        .openTh()
        .setStyleName(style.legacy_id())
        .append(Resources.C.patchSet())
        .closeTh()
        .openTh()
        .append(Resources.C.commit())
        .closeTh()
        .openTh()
        .append(Resources.C.date())
        .closeTh()
        .openTh()
        .append(Resources.C.author())
        .closeTh()
        .closeTr();
  }

  private void revision(SafeHtmlBuilder sb, int index, RevisionInfo r) {
    CommitInfo c = r.commit();
    sb.openTr();
    if (revision.equals(r.name())) {
      sb.setStyleName(style.current());
    }

    sb.openTd().setStyleName(style.legacy_id());
    if (r.draft()) {
      sb.append(Resources.C.draft()).append(' ');
    }
    sb.append(r.id());
    sb.closeTd();

    sb.openTd()
        .setStyleName(style.commit())
        .openAnchor()
        .setAttribute("href", "#" + url(r))
        .setAttribute("onclick", OPEN + "(event," + index + ")")
        .append(r.name().substring(0, 10))
        .closeAnchor()
        .closeTd();

    sb.openTd().append(FormatUtil.shortFormatDayTime(c.committer().date())).closeTd();

    String an = c.author() != null ? c.author().name() : "";
    String cn = c.committer() != null ? c.committer().name() : "";
    sb.openTd();
    sb.append(an);
    if (!"".equals(an) && !"".equals(cn) && !an.equals(cn)) {
      sb.append(" / ").append(cn);
    }
    sb.closeTd();

    sb.closeTr();
  }

  private String url(RevisionInfo r) {
    return PageLinks.toChange(project, changeId, r.id());
  }

  private void closeParent() {
    for (Widget w = getParent(); w != null; w = w.getParent()) {
      if (w instanceof PopupPanel) {
        ((PopupPanel) w).hide(true);
        break;
      }
    }
  }
}
