// Copyright (C) 2009, The Android Open Source Project
// Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
// Copyright (C) 2007, Shawn O. Pearce <spearce@spearce.org>
//
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// - Redistributions of source code must retain the above copyright notice, this
// list of conditions and the following disclaimer.
//
// - Redistributions in binary form must reproduce the above copyright notice,
// this list of conditions and the following disclaimer in the documentation
// and/or other materials provided with the distribution.
//
// - Neither the name of the Git Development Community nor the names of its
// contributors may be used to endorse or promote products derived from this
// software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.
//
// Taken almost literally from JGit's test suite, as it does almost everything
// we need to create a local git repository for testing.

package com.google.gerrit.testutil;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.FileBasedConfig;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.SystemReader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class LocalDiskRepositoryTestCase extends TestCase {
  private static Thread shutdownHook;
  private static int testCount;

  protected PersonIdent author;
  protected PersonIdent committer;

  private final File trash = new File(new File("target"), "trash");
  private final List<Repository> toClose = new ArrayList<Repository>();
  private MockSystemReader mockSystemReader;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    if (shutdownHook == null) {
      shutdownHook = new Thread() {
        @Override
        public void run() {
          System.gc();
          recursiveDelete("SHUTDOWN", trash, false, false);
        }
      };
      Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    recursiveDelete(testName(), trash, true, false);

    mockSystemReader = new MockSystemReader();
    mockSystemReader.userGitConfig =
        new FileBasedConfig(new File(trash, "usergitconfig"));
    SystemReader.setInstance(mockSystemReader);

    final long now = mockSystemReader.getCurrentTime();
    final int tz = mockSystemReader.getTimezone(now);
    author = new PersonIdent("J. Author", "ja@example.com");
    author = new PersonIdent(author, now, tz);

    committer = new PersonIdent("J. Committer", "jc@example.com");
    committer = new PersonIdent(committer, now, tz);
  }

  @Override
  protected void tearDown() throws Exception {
    RepositoryCache.clear();
    for (Repository r : toClose)
      r.close();
    toClose.clear();

    recursiveDelete(testName(), trash, false, true);
    super.tearDown();
  }

  /** Increment the {@link #author} and {@link #committer} times. */
  protected void tick() {
    final long delta = TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);
    final long now = author.getWhen().getTime() + delta;
    final int tz = mockSystemReader.getTimezone(now);

    author = new PersonIdent(author, now, tz);
    committer = new PersonIdent(committer, now, tz);
  }

  /** Recursively delete a directory, failing the test if the delete fails. */
  protected void recursiveDelete(final File dir) {
    recursiveDelete(testName(), dir, false, true);
  }

  private static boolean recursiveDelete(final String testName, final File dir,
      boolean silent, boolean failOnError) {
    assert !(silent && failOnError);
    if (!dir.exists()) {
      return silent;
    }
    final File[] ls = dir.listFiles();
    if (ls != null) {
      for (int k = 0; k < ls.length; k++) {
        final File e = ls[k];
        if (e.isDirectory()) {
          silent = recursiveDelete(testName, e, silent, failOnError);
        } else {
          if (!e.delete()) {
            if (!silent) {
              reportDeleteFailure(testName, failOnError, e);
            }
            silent = !failOnError;
          }
        }
      }
    }
    if (!dir.delete()) {
      if (!silent) {
        reportDeleteFailure(testName, failOnError, dir);
      }
      silent = !failOnError;
    }
    return silent;
  }

  private static void reportDeleteFailure(final String testName,
      final boolean failOnError, final File e) {
    final String severity;
    if (failOnError)
      severity = "ERROR";
    else
      severity = "WARNING";

    final String msg = severity + ": Failed to delete " + e + " in " + testName;
    if (failOnError)
      fail(msg);
    else
      System.err.println(msg);
  }

  protected Repository createWorkRepository() throws IOException {
    return createRepository(false/* not bare */);
  }

  /** Creates a new empty repository. */
  private Repository createRepository(boolean bare) throws IOException {
    final String uniqueId = System.currentTimeMillis() + "_" + (testCount++);
    final String gitdirName = "test" + uniqueId + (bare ? "" : "/") + ".git";
    final File gitdir = new File(trash, gitdirName).getCanonicalFile();
    final Repository db = new Repository(gitdir);

    assertFalse(gitdir.exists());
    db.create();
    toClose.add(db);
    return db;
  }

  /** Run a hook script in the repository, returning the exit status. */
  protected int runHook(final Repository db, final File hook,
      final String... args) throws IOException, InterruptedException {
    final String[] argv = new String[1 + args.length];
    argv[0] = hook.getAbsolutePath();
    System.arraycopy(args, 0, argv, 1, args.length);

    final Map<String, String> env = cloneEnv();
    env.put("GIT_DIR", db.getDirectory().getAbsolutePath());
    putPersonIdent(env, "AUTHOR", author);
    putPersonIdent(env, "COMMITTER", committer);

    final File cwd = db.getWorkDir();
    final Process p = Runtime.getRuntime().exec(argv, toEnvArray(env), cwd);
    p.getOutputStream().close();
    p.getErrorStream().close();
    p.getInputStream().close();
    return p.waitFor();
  }

  private static void putPersonIdent(final Map<String, String> env,
      final String type, final PersonIdent who) {
    final String ident = who.toExternalString();
    final String date = ident.substring(ident.indexOf("> ") + 2);
    env.put("GIT_" + type + "_NAME", who.getName());
    env.put("GIT_" + type + "_EMAIL", who.getEmailAddress());
    env.put("GIT_" + type + "_DATE", date);
  }

  /** Create a string to a UTF-8 temporary file and return the path. */
  protected File write(final String body) throws IOException {
    final File f = File.createTempFile("temp", "txt", trash);
    try {
      write(f, body);
      return f;
    } catch (Error e) {
      f.delete();
      throw e;
    } catch (RuntimeException e) {
      f.delete();
      throw e;
    } catch (IOException e) {
      f.delete();
      throw e;
    }
  }

  /** Write a string as a UTF-8 file. */
  protected void write(final File f, final String body) throws IOException {
    final Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
    try {
      w.write(body);
    } finally {
      w.close();
    }
  }

  /** Fully read a UTF-8 file and return as a string. */
  protected String read(final File f) throws IOException {
    final byte[] body = NB.readFully(f);
    return new String(body, 0, body.length, "UTF-8");
  }

  private static String[] toEnvArray(final Map<String, String> env) {
    final String[] envp = new String[env.size()];
    int i = 0;
    for (Map.Entry<String, String> e : env.entrySet()) {
      envp[i++] = e.getKey() + "=" + e.getValue();
    }
    return envp;
  }

  private static HashMap<String, String> cloneEnv() {
    return new HashMap<String, String>(System.getenv());
  }

  private String testName() {
    return getClass().getName() + "." + getName();
  }
}
