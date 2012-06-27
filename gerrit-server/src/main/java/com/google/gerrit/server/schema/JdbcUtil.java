package com.google.gerrit.server.schema;

public class JdbcUtil {

  public static String hostname(String hostname) {
    if (hostname == null || hostname.isEmpty()) {
      hostname = "localhost";

    } else if (hostname.contains(":") && !hostname.startsWith("[")) {
      hostname = "[" + hostname + "]";
    }
    return hostname;
  }

  static String port(String port) {
    if (port != null && !port.isEmpty()) {
      return ":" + port;
    }
    return "";
  }
}
