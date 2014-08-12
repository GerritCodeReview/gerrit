package com.google.gerrit.server.cache;

import com.google.common.base.Ticker;

public class AdjustableTicker extends Ticker {

  private long ticks = 0;

  public void set(long ticks) {
    this.ticks = ticks;
  }

  @Override
  public long read() {
    return ticks;
  }

}