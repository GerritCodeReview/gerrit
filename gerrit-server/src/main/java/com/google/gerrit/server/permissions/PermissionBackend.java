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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Sets;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.extensions.api.access.GlobalOrPluginPermission;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.DefaultPermissionBackend;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.ImplementedBy;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   @Inject
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
  private static final Logger logger = LoggerFactory.getLogger(PermissionBackend.class);

  /** @return lightweight factory scoped to answer for the specified user. */
  public abstract WithUser user(CurrentUser user);

  /** @return lightweight factory scoped to answer for the specified user. */
  public <U extends CurrentUser> WithUser user(Provider<U> user) {
    return user(checkNotNull(user, "Provider<CurrentUser>").get());
  }

  /**
   * Bulk evaluate a collection of {@link PermissionBackendCondition} for view handling.
   *
   * <p>Overridden implementations should call {@link PermissionBackendCondition#set(boolean)} to
   * cache the result of {@code testOrFalse} in the condition for later evaluation. Caching the
   * result will bypass the usual invocation of {@code testOrFalse}.
   *
   * <p>{@code conds} may contain duplicate entries (such as same user, resource, permission
   * triplet). When duplicates exist, implementations should set a result into all instances to
   * ensure {@code testOrFalse} does not get invoked during evaluation of the containing condition.
   *
   * @param conds conditions to consider.
   */
  public void bulkEvaluateTest(Collection<PermissionBackendCondition> conds) {
    // Do nothing by default. The default implementation of PermissionBackendCondition
    // delegates to the appropriate testOrFalse method in PermissionBackend.
  }

  /** PermissionBackend with an optional per-request ReviewDb handle. */
  public abstract static class AcceptsReviewDb<T> {
    protected Provider<ReviewDb> db;

    public T database(Provider<ReviewDb> db) {
      if (db != null) {
        this.db = db;
      }
      return self();
    }

    public T database(ReviewDb db) {
      return database(Providers.of(checkNotNull(db, "ReviewDb")));
    }

    @SuppressWarnings("unchecked")
    private T self() {
      return (T) this;
    }
  }

  /** PermissionBackend scoped to a specific user. */
  public abstract static class WithUser extends AcceptsReviewDb<WithUser> {
    /** @return instance scoped for the specified project. */
    public abstract ForProject project(Project.NameKey project);

    /** @return instance scoped for the {@code ref}, and its parent project. */
    public ForRef ref(Branch.NameKey ref) {
      return project(ref.getParentKey()).ref(ref.get()).database(db);
    }

    /** @return instance scoped for the change, and its destination ref and project. */
    public ForChange change(ChangeData cd) {
      try {
        return ref(cd.change().getDest()).change(cd);
      } catch (OrmException e) {
        return FailedPermissionBackend.change("unavailable", e);
      }
    }

    /** @return instance scoped for the change, and its destination ref and project. */
    public ForChange change(ChangeNotes notes) {
      return ref(notes.getChange().getDest()).change(notes);
    }

    /**
     * @return instance scoped for the change loaded from index, and its destination ref and
     *     project. This method should only be used when database access is harmful and potentially
     *     stale data from the index is acceptable.
     */
    public ForChange indexedChange(ChangeData cd, ChangeNotes notes) {
      return ref(notes.getChange().getDest()).indexedChange(cd, notes);
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
        logger.warn("Cannot test " + perm + "; assuming false", e);
        return false;
      }
    }

    public BooleanCondition testCond(GlobalOrPluginPermission perm) {
      return new PermissionBackendCondition.WithUser(this, perm);
    }

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
      checkNotNull(perm, "ProjectPermission");
      checkNotNull(projects, "projects");
      Set<Project.NameKey> allowed = Sets.newHashSetWithExpectedSize(projects.size());
      for (Project.NameKey project : projects) {
        try {
          project(project).check(perm);
          allowed.add(project);
        } catch (AuthException e) {
          // Do not include this project in allowed.
        } catch (PermissionBackendException e) {
          if (e.getCause() instanceof RepositoryNotFoundException) {
            logger.warn("Could not find repository of the project {} : ", project.get(), e);
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
  public abstract static class ForProject extends AcceptsReviewDb<ForProject> {
    /** @return new instance rescoped to same project, but different {@code user}. */
    public abstract ForProject user(CurrentUser user);

    /** @return instance scoped for {@code ref} in this project. */
    public abstract ForRef ref(String ref);

    /** @return instance scoped for the change, and its destination ref and project. */
    public ForChange change(ChangeData cd) {
      try {
        return ref(cd.change().getDest().get()).change(cd);
      } catch (OrmException e) {
        return FailedPermissionBackend.change("unavailable", e);
      }
    }

    /** @return instance scoped for the change, and its destination ref and project. */
    public ForChange change(ChangeNotes notes) {
      return ref(notes.getChange().getDest().get()).change(notes);
    }

    /**
     * @return instance scoped for the change loaded from index, and its destination ref and
     *     project. This method should only be used when database access is harmful and potentially
     *     stale data from the index is acceptable.
     */
    public ForChange indexedChange(ChangeData cd, ChangeNotes notes) {
      return ref(notes.getChange().getDest().get()).indexedChange(cd, notes);
    }

    /** Verify scoped user can {@code perm}, throwing if denied. */
    public abstract void check(ProjectPermission perm)
        throws AuthException, PermissionBackendException;

    /** Filter {@code permSet} to permissions scoped user might be able to perform. */
    public abstract Set<ProjectPermission> test(Collection<ProjectPermission> permSet)
        throws PermissionBackendException;

    public boolean test(ProjectPermission perm) throws PermissionBackendException {
      return test(EnumSet.of(perm)).contains(perm);
    }

    public boolean testOrFalse(ProjectPermission perm) {
      try {
        return test(perm);
      } catch (PermissionBackendException e) {
        logger.warn("Cannot test " + perm + "; assuming false", e);
        return false;
      }
    }

    public BooleanCondition testCond(ProjectPermission perm) {
      return new PermissionBackendCondition.ForProject(this, perm);
    }
  }

  /** PermissionBackend scoped to a user, project and reference. */
  public abstract static class ForRef extends AcceptsReviewDb<ForRef> {
    /** @return new instance rescoped to same reference, but different {@code user}. */
    public abstract ForRef user(CurrentUser user);

    /** @return instance scoped to change. */
    public abstract ForChange change(ChangeData cd);

    /** @return instance scoped to change. */
    public abstract ForChange change(ChangeNotes notes);

    /**
     * @return instance scoped to change loaded from index. This method should only be used when
     *     database access is harmful and potentially stale data from the index is acceptable.
     */
    public abstract ForChange indexedChange(ChangeData cd, ChangeNotes notes);

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
        logger.warn("Cannot test " + perm + "; assuming false", e);
        return false;
      }
    }

    public BooleanCondition testCond(RefPermission perm) {
      return new PermissionBackendCondition.ForRef(this, perm);
    }
  }

  /** PermissionBackend scoped to a user, project, reference and change. */
  public abstract static class ForChange extends AcceptsReviewDb<ForChange> {
    /** @return user this instance is scoped to. */
    public abstract CurrentUser user();

    /** @return new instance rescoped to same change, but different {@code user}. */
    public abstract ForChange user(CurrentUser user);

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
        logger.warn("Cannot test " + perm + "; assuming false", e);
        return false;
      }
    }

    public BooleanCondition testCond(ChangePermissionOrLabel perm) {
      return new PermissionBackendCondition.ForChange(this, perm);
    }

    /**
     * Test which values of a label the user may be able to set.
     *
     * @param label definition of the label to test values of.
     * @return set containing values the user may be able to use; may be empty if none.
     * @throws PermissionBackendException if failure consulting backend configuration.
     */
    public Set<LabelPermission.WithValue> test(LabelType label) throws PermissionBackendException {
      return test(valuesOf(checkNotNull(label, "LabelType")));
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
      checkNotNull(types, "LabelType");
      return test(types.stream().flatMap((t) -> valuesOf(t).stream()).collect(toSet()));
    }

    private static Set<LabelPermission.WithValue> valuesOf(LabelType label) {
      return label.getValues().stream()
          .map((v) -> new LabelPermission.WithValue(label, v))
          .collect(toSet());
    }
  }
}
