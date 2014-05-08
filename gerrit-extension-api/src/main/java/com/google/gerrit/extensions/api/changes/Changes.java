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

package com.google.gerrit.extensions.api.changes;

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public interface Changes {
  ChangeApi id(int id) throws RestApiException;
  ChangeApi id(String triplet) throws RestApiException;
  ChangeApi id(String project, String branch, String id)
      throws RestApiException;
  ChangeApi create(ChangeInfo in) throws RestApiException;

  /**
   * Shorthand for {@link #query(QueryParameter)} without any conditions (i.e. lists all changes).
   */
  List<ChangeInfo> query() throws RestApiException;
  List<ChangeInfo> query(QueryParameter queryParameter) throws RestApiException;

  public class QueryParameter {
    private String query;
    private int limit;
    private int start;
    private EnumSet<ListChangesOption> options = EnumSet.noneOf(ListChangesOption.class);

    public QueryParameter() {}

    public QueryParameter(String query) {
      this.query = query;
    }

    public QueryParameter withQuery(String query) {
      this.query = query;
      return this;
    }

    public QueryParameter withLimit(int limit) {
      this.limit = limit;
      return this;
    }

    public QueryParameter withStart(int start) {
      this.start = start;
      return this;
    }

    public QueryParameter withOption(ListChangesOption options) {
      this.options.add(options);
      return this;
    }
    public QueryParameter withOptions(ListChangesOption... options) {
      this.options.addAll(Arrays.asList(options));
      return this;
    }

    public QueryParameter withOptions(EnumSet<ListChangesOption> options) {
      this.options = options;
      return this;
    }

    public String getQuery() {
      return query;
    }

    public int getLimit() {
      return limit;
    }

    public int getStart() {
      return start;
    }

    public EnumSet<ListChangesOption> getOptions() {
      return options;
    }
  }

  /**
   * A default implementation which allows source compatibility
   * when adding new methods to the interface.
   **/
  public class NotImplemented implements Changes {
    @Override
    public ChangeApi id(int id) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi id(String triplet) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi id(String project, String branch, String id) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi create(ChangeInfo in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<ChangeInfo> query() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<ChangeInfo> query(QueryParameter queryParameter) throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
