package com.google.gerrit.server.approval.testing;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.PatchSetApproval.UUID;
import com.google.gerrit.server.approval.PatchSetApprovalUuid;
import java.util.Date;

/**
 * Implementation of {@link PatchSetApprovalUuid.Generator} that returns predictable {@link UUID}.
 */
public class TestPatchSetApprovalUuidGenerator implements PatchSetApprovalUuid.Generator {

  private int invocationCount = 0;

  @Override
  public UUID get(
      PatchSet.Id patchSetId, Account.Id accountId, String label, short value, Date granted) {
    invocationCount++;
    return PatchSetApproval.uuid(
        String.format(
                "%s_%s_%s_%s_%s_%s",
                patchSetId.changeId().get(),
                patchSetId.get(),
                accountId.get(),
                label,
                value,
                invocationCount)
            .replace("-", "_")
            .toLowerCase());
  }
}
