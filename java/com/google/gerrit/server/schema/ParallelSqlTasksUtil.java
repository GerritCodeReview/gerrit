// Copyright (C) 2020 The Android Open Source Project
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

import com.google.gerrit.reviewdb.server.ReviewDb;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class meant to run sql queries in parallel. It assumes index of the provided dataset
 * starts from 1.
 */
public final class ParallelSqlTasksUtil {
  public static final int THREAD_MAX = 10;
  public static final int DEFAULT_START = 0;

  public static void run(
      ReviewDb db, Class<? extends Runnable> task, int start, int end, int threadMax)
      throws SQLException {
    int chunkSize = (end / threadMax) + 1;
    List<Thread> threads = new ArrayList<>();
    for (int i = start; i < threadMax; i++) {
      try {
        Thread thread =
            new Thread(
                task.getConstructor(ReviewDb.class, int.class, int.class)
                    .newInstance(db, (i * chunkSize) + 1, Math.min((i + 1) * chunkSize, end)));
        threads.add(thread);
      } catch (InstantiationException
          | IllegalAccessException
          | IllegalArgumentException
          | InvocationTargetException
          | NoSuchMethodException
          | SecurityException e) {
        throw new SQLException("Failed to run tasks in parallel", e);
      }
    }
    for (Thread thread : threads) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public static void run(ReviewDb db, Class<? extends Runnable> target, int end)
      throws SQLException {
    run(db, target, DEFAULT_START, end, THREAD_MAX);
  }
}
