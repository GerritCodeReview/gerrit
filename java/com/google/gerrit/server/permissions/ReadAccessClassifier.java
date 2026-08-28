// Copyright (C) 2026 The Android Open Source Project
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

import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.project.RefPatternMatcher.ExpandParameters;
import com.google.gerrit.server.project.SectionMatcher;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.List;

/**
 * Pre-compiles read-access rules from a project's ACL sections into three disjoint matcher sets so
 * that {@link RefVisibilityControl} can classify most refs without invoking the full {@link
 * PermissionCollection} / {@link RefControl} stack.
 *
 * <p>Classification per ref:
 *
 * <ol>
 *   <li>If any <em>deny</em> matcher matches → {@link Decision#INVISIBLE}
 *   <li>If any <em>per-user</em> matcher prefix-matches → {@link Decision#NEEDS_FULL_CHECK}
 *   <li>If any <em>allow</em> matcher matches → {@link Decision#VISIBLE}
 *   <li>Otherwise → {@link Decision#INVISIBLE}
 * </ol>
 *
 * <p>Only plain user-created refs (e.g. {@code refs/heads/*}) should be passed to {@link
 * #classify}. Gerrit-internal refs ({@code refs/changes/*}, {@code refs/users/*}, {@code
 * refs/meta/*}, etc.), tag refs, and change refs must not be passed here; they continue to use
 * their existing specialised logic in {@link RefVisibilityControl} and {@link DefaultRefFilter}.
 */
public class ReadAccessClassifier {

  /** Guice factory — use this to obtain instances rather than calling {@code new}. */
  public interface Factory {
    ReadAccessClassifier create(ProjectControl projectControl);
  }

  /** Classification outcome for a single ref name. */
  public enum Decision {
    /** The ref is definitely readable; no further ACL check needed. */
    VISIBLE,
    /** The ref is definitely not readable; no further ACL check needed. */
    INVISIBLE,
    /**
     * The ref matches a per-user pattern and cannot be short-circuited; fall back to the full
     * {@link RefControl} evaluation.
     */
    NEEDS_FULL_CHECK,
  }

  private final List<SectionMatcher> allowMatchers = new ArrayList<>();
  private final List<SectionMatcher> parentAllowMatchers = new ArrayList<>();
  private final List<SectionMatcher> denyMatchers = new ArrayList<>();
  private final List<SectionMatcher> perUserMatchers = new ArrayList<>();
  private final List<SectionMatcher> exclusiveMatchers = new ArrayList<>();

  private final ProjectControl projectControl;

  @Inject
  public ReadAccessClassifier(@Assisted ProjectControl projectControl) {
    this.projectControl = projectControl;
    compileRules();
  }

  /**
   * Classifies {@code refName} against the pre-compiled ACL sets.
   *
   * <p>Must only be called for non-Gerrit refs.
   */
  public Decision classify(String refName) {
    for (SectionMatcher matcher : denyMatchers) {
      if (matcher.match(refName, projectControl.getUser())) {
        return Decision.INVISIBLE;
      }
    }
    for (SectionMatcher matcher : perUserMatchers) {
      if (matcher.getMatcher() instanceof ExpandParameters ep && ep.matchPrefix(refName)) {
        return Decision.NEEDS_FULL_CHECK;
      }
    }
    for (SectionMatcher matcher : allowMatchers) {
      if (matcher.match(refName, projectControl.getUser())) {
        return Decision.VISIBLE;
      }
    }
    // Check whether the ref falls under an exclusive section in the child
    // project. If so, the parent allows are suppressed for this ref.
    boolean coveredByExclusiveSection = false;
    for (SectionMatcher matcher : exclusiveMatchers) {
      if (matcher.match(refName, projectControl.getUser())) {
        coveredByExclusiveSection = true;
        break;
      }
    }
    if (!coveredByExclusiveSection) {
      for (SectionMatcher matcher : parentAllowMatchers) {
        if (matcher.match(refName, projectControl.getUser())) {
          return Decision.VISIBLE;
        }
      }
    }
    return Decision.INVISIBLE;
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private void compileRules() {
    Project.NameKey ownProject = projectControl.getProjectState().getNameKey();

    for (SectionMatcher sectionMatcher : projectControl.getProjectState().getAllSections()) {
      Permission readPermission = sectionMatcher.getSection().getPermission(Permission.READ);
      if (readPermission == null) {
        continue;
      }

      boolean isOwnSection = sectionMatcher.getProject().equals(ownProject);

      // Track exclusive READ sections in the current project. Their patterns
      // suppress parent allows for matching refs, mirroring the break in
      // PermissionCollection.calculateAllowRules().
      if (isOwnSection && readPermission.getExclusiveGroup()) {
        exclusiveMatchers.add(sectionMatcher);
      }

      if (sectionMatcher.getMatcher() instanceof ExpandParameters) {
        // Per-user patterns can't be short-circuited; always fall back.
        perUserMatchers.add(sectionMatcher);
        continue;
      }

      // Evaluate the Permission as a whole, mirroring RefControl.isBlocked():
      // an ALLOW in the same Permission cancels a BLOCK in that same section.
      boolean hasBlockForUser = false;
      boolean hasAllowForUser = false;
      boolean hasDenyForUser = false;
      for (PermissionRule rule : readPermission.getRules()) {
        if (!projectControl.match(rule, /* isChangeOwner= */ false)) {
          continue;
        }
        if (rule.isBlock()) {
          hasBlockForUser = true;
        } else if (rule.isDeny()) {
          hasDenyForUser = true;
        } else {
          hasAllowForUser = true;
        }
      }

      if (hasDenyForUser && !hasAllowForUser) {
        // DENY with no overriding ALLOW: ref is invisible.
        denyMatchers.add(sectionMatcher);
      } else if (hasBlockForUser && !hasAllowForUser) {
        // BLOCK with no ALLOW in the same section: ref is blocked.
        denyMatchers.add(sectionMatcher);
      } else if (hasAllowForUser) {
        // ALLOW present (optionally cancelling a BLOCK in the same section).
        if (isOwnSection) {
          allowMatchers.add(sectionMatcher);
        } else {
          parentAllowMatchers.add(sectionMatcher);
        }
      }
    }
  }
}
