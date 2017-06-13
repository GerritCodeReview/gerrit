// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.client;

import com.google.gerrit.client.ui.AccountGroupSuggestOracle;
import com.google.gerrit.client.ui.AccountSuggestOracle;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gwt.user.client.ui.SuggestOracle;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

public class SearchSuggestOracle extends HighlightSuggestOracle {
  private static final List<ParamSuggester> paramSuggester =
      Arrays.asList(
          new ParamSuggester(
              Arrays.asList("project:", "p:", "parentproject:"), new ProjectNameSuggestOracle()),
          new ParamSuggester(
              Arrays.asList(
                  "owner:",
                  "o:",
                  "reviewer:",
                  "r:",
                  "commentby:",
                  "reviewedby:",
                  "author:",
                  "committer:",
                  "from:",
                  "assignee:",
                  "cc:"),
              new AccountSuggestOracle() {
                @Override
                public void onRequestSuggestions(Request request, Callback done) {
                  super.onRequestSuggestions(
                      request,
                      new Callback() {
                        @Override
                        public void onSuggestionsReady(final Request request, Response response) {
                          if ("self".startsWith(request.getQuery())) {
                            final ArrayList<SuggestOracle.Suggestion> r =
                                new ArrayList<>(response.getSuggestions().size() + 1);
                            r.addAll(response.getSuggestions());
                            r.add(
                                new SuggestOracle.Suggestion() {
                                  @Override
                                  public String getDisplayString() {
                                    return getReplacementString();
                                  }

                                  @Override
                                  public String getReplacementString() {
                                    return "self";
                                  }
                                });
                            response.setSuggestions(r);
                          }
                          done.onSuggestionsReady(request, response);
                        }
                      });
                }
              }),
          new ParamSuggester(
              Arrays.asList("ownerin:", "reviewerin:"), new AccountGroupSuggestOracle()));

  private static final TreeSet<String> suggestions = new TreeSet<>();

  static {
    suggestions.add("age:");
    suggestions.add("age:1week"); // Give an example age

    suggestions.add("change:");

    suggestions.add("owner:");
    suggestions.add("owner:self");
    suggestions.add("ownerin:");
    suggestions.add("author:");
    suggestions.add("committer:");

    suggestions.add("reviewer:");
    suggestions.add("reviewer:self");
    suggestions.add("reviewerin:");
    suggestions.add("reviewedby:");

    suggestions.add("commit:");
    suggestions.add("comment:");
    suggestions.add("message:");
    suggestions.add("commentby:");
    suggestions.add("from:");
    suggestions.add("file:");
    suggestions.add("conflicts:");
    suggestions.add("project:");
    suggestions.add("projects:");
    suggestions.add("parentproject:");
    suggestions.add("branch:");
    suggestions.add("topic:");
    suggestions.add("intopic:");
    suggestions.add("ref:");
    suggestions.add("tr:");
    suggestions.add("bug:");
    suggestions.add("label:");
    suggestions.add("query:");
    suggestions.add("has:");
    suggestions.add("has:draft");
    suggestions.add("has:edit");
    suggestions.add("has:star");
    suggestions.add("has:stars");
    suggestions.add("has:unresolved");
    suggestions.add("star:");

    suggestions.add("is:");
    suggestions.add("is:starred");
    suggestions.add("is:watched");
    suggestions.add("is:reviewed");
    suggestions.add("is:owner");
    suggestions.add("is:reviewer");
    suggestions.add("is:open");
    suggestions.add("is:pending");
    suggestions.add("is:draft");
    suggestions.add("is:private");
    suggestions.add("is:closed");
    suggestions.add("is:merged");
    suggestions.add("is:abandoned");
    suggestions.add("is:mergeable");
    suggestions.add("is:ignored");
    suggestions.add("is:wip");

    suggestions.add("status:");
    suggestions.add("status:open");
    suggestions.add("status:pending");
    suggestions.add("status:reviewed");
    suggestions.add("status:closed");
    suggestions.add("status:merged");
    suggestions.add("status:abandoned");
    suggestions.add("status:draft");

    suggestions.add("added:");
    suggestions.add("deleted:");
    suggestions.add("delta:");
    suggestions.add("size:");

    suggestions.add("unresolved:");

    if (Gerrit.isNoteDbEnabled()) {
      suggestions.add("cc:");
      suggestions.add("hashtag:");
    }

    suggestions.add("is:assigned");
    suggestions.add("is:unassigned");
    suggestions.add("assignee:");

    suggestions.add("AND");
    suggestions.add("OR");
    suggestions.add("NOT");
  }

