// Copyright (C) 2014 The Android Open Source Project
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
import com.google.gerrit.client.projects.BranchInfo;
import com.google.gerrit.client.projects.ProjectApi;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;
import java.util.ArrayList;
import java.util.List;

public abstract class CreateChangeDialog extends TextAreaActionDialog {
  private SuggestBox newChange;
  private List<BranchInfo> branches;
  private TextBox topic;

  public CreateChangeDialog(Project.NameKey project) {
    super(Util.C.dialogCreateChangeTitle(), Util.C.dialogCreateChangeHeading());
    ProjectApi.getBranches(
        project,
        new GerritCallback<JsArray<BranchInfo>>() {
          @Override
          public void onSuccess(JsArray<BranchInfo> result) {
            branches = Natives.asList(result);
          }
        });

    topic = new TextBox();
    topic.setWidth("100%");
    topic.getElement().getStyle().setProperty("boxSizing", "border-box");
    FlowPanel newTopicPanel = new FlowPanel();
    newTopicPanel.setStyleName(Gerrit.RESOURCES.css().commentedActionMessage());
    newTopicPanel.add(topic);
    panel.insert(newTopicPanel, 0);
    panel.insert(new SmallHeading(Util.C.newChangeTopicSuggestion()), 0);

    newChange =
        new SuggestBox(
            new HighlightSuggestOracle() {
              @Override
              protected void onRequestSuggestions(Request request, Callback done) {
                List<BranchSuggestion> suggestions = new ArrayList<>();
                for (BranchInfo b : branches) {
                  if (b.ref().contains(request.getQuery())) {
                    suggestions.add(new BranchSuggestion(b));
                  }
                }
                done.onSuggestionsReady(request, new Response(suggestions));
              }
            });

    newChange.setWidth("100%");
    newChange.getElement().getStyle().setProperty("boxSizing", "border-box");
    FlowPanel newChangePanel = new FlowPanel();
    newChangePanel.setStyleName(Gerrit.RESOURCES.css().commentedActionMessage());
    newChangePanel.add(newChange);
    panel.insert(newChangePanel, 0);
    panel.insert(new SmallHeading(Util.C.newChangeBranchSuggestion()), 0);

    message.setCharacterWidth(70);
  }

  @Override
  public void center() {
    super.center();
    GlobalKey.dialog(this);
    newChange.setFocus(true);
  }

  public String getDestinationBranch() {
    return newChange.getText();
  }

  public String getDestinationTopic() {
    return topic.getText();
  }

  static class BranchSuggestion implements Suggestion {
    private BranchInfo branch;

    BranchSuggestion(BranchInfo branch) {
      this.branch = branch;
    }

    @Override
    public String getDisplayString() {
      return branch.getShortName();
    }

    @Override
    public String getReplacementString() {
      return branch.getShortName();
    }
  }
}
