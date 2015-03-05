// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.index;

import com.google.gerrit.extensions.common.ProjectInfo;

import java.util.List;

/**
 * Secondary index implementation for project list.
 * Strings are inserted into the index and are queried all
 * or by page number and page size.
 */
public interface ProjectListIndex {
  public void reCreateIndex();
  public void deleteAll();
  public void delete(String name);
  public void insert(ProjectInfo info);
  public List<ProjectInfo> getAllProjectInfo();
  public List<ProjectInfo> getPageProjectInfo(int pageNumber, int pageSize);
}
