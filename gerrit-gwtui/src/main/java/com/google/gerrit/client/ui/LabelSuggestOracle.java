// Copyright (C) 2011 The Android Open Source Project
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
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;

import java.util.HashSet;
import java.util.Set;

/** Suggestion Oracle for change labels. */
public class LabelSuggestOracle extends HighlightSuggestOracle {
  private final Change.Id changeId;
  private final Set<ChangeLabel.LabelKey> labelsToExclude;

  public LabelSuggestOracle(final Change.Id changeId,
      final Set<ChangeLabel.LabelKey> labelsToExclude) {
    this.changeId = changeId;
    this.labelsToExclude = labelsToExclude;
  }

  @Override
  protected void onRequestSuggestions(final Request req, final Callback callback) {
    RpcStatus.hide(new Runnable() {
      public void run() {
        SuggestUtil.SVC.suggestLabel(changeId,
            new GerritCallback<Set<ChangeLabel.LabelKey>>() {
              public void onSuccess(final Set<ChangeLabel.LabelKey> result) {
                result.removeAll(labelsToExclude);

                final Set<StringSuggestion> r =
                    new HashSet<StringSuggestion>(result.size());
                for (ChangeLabel.LabelKey l : result) {
                  r.add(new StringSuggestion(l.get()));
                }
                callback.onSuggestionsReady(req, new Response(r));
              }
            });
      }
    });
  }

  public void removeFromExclude(final ChangeLabel.LabelKey label) {
    if (labelsToExclude != null) {
      labelsToExclude.remove(label);
    }
  }
}