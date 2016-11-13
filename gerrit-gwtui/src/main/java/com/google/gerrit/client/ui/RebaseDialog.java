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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class RebaseDialog extends CommentedActionDialog {
  private final SuggestBox base;
  private final CheckBox changeParent;
  private List<ChangeInfo> candidateChanges;
  private final boolean sendEnabled;

  public RebaseDialog(
      final String project,
      final String branch,
      final Change.Id changeId,
      final boolean sendEnabled) {
    super(Util.C.rebaseTitle(), null);
    this.sendEnabled = sendEnabled;
    sendButton.setText(Util.C.buttonRebaseChangeSend());

    // Create the suggestion box to filter over a list of recent changes
    // open on the same branch. The list of candidates is primed by the
    // changeParent CheckBox (below) getting enabled by the user.
    base =
        new SuggestBox(
            new HighlightSuggestOracle() {
              @Override
              protected void onRequestSuggestions(Request request, Callback done) {
                String query = request.getQuery().toLowerCase();
                List<ChangeSuggestion> suggestions = new ArrayList<>();
                for (ChangeInfo ci : candidateChanges) {
                  if (changeId.equals(ci.legacyId())) {
                    continue; // do not suggest current change
                  }
                  String id = String.valueOf(ci.legacyId().get());
                  if (id.contains(query) || ci.subject().toLowerCase().contains(query)) {
                    suggestions.add(new ChangeSuggestion(ci));
                    if (suggestions.size() >= 50) { // limit to 50 suggestions
                      break;
                    }
                  }
                }
                done.onSuggestionsReady(request, new Response(suggestions));
              }
            });
    base.getElement().setAttribute("placeholder", Util.C.rebasePlaceholderMessage());
    base.setStyleName(Gerrit.RESOURCES.css().rebaseSuggestBox());

    // The changeParent checkbox must be clicked to load into browser memory
    // a list of open changes from the same project and same branch that this
    // change may rebase onto.
    changeParent = new CheckBox(Util.C.rebaseConfirmMessage());
    changeParent.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            if (changeParent.getValue()) {
              ChangeList.query(
                  PageLinks.projectQuery(new Project.NameKey(project))
                      + " "
                      + PageLinks.op("branch", branch)
                      + " is:open -age:90d",
                  Collections.<ListChangesOption>emptySet(),
                  new GerritCallback<ChangeList>() {
                    @Override
                    public void onSuccess(ChangeList result) {
                      candidateChanges = Natives.asList(result);
                      updateControls(true);
                    }

                    @Override
                    public void onFailure(Throwable err) {
                      updateControls(false);
                      changeParent.setValue(false);
                      super.onFailure(err);
                    }
                  });
            } else {
              updateControls(false);
            }
          }
        });

    // add the checkbox and suggestbox widgets to the content panel
    contentPanel.add(changeParent);
    contentPanel.add(base);
    contentPanel.setStyleName(Gerrit.RESOURCES.css().rebaseContentPanel());
  }

  @Override
  public void center() {
    super.center();
    GlobalKey.dialog(this);
    updateControls(false);
  }

  private void updateControls(boolean changeParentEnabled) {
    if (changeParentEnabled) {
      sendButton.setTitle(null);
      sendButton.setEnabled(true);
      base.setEnabled(true);
      base.setFocus(true);
    } else {
      base.setEnabled(false);
      sendButton.setEnabled(sendEnabled);
      if (sendEnabled) {
        sendButton.setTitle(null);
        sendButton.setFocus(true);
      } else {
        sendButton.setTitle(Util.C.rebaseNotPossibleMessage());
        cancelButton.setFocus(true);
      }
    }
  }

  public String getBase() {
    return changeParent.getValue() ? base.getText() : null;
  }

  private static class ChangeSuggestion implements Suggestion {
    private ChangeInfo change;

    ChangeSuggestion(ChangeInfo change) {
      this.change = change;
    }

    @Override
    public String getDisplayString() {
      return String.valueOf(change.legacyId().get()) + ": " + change.subject();
    }

    @Override
    public String getReplacementString() {
      return String.valueOf(change.legacyId().get());
    }
  }
}
