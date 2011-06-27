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

import com.google.gerrit.reviewdb.Project;

import java.util.List;
import java.util.Set;

public class ProjectMergeStrategies {

  protected String revision;
  protected Project.NameKey inheritsFrom;
  protected List<MergeStrategySection> local;
  protected Set<String> ownerOf;

  public ProjectMergeStrategies() {
  }

  public String getRevision() {
    return revision;
  }

  public void setRevision(String name) {
    revision = name;
  }

  public Project.NameKey getInheritsFrom() {
    return inheritsFrom;
  }

  public void setInheritsFrom(Project.NameKey name) {
    inheritsFrom = name;
  }

  public List<MergeStrategySection> getLocal() {
    return local;
  }

  public void setLocal(List<MergeStrategySection> as) {
    local = as;
  }

  public boolean isOwnerOf(MergeStrategySection section) {
    return getOwnerOf().contains(section.getName());
  }

  public Set<String> getOwnerOf() {
    return ownerOf;
  }

  public void setOwnerOf(Set<String> refs) {
    ownerOf = refs;
  }

}
