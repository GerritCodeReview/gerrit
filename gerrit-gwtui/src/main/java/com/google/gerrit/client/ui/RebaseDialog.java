// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;

import java.util.LinkedList;
import java.util.List;

public abstract class RebaseDialog extends CommentedActionDialog {
  private final SuggestBox base;
  private final CheckBox cb;
  private List<ChangeInfo> changes;

  public RebaseDialog(final String project, final String branch) {
    super(Util.C.rebaseTitle(), null);
    sendButton.setText(Util.C.buttonRebaseChangeSend());

    // create the suggestion box
    base = new SuggestBox(
      new HighlightSuggestOracle() {
        @Override
        protected void onRequestSuggestions(Request request, Callback done) {
          LinkedList<ChangeSuggestion> suggestions = new LinkedList<>();
          for (final ChangeInfo ci : changes) {
            String id = String.valueOf(ci.legacy_id());
            if (id.contains(request.getQuery())) {
              suggestions.add(new ChangeSuggestion(ci));
            }
          }
          done.onSuggestionsReady(request, new Response(suggestions));
        }
      });

    // create the checkbox which must be clicked before the change list is populated
    cb = new CheckBox(Util.C.rebaseConfirmMessage());
    cb.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        boolean checked = ((CheckBox) event.getSource()).getValue();
        if (checked) {
          // suggest the most recent 50 changes
          ChangeList.next("project:" + project + " AND branch:" + branch
              + " AND is:open NOT age:90d", 0, 50,
              new GerritCallback<ChangeList>() {
              @Override
              public void onSuccess(ChangeList result) {
                changes = Natives.asList(result);
                base.setVisible(true);
              }
            });
        } else {
          base.setVisible(false);
        }
      }
    });

    // replace message with the check box
    for (Widget widget : panel) {
      if (widget instanceof FlowPanel) {
        FlowPanel mwrap = (FlowPanel) widget;
        if (mwrap.remove(message)) {
          mwrap.setWidth("300px");
          mwrap.add(cb);
          mwrap.add(base);
          base.setWidth("100%");
          base.getElement().getStyle().setProperty("boxSizing", "border-box");
          base.getElement().setAttribute("placeholder", Util.C.rebasePlaceholderMessage());
          base.setVisible(false);
          break;
        }
      }
    }

  }

  public String getBase() {
    return cb.getValue() ? base.getText() : null;
  }

  private static class ChangeSuggestion implements Suggestion {
    private ChangeInfo change;

    public ChangeSuggestion(ChangeInfo change) {
      this.change = change;
    }

    @Override
    public String getDisplayString() {
      return String.valueOf(change.legacy_id().get());
    }

    @Override
    public String getReplacementString() {
      return getDisplayString();
    }
  }
}
