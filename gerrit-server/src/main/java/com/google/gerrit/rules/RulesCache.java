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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringReader;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages a cache of compiled Prolog rules.
 * <p>
 * Rules are loaded from the {@code site_path/cache/rules/rules-SHA1.jar}, where
 * {@code SHA1} is the SHA1 of the Prolog {@code rules.pl} in a project's
 * {@link RefNames#REFS_CONFIG} branch.
 */
@Singleton
public class RulesCache {
  /** Maximum size of a dynamic Prolog script, in bytes. */
  private static final int SRC_LIMIT = 128 * 1024;

  /** Default size of the internal Prolog database within each interpreter. */
  private static final int DB_MAX = 256;

  private static final List<String> PACKAGE_LIST = ImmutableList.of(
      Prolog.BUILTIN, "gerrit");

  private final Map<ObjectId, MachineRef> machineCache = new HashMap<>();

  private final ReferenceQueue<PrologMachineCopy> dead =
      new ReferenceQueue<>();

  private static final class MachineRef extends WeakReference<PrologMachineCopy> {
    final ObjectId key;

    MachineRef(ObjectId key, PrologMachineCopy pcm,
        ReferenceQueue<PrologMachineCopy> queue) {
      super(pcm, queue);
      this.key = key;
    }
  }

  private final boolean enableProjectRules;
  private final File cacheDir;
  private final File rulesDir;
  private final GitRepositoryManager gitMgr;
  private final DynamicSet<PredicateProvider> predicateProviders;
  private final ClassLoader systemLoader;
  private final PrologMachineCopy defaultMachine;

  @Inject
  protected RulesCache(@GerritServerConfig Config config, SitePaths site,
      GitRepositoryManager gm, DynamicSet<PredicateProvider> predicateProviders) {
    enableProjectRules = config.getBoolean("rules", null, "enable", true);
    cacheDir = site.resolve(config.getString("cache", null, "directory"));
    rulesDir = cacheDir != null ? new File(cacheDir, "rules") : null;
    gitMgr = gm;
    this.predicateProviders = predicateProviders;

    systemLoader = getClass().getClassLoader();
    defaultMachine = save(newEmptyMachine(systemLoader));
  }

  public boolean isProjectRulesEnabled() {
    return enableProjectRules;
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
    if (!enableProjectRules || project == null || rulesId == null) {
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

  public PrologMachineCopy loadMachine(String name, InputStream in)
      throws CompileException {
    PrologMachineCopy pmc = consultRules(name, new InputStreamReader(in));
    if (pmc == null) {
      throw new CompileException("Cannot consult rules from the stream " + name);
    }
    return pmc;
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
    PrologMachineCopy pmc = consultRules("rules.pl", new StringReader(rules));
    if (pmc == null) {
      throw new CompileException("Cannot consult rules of " + project);
    }
    return pmc;
  }

  private PrologMachineCopy consultRules(String name, Reader rules)
      throws CompileException {
    BufferingPrologControl ctl = newEmptyMachine(systemLoader);
    PushbackReader in = new PushbackReader(rules, Prolog.PUSHBACK_SIZE);
    try {
      if (!ctl.execute(Prolog.BUILTIN, "consult_stream",
          SymbolTerm.intern(name), new JavaObjectTerm(in))) {
        return null;
      }
    } catch (RuntimeException e) {
      throw new CompileException("Error while consulting rules from " + name, e);
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
    } catch (IOException e) {
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

  private BufferingPrologControl newEmptyMachine(ClassLoader cl) {
    BufferingPrologControl ctl = new BufferingPrologControl();
    ctl.setMaxArity(PrologEnvironment.MAX_ARITY);
    ctl.setMaxDatabaseSize(DB_MAX);
    ctl.setPrologClassLoader(new PrologClassLoader(new PredicateClassLoader(
        predicateProviders, cl)));
    ctl.setEnabled(EnumSet.allOf(Prolog.Feature.class), false);

    List<String> packages = Lists.newArrayList();
    packages.addAll(PACKAGE_LIST);
    for (PredicateProvider predicateProvider : predicateProviders) {
      packages.addAll(predicateProvider.getPackages());
    }

    // Bootstrap the interpreter and ensure there is clean state.
    ctl.initialize(packages.toArray(new String[packages.size()]));
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
