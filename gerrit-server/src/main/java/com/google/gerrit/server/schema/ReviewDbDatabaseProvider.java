// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.common.base.Throwables;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.metrics.Timer2;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.Database;
import com.google.gwtorm.jdbc.DatabaseMetrics;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.name.Named;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

/** Provides the {@code Database<ReviewDb>} database handle. */
final class ReviewDbDatabaseProvider implements Provider<Database<ReviewDb>> {
  private final MetricMaker metrics;
  private final DataSource datasource;

  @Inject
  ReviewDbDatabaseProvider(MetricMaker mm,
      @Named("ReviewDb") final DataSource ds) {
    metrics = mm;
    datasource = ds;
  }

  @Override
  public Database<ReviewDb> get() {
    try {
      Database<ReviewDb> db = new Database<>(datasource, ReviewDb.class);
      db.setDatabaseMetrics(new ReviewDbMetrics());
      return db;
    } catch (OrmException e) {
      throw new ProvisionException("Cannot create ReviewDb", e);
    }
  }

  private class ReviewDbMetrics implements DatabaseMetrics {
    private final Timer1<String> sequenceLatency = metrics.newTimer(
        "sql/reviewdb/sequence_latency",
        new Description("Latency to acquire new value from sequence.")
          .setCumulative()
          .setUnit(Units.MILLISECONDS),
        Field.ofString("sequence_name"));

    private final Timer2<String, String> readLatency = metrics.newTimer(
        "sql/reviewdb/read_latency",
        new Description("Latency to read (SELECT) records.")
          .setCumulative()
          .setUnit(Units.MILLISECONDS),
        Field.ofString("table_name"),
        Field.ofString("query_name"));

    private final Timer2<String, Operation> writeLatency = metrics.newTimer(
        "sql/reviewdb/write_latency",
        new Description("Latency to write (INSERT/UPDATE/DELETE) records.")
          .setCumulative()
          .setUnit(Units.MILLISECONDS),
        Field.ofString("table_name"),
        Field.ofEnum(Operation.class, "operation"));

    private final Counter0 poolExhausted =
        metrics.newCounter(
            "sql/reviewdb/pool_exhausted",
            new Description("SQL connections rejected due to pool exhaustion")
              .setGauge()
              .setUnit("attempts"));

    @Override
    public void recordNextLong(String pool, long time, TimeUnit unit) {
      sequenceLatency.record(pool, time, unit);
    }

    @Override
    public void recordAccess(String relation, Operation op, String query,
        long time, TimeUnit unit) {
      if (op == Operation.SELECT) {
        readLatency.record(relation, query, time, unit);
      } else {
        writeLatency.record(relation, op, time, unit);
      }
    }

    @Override
    public void recordOpenFailure(OrmException err) {
      List<Throwable> causes = Throwables.getCausalChain(err);
      Throwable last = causes.get(causes.size() - 1);
      if (last instanceof NoSuchElementException
          && "Timeout waiting for idle object".equals(last.getMessage())) {
        poolExhausted.increment();
      }
    }
  }
}
