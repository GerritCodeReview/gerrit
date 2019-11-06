package com.google.gerrit.server.index;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/** Structured result of a staleness check. */
@AutoValue
public abstract class StalenessCheckResult {

  public static StalenessCheckResult notStale() {
    return new AutoValue_StalenessCheckResult(false, Optional.empty());
  }

  public static StalenessCheckResult stale(String reason) {
    return new AutoValue_StalenessCheckResult(true, Optional.of(reason));
  }

  public static StalenessCheckResult stale(String reason, Object... args) {
    return new AutoValue_StalenessCheckResult(true, Optional.of(String.format(reason, args)));
  }

  public abstract boolean isStale();

  public abstract Optional<String> reason();
}
