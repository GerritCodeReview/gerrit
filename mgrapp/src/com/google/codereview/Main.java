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
import com.google.githacks.BrokenShallowRepositoryCreator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.RepositoryConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

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
      } else if (cmd.equals("bsclone")) {
        try {
          final File src = new File(args[2]);
          final File dst = new File(args[3]);
          BrokenShallowRepositoryCreator.createRecursive(src, dst);
        } catch (IOException err) {
          System.err.println("error: " + err);
        }
      } else {
        System.err.println("error: " + cmd + " not recognized");
      }
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
        pool.shutdownNow();
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
    final Logger root = Logger.getLogger("");
    for (final Handler h : root.getHandlers())
      root.removeHandler(h);

    OutputStream out = System.out;
    final String logfile = config.getString(SEC_LOG, null, "file");
    if (logfile != null && !interactive) {
      try {
        out = new FileOutputStream(logfile, true);
      } catch (IOException err) {
        System.err.println("error: Cannot append to " + logfile);
        System.err.println("error: " + err.toString());
        System.exit(1);
        throw new ThreadDeath();
      }
    }

    final StreamHandler ch = new StreamHandler(out, new Formatter() {
      private final SimpleDateFormat sdf;
      private final StringWriter stringBuffer = new StringWriter();
      private final PrintWriter p = new PrintWriter(stringBuffer);
      {
        sdf = new SimpleDateFormat("yyyyMMdd.HHmmss");
      }

      @Override
      public String format(final LogRecord record) {
        stringBuffer.getBuffer().setLength(0);
        final String levelName = record.getLevel().getName();
        p.print(sdf.format(new Date(record.getMillis())));
        p.print(' ');
        p.print(levelName);
        for (int cnt = "WARNING".length() - levelName.length(); --cnt >= 0;) {
          p.print(' ');
        }
        p.print(' ');
        p.print(record.getLoggerName());
        p.print(" - ");
        p.print(record.getMessage());
        p.print('\n');
        if (record.getThrown() != null) {
          record.getThrown().printStackTrace(p);
          p.print('\n');
        }
        p.flush();
        return stringBuffer.toString();
      }
    }) {
      @Override
      public synchronized void publish(final LogRecord record) {
        super.publish(record);
        flush();
      }
    };
    root.addHandler(ch);

    final Level levelObj;
    final String levelStr = config.getString(SEC_LOG, null, "level");
    if (levelStr == null || levelStr.length() == 0) {
      levelObj = Level.INFO;
    } else if ("trace".equalsIgnoreCase(levelStr)) {
      levelObj = Level.FINEST;
    } else if ("debug".equalsIgnoreCase(levelStr)) {
      levelObj = Level.FINE;
    } else if ("info".equalsIgnoreCase(levelStr)) {
      levelObj = Level.INFO;
    } else if ("warning".equalsIgnoreCase(levelStr)
        || "warn".equalsIgnoreCase(levelStr)) {
      levelObj = Level.WARNING;
    } else if ("error".equalsIgnoreCase(levelStr)) {
      levelObj = Level.SEVERE;
    } else if ("fatal".equalsIgnoreCase(levelStr)) {
      levelObj = Level.SEVERE;
    } else {
      System.out.println("warning: Bad " + SEC_LOG + ".level " + levelStr
          + "; assuming info");
      levelObj = Level.INFO;
    }
    ch.setLevel(levelObj);
    root.setLevel(levelObj);

    Logger.getLogger("httpclient").setLevel(Level.WARNING);
    Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.WARNING);
  }
}
