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

import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import com.googlecode.prolog_cafe.lang.BufferingPrologControl;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.PredicateEncoder;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologMachineCopy;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-thread Prolog interpreter.
 *
 * <p>This class is not thread safe.
 *
 * <p>A single copy of the Prolog interpreter, for the current thread.
 */
public class PrologEnvironment extends BufferingPrologControl {
  private static final Logger log = LoggerFactory.getLogger(PrologEnvironment.class);

  public interface Factory {
    /**
     * Construct a new Prolog interpreter.
     *
     * @param src the machine to template the new environment from.
     * @return the new interpreter.
     */
    PrologEnvironment create(PrologMachineCopy src);
  }

  private final Args args;
  private final Map<StoredValue<Object>, Object> storedValues;
  private List<Runnable> cleanup;

  @Inject
  PrologEnvironment(Args a, @Assisted PrologMachineCopy src) {
    super(src);
    setEnabled(EnumSet.allOf(Prolog.Feature.class), false);
    args = a;
    storedValues = new HashMap<>();
    cleanup = new LinkedList<>();
  }

  public Args getArgs() {
    return args;
  }

  @Override
  public void setPredicate(Predicate goal) {
    super.setPredicate(goal);
    setReductionLimit(args.reductionLimit(goal));
  }

  /**
   * Lookup a stored value in the interpreter's hash manager.
   *
   * @param <T> type of stored Java object.
   * @param sv unique key.
   * @return the value; null if not stored.
   */
  @SuppressWarnings("unchecked")
  public <T> T get(StoredValue<T> sv) {
    return (T) storedValues.get(sv);
  }

  /**
   * Set a stored value on the interpreter's hash manager.
   *
   * @param <T> type of stored Java object.
   * @param sv unique key.
   * @param obj the value to store under {@code sv}.
   */
  @SuppressWarnings("unchecked")
  public <T> void set(StoredValue<T> sv, T obj) {
    storedValues.put((StoredValue<Object>) sv, obj);
  }

  /**
   * Copy the stored values from another interpreter to this one. Also gets the cleanup from the
   * child interpreter
   */
  public void copyStoredValues(PrologEnvironment child) {
    storedValues.putAll(child.storedValues);
    setCleanup(child.cleanup);
  }

  /**
   * Assign the environment a cleanup list (in order to use a centralized list) If this
   * enivronment's list is non-empty, append its cleanup tasks to the assigning list.
   */
  public void setCleanup(List<Runnable> newCleanupList) {
    newCleanupList.addAll(cleanup);
    cleanup = newCleanupList;
  }

  /**
   * Adds cleanup task to run when close() is called
   *
   * @param task is run when close() is called
   */
  public void addToCleanup(Runnable task) {
    cleanup.add(task);
  }

  /** Release resources stored in interpreter's hash manager. */
  public void close() {
    for (Iterator<Runnable> i = cleanup.iterator(); i.hasNext(); ) {
      try {
        i.next().run();
      } catch (Throwable err) {
        log.error("Failed to execute cleanup for PrologEnvironment", err);
      }
      i.remove();
    }
  }

  @Singleton
  public static class Args {
    private static final Class<Predicate> CONSULT_STREAM_2;

    static {
      try {
        @SuppressWarnings("unchecked")
        Class<Predicate> c =
            (Class<Predicate>)
                Class.forName(
                    PredicateEncoder.encode(Prolog.BUILTIN, "consult_stream", 2),
                    false,
                    RulesCache.class.getClassLoader());
        CONSULT_STREAM_2 = c;
      } catch (ClassNotFoundException e) {
        throw new LinkageError("cannot find predicate consult_stream", e);
      }
    }

    private final ProjectCache projectCache;
    private final PermissionBackend permissionBackend;
    private final GitRepositoryManager repositoryManager;
    private final PatchListCache patchListCache;
    private final PatchSetInfoFactory patchSetInfoFactory;
    private final IdentifiedUser.GenericFactory userFactory;
    private final Provider<AnonymousUser> anonymousUser;
    private final int reductionLimit;
    private final int compileLimit;

    @Inject
    Args(
        ProjectCache projectCache,
        PermissionBackend permissionBackend,
        GitRepositoryManager repositoryManager,
        PatchListCache patchListCache,
        PatchSetInfoFactory patchSetInfoFactory,
        IdentifiedUser.GenericFactory userFactory,
        Provider<AnonymousUser> anonymousUser,
        @GerritServerConfig Config config) {
      this.projectCache = projectCache;
      this.permissionBackend = permissionBackend;
      this.repositoryManager = repositoryManager;
      this.patchListCache = patchListCache;
      this.patchSetInfoFactory = patchSetInfoFactory;
      this.userFactory = userFactory;
      this.anonymousUser = anonymousUser;

      int limit = config.getInt("rules", null, "reductionLimit", 100000);
      reductionLimit = limit <= 0 ? Integer.MAX_VALUE : limit;

      limit =
          config.getInt(
              "rules",
              null,
              "compileReductionLimit",
              (int) Math.min(10L * limit, Integer.MAX_VALUE));
      compileLimit = limit <= 0 ? Integer.MAX_VALUE : limit;
    }

    private int reductionLimit(Predicate goal) {
      if (goal.getClass() == CONSULT_STREAM_2) {
        return compileLimit;
      }
      return reductionLimit;
    }

    public ProjectCache getProjectCache() {
      return projectCache;
    }

    public PermissionBackend getPermissionBackend() {
      return permissionBackend;
    }

    public GitRepositoryManager getGitRepositoryManager() {
      return repositoryManager;
    }

    public PatchListCache getPatchListCache() {
      return patchListCache;
    }

    public PatchSetInfoFactory getPatchSetInfoFactory() {
      return patchSetInfoFactory;
    }

    public IdentifiedUser.GenericFactory getUserFactory() {
      return userFactory;
    }

    public AnonymousUser getAnonymousUser() {
      return anonymousUser.get();
    }
  }
}
