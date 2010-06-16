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

package com.google.gerrit.httpd;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.ActiveSession;
import com.google.gerrit.reviewdb.ActiveSessionAccess;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.ActiveSession.Key;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

/** Removes expired sessions from the session database cache */
public class SessionCacheCleaner implements Runnable {
  private static final Logger log =
      LoggerFactory.getLogger(SessionCacheCleaner.class);

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(Lifecycle.class);
    }
  }

  static class Lifecycle implements LifecycleListener {
    private final WorkQueue queue;
    private final SessionCacheCleaner cleaner;

    @Inject
    Lifecycle(final WorkQueue queue, final SessionCacheCleaner cleaner) {
      this.queue = queue;
      this.cleaner = cleaner;
    }

    @Override
    public void start() {
      queue.getDefaultQueue().scheduleWithFixedDelay(cleaner, 1, 12, HOURS);
    }

    @Override
    public void stop() {
    }
  }

  private final ActiveSessionAccess activeSessionAccess;
  private final Cache<Key, ActiveSession> cache;

  @Inject
  public SessionCacheCleaner(
      SchemaFactory<ReviewDb> reviewDbFactory,
      @Named(WebSession.CACHE_NAME) final Cache<ActiveSession.Key, ActiveSession> cache)
      throws OrmException {
    this.activeSessionAccess = reviewDbFactory.open().activeSessions();
    this.cache = cache;
  }

  @Override
  public void run() {
    List<ActiveSession> activeSessions;
    try {
      activeSessions = activeSessionAccess.all().toList();
    } catch (OrmException e) {
      log.error("Unable to fetch sessions from database", e);
      return;
    }
    for (ActiveSession as : activeSessions) {
      if (expiredFromCache(as)) {
        try {
          activeSessionAccess.delete(Arrays.asList(as));
        } catch (OrmException e) {
          log.error("Unable to delete session with key " + as.getExternalId()
              + " from database", e);
        }
      }
    }
  }

  private boolean expiredFromCache(ActiveSession as) {
    if (as.getLastSeen() == null) {
      return true;
    }
    Timestamp expireAt =
        new Timestamp(as.getLastSeen().getTime()
            + cache.getTimeToLive(MILLISECONDS));
    Timestamp now = new Timestamp(System.currentTimeMillis());

    return now.after(expireAt);
  }
}
