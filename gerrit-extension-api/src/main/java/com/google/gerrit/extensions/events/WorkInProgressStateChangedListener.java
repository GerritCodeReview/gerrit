
package com.google.gerrit.extensions.events;

public interface WorkInProgressStateChangedListener {
  interface Event extends ChangeEvent {
    boolean isWip();
  }

  void onWorkInProgressStateChanged(Event event);
}
