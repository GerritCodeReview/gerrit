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

package com.google.gerrit.server.account;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.OptionUtil;

import org.kohsuke.args4j.Option;

import java.util.Map;
import java.util.Set;

public class GetProjectCapabilities implements
    RestReadView<AccountResource.Project> {
  public static final String CREATE_REF = "createRef";

  @Option(name = "-q", metaVar = "CAP", multiValued = true, usage = "Capability to inspect")
  void addQuery(String name) {
    if (query == null) {
      query = Sets.newHashSet();
    }
    Iterables.addAll(query, OptionUtil.splitOptionValue(name));
  }
  private Set<String> query;

  @Override
  public Map<String, Object> apply(AccountResource.Project rsrc) {
    Map<String, Object> projectCapabilities = Maps.newHashMap();
    if (want(CREATE_REF) && rsrc.getControl().canAddRefs()) {
      projectCapabilities.put(CREATE_REF, true);
    }
    return projectCapabilities;
  }

  private boolean want(String name) {
    return query == null || query.contains(name.toLowerCase());
  }
}
