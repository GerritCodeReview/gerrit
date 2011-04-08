// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.client.RpcStatus;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeLabel;
import com.google.gwt.user.client.ui.SuggestOracle;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;

import java.util.ArrayList;
import java.util.List;

/** Suggestion Oracle for arbitrary labels. */
public class LabelSuggestOracle extends HighlightSuggestOracle {
  private final Change.Id changeId;
  private final List<ChangeLabel.LabelKey> labelsToExclude;

  public LabelSuggestOracle(final Change.Id changeId,
      final List<ChangeLabel.LabelKey> labelsToExclude) {
    this.changeId = changeId;
    this.labelsToExclude = labelsToExclude;
  }

  @Override
  protected void onRequestSuggestions(final Request req, final Callback callback) {
    RpcStatus.hide(new Runnable() {
      public void run() {
        SuggestUtil.SVC.suggestLabel(changeId,
            new GerritCallback<List<ChangeLabel.LabelKey>>() {
              public void onSuccess(final List<ChangeLabel.LabelKey> result) {
                final List<LabelSuggestion> r =
                    new ArrayList<LabelSuggestion>(result.size());
                for (ChangeLabel.LabelKey p : result) {
                  if (!labelsToExclude.contains(p)) {
                    r.add(new LabelSuggestion(p.get()));
                  }
                }
                callback.onSuggestionsReady(req, new Response(r));
              }
            });
      }
    });
  }

  public void updateLabelsToExclude(final ChangeLabel.LabelKey label) {
    if (labelsToExclude != null) {
      int index = labelsToExclude.indexOf(label);
      if (index != -1) {
        labelsToExclude.remove(index);
      }
    }
  }

  private static class LabelSuggestion implements SuggestOracle.Suggestion {
    private final String label;

    LabelSuggestion(final String l) {
      label = l;
    }

    public String getDisplayString() {
      return label;
    }

    public String getReplacementString() {
      return label;
    }
  }
}
