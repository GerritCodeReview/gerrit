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

package com.google.gerrit.server.rules;

import static com.googlecode.prolog_cafe.lang.PrologMachineCopy.save;

import com.google.common.base.Joiner;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.googlecode.prolog_cafe.exceptions.CompileException;
import com.googlecode.prolog_cafe.exceptions.SyntaxException;
import com.googlecode.prolog_cafe.exceptions.TermException;
import com.googlecode.prolog_cafe.lang.BufferingPrologControl;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.ListTerm;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologClassLoader;
import com.googlecode.prolog_cafe.lang.PrologMachineCopy;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Manages a cache of compiled Prolog rules.
 *
 * <p>Rules are loaded from the {@code site_path/cache/rules/rules-SHA1.jar}, where {@code SHA1} is
 * the SHA1 of the Prolog {@code rules.pl} in a project's {@link RefNames#REFS_CONFIG} branch.
 */
@Singleton
public class RulesCache {
  private static final ImmutableList<String> PACKAGE_LIST =
      ImmutableList.of(Prolog.BUILTIN, "gerrit");

  static final String CACHE_NAME = "prolog_rules";

  private final boolean enableProjectRules;
  private final int maxDbSize;
  private final int maxSrcBytes;
  private final Path cacheDir;
  private final Path rulesDir;
  private final GitRepositoryManager gitMgr;
  private final DynamicSet<PredicateProvider> predicateProviders;
  private final ClassLoader systemLoader;
  private final PrologMachineCopy defaultMachine;
  private final Cache<ObjectId, PrologMachineCopy> machineCache;

  @Inject
  protected RulesCache(
      @GerritServerConfig Config config,
      SitePaths site,
      GitRepositoryManager gm,
      DynamicSet<PredicateProvider> predicateProviders,
      @Named(CACHE_NAME) Cache<ObjectId, PrologMachineCopy> machineCache) {
    maxDbSize = config.getInt("rules", null, "maxPrologDatabaseSize", 256);
    maxSrcBytes = config.getInt("rules", null, "maxSourceBytes", 128 << 10);
    enableProjectRules = config.getBoolean("rules", null, "enable", true) && maxSrcBytes > 0;
    cacheDir = site.resolve(config.getString("cache", null, "directory"));
    rulesDir = cacheDir != null ? cacheDir.resolve("rules") : null;
    gitMgr = gm;
    this.predicateProviders = predicateProviders;
    this.machineCache = machineCache;

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
  public synchronized PrologMachineCopy loadMachine(Project.NameKey project, ObjectId rulesId)
      throws CompileException {
    if (!enableProjectRules || project == null || rulesId == null) {
      return defaultMachine;
    }

    try {
      return machineCache.get(rulesId, () -> createMachine(project, rulesId));
    } catch (ExecutionException e) {
      if (e.getCause() instanceof CompileException) {
        throw new CompileException(e.getCause().getMessage(), e);
      }
      throw new CompileException("Error while consulting rules from " + project, e);
    }
  }

  public PrologMachineCopy loadMachine(String name, Reader in) throws CompileException {
    PrologMachineCopy pmc = consultRules(name, in);
    if (pmc == null) {
      throw new CompileException("Cannot consult rules from the stream " + name);
    }
    return pmc;
  }

  private PrologMachineCopy createMachine(Project.NameKey project, ObjectId rulesId)
      throws CompileException {
    // If the rules are available as a complied JAR on local disk, prefer
    // that over dynamic consult as the bytecode will be faster.
    //
    if (rulesDir != null) {
      Path jarPath = rulesDir.resolve("rules-" + rulesId.getName() + ".jar");
      if (Files.isRegularFile(jarPath)) {
        URL[] cp = new URL[] {toURL(jarPath)};
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

  private PrologMachineCopy consultRules(String name, Reader rules) throws CompileException {
    BufferingPrologControl ctl = newEmptyMachine(systemLoader);
    PushbackReader in = new PushbackReader(rules, Prolog.PUSHBACK_SIZE);
    try {
      if (!ctl.execute(
          Prolog.BUILTIN, "consult_stream", SymbolTerm.intern(name), new JavaObjectTerm(in))) {
        return null;
      }
    } catch (SyntaxException e) {
      throw new CompileException(e.toString(), e);
    } catch (TermException e) {
      Term m = e.getMessageTerm();
      if (m instanceof StructureTerm && "syntax_error".equals(m.name()) && m.arity() >= 1) {
        StringBuilder msg = new StringBuilder();
        if (m.arg(0) instanceof ListTerm) {
          msg.append(Joiner.on(' ').join(((ListTerm) m.arg(0)).toJava()));
        } else {
          msg.append(m.arg(0).toString());
        }
        if (m.arity() == 2 && m.arg(1) instanceof StructureTerm && "at".equals(m.arg(1).name())) {
          Term at = m.arg(1).arg(0).dereference();
          if (at instanceof ListTerm) {
            msg.append(" at: ");
            msg.append(prettyProlog(at));
          }
        }
        throw new CompileException(msg.toString(), e);
      }
      throw new CompileException("Error while consulting rules from " + name, e);
    } catch (RuntimeException e) {
      throw new CompileException("Error while consulting rules from " + name, e);
    }
    return save(ctl);
  }

  private static String prettyProlog(Term at) {
    StringBuilder b = new StringBuilder();
    for (Object o : ((ListTerm) at).toJava()) {
      if (o instanceof Term) {
        Term t = (Term) o;
        if (!(t instanceof StructureTerm)) {
          b.append(t.toString()).append(' ');
          continue;
        }
        switch (t.name()) {
          case "atom":
            SymbolTerm atom = (SymbolTerm) t.arg(0);
            b.append(atom.toString());
            break;
          case "var":
            b.append(t.arg(0).toString());
            break;
        }
      } else {
        b.append(o);
      }
    }
    return b.toString().trim();
  }

  private String read(Project.NameKey project, ObjectId rulesId) throws CompileException {
    try (Repository git = gitMgr.openRepository(project)) {
      try {
        ObjectLoader ldr = git.open(rulesId, Constants.OBJ_BLOB);
        byte[] raw = ldr.getCachedBytes(maxSrcBytes);
        return RawParseUtils.decode(raw);
      } catch (LargeObjectException e) {
        throw new CompileException("rules of " + project + " are too large", e);
      } catch (RuntimeException | IOException e) {
        throw new CompileException("Cannot load rules of " + project, e);
      }
    } catch (IOException e) {
      throw new CompileException("Cannot open repository " + project, e);
    }
  }

  private BufferingPrologControl newEmptyMachine(ClassLoader cl) {
    BufferingPrologControl ctl = new BufferingPrologControl();
    ctl.setMaxDatabaseSize(maxDbSize);
    ctl.setPrologClassLoader(
        new PrologClassLoader(new PredicateClassLoader(predicateProviders, cl)));
    ctl.setEnabled(EnumSet.allOf(Prolog.Feature.class), false);

    List<String> packages = new ArrayList<>();
    packages.addAll(PACKAGE_LIST);
    for (PredicateProvider predicateProvider : predicateProviders) {
      packages.addAll(predicateProvider.getPackages());
    }

    // Bootstrap the interpreter and ensure there is clean state.
    ctl.initialize(packages.toArray(new String[packages.size()]));
    return ctl;
  }

  private static URL toURL(Path jarPath) throws CompileException {
    try {
      return jarPath.toUri().toURL();
    } catch (MalformedURLException e) {
      throw new CompileException("Cannot create URL for " + jarPath, e);
    }
  }
}
