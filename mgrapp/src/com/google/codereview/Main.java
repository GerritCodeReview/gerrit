// Copyright 2008 Google Inc.
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

package com.google.codereview;

import com.google.codereview.manager.Backend;
import com.google.codereview.manager.ProjectSync;
import com.google.codereview.manager.RepositoryCache;
import com.google.codereview.manager.merge.PendingMerger;
import com.google.codereview.manager.prune.BuildPruner;
import com.google.codereview.manager.prune.BundlePruner;
import com.google.codereview.manager.unpack.ReceivedBundleUnpacker;
import com.google.codereview.rpc.HttpRpc;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.RepositoryConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Server startup, invoked from the command line. */
public class Main {
  private static final Log LOG = LogFactory.getLog("main");
  private static final String SEC_CODEREVIEW = "codereview";
  private static final String SEC_LOG = "log";
  private static final int FOUR_HOURS = 4 * 60 * 60; // seconds
  private static final int ONCE_PER_DAY = 24 * 60 * 60;// seconds

  public static void main(final String[] args) {
    if (args.length == 0) {
      System.err.println("usage: " + Main.class.getName() + " configfile");
      System.exit(1);
    }

    final File configPath = new File(args[0]).getAbsoluteFile();
    final RepositoryConfig config = new RepositoryConfig(null, configPath);
    try {
      config.load();
    } catch (FileNotFoundException e) {
      System.err.println("error: " + configPath + " not found");
      System.exit(1);
    } catch (IOException e) {
      System.err.println("error: " + configPath + " not readable");
      e.printStackTrace(System.err);
      System.exit(1);
    }

    configureLogging(config, args.length > 1);
    LOG.info("Read " + configPath);
    final Main me = new Main(configPath, config);

    if (args.length == 1) {
      me.addShutdownHook();
      me.start();
    } else {
      final String cmd = args[1];
      if (cmd.equals("sync")) {
        new ProjectSync(me.backend).sync();
      } else {
        System.err.println("error: " + cmd + " not recognized");
      }
      LogManager.shutdown();
    }
  }

  private final RepositoryConfig config;
  private final ScheduledThreadPoolExecutor pool;
  private final int taskSleep;
  private final Backend backend;

  private Main(final File configPath, final RepositoryConfig rc) {
    config = rc;

    final int threads = config.getInt(SEC_CODEREVIEW, "threads", 10);
    taskSleep = config.getInt(SEC_CODEREVIEW, "sleep", 10);
    LOG.info("Starting thread pool with " + threads + " initial threads.");
    pool = new ScheduledThreadPoolExecutor(threads);

    final RepositoryCache repoCache = createRepositoryCache();
    final HttpRpc rpc = createHttpRpc(configPath.getParentFile());
    backend = new Backend(repoCache, rpc, pool, createUserPersonIdent());
  }

  private RepositoryCache createRepositoryCache() {
    final File basedir = new File(required(SEC_CODEREVIEW, "basedir"));
    return new RepositoryCache(basedir);
  }

  private HttpRpc createHttpRpc(final File base) throws ThreadDeath {
    final URL serverUrl;
    try {
      serverUrl = new URL(required(SEC_CODEREVIEW, "server"));
    } catch (MalformedURLException err) {
      System.err.println("error: Bad URL in " + SEC_CODEREVIEW + ".server");
      System.exit(1);
      throw new ThreadDeath();
    }

    final String roleUser = config.getString(SEC_CODEREVIEW, null, "username");
    File pwf = new File(required(SEC_CODEREVIEW, "secureconfig"));
    if (!pwf.isAbsolute()) {
      pwf = new File(base, pwf.getPath());
    }

    final RepositoryConfig pwc = new RepositoryConfig(null, pwf);
    try {
      pwc.load();
    } catch (IOException e) {
      System.err.println("error: Cannot read secureconfig: " + pwf + ": "
          + e.getMessage());
      System.exit(1);
      throw new ThreadDeath();
    }

    final String rolePass = pwc.getString(SEC_CODEREVIEW, null, "password");
    final String apiKey = pwc.getString(SEC_CODEREVIEW, null, "internalapikey");
    return new HttpRpc(serverUrl, roleUser, rolePass, apiKey);
  }

  private PersonIdent createUserPersonIdent() {
    final String name = required("user", "name");
    final String email = required("user", "email");
    return new PersonIdent(name, email, System.currentTimeMillis(), 0);
  }

  private void addShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        LOG.info("Shutting down thread pool.");
        pool.shutdown();

        boolean waiting = true;
        do {
          try {
            waiting = pool.awaitTermination(10, TimeUnit.SECONDS);
          } catch (InterruptedException ie) {
          }
        } while (waiting);
        LOG.info("Thread pool shutdown.");
        LogManager.shutdown();
      }
    });
  }

  private void start() {
    schedule(new ReceivedBundleUnpacker(backend));
    schedule(new PendingMerger(backend));
    schedule(new BundlePruner(backend), FOUR_HOURS);
    schedule(new BuildPruner(backend), ONCE_PER_DAY);
  }

  private void schedule(final Runnable t) {
    schedule(t, taskSleep);
  }

  private void schedule(final Runnable t, final int sleep) {
    pool.scheduleWithFixedDelay(t, 0, sleep, TimeUnit.SECONDS);
  }

  private String required(final String sec, final String key) {
    final String r = config.getString(sec, null, key);
    if (r == null || r.length() == 0) {
      System.err.println("error: Missing required config " + sec + "." + key);
      System.exit(1);
    }
    return r;
  }

  private static void configureLogging(final RepositoryConfig config,
      final boolean interactive) {
    final String logfile = config.getString(SEC_LOG, null, "file");
    final Layout layout;
    final Appender out;

    layout = new PatternLayout("%d{yyyyMMdd.HHmmss} %-5p %c - %m%n");
    if (logfile != null && !interactive) {
      try {
        out = new FileAppender(layout, logfile, true);
      } catch (IOException err) {
        System.err.println("fatal: Cannot open log '" + logfile + "': " + err);
        System.exit(1);
        throw new ThreadDeath();
      }
    } else {
      out = new ConsoleAppender(layout);
    }
    LogManager.getRootLogger().addAppender(out);

    final Level levelObj;
    final String levelStr = config.getString(SEC_LOG, null, "level");
    if (levelStr == null || levelStr.length() == 0) {
      levelObj = Level.INFO;
    } else if ("trace".equalsIgnoreCase(levelStr)) {
      levelObj = Level.TRACE;
    } else if ("debug".equalsIgnoreCase(levelStr)) {
      levelObj = Level.DEBUG;
    } else if ("info".equalsIgnoreCase(levelStr)) {
      levelObj = Level.INFO;
    } else if ("warning".equalsIgnoreCase(levelStr)
        || "warn".equalsIgnoreCase(levelStr)) {
      levelObj = Level.WARN;
    } else if ("error".equalsIgnoreCase(levelStr)) {
      levelObj = Level.ERROR;
    } else if ("fatal".equalsIgnoreCase(levelStr)) {
      levelObj = Level.FATAL;
    } else {
      System.out.println("warning: Bad " + SEC_LOG + ".level " + levelStr
          + "; assuming info");
      levelObj = Level.INFO;
    }
    LogManager.getRootLogger().setLevel(levelObj);

    Logger.getLogger("httpclient").setLevel(Level.WARN);
    Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.WARN);
  }
}
