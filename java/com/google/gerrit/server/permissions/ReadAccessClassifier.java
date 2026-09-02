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
 * Pre-compiles read-access rules from a project's ACL sections into matcher
 * sets so that {@link DefaultRefFilter} can classify most refs without
 * invoking the full {@link PermissionCollection} / {@link RefControl} stack.
 *
 * <p>Classification per ref:
 *
 * <ol>
 *   <li>If any <em>block/deny</em> matcher matches → {@link Decision#NEEDS_FULL_CHECK}
 *   <li>If any <em>allow</em> matcher matches → {@link Decision#VISIBLE}
 *   <li>If any <em>per-user</em> ({@code ${username}}) matcher prefix-matches
 *       → {@link Decision#NEEDS_FULL_CHECK}
 *   <li>Otherwise → {@link Decision#INVISIBLE}
 * </ol>
 *
 * <p>Sections with BLOCK or DENY rules are routed to the full-check
 * path rather than short-circuited. This avoids reimplementing the
 * cross-section ACL semantics (BLOCK+ALLOW cancellation in the same
 * section, SeenRule suppression of inherited ALLOWs, exclusive-group
 * override of parent BLOCKs) that already exist in {@link RefControl}
 * and {@link PermissionCollection}.
 *
 * <p>Only plain user-created refs (e.g. {@code refs/heads/*}) should be
 * passed to {@link #classify}. Gerrit-internal refs ({@code refs/changes/*},
 * {@code refs/users/*}, {@code refs/meta/*}, etc.), tag refs, and change
 * refs must not be passed here; they continue to use their existing
 * specialised logic in {@link RefVisibilityControl} and
 * {@link DefaultRefFilter}.
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

  private List<SectionMatcher> allowMatchers;
  private List<SectionMatcher> parentAllowMatchers;
  /**
   * Sections with BLOCK or DENY rules requiring a full ACL check.
   * Checked before allowMatchers because BLOCK/DENY takes precedence.
   */
  private List<SectionMatcher> blockDenyMatchers;
  /** Per-user (${username}) sections; checked after allowMatchers since they only add access. */
  private List<SectionMatcher> perUserMatchers;
  private List<SectionMatcher> exclusiveMatchers;
  private boolean compiled;

  private final ProjectControl projectControl;

  @Inject
  public ReadAccessClassifier(@Assisted ProjectControl projectControl) {
    this.projectControl = projectControl;
  }

  /**
   * Returns {@code true} if this classifier has any allow matchers that could
   * return {@link Decision#VISIBLE} for at least some refs. When this returns
   * {@code false}, every ref would get {@link Decision#NEEDS_FULL_CHECK} or
   * {@link Decision#INVISIBLE}, so callers can skip the classifier entirely
   * and go straight to the full visibility check without any loss.
   */
  public boolean hasShortcuttableRefs() {
    ensureCompiled();
    return !allowMatchers.isEmpty() || !parentAllowMatchers.isEmpty();
  }

  /**
   * Classifies {@code refName} against the pre-compiled ACL sets.
   *
   * <p>Must only be called for non-Gerrit refs.
   */
  public Decision classify(String refName) {
    ensureCompiled();
    // BLOCK/DENY rules take priority — check these first.
    for (SectionMatcher matcher : blockDenyMatchers) {
      if (matcher.match(refName, projectControl.getUser())) {
        return Decision.NEEDS_FULL_CHECK;
      }
    }
    // Pure ALLOW sections — safe to short-circuit.
    for (SectionMatcher matcher : allowMatchers) {
      if (matcher.match(refName, projectControl.getUser())) {
        return Decision.VISIBLE;
      }
    }
    // Check exclusive sections before consulting parent allows.
    // If a ref matches an exclusive section anywhere in the hierarchy, the
    // full ACL check must determine visibility — the classifier cannot safely
    // short-circuit because exclusive sections suppress ancestor allows in ways
    // that depend on the project ordering in PermissionCollection.
    for (SectionMatcher matcher : exclusiveMatchers) {
      if (matcher.match(refName, projectControl.getUser())) {
        return Decision.NEEDS_FULL_CHECK;
      }
    }
    for (SectionMatcher matcher : parentAllowMatchers) {
      if (matcher.match(refName, projectControl.getUser())) {
        return Decision.VISIBLE;
      }
    }
    // Per-user patterns are additive and checked last; they cannot override
    // a VISIBLE decision already made above, but may grant access otherwise.
    for (SectionMatcher matcher : perUserMatchers) {
      if (matcher.getMatcher() instanceof ExpandParameters ep && ep.matchPrefix(refName)) {
        return Decision.NEEDS_FULL_CHECK;
      }
    }
    return Decision.INVISIBLE;
  }

  private void ensureCompiled() {
    if (!compiled) {
      allowMatchers = new ArrayList<>();
      parentAllowMatchers = new ArrayList<>();
      blockDenyMatchers = new ArrayList<>();
      perUserMatchers = new ArrayList<>();
      exclusiveMatchers = new ArrayList<>();
      compileRules();
      compiled = true;
    }
  }

  private void compileRules() {
    Project.NameKey ownProject = projectControl.getProjectState().getNameKey();

    for (SectionMatcher sectionMatcher : projectControl.getProjectState().getAllSections()) {
      Permission readPermission = sectionMatcher.getSection().getPermission(Permission.READ);
      if (readPermission == null) {
        continue;
      }

      boolean isOwnSection = sectionMatcher.getProject().equals(ownProject);

      // Track exclusive READ sections from any project in the hierarchy.
      // An exclusive section in an intermediate parent suppresses allows from
      // grandparent projects (e.g. All-Projects) for refs matching its pattern,
      // mirroring the break in PermissionCollection.calculateAllowRules().
      // Restricting this to ownProject only would allow grandparent allows to
      // leak through for refs covered by a parent's exclusive section.
      if (readPermission.getExclusiveGroup()) {
        exclusiveMatchers.add(sectionMatcher);
      }

      if (sectionMatcher.getMatcher() instanceof ExpandParameters) {
        // Per-user patterns are additive; checked after allow matchers.
        perUserMatchers.add(sectionMatcher);
        continue;
      }

      // Examine the rules that apply to this user in this section.
      boolean hasAllowForUser = false;
      boolean hasBlockOrDenyForUser = false;
      for (PermissionRule rule : readPermission.getRules()) {
        if (!projectControl.match(rule, /* isChangeOwner= */ false)) {
          continue;
        }
        if (rule.isBlock() || rule.isDeny()) {
          hasBlockOrDenyForUser = true;
        } else {
          hasAllowForUser = true;
        }
      }

      if (hasBlockOrDenyForUser) {
        blockDenyMatchers.add(sectionMatcher);
      } else if (hasAllowForUser) {
        if (isOwnSection) {
          allowMatchers.add(sectionMatcher);
        } else {
          parentAllowMatchers.add(sectionMatcher);
        }
      }
    }
  }
}
