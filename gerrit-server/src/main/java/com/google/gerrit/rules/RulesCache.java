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

package com.google.gerrit.rules;

import static com.googlecode.prolog_cafe.lang.PrologMachineCopy.save;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.googlecode.prolog_cafe.compiler.CompileException;
import com.googlecode.prolog_cafe.lang.BufferingPrologControl;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologClassLoader;
import com.googlecode.prolog_cafe.lang.PrologMachineCopy;
import com.googlecode.prolog_cafe.lang.SymbolTerm;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.RawParseUtils;

import java.io.File;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages a cache of compiled Prolog rules.
 * <p>
 * Rules are loaded from the {@code site_path/cache/rules/rules-SHA1.jar}, where
 * {@code SHA1} is the SHA1 of the Prolog {@code rules.pl} in a project's
 * {@link GitRepositoryManager#REF_CONFIG} branch.
 */
@Singleton
public class RulesCache {
  /** Maximum size of a dynamic Prolog script, in bytes. */
  private static final int SRC_LIMIT = 128 * 1024;

  /** Default size of the internal Prolog database within each interpreter. */
  private static final int DB_MAX = 256;


  private final Map<ObjectId, MachineRef> machineCache =
      new HashMap<ObjectId, MachineRef>();

  private final ReferenceQueue<PrologMachineCopy> dead =
      new ReferenceQueue<PrologMachineCopy>();

  private static final class MachineRef extends WeakReference<PrologMachineCopy> {
    final ObjectId key;

    MachineRef(ObjectId key, PrologMachineCopy pcm,
        ReferenceQueue<PrologMachineCopy> queue) {
      super(pcm, queue);
      this.key = key;
    }
  }

  private final File cacheDir;
  private final File rulesDir;
  private final GitRepositoryManager gitMgr;
  private final ClassLoader systemLoader;
  private final PrologMachineCopy defaultMachine;

  @Inject
  protected RulesCache(@GerritServerConfig Config config, SitePaths site,
      GitRepositoryManager gm) {
    cacheDir = site.resolve(config.getString("cache", null, "directory"));
    rulesDir = cacheDir != null ? new File(cacheDir, "rules") : null;
    gitMgr = gm;

    systemLoader = getClass().getClassLoader();
    defaultMachine = save(newEmptyMachine(systemLoader));
  }

  /**
   * Locate a cached Prolog machine state, or create one if not available.
   *
   * @return a Prolog machine, after loading the specified rules.
   * @throws CompileException the machine cannot be created.
   */
  public synchronized PrologMachineCopy loadMachine(
      Project.NameKey project,
      ObjectId rulesId)
      throws CompileException {
    if (project == null || rulesId == null) {
      return defaultMachine;
    }

    Reference<? extends PrologMachineCopy> ref = machineCache.get(rulesId);
    if (ref != null) {
      PrologMachineCopy pmc = ref.get();
      if (pmc != null) {
        return pmc;
      }

      machineCache.remove(rulesId);
      ref.enqueue();
    }

    gc();

    PrologMachineCopy pcm = createMachine(project, rulesId);
    MachineRef newRef = new MachineRef(rulesId, pcm, dead);
    machineCache.put(rulesId, newRef);
    return pcm;
  }

  private void gc() {
    Reference<?> ref;
    while ((ref = dead.poll()) != null) {
      ObjectId key = ((MachineRef) ref).key;
      if (machineCache.get(key) == ref) {
        machineCache.remove(key);
      }
    }
  }

  private PrologMachineCopy createMachine(Project.NameKey project,
      ObjectId rulesId) throws CompileException {
    // If the rules are available as a complied JAR on local disk, prefer
    // that over dynamic consult as the bytecode will be faster.
    //
    if (rulesDir != null) {
      File jarFile = new File(rulesDir, "rules-" + rulesId.getName() + ".jar");
      if (jarFile.isFile()) {
        URL[] cp = new URL[] {toURL(jarFile)};
        return save(newEmptyMachine(new URLClassLoader(cp, systemLoader)));
      }
    }

    // Dynamically consult the rules into the machine's internal database.
    //
    String rules = read(project, rulesId);
    BufferingPrologControl ctl = newEmptyMachine(systemLoader);
    PushbackReader in = new PushbackReader(
        new StringReader(rules),
        Prolog.PUSHBACK_SIZE);

    if (!ctl.execute(
        Prolog.BUILTIN, "consult_stream",
        SymbolTerm.intern("rules.pl"),
        new JavaObjectTerm(in))) {
      throw new CompileException("Cannot consult rules of " + project);
    }
    return save(ctl);
  }

  private String read(Project.NameKey project, ObjectId rulesId)
      throws CompileException {
    Repository git;
    try {
      git = gitMgr.openRepository(project);
    } catch (RepositoryNotFoundException e) {
      throw new CompileException("Cannot open repository " + project, e);
    }
    try {
      ObjectLoader ldr = git.open(rulesId, Constants.OBJ_BLOB);
      byte[] raw = ldr.getCachedBytes(SRC_LIMIT);
      return RawParseUtils.decode(raw);
    } catch (LargeObjectException e) {
      throw new CompileException("rules of " + project + " are too large", e);
    } catch (RuntimeException e) {
      throw new CompileException("Cannot load rules of " + project, e);
    } catch (IOException e) {
      throw new CompileException("Cannot load rules of " + project, e);
    } finally {
      git.close();
    }
  }

  private static BufferingPrologControl newEmptyMachine(ClassLoader cl) {
    BufferingPrologControl ctl = new BufferingPrologControl();
    ctl.setMaxArity(PrologEnvironment.MAX_ARITY);
    ctl.setMaxDatabaseSize(DB_MAX);
    ctl.setPrologClassLoader(new PrologClassLoader(cl));
    ctl.setEnabled(EnumSet.allOf(Prolog.Feature.class), false);
    return ctl;
  }

  private static URL toURL(File jarFile) throws CompileException {
    try {
      return jarFile.toURI().toURL();
    } catch (MalformedURLException e) {
      throw new CompileException("Cannot create URL for " + jarFile, e);
    }
  }
}
