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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Change;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.index.query.LazyResultSet;
import com.google.gerrit.index.query.OrPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class OrSource extends OrPredicate<ChangeData> implements ChangeDataSource {
  private int cardinality = -1;

  public OrSource(Collection<? extends Predicate<ChangeData>> that) {
    super(that);
  }

  @Override
  public ResultSet<ChangeData> read() {
    Optional<Predicate<ChangeData>> nonChangeDataSource =
        getChildren().stream().filter(p -> !(p instanceof ChangeDataSource)).findAny();
    if (nonChangeDataSource.isPresent()) {
      throw new StorageException("No ChangeDataSource: " + nonChangeDataSource.get());
    }

    // ResultSets are lazy. Calling #read here first and then dealing with ResultSets only when
    // requested allows the index to run asynchronous queries.
    List<ResultSet<ChangeData>> results =
        getChildren().stream().map(p -> ((ChangeDataSource) p).read()).collect(toImmutableList());
    return new LazyResultSet<>(
        () -> {
          List<ChangeData> r = new ArrayList<>();
          Set<Change.Id> have = new HashSet<>();
          for (ResultSet<ChangeData> resultSet : results) {
            for (ChangeData result : resultSet) {
              if (have.add(result.getId())) {
                r.add(result);
              }
            }
          }
          return ImmutableList.copyOf(r);
        });
  }

  @Override
  public ResultSet<FieldBundle> readRaw() {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public boolean hasChange() {
    for (Predicate<ChangeData> p : getChildren()) {
      if (!(p instanceof ChangeDataSource) || !((ChangeDataSource) p).hasChange()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int getCardinality() {
    if (cardinality < 0) {
      cardinality = 0;
      for (Predicate<ChangeData> p : getChildren()) {
        if (p instanceof ChangeDataSource) {
          cardinality += ((ChangeDataSource) p).getCardinality();
        }
      }
    }
    return cardinality;
  }
}
