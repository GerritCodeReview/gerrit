// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.access.CoreOrPluginProjectPermission;
import com.google.gerrit.extensions.api.access.GlobalOrPluginPermission;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.ImplementedBy;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Checks authorization to perform an action on a project, reference, or change.
 *
 * <p>{@code check} methods should be used during action handlers to verify the user is allowed to
 * exercise the specified permission. For convenience in implementation {@code check} methods throw
 * {@link AuthException} if the permission is denied.
 *
 * <p>{@code test} methods should be used when constructing replies to the client and the result
 * object needs to include a true/false hint indicating the user's ability to exercise the
 * permission. This is suitable for configuring UI button state, but should not be relied upon to
 * guard handlers before making state changes.
 *
 * <p>{@code PermissionBackend} is a singleton for the server, acting as a factory for lightweight
 * request instances. Implementation classes may cache supporting data inside of {@link WithUser},
 * {@link ForProject}, {@link ForRef}, and {@link ForChange} instances, in addition to storing
 * within {@link CurrentUser} using a {@link com.google.gerrit.server.CurrentUser.PropertyKey}.
 * {@link GlobalPermission} caching for {@link WithUser} may best cached inside {@link CurrentUser}
 * as {@link WithUser} instances are frequently created.
 *
 * <p>Example use:
 *
 * <pre>
 *   private final PermissionBackend permissions;
 *   private final Provider<CurrentUser> user;
 *
 *   {@literal @}Inject
 *   Foo(PermissionBackend permissions, Provider<CurrentUser> user) {
 *     this.permissions = permissions;
 *     this.user = user;
 *   }
 *
 *   public void apply(...) {
 *     permissions.user(user).change(cd).check(ChangePermission.SUBMIT);
 *   }
 *
 *   public UiAction.Description getDescription(ChangeResource rsrc) {
 *     return new UiAction.Description()
 *       .setLabel("Submit")
 *       .setVisible(rsrc.permissions().testCond(ChangePermission.SUBMIT));
 * }
 * </pre>
 */
@ImplementedBy(DefaultPermissionBackend.class)
public abstract class PermissionBackend {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Returns an instance scoped to the current user. */
  public abstract WithUser currentUser();

  /**
   * Returns an instance scoped to the specified user. Should be used in cases where the user could
   * either be the issuer of the current request or an impersonated user. PermissionBackends that do
   * not support impersonation can fail with an {@code IllegalStateException}.
   *
   * <p>If an instance scoped to the current user is desired, use {@code currentUser()} instead.
   */
  public abstract WithUser user(CurrentUser user);

  /**
   * Returns an instance scoped to the provided user. Should be used in cases where the caller wants
   * to check the permissions of a user who is not the issuer of the current request and not the
   * target of impersonation.
   *
   * <p>Usage should be very limited as this can expose a group-oracle.
   */
  public abstract WithUser absentUser(Account.Id id);

  /**
   * Check whether this {@code PermissionBackend} respects the same global capabilities as the
   * {@link DefaultPermissionBackend}.
   *
   * <p>If true, then it makes sense for downstream callers to refer to built-in Gerrit capability
   * names in user-facing error messages, for example.
   *
   * @return whether this is the default permission backend.
   */
  public boolean usesDefaultCapabilities() {
    return false;
  }

  /**
   * Throw {@link ResourceNotFoundException} if this backend does not use the default global
   * capabilities.
   */
  public void checkUsesDefaultCapabilities() throws ResourceNotFoundException {
    if (!usesDefaultCapabilities()) {
      throw new ResourceNotFoundException("Gerrit capabilities not used on this server");
    }
  }

  /**
   * Bulk evaluate a set of {@link PermissionBackendCondition} for view handling.
   *
   * <p>Overridden implementations should call {@link PermissionBackendCondition#set(boolean)} to
   * cache the result of {@code testOrFalse} in the condition for later evaluation. Caching the
   * result will bypass the usual invocation of {@code testOrFalse}.
   *
   * @param conds conditions to consider.
   */
  public void bulkEvaluateTest(Set<PermissionBackendCondition> conds) {
    // Do nothing by default. The default implementation of PermissionBackendCondition
    // delegates to the appropriate testOrFalse method in PermissionBackend.
  }

  /** PermissionBackend scoped to a specific user. */
  public abstract static class WithUser {
    /** Returns an instance scoped for the specified project. */
    public abstract ForProject project(Project.NameKey project);

    /** Returns an instance scoped for the {@code ref}, and its parent project. */
    public ForRef ref(BranchNameKey ref) {
      return project(ref.project()).ref(ref.branch());
    }

    /** Returns an instance scoped for the change, and its destination ref and project. */
    public ForChange change(ChangeData cd) {
      try {
        return ref(cd.change().getDest()).change(cd);
      } catch (StorageException e) {
        return FailedPermissionBackend.change("unavailable", e);
      }
    }

    /** Returns an instance scoped for the change, and its destination ref and project. */
    public ForChange change(ChangeNotes notes) {
      return ref(notes.getChange().getDest()).change(notes);
    }

