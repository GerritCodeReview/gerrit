package com.google.gerrit.server.query.approval;

import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collection;
import java.util.Objects;

/** Predicate that matches patch set patchset approvals we want to copy based on the value. */
public class MagicValuePredicate extends ApprovalPredicate {
  enum MagicValue {
    MIN,
    MAX,
    ANY
  }

  public interface Factory {
    MagicValuePredicate create(MagicValue value);
  }

  private final MagicValue value;
  private final ProjectCache projectCache;

  @Inject
  MagicValuePredicate(ProjectCache projectCache, @Assisted MagicValue value) {
    this.projectCache = projectCache;
    this.value = value;
  }

  @Override
  public boolean match(ApprovalContext ctx) {
    if (value == MagicValue.ANY) {
      return true;
    }

    ProjectState project =
        projectCache
            .get(ctx.project())
            .orElseThrow(() -> new IllegalStateException(ctx.project() + " absent"));
    if (value == MagicValue.MIN) {
      short min =
          project.getLabelTypes().byLabel(ctx.patchSetApproval().labelId()).getMaxNegative();
      return min == ctx.patchSetApproval().value();
    }
    if (value == MagicValue.MAX) {
      short max =
          project.getLabelTypes().byLabel(ctx.patchSetApproval().labelId()).getMaxPositive();
      return max == ctx.patchSetApproval().value();
    }
    return false;
  }

  @Override
  public Predicate<ApprovalContext> copy(
      Collection<? extends Predicate<ApprovalContext>> children) {
    return new MagicValuePredicate(projectCache, value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof MagicValuePredicate)) {
      return false;
    }
    return ((MagicValuePredicate) other).value.equals(value);
  }
}
