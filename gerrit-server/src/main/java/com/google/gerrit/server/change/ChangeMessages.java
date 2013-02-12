package com.google.gerrit.server.change;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

public class ChangeMessages extends TranslationBundle {
  public static ChangeMessages get() {
    return NLS.getBundleFor(ChangeMessages.class);
  }

  public String groupIsNotAllowed;
  public String groupHasTooManyMembers;
  public String groupManyMembersConfirmation;
}
