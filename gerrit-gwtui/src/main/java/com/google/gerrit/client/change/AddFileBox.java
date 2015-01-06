//Copyright (C) 2013 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.google.gerrit.client.change;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.RemoteSuggestBox;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class AddFileBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, AddFileBox> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  private final Change.Id changeId;
  private final RevisionInfo revision;

  @UiField Button open;
  @UiField Button cancel;

  @UiField(provided = true)
  RemoteSuggestBox path;

  AddFileBox(Change.Id changeId, RevisionInfo revision) {
    this.changeId = changeId;
    this.revision = revision;

    path = new RemoteSuggestBox(new PathSuggestOracle());
    path.addSelectionHandler(new SelectionHandler<String>() {
      @Override
      public void onSelection(SelectionEvent<String> event) {
        open(event.getSelectedItem());
      }
    });
    path.addCloseHandler(new CloseHandler<RemoteSuggestBox>() {
      @Override
      public void onClose(CloseEvent<RemoteSuggestBox> event) {
        hide();
      }
    });

    initWidget(uiBinder.createAndBindUi(this));
  }

  void setFocus(boolean focus) {
    path.setFocus(focus);
  }

  void clearPath() {
    path.setText("");
  }

  @UiHandler("open")
  void onOpen(@SuppressWarnings("unused") ClickEvent e) {
    open(path.getText());
  }

  private void open(String path) {
    hide();
    Gerrit.display(Dispatcher.toEditScreen(
        new PatchSet.Id(changeId, revision._number()),
        path));
  }

  @UiHandler("cancel")
  void onCancel(@SuppressWarnings("unused") ClickEvent e) {
    hide();
  }

  private void hide() {
    for (Widget w = getParent(); w != null; w = w.getParent()) {
      if (w instanceof PopupPanel) {
        ((PopupPanel) w).hide();
        break;
      }
    }
  }

  private class PathSuggestOracle extends HighlightSuggestOracle {
    @Override
    protected void onRequestSuggestions(final Request req, final Callback cb) {
      ChangeApi.revision(changeId.get(), revision.name())
        .view("files")
        .addParameter("q", req.getQuery())
        .background()
        .get(new AsyncCallback<JsArrayString>() {
            @Override
            public void onSuccess(JsArrayString result) {
              List<Suggestion> r = new ArrayList<>();
              for (String path : Natives.asList(result)) {
                r.add(new PathSuggestion(path));
              }
              cb.onSuggestionsReady(req, new Response(r));
            }

            @Override
            public void onFailure(Throwable caught) {
              List<Suggestion> none = Collections.emptyList();
              cb.onSuggestionsReady(req, new Response(none));
            }
          });
    }
  }

  private static class PathSuggestion implements Suggestion {
    private final String path;

    PathSuggestion(String path) {
      this.path = path;
    }

    @Override
    public String getDisplayString() {
      return path;
    }

    @Override
    public String getReplacementString() {
      return path;
    }
  }
}
