package com.google.gerrit.metrics;

/** Type of reservoir for collecting metrics into. */
public enum ReservoirType {
  ExponentiallyDecaying,
  SlidingTimeWindowArray,
  SlidingTimeWindow,
  SlidingWindow,
  Uniform;
}
