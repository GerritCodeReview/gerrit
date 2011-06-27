// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.RequestCleanup;
import com.google.gerrit.server.config.GerritRequestModule;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.Singleton;
import com.google.inject.servlet.RequestScoped;

import com.jcraft.jsch.HostKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Singleton
public class ChangeTestMergeQueue {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeTestMergeQueue.class);

  private final Map<Change, TestMergeEntry> queue =
      new HashMap<Change, TestMergeEntry>();

  private final WorkQueue workQueue;
  private final Provider<MergeOp.Factory> bgFactory;

  @Inject
  ChangeTestMergeQueue(final WorkQueue wq, Injector parent) {
    workQueue = wq;

    Injector child = parent.createChildInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindScope(RequestScoped.class, MyScope.REQUEST);
        install(new GerritRequestModule());

        bind(CurrentUser.class).to(IdentifiedUser.class);
        bind(IdentifiedUser.class).toProvider(new Provider<IdentifiedUser>() {
          @Override
          public IdentifiedUser get() {
            throw new OutOfScopeException("No user on merge thread");
          }
        });
        bind(SocketAddress.class).annotatedWith(RemotePeer.class).toProvider(
            new Provider<SocketAddress>() {
              @Override
              public SocketAddress get() {
                throw new OutOfScopeException("No remote peer on merge thread");
              }
            });
        bind(SshInfo.class).toInstance(new SshInfo() {
          @Override
          public List<HostKey> getHostKeys() {
            return Collections.emptyList();
          }
        });
      }
    });
    bgFactory = child.getProvider(MergeOp.Factory.class);
  }

  public synchronized void add(Change change) {
    if (!queue.containsKey(change)) {
      final TestMergeEntry e = new TestMergeEntry(change);
      queue.put(change, e);
      scheduleJob(e);
    }
  }

  private void testMergeImpl(Change change) {
    try {
      MyScope ctx = new MyScope();
      MyScope old = MyScope.set(ctx);
      try {
        try {
          bgFactory.get().create(change.getDest()).runTestMerge(change);
        } finally {
          ctx.cleanup.run();
        }
      } finally {
        MyScope.set(old);
      }
    } catch (Throwable e) {
      log.error("Test merge attempt for change: " + change.getId() + " failed",
          e);
    }
  }

  private class TestMergeEntry implements Runnable {
    final Change change;
    boolean jobScheduled;

    TestMergeEntry(final Change change) {
      this.change = change;
    }

    public void run() {
      queue.remove(change);
      testMergeImpl(change);
    }

    @Override
    public String toString() {
      final Project.NameKey project = change.getProject();
      return "Test Merge for " + project.get() + ": "
          + change.getId().toString();
    }
  }

  private void scheduleJob(final TestMergeEntry e) {
    if (!e.jobScheduled) {
      e.jobScheduled = true;
      workQueue.getDefaultQueue().schedule(e, 0, TimeUnit.SECONDS);
    }
  }

  private static class MyScope {
    private static final ThreadLocal<MyScope> current =
        new ThreadLocal<MyScope>();

    private static MyScope getContext() {
      final MyScope ctx = current.get();
      if (ctx == null) {
        throw new OutOfScopeException("Not in command/request");
      }
      return ctx;
    }

    static MyScope set(MyScope ctx) {
      MyScope old = current.get();
      current.set(ctx);
      return old;
    }

    static final Scope REQUEST = new Scope() {
      public <T> Provider<T> scope(final Key<T> key, final Provider<T> creator) {
        return new Provider<T>() {
          public T get() {
            return getContext().get(key, creator);
          }

          @Override
          public String toString() {
            return String.format("%s[%s]", creator, REQUEST);
          }
        };
      }

      @Override
      public String toString() {
        return "MergeQueue.REQUEST";
      }
    };

    private static final Key<RequestCleanup> RC_KEY =
        Key.get(RequestCleanup.class);

    private final RequestCleanup cleanup;
    private final Map<Key<?>, Object> map;

    MyScope() {
      cleanup = new RequestCleanup();
      map = new HashMap<Key<?>, Object>();
      map.put(RC_KEY, cleanup);
    }

    synchronized <T> T get(Key<T> key, Provider<T> creator) {
      @SuppressWarnings("unchecked")
      T t = (T) map.get(key);
      if (t == null) {
        t = creator.get();
        map.put(key, t);
      }
      return t;
    }
  }
}
