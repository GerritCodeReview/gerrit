package com.google.gerrit.server;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Comment;
import org.eclipse.jgit.lib.PersonIdent;

/** Verifier for {@link Comment} objects */
public final class CommentVerifier {
  private final Account.Id accountId;
  private final Account.Id realAccountId;
  private final PersonIdent authorIdent;

  public CommentVerifier(Account.Id accountId, Account.Id realAccountId, PersonIdent authorIdent) {
    this.accountId = accountId;
    this.realAccountId = realAccountId != null ? realAccountId : accountId;
    this.authorIdent = authorIdent;
  }

  public void verify(Comment c) {
    checkArgument(c.getCommitId() != null, "commit ID required for comment: %s", c);
    checkArgument(
        c.author.getId().equals(getAccountId()),
        "The author for the following comment does not match the author of this %s (%s): %s",
        getClass().getSimpleName(),
        getAccountId(),
        c);
    checkArgument(
        c.getRealAuthor().getId().equals(realAccountId),
        "The real author for the following comment does not match the real"
            + " author of this %s (%s): %s",
        getClass().getSimpleName(),
        realAccountId,
        c);
  }

  private Account.Id getAccountId() {
    checkState(
        accountId != null,
        "author identity for %s is not from an IdentifiedUser: %s",
        getClass().getSimpleName(),
        authorIdent.toExternalString());
    return accountId;
  }
}
