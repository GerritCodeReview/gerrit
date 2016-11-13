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

import com.google.gerrit.server.query.AndSource;
import com.google.gerrit.server.query.IsVisibleToPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import java.util.Collection;
import java.util.List;

public class AndChangeSource extends AndSource<ChangeData> implements ChangeDataSource {

  public AndChangeSource(Collection<Predicate<ChangeData>> that) {
    super(that);
  }

  public AndChangeSource(
      Predicate<ChangeData> that,
      IsVisibleToPredicate<ChangeData> isVisibleToPredicate,
      int start) {
    super(that, isVisibleToPredicate, start);
  }

  @Override
  public boolean hasChange() {
    return source != null
        && source instanceof ChangeDataSource
        && ((ChangeDataSource) source).hasChange();
  }

  @Override
  protected List<ChangeData> transformBuffer(List<ChangeData> buffer) throws OrmRuntimeException {
    if (!hasChange()) {
      try {
        ChangeData.ensureChangeLoaded(buffer);
      } catch (OrmException e) {
        throw new OrmRuntimeException(e);
      }
    }
    return super.transformBuffer(buffer);
  }

  @Override
  public int compare(Predicate<ChangeData> a, Predicate<ChangeData> b) {
    int cmp = super.compare(a, b);
    if (cmp == 0 && a instanceof ChangeDataSource && b instanceof ChangeDataSource) {
      ChangeDataSource as = (ChangeDataSource) a;
      ChangeDataSource bs = (ChangeDataSource) b;
      cmp = (as.hasChange() ? 0 : 1) - (bs.hasChange() ? 0 : 1);
    }
    return cmp;
  }
}
