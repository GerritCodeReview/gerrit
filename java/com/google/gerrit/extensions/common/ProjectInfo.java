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

package com.google.gerrit.extensions.common;

import com.google.gerrit.extensions.client.ProjectState;
import java.util.List;
import java.util.Map;

public class ProjectInfo {
  public String id;
  public String name;
  public String parent;
  public String description;
  public ProjectState state;
  public Map<String, String> branches;
  public List<WebLinkInfo> webLinks;
  public Map<String, LabelTypeInfo> labels;

  /**
   * Whether the query would deliver more results if not limited. Only set on the last project that
   * is returned as a query result.
   */
  public Boolean _moreProjects;
}