  @Override
  public void requestDefaultSuggestions(Request request, Callback done) {
    final ArrayList<SearchSuggestion> r = new ArrayList<>();
    // No text - show some default suggestions.
    r.add(new SearchSuggestion("status:open", "status:open"));
    r.add(new SearchSuggestion("age:1week", "age:1week"));
    if (Gerrit.isSignedIn()) {
      r.add(new SearchSuggestion("owner:self", "owner:self"));
    }
    done.onSuggestionsReady(request, new Response(r));
  }

  @Override
  protected void onRequestSuggestions(Request request, Callback done) {
    final String query = request.getQuery();

    final String lastWord = getLastWord(query);
    if (lastWord == null) {
      // Starting a new word - don't show suggestions yet.
      done.onSuggestionsReady(request, null);
      return;
    }

    for (ParamSuggester ps : paramSuggester) {
      if (ps.applicable(lastWord)) {
        ps.suggest(lastWord, request, done);
        return;
      }
    }

    final ArrayList<SearchSuggestion> r = new ArrayList<>();
    for (String suggestion : suggestions.tailSet(lastWord)) {
      if ((lastWord.length() < suggestion.length()) && suggestion.startsWith(lastWord)) {
        if (suggestion.contains("self") && !Gerrit.isSignedIn()) {
          continue;
        }
        r.add(new SearchSuggestion(suggestion, query + suggestion.substring(lastWord.length())));
      }
    }
    done.onSuggestionsReady(request, new Response(r));
  }

  private String getLastWord(String query) {
    final int lastSpace = query.lastIndexOf(' ');
    if (lastSpace == query.length() - 1) {
      return null;
    }
    if (lastSpace == -1) {
      return query;
    }
    return query.substring(lastSpace + 1);
  }

  @Override
  protected String getQueryPattern(String query) {
    return super.getQueryPattern(getLastWord(query));
  }

  @Override
  protected boolean isHTML() {
    return true;
  }

  private static class SearchSuggestion implements SuggestOracle.Suggestion {
    private final String suggestion;
    private final String fullQuery;

    SearchSuggestion(String suggestion, String fullQuery) {
      this.suggestion = suggestion;
      // Add a space to the query if it is a complete operation (e.g.
      // "status:open") so the user can keep on typing.
      this.fullQuery = fullQuery.endsWith(":") ? fullQuery : fullQuery + " ";
    }

    @Override
    public String getDisplayString() {
      return suggestion;
    }

    @Override
    public String getReplacementString() {
      return fullQuery;
    }
  }

  private static class ParamSuggester {
    private final List<String> operators;
    private final SuggestOracle parameterSuggestionOracle;

    ParamSuggester(List<String> operators, SuggestOracle parameterSuggestionOracle) {
      this.operators = operators;
      this.parameterSuggestionOracle = parameterSuggestionOracle;
    }

    boolean applicable(String query) {
      final String operator = getApplicableOperator(query, operators);
      return operator != null && query.length() > operator.length();
    }

    private String getApplicableOperator(String lastWord, List<String> operators) {
      for (String operator : operators) {
        if (lastWord.startsWith(operator)) {
          return operator;
        }
      }
      return null;
    }

    void suggest(String lastWord, Request request, Callback done) {
      final String operator = getApplicableOperator(lastWord, operators);
      parameterSuggestionOracle.requestSuggestions(
          new Request(lastWord.substring(operator.length()), request.getLimit()),
          new Callback() {
            @Override
            public void onSuggestionsReady(Request req, Response response) {
              final String query = request.getQuery();
              final List<SearchSuggestOracle.Suggestion> r =
                  new ArrayList<>(response.getSuggestions().size());
              for (SearchSuggestOracle.Suggestion s : response.getSuggestions()) {
                r.add(
                    new SearchSuggestion(
                        s.getDisplayString(),
                        query.substring(0, query.length() - lastWord.length())
                            + operator
                            + quoteIfNeeded(s.getReplacementString())));
              }
              done.onSuggestionsReady(request, new Response(r));
            }

            private String quoteIfNeeded(String s) {
              if (!s.matches("^\\S*$")) {
                return "\"" + s + "\"";
              }
              return s;
            }
          });
    }
  }
}
