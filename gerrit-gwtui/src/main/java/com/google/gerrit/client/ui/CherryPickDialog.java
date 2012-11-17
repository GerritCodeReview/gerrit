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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.data.ListBranchesResult;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;

import java.util.LinkedList;
import java.util.List;

public abstract class CherryPickDialog extends ActionDialog {
  private SuggestBox newBranch;
  private List<Branch> branches;

  public CherryPickDialog(final FocusWidget enableOnFailure, Project.NameKey project) {
    super(enableOnFailure, true, Util.C.cherryPickTitle(), Util.C
        .cherryPickCommitMessage());
    com.google.gerrit.client.account.Util.PROJECT_SVC.listBranches(project,
        new GerritCallback<ListBranchesResult>() {
          @Override
          public void onSuccess(ListBranchesResult result) {
            branches = result.getBranches();
          }
        });

    newBranch = new SuggestBox(new HighlightSuggestOracle() {
      @Override
      protected void onRequestSuggestions(Request request, Callback done) {
        LinkedList<BranchSuggestion> suggestions =
            new LinkedList<BranchSuggestion>();
        for (final Branch b : branches) {
          if (b.getName().indexOf(request.getQuery()) >= 0) {
            suggestions.add(new BranchSuggestion(b));
          }
        }
        done.onSuggestionsReady(request, new Response(suggestions));
      }
    });

    newBranch.setWidth("70ex");
    message.setCharacterWidth(70);
    panel.insert(newBranch, 0);
    panel.insert(new SmallHeading(Util.C.headingCherryPickBranch()), 0);
  }

  @Override
  public void center() {
    super.center();
    GlobalKey.dialog(this);
    newBranch.setFocus(true);
  }

  public String getDestinationBranch() {
    return newBranch.getText();
  }

  class BranchSuggestion implements Suggestion {
    private Branch branch;

    public BranchSuggestion(Branch branch) {
      this.branch = branch;
    }

    @Override
    public String getDisplayString() {
      return branch.getName();
    }

    @Override
    public String getReplacementString() {
      return branch.getShortName();
    }
  }
}
