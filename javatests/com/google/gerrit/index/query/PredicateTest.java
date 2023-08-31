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

package com.google.gerrit.index.query;

import com.google.gerrit.testing.ConfigSuite;
import org.eclipse.jgit.lib.Config;
import org.junit.Ignore;

@Ignore
public abstract class PredicateTest {

  @ConfigSuite.Default
  public static Config defaultConfig() {
    return com.google.gerrit.testing.IndexConfig.create();
  }

  @ConfigSuite.Config
  public static Config searchAfterPaginationType() {
    Config config = defaultConfig();
    config.setString("index", null, "paginationType", "SEARCH_AFTER");
    return config;
  }

  @ConfigSuite.Config
  public static Config nonePaginationType() {
    Config config = defaultConfig();
    config.setString("index", null, "paginationType", "NONE");
    return config;
  }

  @SuppressWarnings("ProtectedMembersInFinalClass")
  protected static class TestDataSourcePredicate extends TestMatchablePredicate<String>
      implements DataSource<String> {
    protected final int cardinality;

    protected TestDataSourcePredicate(String name, String value, int cost, int cardinality) {
      super(name, value, cost);
      this.cardinality = cardinality;
    }

    @Override
    public int getCardinality() {
      return cardinality;
    }

    @Override
    public ResultSet<String> read() {
      return null;
    }

    @Override
    public ResultSet<FieldBundle> readRaw() {
      return null;
    }
  }

  protected static class TestCardinalPredicate<T> extends TestMatchablePredicate<T>
      implements HasCardinality {
    protected TestCardinalPredicate(String name, String value, int cost) {
      super(name, value, cost);
    }

    @Override
    public int getCardinality() {
      return 1;
    }
  }

  protected static class TestMatchablePredicate<T> extends TestPredicate<T>
      implements Matchable<T> {
    protected int cost;
    protected boolean ranMatch = false;

    protected TestMatchablePredicate(String name, String value, int cost) {
      super(name, value);
      this.cost = cost;
    }

    @Override
    public boolean match(T object) {
      ranMatch = true;
      return false;
    }

    @Override
    public int getCost() {
      return cost;
    }
  }

  protected static class TestPredicate<T> extends OperatorPredicate<T> {
    protected TestPredicate(String name, String value) {
      super(name, value);
    }
  }

  protected static TestPredicate<String> f(String name, String value) {
    return new TestPredicate<>(name, value);
  }
}