    /** Verify scoped user can {@code perm}, throwing if denied. */
    public abstract void check(GlobalOrPluginPermission perm)
        throws AuthException, PermissionBackendException;

    /**
     * Verify scoped user can perform at least one listed permission.
     *
     * <p>If {@code any} is empty, the method completes normally and allows the caller to continue.
     * Since no permissions were supplied to check, its assumed no permissions are necessary to
     * continue with the caller's operation.
     *
     * <p>If the user has at least one of the permissions in {@code any}, the method completes
     * normally, possibly without checking all listed permissions.
     *
     * <p>If {@code any} is non-empty and the user has none, {@link AuthException} is thrown for one
     * of the failed permissions.
     *
     * @param any set of permissions to check.
     */
    public void checkAny(Set<GlobalOrPluginPermission> any)
        throws PermissionBackendException, AuthException {
      for (Iterator<GlobalOrPluginPermission> itr = any.iterator(); itr.hasNext(); ) {
        try {
          check(itr.next());
          return;
        } catch (AuthException err) {
          if (!itr.hasNext()) {
            throw err;
          }
        }
      }
    }

    /** Filter {@code permSet} to permissions scoped user might be able to perform. */
    public abstract <T extends GlobalOrPluginPermission> Set<T> test(Collection<T> permSet)
        throws PermissionBackendException;

    public boolean test(GlobalOrPluginPermission perm) throws PermissionBackendException {
      return test(Collections.singleton(perm)).contains(perm);
    }

    public boolean testOrFalse(GlobalOrPluginPermission perm) {
      try {
        return test(perm);
      } catch (PermissionBackendException e) {
        logger.atWarning().withCause(e).log("Cannot test %s; assuming false", perm);
        return false;
      }
    }

    public abstract BooleanCondition testCond(GlobalOrPluginPermission perm);

    /**
     * Filter a set of projects using {@code check(perm)}.
     *
     * @param perm required permission in a project to be included in result.
     * @param projects candidate set of projects; may be empty.
     * @return filtered set of {@code projects} where {@code check(perm)} was successful.
     * @throws PermissionBackendException backend cannot access its internal state.
     */
    public Set<Project.NameKey> filter(ProjectPermission perm, Collection<Project.NameKey> projects)
        throws PermissionBackendException {
      requireNonNull(perm, "ProjectPermission");
      requireNonNull(projects, "projects");
      Set<Project.NameKey> allowed = Sets.newHashSetWithExpectedSize(projects.size());
      for (Project.NameKey project : projects) {
        try {
          project(project).check(perm);
          allowed.add(project);
        } catch (AuthException e) {
          // Do not include this project in allowed.
        } catch (PermissionBackendException e) {
          if (e.getCause() instanceof RepositoryNotFoundException) {
            logger.atWarning().withCause(e).log(
                "Could not find repository of the project %s", project.get());
            // Do not include this project because doesn't exist
          } else {
            throw e;
          }
        }
      }
      return allowed;
    }
  }

  /** PermissionBackend scoped to a user and project. */
  public abstract static class ForProject {
    /** Returns the fully qualified resource path that this instance is scoped to. */
    public abstract String resourcePath();

    /** Returns an instance scoped for {@code ref} in this project. */
    public abstract ForRef ref(String ref);

    /** Returns an instance scoped for the change, and its destination ref and project. */
    public ForChange change(ChangeData cd) {
      try {
        return ref(cd.change().getDest().branch()).change(cd);
      } catch (StorageException e) {
        return FailedPermissionBackend.change("unavailable", e);
      }
    }

    /** Returns an instance scoped for the change, and its destination ref and project. */
    public ForChange change(ChangeNotes notes) {
      return ref(notes.getChange().getDest().branch()).change(notes);
    }

    /** Verify scoped user can {@code perm}, throwing if denied. */
    public abstract void check(CoreOrPluginProjectPermission perm)
        throws AuthException, PermissionBackendException;

    /** Filter {@code permSet} to permissions scoped user might be able to perform. */
    public abstract <T extends CoreOrPluginProjectPermission> Set<T> test(Collection<T> permSet)
        throws PermissionBackendException;

    public boolean test(CoreOrPluginProjectPermission perm) throws PermissionBackendException {
      if (perm instanceof ProjectPermission) {
        return test(EnumSet.of((ProjectPermission) perm)).contains(perm);
      }

      // TODO(xchangcheng): implement for plugin defined project permissions.
      return false;
    }

    public boolean testOrFalse(CoreOrPluginProjectPermission perm) {
      try {
        return test(perm);
      } catch (PermissionBackendException e) {
        logger.atWarning().withCause(e).log("Cannot test %s; assuming false", perm);
        return false;
      }
    }

    public abstract BooleanCondition testCond(CoreOrPluginProjectPermission perm);

    /**
     * Filter a list of references by visibility.
     *
     * @param refs a collection of references to filter.
     * @param repo an open {@link Repository} handle for this instance's project
     * @param opts further options for filtering.
     * @return a partition of the provided refs that are visible to the user that this instance is
     *     scoped to.
     * @throws PermissionBackendException if failure consulting backend configuration.
     */
    public abstract Collection<Ref> filter(
        Collection<Ref> refs, Repository repo, RefFilterOptions opts)
        throws PermissionBackendException;
  }

