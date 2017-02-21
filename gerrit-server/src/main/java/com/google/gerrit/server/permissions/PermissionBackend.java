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

import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks authorization to perform an action on project, ref, or change.
 *
 * <p>{@code PermissionBackend} should be a singleton for the server, acting as a factory for
 * lightweight request instances.
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
 *       .setVisible(rsrc.permissions().testOrFalse(ChangePermission.SUBMIT));
 * }
 * </pre>
 */
public abstract class PermissionBackend {
  private static final Logger logger = LoggerFactory.getLogger(PermissionBackend.class);

  /** @return lightweight factory scoped to answer for the specified user. */
  public abstract WithUser user(CurrentUser user);

  /** @return lightweight factory scoped to answer for the specified user. */
  public <U extends CurrentUser> WithUser user(Provider<U> user) {
    return user(checkNotNull(user, "Provider<CurrentUser>").get());
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
  }

  /** PermissionBackend scoped to a user and project. */
  public abstract static class ForProject extends AcceptsReviewDb<ForProject> {
    /** @return new instance rescoped to same project, but different {@code user}. */
    public abstract ForProject user(CurrentUser user);

    /** @return instance scoped for {@code ref} in this project. */
    public abstract ForRef ref(String ref);

    /** Verify scoped user can {@code perm}, throwing if denied. */
    public abstract void check(ProjectPermission perm)
        throws AuthException, PermissionBackendException;

    /** Filter {@code permSet} to permissions scoped user might be able to perform. */
    public abstract Set<ProjectPermission> test(Collection<ProjectPermission> permSet)
        throws PermissionBackendException;

    public boolean test(ProjectPermission perm) throws PermissionBackendException {
      return test(EnumSet.of(perm)).contains(perm);
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

    /** Verify scoped user can {@code perm}, throwing if denied. */
    public abstract void check(RefPermission perm) throws AuthException, PermissionBackendException;

    /** Filter {@code permSet} to permissions scoped user might be able to perform. */
    public abstract Set<RefPermission> test(Collection<RefPermission> permSet)
        throws PermissionBackendException;

    public boolean test(RefPermission perm) throws PermissionBackendException {
      return test(EnumSet.of(perm)).contains(perm);
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
      return test(types.stream().flatMap((t) -> valuesOf(t).stream()).collect(Collectors.toSet()));
    }

    private static Set<LabelPermission.WithValue> valuesOf(LabelType label) {
      return label
          .getValues()
          .stream()
          .map((v) -> new LabelPermission.WithValue(label, v))
          .collect(Collectors.toSet());
    }

    /**
     * Squash a label value to the nearest allowed value.
     *
     * <p>For multi-valued labels like Code-Review with values -2..+2 a user may try to use +2, but
     * only have permission for the -1..+1 range. The caller should have already tried:
     *
     * <pre>
     * check(new LabelPermission.WithValue("Code-Review", 2));
     * </pre>
     *
     * and caught {@link AuthException}. {@code squashThenCheck} will use {@link #test(LabelType)}
     * to determine potential values of Code-Review the user can use, and select the nearest value
     * along the same sign, e.g. -1 for -2 and +1 for +2.
     *
     * @param label definition of the label to test values of.
     * @param val previously denied value the user attempted.
     * @return nearest allowed value, or {@code 0} if no value was allowed.
     * @throws PermissionBackendException backend cannot run test or check.
     */
    public short squashThenCheck(LabelType label, short val) throws PermissionBackendException {
      short s = squashByTest(label, val);
      if (s == 0 || s == val) {
        return 0;
      }
      try {
        check(new LabelPermission.WithValue(label, s));
        return s;
      } catch (AuthException e) {
        return 0;
      }
    }

    /**
     * Squash a label value to the nearest allowed value using only test methods.
     *
     * <p>Tests all possible values and selects the closet available to {@code val} while matching
     * the sign of {@code val}. Unlike {@code #squashThenCheck(LabelType, short)} this method only
     * uses {@code test} methods and should not be used in contexts like a review handler without
     * checking the resulting score.
     *
     * @param label definition of the label to test values of.
     * @param val previously denied value the user attempted.
     * @return nearest likely allowed value, or {@code 0} if no value was identified.
     * @throws PermissionBackendException backend cannot run test.
     */
    public short squashByTest(LabelType label, short val) throws PermissionBackendException {
      return nearest(test(label), val);
    }

    private static short nearest(Iterable<LabelPermission.WithValue> possible, short wanted) {
      short s = 0;
      for (LabelPermission.WithValue v : possible) {
        if ((wanted < 0 && v.value() < 0 && wanted <= v.value() && v.value() < s)
            || (wanted > 0 && v.value() > 0 && wanted >= v.value() && v.value() > s)) {
          s = v.value();
        }
      }
      return s;
    }
  }
}
