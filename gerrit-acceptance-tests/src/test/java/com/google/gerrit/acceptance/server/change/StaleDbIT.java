// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.change;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class StaleDbIT extends AbstractDaemonTest {
  @Test
  public void staleDb() throws Exception {
    final SchemaFactory<ReviewDb> sf = reviewDbProvider;
    final CountDownLatch read = new CountDownLatch(1);
    final CountDownLatch write = new CountDownLatch(1);
    final Account.Id id = new Account.Id(db.nextAccountId());
    final AtomicReference<Throwable> err = new AtomicReference<>();
    final List<String> status = Collections.synchronizedList(
        new ArrayList<String>());

    Account a = new Account(id, TimeUtil.nowTs());

    ReviewDb writeConn = sf.open();
    try {
      a.setFullName("user1");
      writeConn.accounts().insert(Collections.singletonList(a));


      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            ReviewDb db = sf.open();
            try {
              status.add("Name before write: "
                  + db.accounts().get(id).getFullName());
              read.countDown();
              write.await();
              status.add("Name after write: "
                  + db.accounts().get(id).getFullName());
            } finally {
              db.close();
            }
          } catch (InterruptedException | OrmException e) {
            err.set(e);
          }
        }
      };
      t.start();

      read.await();
      a.setFullName("user2");
      writeConn.accounts().update(Collections.singletonList(a));
      write.countDown();
      t.join();
    } finally {
      writeConn.close();
    }

    assertEquals(null, err.get());
    assertEquals(ImmutableList.of(
          "Name before write: user1",
          "Name after write: user2"),
        status);
  }
}
