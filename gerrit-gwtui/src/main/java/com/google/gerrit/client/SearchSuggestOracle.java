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
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

public class SearchSuggestOracle extends HighlightSuggestOracle {
  private static final List<ParamSuggester> paramSuggester = Arrays.asList(
      new ParamSuggester("project:", new ProjectNameSuggestOracle()),
      new ParamSuggester(Arrays.asList("owner:", "reviewer:"),
          new AccountSuggestOracle() {
            @Override
            public void onRequestSuggestions(final Request request, final Callback done) {
              super.onRequestSuggestions(request, new Callback() {
                @Override
                public void onSuggestionsReady(final Request request,
                    final Response response) {
                  if ("self".startsWith(request.getQuery())) {
                    final ArrayList<SuggestOracle.Suggestion> r =
                        new ArrayList<SuggestOracle.Suggestion>(response
                            .getSuggestions().size() + 1);
                    r.addAll(response.getSuggestions());
                    r.add(new SuggestOracle.Suggestion() {
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
      new ParamSuggester(Arrays.asList("ownerin:", "reviewerin:"),
          new AccountGroupSuggestOracle()));

  private static final TreeSet<String> suggestions = new TreeSet<String>();

  static {
    suggestions.add("age:");
    suggestions.add("age:1week"); // Give an example age

    suggestions.add("change:");

    suggestions.add("owner:");
    suggestions.add("owner:self");
    suggestions.add("ownerin:");

    suggestions.add("reviewer:");
    suggestions.add("reviewer:self");
    suggestions.add("reviewerin:");

    suggestions.add("commit:");
    suggestions.add("project:");
    suggestions.add("branch:");
    suggestions.add("topic:");
    suggestions.add("ref:");
    suggestions.add("tr:");
    suggestions.add("bug:");
    suggestions.add("label:");
    suggestions.add("message:");
    suggestions.add("file:");

    suggestions.add("has:");
    suggestions.add("has:draft");
    suggestions.add("has:star");

    suggestions.add("inref:");

    suggestions.add("is:");
    suggestions.add("is:starred");
    suggestions.add("is:watched");
    suggestions.add("is:reviewed");
    suggestions.add("is:owner");
    suggestions.add("is:reviewer");
    suggestions.add("is:open");
    suggestions.add("is:draft");
    suggestions.add("is:closed");
    suggestions.add("is:submitted");
    suggestions.add("is:merged");
    suggestions.add("is:abandoned");

    suggestions.add("status:");
    suggestions.add("status:open");
    suggestions.add("status:reviewed");
    suggestions.add("status:submitted");
    suggestions.add("status:closed");
    suggestions.add("status:merged");
    suggestions.add("status:abandoned");

    suggestions.add("AND");
    suggestions.add("OR");
    suggestions.add("NOT");
  }

  @Override
  public void requestDefaultSuggestions(Request request, Callback done) {
    final ArrayList<SearchSuggestion> r = new ArrayList<SearchSuggestOracle.SearchSuggestion>();
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

    for (final ParamSuggester ps : paramSuggester) {
      if (ps.applicable(lastWord)) {
        ps.suggest(lastWord, request, done);
        return;
      }
    }

    final ArrayList<SearchSuggestion> r = new ArrayList<SearchSuggestOracle.SearchSuggestion>();
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

  private String getLastWord(final String query) {
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
  protected String getQueryPattern(final String query) {
    return super.getQueryPattern(getLastWord(query));
  }

  @Override
  protected boolean isHTML() {
    return true;
  }

  private static class SearchSuggestion implements SuggestOracle.Suggestion {
    private final String suggestion;
    private final String fullQuery;
    public SearchSuggestion(String suggestion, String fullQuery) {
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

    ParamSuggester(final String operator,
        final SuggestOracle parameterSuggestionOracle) {
      this(Collections.singletonList(operator), parameterSuggestionOracle);
    }

    ParamSuggester(final List<String> operators,
        final SuggestOracle parameterSuggestionOracle) {
      this.operators = operators;
      this.parameterSuggestionOracle = parameterSuggestionOracle;
    }

    boolean applicable(final String query) {
      final String operator = getApplicableOperator(query, operators);
      return operator != null && query.length() > operator.length();
    }

    private String getApplicableOperator(final String lastWord,
        final List<String> operators) {
      for (final String operator : operators) {
        if (lastWord.startsWith(operator)) {
          return operator;
        }
      }
      return null;
    }

    void suggest(final String lastWord, final Request request, final Callback done) {
      final String operator = getApplicableOperator(lastWord, operators);
      parameterSuggestionOracle.requestSuggestions(
          new Request(lastWord.substring(operator.length()), request.getLimit()),
          new Callback() {
            @Override
            public void onSuggestionsReady(final Request req,
                final Response response) {
              final String query = request.getQuery();
              final List<SearchSuggestOracle.Suggestion> r =
                  new ArrayList<SuggestOracle.Suggestion>(response
                      .getSuggestions().size());
              for (final SearchSuggestOracle.Suggestion s : response
                  .getSuggestions()) {
                r.add(new SearchSuggestion(s.getDisplayString(),
                    query.substring(0, query.length() - lastWord.length()) +
                    operator + quoteIfNeeded(s.getReplacementString())));
              }
              done.onSuggestionsReady(request, new Response(r));
            }

            private String quoteIfNeeded(final String s) {
              if (!s.matches("^\\S*$")) {
                return "\"" + s + "\"";
              }
              return s;
            }
          });
    }
  }
}
