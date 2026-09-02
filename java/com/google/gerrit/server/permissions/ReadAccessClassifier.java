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

import com.google.common.flogger.FluentLogger;
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
 * Pre-compiles read-access rules from a project's ACL sections into matcher sets so that {@link
 * DefaultRefFilter} can classify most refs without invoking the full {@link PermissionCollection} /
 * {@link RefControl} stack.
 *
 * <p>Classification per ref:
 *
 * <ol>
 *   <li>If any <em>block/deny</em> matcher matches → {@link Decision#NEEDS_FULL_CHECK} (section
 *       contains a BLOCK or DENY for this user with <em>no</em> same-section ALLOW override;
 *       same-section ALLOW overrides BLOCK, mirroring {@link RefControl#isBlocked})
 *   <li>If any <em>allow</em> matcher matches → {@link Decision#VISIBLE} (includes sections where a
 *       BLOCK is cancelled by a same-section ALLOW for this user)
 *   <li>If any <em>exclusive-invisible</em> matcher matches → {@link Decision#INVISIBLE} (exclusive
 *       section in the hierarchy that grants no access to this user; it stops {@link
 *       PermissionCollection#calculateAllowRules} from walking further up the hierarchy, so the ref
 *       is definitively invisible without a full check)
 *   <li>If any <em>exclusive-blockdeny</em> matcher matches → {@link Decision#NEEDS_FULL_CHECK}
 *       (exclusive section with a BLOCK/DENY for this user; same-section ALLOW-cancels-BLOCK
 *       semantics are too complex to replicate safely here)
 *   <li>If any <em>per-user</em> ({@code ${username}}) matcher prefix-matches → {@link
 *       Decision#NEEDS_FULL_CHECK}
 *   <li>Otherwise → {@link Decision#INVISIBLE}
 * </ol>
 *
 * <p>Sections with BLOCK or DENY rules are routed to the full-check path rather than
 * short-circuited. This avoids reimplementing the cross-section ACL semantics (BLOCK+ALLOW
 * cancellation in the same section, SeenRule suppression of inherited ALLOWs, exclusive-group
 * override of parent BLOCKs) that already exist in {@link RefControl} and {@link
 * PermissionCollection}.
 *
 * <p>Only plain user-created refs (e.g. {@code refs/heads/*}) should be passed to {@link
 * #classify}. Gerrit-internal refs ({@code refs/changes/*}, {@code refs/users/*}, {@code
 * refs/meta/*}, etc.), tag refs, and change refs must not be passed here; they continue to use
 * their existing specialised logic in {@link RefVisibilityControl} and {@link DefaultRefFilter}.
 */
public class ReadAccessClassifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
    /** The ref cannot be short-circuited; fall back to the full {@link RefControl} evaluation. */
    NEEDS_FULL_CHECK,
  }

  /** Own-project ALLOW sections for the current user. */
  private List<SectionMatcher> allowMatchers;

  /** Parent-project ALLOW sections for the current user (no BLOCK/DENY). */
  private List<SectionMatcher> parentAllowMatchers;

  /**
   * Non-exclusive sections with a BLOCK or DENY rule that matches the current user. Checked before
   * allow matchers because BLOCK/DENY takes precedence.
   */
  private List<SectionMatcher> blockDenyMatchers;

  /**
   * Exclusive sections that have <em>no</em> rule (ALLOW or BLOCK/DENY) matching this user.
   *
   * <p>{@link PermissionCollection#calculateAllowRules} stops walking up the project hierarchy when
   * it reaches an exclusive section, regardless of whether that section grants anything to the
   * current user. A ref that matches such a section therefore has no inherited parent ALLOW to fall
   * back on: the ref is definitively invisible for this user and can be short-circuited to {@link
   * Decision#INVISIBLE} without a full ACL check.
   *
   * <p>This is the key optimisation that preserves classifier effectiveness when the common
   * configuration pattern is used: {@code BLOCK Anonymous Users} + {@code ALLOW Registered Users}
   * on {@code refs/*} in All-Projects, with a child project adding an exclusive READ rule on a
   * specific ref pattern (e.g. {@code refs/heads/internal/*}). For registered users who are not in
   * the exclusive group, the classifier can immediately return {@link Decision#INVISIBLE} for all
   * refs under that pattern rather than falling back to a full per-ref ACL evaluation.
   */
  private List<SectionMatcher> exclusiveInvisibleMatchers;

  /**
   * Exclusive sections that have a BLOCK or DENY rule matching the current user. The same-section
   * ALLOW-overrides-BLOCK cancellation semantics in {@link RefControl} are too complex to replicate
   * safely in the classifier, so these still require a full check.
   */
  private List<SectionMatcher> exclusiveBlockDenyMatchers;

  /** Per-user (${username}) sections; checked after allow matchers since they only add access. */
  private List<SectionMatcher> perUserMatchers;

  private boolean compiled;

  private final ProjectControl projectControl;

  @Inject
  public ReadAccessClassifier(@Assisted ProjectControl projectControl) {
    this.projectControl = projectControl;
  }

  /**
   * Returns {@code true} if this classifier can short-circuit at least some refs. When this returns
   * {@code false}, every ref would get {@link Decision#NEEDS_FULL_CHECK} or {@link
   * Decision#INVISIBLE}, so callers can skip the classifier entirely and go straight to the full
   * visibility check without any loss.
   */
  public boolean hasShortcuttableRefs() {
    ensureCompiled();
    return !allowMatchers.isEmpty()
        || !parentAllowMatchers.isEmpty()
        || !exclusiveInvisibleMatchers.isEmpty()
        || !exclusiveBlockDenyMatchers.isEmpty();
  }

  /**
   * Classifies {@code refName} against the pre-compiled ACL sets.
   *
   * <p>Must only be called for non-Gerrit refs.
   */
  public Decision classify(String refName) {
    ensureCompiled();

    // Non-exclusive BLOCK/DENY rules take priority.
    for (SectionMatcher matcher : blockDenyMatchers) {
      if (matcher.match(refName, projectControl.getUser())) {
        logger.atFinest().log(
            "Ref %s matches block/deny section %s: requires full ACL check",
            refName, matcher.getSection());
        return Decision.NEEDS_FULL_CHECK;
      }
    }

    // Own-project ALLOW: definitely visible.
    for (SectionMatcher matcher : allowMatchers) {
      if (matcher.match(refName, projectControl.getUser())) {
        logger.atFinest().log(
            "Ref %s matches allow section %s: visible without full ACL check",
            refName, matcher.getSection());
        return Decision.VISIBLE;
      }
    }

    // Exclusive sections with no rule for this user: calculateAllowRules() stops here and
    // never reaches the parent ALLOW, so the ref is definitively invisible.
    for (SectionMatcher matcher : exclusiveInvisibleMatchers) {
      if (matcher.match(refName, projectControl.getUser())) {
        logger.atFinest().log(
            "Ref %s matches exclusive-invisible section %s: invisible (exclusive stops parent"
                + " allow traversal, no allow for this user)",
            refName, matcher.getSection());
        return Decision.INVISIBLE;
      }
    }

    // Exclusive sections with a BLOCK/DENY for this user: same-section ALLOW-cancels-BLOCK
    // semantics are too complex to replicate safely.
    for (SectionMatcher matcher : exclusiveBlockDenyMatchers) {
      if (matcher.match(refName, projectControl.getUser())) {
        logger.atFinest().log(
            "Ref %s matches exclusive block/deny section %s: requires full ACL check",
            refName, matcher.getSection());
        return Decision.NEEDS_FULL_CHECK;
      }
    }

    // No exclusive section fired, so parent allows are not suppressed.
    for (SectionMatcher matcher : parentAllowMatchers) {
      if (matcher.match(refName, projectControl.getUser())) {
        logger.atFinest().log(
            "Ref %s matches parent allow section %s: visible without full ACL check",
            refName, matcher.getSection());
        return Decision.VISIBLE;
      }
    }

    // Per-user patterns are additive; they cannot override a VISIBLE decision above.
    for (SectionMatcher matcher : perUserMatchers) {
      if (matcher.getMatcher() instanceof ExpandParameters ep && ep.matchPrefix(refName)) {
        logger.atFinest().log(
            "Ref %s matches per-user section %s: requires full ACL check",
            refName, matcher.getSection());
        return Decision.NEEDS_FULL_CHECK;
      }
    }

    logger.atFinest().log(
        "Ref %s does not match any allow or per-user sections: invisible", refName);
    return Decision.INVISIBLE;
  }

  private void ensureCompiled() {
    if (!compiled) {
      allowMatchers = new ArrayList<>();
      parentAllowMatchers = new ArrayList<>();
      blockDenyMatchers = new ArrayList<>();
      exclusiveInvisibleMatchers = new ArrayList<>();
      exclusiveBlockDenyMatchers = new ArrayList<>();
      perUserMatchers = new ArrayList<>();
      compileRules();
      compiled = true;
    }
  }

  private void compileRules() {
    Project.NameKey ownProject = projectControl.getProjectState().getNameKey();

    for (SectionMatcher sectionMatcher : projectControl.getProjectState().getAllSections()) {
      Permission readPermission = sectionMatcher.getSection().getPermission(Permission.READ);
      if (readPermission == null) {
        logger.atFinest().log(
            "Skipping section %s: no READ permission", sectionMatcher.getSection());
        continue;
      }

      boolean isOwnSection = sectionMatcher.getProject().equals(ownProject);

      if (sectionMatcher.getMatcher() instanceof ExpandParameters) {
        // Per-user patterns are additive; checked after allow matchers.
        logger.atFinest().log(
            "Adding per-user matcher for section %s: contains ${username}",
            sectionMatcher.getSection());
        perUserMatchers.add(sectionMatcher);
        continue;
      }

      // Compute which rules in this section apply to the current user before
      // deciding which bucket the section belongs to.
      boolean hasAllowForUser = false;
      boolean hasBlockOrDenyForUser = false;
      for (PermissionRule rule : readPermission.getRules()) {
        if (!projectControl.match(rule, /* isChangeOwner= */ false)) {
          logger.atFinest().log(
              "Skipping rule %s in section %s: does not match user",
              rule, sectionMatcher.getSection());
          continue;
        }
        if (rule.isBlock() || rule.isDeny()) {
          hasBlockOrDenyForUser = true;
        } else {
          hasAllowForUser = true;
        }
      }

      if (readPermission.getExclusiveGroup()) {
        // Exclusive sections stop calculateAllowRules() from walking further up the hierarchy,
        // regardless of whether this user has a rule in the section.  We therefore classify
        // based solely on what the exclusive section itself grants the current user:
        //
        //  • ALLOW for user (with or without BLOCK on other groups):
        //    The allow-bucket logic below will fire before the exclusive checks in classify(),
        //    so we do NOT add to any exclusive bucket — that would cause a spurious
        //    NEEDS_FULL_CHECK on step 3/4 before the allow bucket fires on step 2.
        //
        //  • BLOCK/DENY for user:
        //    Same-section ALLOW-cancels-BLOCK semantics are complex; route to a full check.
        //
        //  • No rule for user:
        //    calculateAllowRules() breaks here and never reaches the parent ALLOW.
        //    The ref is definitively invisible — short-circuit to INVISIBLE.
        if (hasAllowForUser) {
          // Falls through to the allow-bucket logic below; no exclusive bucket needed.
        } else if (hasBlockOrDenyForUser) {
          logger.atFinest().log(
              "Adding exclusive-blockdeny matcher for section %s:"
                  + " exclusive, BLOCK/DENY for user",
              sectionMatcher.getSection());
          exclusiveBlockDenyMatchers.add(sectionMatcher);
          continue; // already handled; skip the non-exclusive buckets below
        } else {
          logger.atFinest().log(
              "Adding exclusive-invisible matcher for section %s: exclusive, no rule for user",
              sectionMatcher.getSection());
          exclusiveInvisibleMatchers.add(sectionMatcher);
          continue; // already handled; skip the non-exclusive buckets below
        }
      }

      // Non-exclusive section (or exclusive section with an ALLOW for this user).
      //
      // When a section has both a BLOCK/DENY and an ALLOW for the current user, RefControl applies
      // the "ALLOW in the same AccessSection overrides BLOCK" rule (see RefControl.isBlocked()).
      // We can mirror that here: the ALLOW wins, so treat the section as a plain allow.
      // This is the key case for the common "BLOCK Anonymous Users + ALLOW Registered Users on
      // refs/*" pattern in All-Projects: every identified user is a member of both
      // ANONYMOUS_USERS and REGISTERED_USERS, so both rules match; the ALLOW cancels the BLOCK
      // and the section belongs in the allow bucket.
      if (hasBlockOrDenyForUser && !hasAllowForUser) {
        logger.atFinest().log(
            "Adding block/deny matcher for section %s: contains BLOCK or DENY rule for user"
                + " with no same-section ALLOW override",
            sectionMatcher.getSection());
        blockDenyMatchers.add(sectionMatcher);
      } else if (hasAllowForUser) {
        if (isOwnSection) {
          logger.atFinest().log(
              "Adding allow matcher for section %s: contains ALLOW rule for user",
              sectionMatcher.getSection());
          allowMatchers.add(sectionMatcher);
        } else {
          logger.atFinest().log(
              "Adding parent allow matcher for section %s:"
                  + " contains ALLOW rule for user in parent project",
              sectionMatcher.getSection());
          parentAllowMatchers.add(sectionMatcher);
        }
      }
    }
  }
}
