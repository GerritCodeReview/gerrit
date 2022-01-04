package com.google.gerrit.server.approval.testing;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.PatchSetApproval.UUID;
import com.google.gerrit.server.approval.PatchSetApprovalUuidGenerator;
import java.time.Instant;
import javax.inject.Singleton;

/**
 * Implementation of {@link PatchSetApprovalUuidGenerator} that returns predictable {@link UUID}.
 */
@Singleton
public class TestPatchSetApprovalUuidGenerator implements PatchSetApprovalUuidGenerator {

  private int invocationCount = 0;

  @Override
  public UUID get(
      PatchSet.Id patchSetId, Account.Id accountId, String label, short value, Instant granted) {
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
