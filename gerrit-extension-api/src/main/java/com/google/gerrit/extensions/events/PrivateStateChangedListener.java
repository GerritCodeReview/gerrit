
package com.google.gerrit.extensions.events;

public interface PrivateStateChangedListener {
  interface Event extends ChangeEvent {
    boolean isPrivate();
  }

  void onPrivateStateChanged(Event event);
}
