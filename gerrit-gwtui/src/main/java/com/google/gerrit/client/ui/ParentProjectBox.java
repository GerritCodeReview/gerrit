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

import com.google.gerrit.client.projects.ProjectApi;
import com.google.gerrit.client.projects.ProjectInfo;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import java.util.HashSet;
import java.util.Set;

public class ParentProjectBox extends Composite {
  private final RemoteSuggestBox suggestBox;
  private final ParentProjectNameSuggestOracle suggestOracle;

  public ParentProjectBox() {
    suggestOracle = new ParentProjectNameSuggestOracle();
    suggestBox = new RemoteSuggestBox(suggestOracle);
    initWidget(suggestBox);
  }

  public void setVisibleLength(int len) {
    suggestBox.setVisibleLength(len);
  }

  public void setProject(final Project.NameKey project) {
    suggestOracle.setProject(project);
  }

  public void setParentProject(final Project.NameKey parent) {
    suggestBox.setText(parent != null ? parent.get() : "");
  }

  public Project.NameKey getParentProjectName() {
    final String projectName = suggestBox.getText().trim();
    if (projectName.isEmpty()) {
      return null;
    }
    return new Project.NameKey(projectName);
  }

  private static class ParentProjectNameSuggestOracle extends ProjectNameSuggestOracle {
    private Set<String> exclude = new HashSet<>();

    public void setProject(Project.NameKey project) {
      exclude.clear();
      exclude.add(project.get());
      ProjectApi.getChildren(
          project,
          true,
          new AsyncCallback<JsArray<ProjectInfo>>() {
            @Override
            public void onSuccess(JsArray<ProjectInfo> result) {
              for (ProjectInfo p : Natives.asList(result)) {
                exclude.add(p.name());
              }
            }

            @Override
            public void onFailure(Throwable caught) {}
          });
    }

    @Override
    public void _onRequestSuggestions(Request req, final Callback callback) {
      super._onRequestSuggestions(
          req,
          new Callback() {
            @Override
            public void onSuggestionsReady(Request request, Response response) {
              if (exclude.size() > 0) {
                Set<Suggestion> filteredSuggestions = new HashSet<>(response.getSuggestions());
                for (Suggestion s : response.getSuggestions()) {
                  if (exclude.contains(s.getReplacementString())) {
                    filteredSuggestions.remove(s);
                  }
                }
                response.setSuggestions(filteredSuggestions);
              }
              callback.onSuggestionsReady(request, response);
            }
          });
    }
  }
}
