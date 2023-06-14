package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import java.util.Optional;

@AutoValue
public abstract class ParentCommitData {
  @AutoValue
  public abstract static class TargetBranch {

    public abstract String branchName();

    public abstract String objectId();

    public static TargetBranch create(String branchName, String objectId) {
      return new AutoValue_ParentCommitData_TargetBranch(branchName, objectId);
    }
  }

  @AutoValue
  public abstract static class ChangeRevision {

    public abstract int changeNumber();

    public abstract String changeId();

    public abstract int patchSetNumber();

    public abstract String status();

    public static ChangeRevision create(
        int changeNumber, String changeId, int patchSetNumber, String status) {
      return new AutoValue_ParentCommitData_ChangeRevision(
          changeNumber, changeId, patchSetNumber, status);
    }
  }

  /** Set if the parent commit is reachable from the target branch. */
  public abstract Optional<TargetBranch> targetBranch();

  /** Set if the parent commit is a patch-set revision of another Gerrit change. */
  public abstract Optional<ChangeRevision> changeRevision();

  public static ParentCommitData create(
      Optional<TargetBranch> targetBranch, Optional<ChangeRevision> changeRevision) {
    return new AutoValue_ParentCommitData(targetBranch, changeRevision);
  }
}
