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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.diff.FileInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.ui.FancyFlexTableImpl;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtorm.client.KeyUtil;

class FileTable extends Composite {
  private static final FileTableResources R = GWT
      .create(FileTableResources.class);

  interface FileTableResources extends ClientBundle {
    @Source("file_table.css")
    FileTableCss css();
  }

  interface FileTableCss extends CssResource {
    String deltaColumn1();
    String deltaColumn2();
    String inserted();
    String deleted();
  }

  private final InnerTable table;
  private PatchSet.Id base;
  private PatchSet.Id curr;

  private int inserted;
  private int deleted;

  FileTable () {
    initWidget(table = new InnerTable());
  }

  @Override
  protected void onLoad() {
    R.css().ensureInjected();
  }

  void setRevisions(PatchSet.Id base, PatchSet.Id curr) {
    this.base = base;
    this.curr = curr;
  }

  void setValue(NativeMap<FileInfo> fileMap) {
    JsArray<FileInfo> files = fileMap.values();
    for (int i = 0; i < files.length(); i++) {
      FileInfo info = files.get(i);
      if (!Patch.COMMIT_MSG.equals(info.path()) && !info.binary()) {
        inserted += info.lines_inserted();
        deleted += info.lines_deleted();
      }
    }

    SafeHtmlBuilder sb = new SafeHtmlBuilder();
    header(sb);
    for (int i = 0; i < files.length(); i++) {
      render(sb, files.get(i));
    }
    footer(sb);
    FancyFlexTableImpl.get().resetHtml(table, sb);
  }

  private void header(SafeHtmlBuilder sb) {
    sb.openTr();
    sb.openTh().append(Util.C.patchTableColumnName()).closeTh();
    sb.openTh()
      .setAttribute("colspan", 2)
      .append(Util.C.patchTableColumnSize())
      .closeTh();
    sb.closeTr();
  }

  private void render(SafeHtmlBuilder sb, FileInfo info) {
    sb.openTr();
    columnPath(sb, info);
    columnDelta1(sb, info);
    columnDelta2(sb, info);
    sb.closeTr();
  }

  private void columnPath(SafeHtmlBuilder sb, FileInfo info) {
    // TODO(sop): Use JS to link, avoiding early URL update.
    sb.openTd()
      .openAnchor()
      .setAttribute("href", url(info))
      .append(Patch.COMMIT_MSG.equals(info.path())
          ? Util.C.commitMessage()
          : info.path())
      .closeAnchor()
      .closeTd();
  }

  private String url(FileInfo info) {
    // TODO(sop): Switch to Dispatcher.toPatchSideBySide.
    Change.Id c = curr.getParentKey();
    StringBuilder p = new StringBuilder();
    p.append("#/c/").append(c).append('/');
    if (base != null) {
      p.append(base.get()).append("..");
    }
    p.append(curr.get()).append('/').append(KeyUtil.encode(info.path()));
    p.append(info.binary() ? ",unified" : ",codemirror");
    return p.toString();
  }

  private void columnDelta1(SafeHtmlBuilder sb, FileInfo info) {
    sb.openTd().setStyleName(R.css().deltaColumn1());
    if (!Patch.COMMIT_MSG.equals(info.path()) && !info.binary()) {
      sb.append(info.lines_inserted() - info.lines_deleted());
    }
    sb.closeTd();
  }

  private void columnDelta2(SafeHtmlBuilder sb, FileInfo info) {
    sb.openTd().setStyleName(R.css().deltaColumn2());
    if (!Patch.COMMIT_MSG.equals(info.path()) && !info.binary()
        && (info.lines_inserted() != 0 || info.lines_deleted() != 0)) {
      int w = 80;
      int t = inserted + deleted;
      int i = Math.max(5, (int) (((double) w) * info.lines_inserted() / t));
      int d = Math.max(5, (int) (((double) w) * info.lines_deleted() / t));

      sb.setAttribute(
          "title",
          Util.M.patchTableSize_LongModify(info.lines_inserted(),
              info.lines_deleted()));

      if (0 < info.lines_inserted()) {
        sb.openDiv()
          .setStyleName(R.css().inserted())
          .setAttribute("style", "width:" + i + "px")
          .closeDiv();
      }
      if (0 < info.lines_deleted()) {
        sb.openDiv()
          .setStyleName(R.css().deleted())
          .setAttribute("style", "width:" + d + "px")
          .closeDiv();
      }
    }
    sb.closeTd();
  }

  private void footer(SafeHtmlBuilder sb) {
    sb.openTr();
    sb.openTd().closeTd(); // path

    // delta1
    sb.openTh().setStyleName(R.css().deltaColumn1())
      .append(Util.M.patchTableSize_Modify(inserted, deleted))
      .closeTh();

    // delta2
    sb.openTh().setStyleName(R.css().deltaColumn2());
    int w = 80;
    int t = inserted + deleted;
    int i = Math.max(1, (int) (((double) w) * inserted / t));
    int d = Math.max(1, (int) (((double) w) * deleted / t));
    if (i + d > w && i > d) {
      i = w - d;
    } else if (i + d > w && d > i) {
      d = w - i;
    }
    if (0 < inserted) {
      sb.openDiv()
      .setStyleName(R.css().inserted())
      .setAttribute("style", "width:" + i + "px")
      .closeDiv();
    }
    if (0 < deleted) {
      sb.openDiv()
        .setStyleName(R.css().deleted())
        .setAttribute("style", "width:" + d + "px")
        .closeDiv();
    }
    sb.closeTh();

    sb.closeTr();
  }

  private static class InnerTable extends FlexTable {
    InnerTable() {
      sinkEvents(Event.ONCLICK);
    }

    @Override
    public void onBrowserEvent(Event event) {
      if (DOM.eventGetType(event) == Event.ONCLICK) {
        Element e = DOM.eventGetTarget(event);
        if ("a".equalsIgnoreCase(DOM.getElementProperty(e, "tagName"))) {
          String href = AnchorElement.as(e).getHref();
          String url = GWT.getHostPageBaseURL();
          int hash = href != null ? href.indexOf('#') : -1;
          if (href.startsWith(url) && 0 < hash) {
            Gerrit.display(href.substring(hash + 1));
            event.preventDefault();
            event.stopPropagation();
            return;
          }
        }
      }
      super.onBrowserEvent(event);
    }
  }
}
