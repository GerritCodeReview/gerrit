package com.google.gerrit.extensions.events;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

@ExtensionPoint
public interface DraftCommentAddedListener {

  interface Event extends ChangeEvent {}

  void onDraftCommentAdded(DraftCommentAddedListener.Event event);
}
