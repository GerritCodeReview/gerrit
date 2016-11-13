// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.extensions.api.groups;

import com.google.gerrit.extensions.client.ListGroupsOption;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public interface Groups {
  /**
   * Look up a group by ID.
   *
   * <p><strong>Note:</strong> This method eagerly reads the group. Methods that mutate the group do
   * not necessarily re-read the group. Therefore, calling a getter method on an instance after
   * calling a mutation method on that same instance is not guaranteed to reflect the mutation. It
   * is not recommended to store references to {@code groupApi} instances.
   *
   * @param id any identifier supported by the REST API, including group name or UUID.
   * @return API for accessing the group.
   * @throws RestApiException if an error occurred.
   */
  GroupApi id(String id) throws RestApiException;

  /** Create a new group with the given name and default options. */
  GroupApi create(String name) throws RestApiException;

  /** Create a new group. */
  GroupApi create(GroupInput input) throws RestApiException;

  /** @return new request for listing groups. */
  ListRequest list();

  abstract class ListRequest {
    private final EnumSet<ListGroupsOption> options = EnumSet.noneOf(ListGroupsOption.class);
    private final List<String> projects = new ArrayList<>();
    private final List<String> groups = new ArrayList<>();

    private boolean visibleToAll;
    private String user;
    private boolean owned;
    private int limit;
    private int start;
    private String substring;
    private String suggest;

    public List<GroupInfo> get() throws RestApiException {
      Map<String, GroupInfo> map = getAsMap();
      List<GroupInfo> result = new ArrayList<>(map.size());
      for (Map.Entry<String, GroupInfo> e : map.entrySet()) {
        // ListGroups "helpfully" nulls out names when converting to a map.
        e.getValue().name = e.getKey();
        result.add(e.getValue());
      }
      return Collections.unmodifiableList(result);
    }

    public abstract Map<String, GroupInfo> getAsMap() throws RestApiException;

    public ListRequest addOption(ListGroupsOption option) {
      options.add(option);
      return this;
    }

    public ListRequest addOptions(ListGroupsOption... options) {
      return addOptions(Arrays.asList(options));
    }

    public ListRequest addOptions(Iterable<ListGroupsOption> options) {
      for (ListGroupsOption option : options) {
        this.options.add(option);
      }
      return this;
    }

    public ListRequest withProject(String project) {
      projects.add(project);
      return this;
    }

    public ListRequest addGroup(String uuid) {
      groups.add(uuid);
      return this;
    }

    public ListRequest withVisibleToAll(boolean visible) {
      visibleToAll = visible;
      return this;
    }

    public ListRequest withUser(String user) {
      this.user = user;
      return this;
    }

    public ListRequest withOwned(boolean owned) {
      this.owned = owned;
      return this;
    }

    public ListRequest withLimit(int limit) {
      this.limit = limit;
      return this;
    }

    public ListRequest withStart(int start) {
      this.start = start;
      return this;
    }

    public ListRequest withSubstring(String substring) {
      this.substring = substring;
      return this;
    }

    public ListRequest withSuggest(String suggest) {
      this.suggest = suggest;
      return this;
    }

    public EnumSet<ListGroupsOption> getOptions() {
      return options;
    }

    public List<String> getProjects() {
      return Collections.unmodifiableList(projects);
    }

    public List<String> getGroups() {
      return Collections.unmodifiableList(groups);
    }

    public boolean getVisibleToAll() {
      return visibleToAll;
    }

    public String getUser() {
      return user;
    }

    public boolean getOwned() {
      return owned;
    }

    public int getLimit() {
      return limit;
    }

    public int getStart() {
      return start;
    }

    public String getSubstring() {
      return substring;
    }

    public String getSuggest() {
      return suggest;
    }
  }

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements Groups {
    @Override
    public GroupApi id(String id) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public GroupApi create(String name) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public GroupApi create(GroupInput input) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ListRequest list() {
      throw new NotImplementedException();
    }
  }
}
