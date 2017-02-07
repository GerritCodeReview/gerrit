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

package com.google.gerrit.server.git;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class QueryList extends TabFile {
  public static final String FILE_NAME = "queries";
  protected final Map<String, String> queriesByName;

  private QueryList(List<Row> queriesByName) {
    this.queriesByName = toMap(queriesByName);
  }

  public static QueryList parse(String text, ValidationError.Sink errors) throws IOException {
    return new QueryList(parse(text, FILE_NAME, TRIM, TRIM, errors));
  }

  public String getQuery(String name) {
    return queriesByName.get(name);
  }

  public String asText() {
    return asText("Name", "Query", queriesByName);
  }
}
