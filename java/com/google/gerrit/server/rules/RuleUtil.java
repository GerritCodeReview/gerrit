package com.google.gerrit.server.rules;

import org.eclipse.jgit.lib.Config;

/** Provides utility methods for configuring and running Prolog rules inside Gerrit. */
public class RuleUtil {

  /**
   * Returns the reduction limit to be applied to the Prolog machine to prevent infinite loops and
   * other forms of computational overflow.
   */
  public static int reductionLimit(Config gerritConfig) {
    int limit = gerritConfig.getInt("rules", null, "reductionLimit", 100000);
    return limit <= 0 ? Integer.MAX_VALUE : limit;
  }

  /**
   * Returns the compile reduction limit to be applied to the Prolog machine to prevent infinite
   * loops and other forms of computational overflow. The compiled reduction limit should be used
   * when user-provided Prolog code is compiled by the interpreter before the limit gets applied.
   */
  public static int compileReductionLimit(Config gerritConfig) {
    int limit =
        gerritConfig.getInt(
            "rules",
            null,
            "compileReductionLimit",
            (int) Math.min(10L * reductionLimit(gerritConfig), Integer.MAX_VALUE));
    return limit <= 0 ? Integer.MAX_VALUE : limit;
  }
}
