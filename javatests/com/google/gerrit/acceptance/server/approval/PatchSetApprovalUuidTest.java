package com.google.gerrit.acceptance.server.approval;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.server.approval.PatchSetApprovalUuid;
import com.google.gerrit.server.approval.PatchSetApprovalUuid.GeneratorImpl;
import com.google.gerrit.server.util.time.TimeUtil;
import java.util.Date;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the default implementation of {@link PatchSetApprovalUuid.Generator}. */
@RunWith(JUnit4.class)
public class PatchSetApprovalUuidTest {

  @Test
  public void sameInput_differentUuid() {
    PatchSetApprovalUuid.Generator patchSetApprovalUuidGenerator = new GeneratorImpl();
    for (short value = -2; value <= 2; value++) {
      PatchSet.Id patchSetId = PatchSet.id(Change.id(1), 1);
      Account.Id accountId = Account.id(1);
      String label = LabelId.CODE_REVIEW;
      Date granted = TimeUtil.nowTs();
      PatchSetApproval.UUID uuid1 =
          patchSetApprovalUuidGenerator.get(patchSetId, accountId, label, value, granted);
      PatchSetApproval.UUID uuid2 =
          patchSetApprovalUuidGenerator.get(patchSetId, accountId, label, value, granted);
      assertThat(uuid2).isNotEqualTo(uuid1);
    }
  }
}