  /** Options for filtering refs using {@link ForProject}. */
  @AutoValue
  public abstract static class RefFilterOptions {
    /** Remove all NoteDb refs (refs/changes/*, refs/users/*, edit refs) from the result. */
    public abstract boolean filterMeta();

    /**
     * Select only refs with names matching prefixes per {@link
     * org.eclipse.jgit.lib.RefDatabase#getRefsByPrefix}.
     */
    public abstract ImmutableList<String> prefixes();

    public abstract Builder toBuilder();

    public static Builder builder() {
      return new AutoValue_PermissionBackend_RefFilterOptions.Builder()
          .setFilterMeta(false)
          .setPrefixes(Collections.singletonList(""));
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setFilterMeta(boolean val);

      public abstract Builder setPrefixes(List<String> prefixes);

      public abstract RefFilterOptions build();
    }

    public static RefFilterOptions defaults() {
      return builder().build();
    }
  }

  /** PermissionBackend scoped to a user, project and reference. */
  public abstract static class ForRef {
    /** Returns a fully qualified resource path that this instance is scoped to. */
    public abstract String resourcePath();

    /** Returns an instance scoped to change. */
    public abstract ForChange change(ChangeData cd);

    /** Returns an instance scoped to change. */
    public abstract ForChange change(ChangeNotes notes);

    /** Verify scoped user can {@code perm}, throwing if denied. */
    public abstract void check(RefPermission perm) throws AuthException, PermissionBackendException;

    /** Filter {@code permSet} to permissions scoped user might be able to perform. */
    public abstract Set<RefPermission> test(Collection<RefPermission> permSet)
        throws PermissionBackendException;

    public boolean test(RefPermission perm) throws PermissionBackendException {
      return test(EnumSet.of(perm)).contains(perm);
    }

    /**
     * Test if user may be able to perform the permission.
     *
     * <p>Similar to {@link #test(RefPermission)} except this method returns {@code false} instead
     * of throwing an exception.
     *
     * @param perm the permission to test.
     * @return true if the user might be able to perform the permission; false if the user may be
     *     missing the necessary grants or state, or if the backend threw an exception.
     */
    public boolean testOrFalse(RefPermission perm) {
      try {
        return test(perm);
      } catch (PermissionBackendException e) {
        logger.atWarning().withCause(e).log("Cannot test %s; assuming false", perm);
        return false;
      }
    }

    public abstract BooleanCondition testCond(RefPermission perm);
  }

  /** PermissionBackend scoped to a user, project, reference and change. */
  public abstract static class ForChange {
    /** Returns the fully qualified resource path that this instance is scoped to. */
    public abstract String resourcePath();

    /** Verify scoped user can {@code perm}, throwing if denied. */
    public abstract void check(ChangePermissionOrLabel perm)
        throws AuthException, PermissionBackendException;

    /** Filter {@code permSet} to permissions scoped user might be able to perform. */
    public abstract <T extends ChangePermissionOrLabel> Set<T> test(Collection<T> permSet)
        throws PermissionBackendException;

    public boolean test(ChangePermissionOrLabel perm) throws PermissionBackendException {
      return test(Collections.singleton(perm)).contains(perm);
    }

    /**
     * Test if user may be able to perform the permission.
     *
     * <p>Similar to {@link #test(ChangePermissionOrLabel)} except this method returns {@code false}
     * instead of throwing an exception.
     *
     * @param perm the permission to test.
     * @return true if the user might be able to perform the permission; false if the user may be
     *     missing the necessary grants or state, or if the backend threw an exception.
     */
    public boolean testOrFalse(ChangePermissionOrLabel perm) {
      try {
        return test(perm);
      } catch (PermissionBackendException e) {
        logger.atWarning().withCause(e).log("Cannot test %s; assuming false", perm);
        return false;
      }
    }

    public abstract BooleanCondition testCond(ChangePermissionOrLabel perm);

    /**
     * Test which values of a label the user may be able to set.
     *
     * @param label definition of the label to test values of.
     * @return set containing values the user may be able to use; may be empty if none.
     * @throws PermissionBackendException if failure consulting backend configuration.
     */
    public Set<LabelPermission.WithValue> test(LabelType label) throws PermissionBackendException {
      return test(valuesOf(requireNonNull(label, "LabelType")));
    }

    /**
     * Test which values of a group of labels the user may be able to set.
     *
     * @param types definition of the labels to test values of.
     * @return set containing values the user may be able to use; may be empty if none.
     * @throws PermissionBackendException if failure consulting backend configuration.
     */
    public Set<LabelPermission.WithValue> testLabels(Collection<LabelType> types)
        throws PermissionBackendException {
      requireNonNull(types, "LabelType");
      return test(types.stream().flatMap(t -> valuesOf(t).stream()).collect(toSet()));
    }

    private static Set<LabelPermission.WithValue> valuesOf(LabelType label) {
      return label.getValues().stream()
          .map(v -> new LabelPermission.WithValue(label, v))
          .collect(toSet());
    }
  }
}
