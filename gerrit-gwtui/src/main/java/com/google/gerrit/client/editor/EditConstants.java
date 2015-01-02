package com.google.gerrit.client.editor;

import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.Constants;

interface EditConstants extends Constants {
  static final EditConstants I = GWT.create(EditConstants.class);

  String discardUnsavedChanges();
}
