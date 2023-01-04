// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.query.project;

import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import java.util.List;

/**
 * Provides methods required for parsing projects queries.
 *
 * Internally (at google), this interface has a different implementation, comparing to upstream.
 */
public interface ProjectQueryBuilder {
  String FIELD_LIMIT = "limit";

  /** See {@link com.google.gerrit.index.query.QueryBuilder#parse(String)}. */
  Predicate<ProjectData> parse(String query) throws QueryParseException;
  /** See {@link com.google.gerrit.index.query.QueryBuilder#parse(List<String>)}. */
  List<Predicate<ProjectData>> parse(List<String> queries) throws QueryParseException;
}
