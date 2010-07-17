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

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gwtorm.client.ResultSet;

import java.util.Iterator;

abstract class ChangeDataResultSet<T> extends AbstractResultSet<ChangeData> {
  static ResultSet<ChangeData> change(final ResultSet<Change> rs) {
    return new ChangeDataResultSet<Change>(rs) {
      @Override
      ChangeData convert(Change t) {
        return new ChangeData(t);
      }
    };
  }

  static ResultSet<ChangeData> patchSet(final ResultSet<PatchSet> rs) {
    return new ChangeDataResultSet<PatchSet>(rs) {
      @Override
      ChangeData convert(PatchSet t) {
        return new ChangeData(t.getId().getParentKey());
      }
    };
  }

  static ResultSet<ChangeData> patchSetApproval(
      final ResultSet<PatchSetApproval> rs) {
    return new ChangeDataResultSet<PatchSetApproval>(rs) {
      @Override
      ChangeData convert(PatchSetApproval t) {
        return new ChangeData(t.getPatchSetId().getParentKey());
      }
    };
  }

  private final ResultSet<T> source;

  ChangeDataResultSet(ResultSet<T> source) {
    this.source = source;
  }

  @Override
  public Iterator<ChangeData> iterator() {
    return new Iterator<ChangeData>() {
      private final Iterator<T> itr = source.iterator();

      @Override
      public boolean hasNext() {
        return itr.hasNext();
      }

      @Override
      public ChangeData next() {
        return convert(itr.next());
      }

      @Override
      public void remove() {
        itr.remove();
      }
    };
  }

  @Override
  public void close() {
    source.close();
  }

  abstract ChangeData convert(T t);
}
