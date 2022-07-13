package com.google.gerrit.metrics;

import java.time.Duration;

/** Configuration of the Metrics' reservoir type and size. */
public interface MetricsReservoirConfig {

  /** @return the reservoir type. */
  ReservoirType reservoirType();

  /** @return the reservoir window duration. */
  Duration reservoirWindow();

  /** @return the number of samples that the reservoir can contain */
  int reservoirSize();

  /** @return the alpha parameter of the ExponentiallyDecaying reservoir */
  double reservoirAlpha();
}
