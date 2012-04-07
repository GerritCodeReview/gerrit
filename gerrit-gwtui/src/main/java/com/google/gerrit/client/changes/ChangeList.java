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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.rpc.NativeList;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.KeyUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** List of changes available from {@code /changes/}. */
public class ChangeList extends NativeList<ChangeInfo> {
  private static final String URI = "/changes/";

  public static void prev(String query,
      int limit, String sortkey,
      AsyncCallback<ChangeList> callback) {
    RestApi call = newQuery(query);
    if (limit > 0) {
      call.addParameter("n", limit);
    }
    if (!PagedSingleListScreen.MIN_SORTKEY.equals(sortkey)) {
      call.addParameter("P", sortkey);
    }
    call.send(callback);
  }

  public static void next(String query,
      int limit, String sortkey,
      AsyncCallback<ChangeList> callback) {
    RestApi call = newQuery(query);
    if (limit > 0) {
      call.addParameter("n", limit);
    }
    if (!PagedSingleListScreen.MAX_SORTKEY.equals(sortkey)) {
      call.addParameter("N", sortkey);
    }
    call.send(callback);
  }

  private static RestApi newQuery(String query) {
    // Note to reader: If you are reading this logic to emulate in your own
    // tool or application, STOP. Just pass the entire query string as ?q=...
    // and accept that things work correctly. The rest of this method is
    // about trying to make pretty examples showing other forms that
    // also work, making it clear the server has a lot of do-what-i-mean-ery
    // to help users quickly ask for changes.

    if ("status:open".equals(query) || "is:open".equals(query)) {
      // The server default is ?q=status:open so don't repeat it.
      return new RestApi(URI);
    }

    if (isDefaultField(query)) {
      return new RestApi(URI + query);
    }

    if (query.matches(".*([\"()]| OR ).*")) {
      // Breaking up this query using the below logic may produce a different
      // expression at the server. Pass the query directly in the q parameter.
      return queryWithParameter(query);
    }

    // Because the GWT UI client is the canonical example client everyone else
    // will look to for examples of queries they can perform, try to construct a
    // "nice looking" request URI by putting the most reasonable default field
    // term into the path, and any other part of the query into the q parameter.

    String[] terms = query.split(" +");
    List<Term> defaultFields = new ArrayList<Term>(terms.length);
    StringBuilder operators = new StringBuilder();

    for (String term : terms) {
      if (term.matches("refs/[a-zA-Z0-9._+/-]*")) {
        defaultFields.add(new Term(REF, term));
      } else if (term.matches("([0-9a-fA-F]{4,40})")) {
        defaultFields.add(new Term(ID, term));
      } else if (term.matches("([1-9][0-9]*|[iI][0-9a-f]{4,}.*)")) {
        defaultFields.add(new Term(ID, term));
      } else if (term.matches("[a-zA-Z0-9._+-][a-zA-Z0-9._+/-]*/[a-zA-Z0-9._+/-]*")) {
        defaultFields.add(new Term(PROJECT, term));
      } else if (isDefaultField(term)) {
        defaultFields.add(new Term(OTHER, term));
      } else {
        operators.append(term).append(" ");
      }
    }

    if (defaultFields.isEmpty()) {
      return queryWithParameter(query);
    }

    if (defaultFields.size() == 1) {
      RestApi api = new RestApi(URI + defaultFields.get(0).term);
      if (operators.length() > 0) {
        operators.setLength(operators.length() - 1);
        api.addParameterRaw("q", KeyUtil.encode(operators.toString()));
      }
      return api;
    }

    Collections.sort(defaultFields);

    if (defaultFields.get(0).priority == defaultFields.get(1).priority) {
      // When multiple terms have the same priority, don't break up
      // the query, instead pass the entire thing within q parameter.
      return queryWithParameter(query);
    }

    for (int i = 1; i < defaultFields.size(); i++) {
      operators.append(defaultFields.get(i).term).append(" ");
    }
    operators.setLength(operators.length() - 1);
    return new RestApi(URI + defaultFields.get(0).term)
        .addParameterRaw("q", KeyUtil.encode(operators.toString()));
  }

  private static boolean isDefaultField(String query) {
    return query.matches("[a-zA-Z0-9._+-][a-zA-Z0-9._+/-]*");
  }

  private static RestApi queryWithParameter(String query) {
    return new RestApi(URI).addParameterRaw("q", KeyUtil.encode(query));
  }

  private static final int PROJECT = 1;
  private static final int ID = 2;
  private static final int REF = 3;
  private static final int OTHER = 4;

  private static class Term implements Comparable<Term> {
    int priority;
    String term;

    Term(int p, String t) {
      priority = p;
      term = t;
    }

    @Override
    public int compareTo(Term o) {
      return priority - o.priority;
    }
  }

  protected ChangeList() {
  }
}
