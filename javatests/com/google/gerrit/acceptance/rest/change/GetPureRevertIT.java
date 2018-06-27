package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.api.changes.ChangeEditApi;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.PureRevertInfo;
import org.junit.Test;

public class GetPureRevertIT extends AbstractDaemonTest {
  @Test
  public void createNormalChange_NotPureRevert() throws Exception {
    ChangeInfo revertCommit = createRevertChange();
    ChangeEditApi edit = gApi.changes().id(revertCommit.changeId).edit();
    edit.modifyFile("readme", RawInputUtil.create("new content"));
    edit.publish();

    PureRevertInfo pureRevertInfo = gApi.changes().id(revertCommit.changeId).pureRevert();
    assertThat(pureRevertInfo.isPureRevert).isFalse();
  }

  @Test
  public void createRevertChange_IsPureRevert() throws Exception {
    ChangeInfo revertCommit = createRevertChange();
    PureRevertInfo pureRevertInfo = gApi.changes().id(revertCommit.changeId).pureRevert();
    assertThat(pureRevertInfo.isPureRevert).isTrue();
  }

  private ChangeInfo createRevertChange() throws Exception {
    Result commit1 = createChange("Add tests for pure revert", "GetPureRevertIT", "demo code");
    approve(commit1.getChangeId());
    gApi.changes().id(commit1.getChangeId()).current().submit();
    return gApi.changes().id(commit1.getChangeId()).revert().get();
  }
}
