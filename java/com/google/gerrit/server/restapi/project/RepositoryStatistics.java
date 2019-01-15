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

package com.google.gerrit.server.restapi.project;

import com.google.common.base.CaseFormat;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public class RepositoryStatistics extends TreeMap<String, Object> {
  private static final long serialVersionUID = 1L;

  RepositoryStatistics(Properties p) {
    for (Map.Entry<Object, Object> e : p.entrySet()) {
      put(
          CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, e.getKey().toString()),
          e.getValue());
    }
  }
}
