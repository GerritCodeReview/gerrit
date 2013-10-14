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

package com.google.gerrit.server.query.doc;

import com.google.gerrit.server.query.doc.QueryDocs.DocResult;
import com.google.gwtorm.server.OrmException;

import java.util.List;

public interface DocQueryProcessor {
  public int getLimit();
  public void setLimit(int n);
  public boolean isDisabled();

  /**
   * Query for changes that match the query string.
   * <p>
   * If a limit was specified using {@link #setLimit(int)} this method may
   * return up to {@code limit + 1} results, allowing the caller to determine if
   * there are more than {@code limit} matches and suggest to its own caller
   * that the query could be retried with {@link #setSortkeyBefore(String)}.
   */
  public List<DocResult> queryDocs(List<String> queries) throws OrmException;
}
