// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gwtexpui.safehtml.client;

import com.google.gwt.user.client.ui.SuggestOracle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A suggestion oracle that tries to highlight the matched text.
 *
 * <p>Suggestions supplied by the implementation of {@link #onRequestSuggestions(Request, Callback)}
 * are modified to wrap all occurrences of the {@link
 * com.google.gwt.user.client.ui.SuggestOracle.Request#getQuery()} substring in HTML {@code
 * &lt;strong&gt;} tags, so they can be emphasized to the user.
 */
public abstract class HighlightSuggestOracle extends SuggestOracle {
  private static String escape(String ds) {
    return new SafeHtmlBuilder().append(ds).asString();
  }

  @Override
  public final boolean isDisplayStringHTML() {
    return true;
  }

  @Override
  public final void requestSuggestions(Request request, Callback cb) {
    onRequestSuggestions(
        request,
        new Callback() {
          @Override
          public void onSuggestionsReady(Request request, Response response) {
            final String qpat = getQueryPattern(request.getQuery());
            final boolean html = isHTML();
            final ArrayList<Suggestion> r = new ArrayList<>();
            for (Suggestion s : response.getSuggestions()) {
              r.add(new BoldSuggestion(qpat, s, html));
            }
            cb.onSuggestionsReady(request, new Response(r));
          }
        });
  }

  protected String getQueryPattern(String query) {
    return query;
  }

  /**
   * @return true if {@link
   *     com.google.gwt.user.client.ui.SuggestOracle.Suggestion#getDisplayString()} returns HTML;
   *     false if the text must be escaped before evaluating in an HTML like context.
   */
  protected boolean isHTML() {
    return false;
  }

  /** Compute the suggestions and return them for display. */
  protected abstract void onRequestSuggestions(Request request, Callback done);

  private static class BoldSuggestion implements Suggestion {
    private final Suggestion suggestion;
    private final String displayString;

    BoldSuggestion(String qstr, Suggestion s, boolean html) {
      suggestion = s;

      String ds = s.getDisplayString();
      if (!html) {
        ds = escape(ds);
      }

      if (qstr != null && !qstr.isEmpty()) {
        StringBuilder pattern = new StringBuilder();
        for (String qterm : splitQuery(qstr)) {
          qterm = escape(qterm);
          // We now surround qstr by <strong>. But the chosen approach is not too
          // smooth, if qstr is small (e.g.: "t") and this small qstr may occur in
          // escapes (e.g.: "Tim &lt;email@example.org&gt;"). Those escapes will
          // get <strong>-ed as well (e.g.: "&lt;" -> "&<strong>l</strong>t;"). But
          // as repairing those mangled escapes is easier than not mangling them in
          // the first place, we repair them afterwards.
          if (pattern.length() > 0) {
            pattern.append("|");
          }
          pattern.append(qterm);
        }

        ds = sgi(ds, "(" + pattern.toString() + ")", "<strong>$1</strong>");

        // Repairing <strong>-ed escapes.
        ds = sgi(ds, "(&[a-z]*)<strong>([a-z]*)</strong>([a-z]*;)", "$1$2$3");
      }

      displayString = ds;
    }

    /**
     * Split the query by whitespace and filter out query terms which are substrings of other query
     * terms.
     */
    private static List<String> splitQuery(String query) {
      List<String> queryTerms = Arrays.asList(query.split("\\s+"));
      Collections.sort(
          queryTerms,
          new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
              return Integer.compare(s2.length(), s1.length());
            }
          });

      List<String> result = new ArrayList<>();
      for (String s : queryTerms) {
        boolean add = true;
        for (String queryTerm : result) {
          if (queryTerm.toLowerCase().contains(s.toLowerCase())) {
            add = false;
            break;
          }
        }
        if (add) {
          result.add(s);
        }
      }
      return result;
    }

    private static native String sgi(String inString, String pat, String newHtml)
        /*-{ return inString.replace(RegExp(pat, 'gi'), newHtml); }-*/ ;

    @Override
    public String getDisplayString() {
      return displayString;
    }

    @Override
    public String getReplacementString() {
      return suggestion.getReplacementString();
    }
  }
}
