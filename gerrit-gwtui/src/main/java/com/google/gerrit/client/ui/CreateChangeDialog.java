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
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;

import java.util.ArrayList;
import java.util.List;

public abstract class CreateChangeDialog extends TextAreaActionDialog {
  private SuggestBox newChange;
  private List<BranchInfo> branches;

  public CreateChangeDialog(Project.NameKey project) {
    super(Util.C.dialogCreateChangeTitle(),
        Util.C.dialogCreateChangeHeading());
    ProjectApi.getBranches(project,
        new GerritCallback<JsArray<BranchInfo>>() {
          @Override
          public void onSuccess(JsArray<BranchInfo> result) {
            branches = Natives.asList(result);
          }
        });

    newChange = new SuggestBox(new HighlightSuggestOracle() {
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
    message.setCharacterWidth(70);

    FlowPanel mwrap = new FlowPanel();
    mwrap.setStyleName(Gerrit.RESOURCES.css().commentedActionMessage());
    mwrap.add(newChange);

    panel.insert(mwrap, 0);
    panel.insert(new SmallHeading(Util.C.newChangeBranchSuggestion()), 0);
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

  class BranchSuggestion implements Suggestion {
    private BranchInfo branch;

    public BranchSuggestion(BranchInfo branch) {
      this.branch = branch;
    }

    @Override
    public String getDisplayString() {
      if (branch.ref().startsWith(Branch.R_HEADS)) {
        return branch.ref().substring(Branch.R_HEADS.length());
      }
      return branch.ref();
    }

    @Override
    public String getReplacementString() {
      return branch.getShortName();
    }
  }
}
