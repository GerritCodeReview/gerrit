package com.google.gerrit.server.change;

import static com.google.gerrit.server.util.AttentionSetUtil.additionsOnly;
import static java.util.Objects.requireNonNull;

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Set;
import java.util.stream.Collectors;

public class ClearAttentionSetOp implements BatchUpdateOp {

  public interface Factory {
    ClearAttentionSetOp create(String reason);
  }

  private final RemoveFromAttentionSetOp.Factory removeFromAttentionSetOpFactory;
  private final String reason;

  @Inject
  ClearAttentionSetOp(
      RemoveFromAttentionSetOp.Factory removeFromAttentionSetOpFactory, @Assisted String reason) {
    this.removeFromAttentionSetOpFactory = removeFromAttentionSetOpFactory;
    this.reason = requireNonNull(reason, "reason");
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws RestApiException {
    Set attentionUserIdSet =
        additionsOnly(ctx.getNotes().getAttentionSet()).stream()
            .map(a -> a.account())
            .collect(Collectors.toSet());
    return removeFromAttentionSetOpFactory.create(attentionUserIdSet, reason).updateChange(ctx);
  }
}
