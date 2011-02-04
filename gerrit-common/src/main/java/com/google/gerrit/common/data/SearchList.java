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

package com.google.gerrit.common.data;

import com.google.gerrit.common.data.OwnerInfo;
import com.google.gerrit.reviewdb.Owner;
import com.google.gerrit.reviewdb.Search;

import java.util.ArrayList;
import java.util.List;

/** Summary information about a {@link SearchList}. */
public class SearchList {
  private OwnerInfo ownerInfo;
  private boolean editable = false;
  private List<Search> searches = new ArrayList<Search>(0);

  protected SearchList() {
  }

  public SearchList(OwnerInfo ownerInfo) {
    setOwnerInfo(ownerInfo);
  }


  public OwnerInfo getOwnerInfo() {
    return ownerInfo;
  }

  public void setOwnerInfo(OwnerInfo ownerInfo) {
    this.ownerInfo = ownerInfo;
  }

  public Owner.Type getType() {
    return getOwnerInfo().getType();
  }

  public boolean isEditable() {
    return editable;
  }

  public void setEditable(boolean editable) {
    this.editable = editable;
  }

  public List<Search> getSearches() {
    return searches;
  }

  public void setSearches(List<Search> searches) {
    this.searches = searches;
  }
}
