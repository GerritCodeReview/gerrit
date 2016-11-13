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
import com.google.gerrit.client.info.ChangeInfo.IncludedInInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

class IncludedInBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, IncludedInBox> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String includedInElement();
  }

  private final Change.Id changeId;
  private boolean loaded;

  @UiField Style style;
  @UiField TableElement table;
  @UiField Element branches;
  @UiField Element tags;

  IncludedInBox(Change.Id changeId) {
    this.changeId = changeId;
    initWidget(uiBinder.createAndBindUi(this));
  }

  @Override
  protected void onLoad() {
    if (!loaded) {
      ChangeApi.includedIn(
          changeId.get(),
          new AsyncCallback<IncludedInInfo>() {
            @Override
            public void onSuccess(IncludedInInfo r) {
              branches.setInnerSafeHtml(formatList(r.branches()));
              tags.setInnerSafeHtml(formatList(r.tags()));
              for (String n : r.externalNames()) {
                JsArrayString external = r.external(n);
                if (external.length() > 0) {
                  appendRow(n, external);
                }
              }
              loaded = true;
            }

            @Override
            public void onFailure(Throwable caught) {}
          });
    }
  }

  private SafeHtml formatList(JsArrayString l) {
    SafeHtmlBuilder html = new SafeHtmlBuilder();
    int size = l.length();
    for (int i = 0; i < size; i++) {
      html.openSpan().addStyleName(style.includedInElement()).append(l.get(i)).closeSpan();
      if (i < size - 1) {
        html.append(", ");
      }
    }
    return html;
  }

  private void appendRow(String title, JsArrayString l) {
    TableRowElement row = table.insertRow(-1);
    TableCellElement th = Document.get().createTHElement();
    th.setInnerText(title);
    row.appendChild(th);
    row.insertCell(-1).setInnerSafeHtml(formatList(l));
  }
}
