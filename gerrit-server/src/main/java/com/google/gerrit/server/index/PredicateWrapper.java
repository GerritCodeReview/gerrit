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

package com.google.gerrit.server.index;

import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import java.util.Collection;

/**
 * Wrapper combining an {@link IndexPredicate} together with a
 * {@link ChangeDataSource} that returns matching results from the index.
 * <p>
 * Appropriate to return as the rootmost predicate that can be processed using
 * the secondary index; such predicates must also implement
 * {@link ChangeDataSource} to be chosen by the query processor.
 */
public class PredicateWrapper extends Predicate<ChangeData> implements
    ChangeDataSource {
  private final Predicate<ChangeData> pred;
  private final ChangeDataSource source;

  public PredicateWrapper(ChangeIndex index, Predicate<ChangeData> pred)
      throws QueryParseException {
    this.pred = pred;
    this.source = index.getSource(pred);
  }

  @Override
  public int getCardinality() {
    return source.getCardinality();
  }

  @Override
  public boolean hasChange() {
    return source.hasChange();
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    return source.read();
  }

  @Override
  public Predicate<ChangeData> copy(
      Collection<? extends Predicate<ChangeData>> children) {
    return this;
  }

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    return pred.match(cd);
  }

  @Override
  public int getCost() {
    return pred.getCost();
  }

  @Override
  public int hashCode() {
    return pred.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    return other != null
        && getClass() == other.getClass()
        && pred.equals(((PredicateWrapper) other).pred);
  }

  @Override
  public String toString() {
    return "index(" + pred + ")";
  }
}
