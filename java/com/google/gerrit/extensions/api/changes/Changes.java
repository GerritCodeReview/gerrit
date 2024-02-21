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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public interface Changes {
  /**
   * Look up a change by numeric ID.
   *
   * <p><strong>Note:</strong> Change number is not guaranteed to unambiguously identify a change.
   *
   * @see #id(String, int)
   * @deprecated in favor of {@link #id(String, int)}
   * @param id change number.
   * @return API for accessing the change.
   * @throws RestApiException if an error occurred.
   */
  @Deprecated(since = "3.9")
  ChangeApi id(int id) throws RestApiException;

  /**
   * Look up a change by string ID.
   *
   * @see #id(String, int)
   * @param id any identifier supported by the REST API, including change number, Change-Id, or
   *     project~branch~Change-Id triplet.
   * @return API for accessing the change.
   * @throws RestApiException if an error occurred.
   */
  ChangeApi id(String id) throws RestApiException;

  /**
   * Look up a change by project, branch, and change ID.
   *
   * @see #id(String, int)
   */
  ChangeApi id(String project, String branch, String id) throws RestApiException;

  /**
   * Look up a change by project and numeric ID.
   *
   * <p><strong>Note:</strong> This method eagerly reads the change. Methods that mutate the change
   * do not necessarily re-read the change. Therefore, calling a getter method on an instance after
   * calling a mutation method on that same instance is not guaranteed to reflect the mutation. It
   * is not recommended to store references to {@code ChangeApi} instances. Also note that the
   * change numeric id without a project name parameter may fail to identify a unique change
   * element, because the potential conflict with other changes imported from Gerrit instances with
   * a different Server-Id.
   *
   * @param project project name.
   * @param id change number.
   */
  ChangeApi id(String project, int id) throws RestApiException;

  ChangeApi create(ChangeInput in) throws RestApiException;

  ChangeInfo createAsInfo(ChangeInput in) throws RestApiException;

  QueryRequest query();

  QueryRequest query(String query);

  abstract class QueryRequest {
    private String query;
    private int limit;
    private int start;
    private boolean isNoLimit;
    private boolean allowIncompleteResults;
    private Set<ListChangesOption> options = EnumSet.noneOf(ListChangesOption.class);
    private ListMultimap<String, String> pluginOptions = ArrayListMultimap.create();

    public abstract List<ChangeInfo> get() throws RestApiException;

    public QueryRequest withQuery(String query) {
      this.query = query;
      return this;
    }

    public QueryRequest withLimit(int limit) {
      this.limit = limit;
      return this;
    }

    public QueryRequest withNoLimit() {
      this.isNoLimit = true;
      return this;
    }

    public QueryRequest withStart(int start) {
      this.start = start;
      return this;
    }

    @UsedAt(UsedAt.Project.GOOGLE)
    public QueryRequest withAllowIncompleteResults(boolean allow) {
      this.allowIncompleteResults = allow;
      return this;
    }

    /** Set an option on the request, appending to existing options. */
    public QueryRequest withOption(ListChangesOption options) {
      this.options.add(options);
      return this;
    }

    /** Set options on the request, appending to existing options. */
    public QueryRequest withOptions(ListChangesOption... options) {
      this.options.addAll(Arrays.asList(options));
      return this;
    }

    /** Set options on the request, replacing existing options. */
    public QueryRequest withOptions(Set<ListChangesOption> options) {
      this.options = options;
      return this;
    }

    /** Set a plugin option on the request, appending to existing options. */
    public QueryRequest withPluginOption(String name, String value) {
      this.pluginOptions.put(name, value);
      return this;
    }

    /** Set a plugin option on the request, replacing existing options. */
    public QueryRequest withPluginOptions(ListMultimap<String, String> options) {
      this.pluginOptions = ArrayListMultimap.create(options);
      return this;
    }

    public String getQuery() {
      return query;
    }

    public int getLimit() {
      return limit;
    }

    public boolean getNoLimit() {
      return isNoLimit;
    }

    public int getStart() {
      return start;
    }

    @UsedAt(UsedAt.Project.GOOGLE)
    public boolean getAllowIncompleteResults() {
      return allowIncompleteResults;
    }

    public Set<ListChangesOption> getOptions() {
      return options;
    }

    public ListMultimap<String, String> getPluginOptions() {
      return pluginOptions;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('{').append(query);
      if (limit != 0) {
        sb.append(", limit=").append(limit);
      }
      if (start != 0) {
        sb.append(", start=").append(start);
      }
      if (!options.isEmpty()) {
        sb.append("options=").append(options);
      }
      sb.append('}');
      if (isNoLimit == true) {
        sb.append(" --no-limit");
      }
      return sb.toString();
    }
  }

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements Changes {
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
    public ChangeApi id(String project, int id) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi create(ChangeInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo createAsInfo(ChangeInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public QueryRequest query() {
      throw new NotImplementedException();
    }

    @Override
    public QueryRequest query(String query) {
      throw new NotImplementedException();
    }
  }
}
