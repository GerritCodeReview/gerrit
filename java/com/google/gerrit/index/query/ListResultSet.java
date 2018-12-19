// Copyright 2008 Google Inc.
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

package com.google.gerrit.index.query;

import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;

public class ListResultSet<T> implements ResultSet<T> {
  private ImmutableList<T> items;

  public ListResultSet(List<T> r) {
    items = ImmutableList.copyOf(r);
  }

  @Override
  public Iterator<T> iterator() {
    return toList().iterator();
  }

  @Override
  public ImmutableList<T> toList() {
    if (items == null) {
      throw new IllegalStateException("Results already obtained");
    }
    ImmutableList<T> r = items;
    items = null;
    return r;
  }

  @Override
  public void close() {
    items = null;
  }
}
