package com.google.gerrit.httpd.raw;

import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

public interface PrivateToPublicHostMapProvider {
  Optional<Map<String, String>> getMapForRequest(HttpServletRequest request);
}
