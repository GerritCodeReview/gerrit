// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.client.admin;

import com.google.gerrit.common.data.ProjectData;
import com.google.gwt.event.shared.GwtEvent;

import java.util.List;

/** Searching projects event */
public class SearchEvent extends GwtEvent<SearchHandler> {
  private static final Type<SearchHandler> TYPE = new Type<SearchHandler>();

  private final List<ProjectData> filterProjectsList;
  private final boolean searchAll;

  public SearchEvent(final List<ProjectData> filterProjectsList,
      final boolean searchAll) {
    this.filterProjectsList = filterProjectsList;
    this.searchAll = searchAll;
  }

  public static Type<SearchHandler> getType() {
    return TYPE;
  }

  /** @returns List of projects filtered on search operation */
  public List<ProjectData> getFilteredProjects() {
    return filterProjectsList;
  }

  /** @returns True if no project or parent were supplied. False otherwise. */
  public boolean isSearchAll() {
    return searchAll;
  }

  @Override
  protected void dispatch(SearchHandler handler) {
    handler.onSearch(this);
  }

  @Override
  public Type<SearchHandler> getAssociatedType() {
    return TYPE;
  }
}
