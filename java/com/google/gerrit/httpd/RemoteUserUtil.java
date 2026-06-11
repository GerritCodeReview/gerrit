// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.httpd;

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.flogger.FluentLogger;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.ProvisionException;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.net.util.SubnetUtils;

@Singleton
public class RemoteUserUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Set<SubnetUtils.SubnetInfo> trustedProxySubnets;
  private final Set<String> trustedProxyNetworks;

  @Inject
  RemoteUserUtil(AuthConfig authConfig) {
    trustedProxyNetworks = authConfig.getTrustedProxyNetworks();

    try {
      trustedProxySubnets =
          trustedProxyNetworks.stream()
              .map(SubnetUtils::new)
              .map(SubnetUtils::getInfo)
              .collect(Collectors.toSet());
    } catch (IllegalArgumentException e) {
      throw new ProvisionException("Invalid auth trusted proxy definition: " + e.getMessage(), e);
    }
  }

  /**
   * Tries to get username from a request with following strategies:
   *
   * <ul>
   *   <li>ServletRequest#getRemoteUser
   *   <li>HTTP 'Authorization' header
   *   <li>Custom HTTP header
   * </ul>
   *
   * @param req request to extract username from.
   * @param loginHeader name of header which is used for extracting username. authentication
   * @return the extracted username or null.
   */
  @Nullable
  public String getRemoteUser(HttpServletRequest req, String loginHeader) {
    boolean isAuthorizationHeader = AUTHORIZATION.equals(loginHeader);

    if (isAuthorizationHeader) {
      String user = emptyToNull(req.getRemoteUser());
      if (user != null) {
        // The container performed the authentication, and has the user
        // identity already decoded for us. Honor that as we have been
        // configured to honor HTTP authentication.
        return user;
      }
    }

    if (!isRequestFromTrustedProxyNetworks(req)) {
      return null;
    }

    String auth = req.getHeader(loginHeader);
    return isAuthorizationHeader
        ?
        // If the container didn't do the authentication we might
        // have done it in the front-end web server. Try to split
        // the identity out of the Authorization header and honor it.
        extractUsername(auth)
        :
        // Nonstandard HTTP header. We have been told to trust this
        // header blindly as-is.
        emptyToNull(auth);
  }

  private boolean isRequestFromTrustedProxyNetworks(HttpServletRequest req) {
    if (trustedProxySubnets.isEmpty()) {
      return true;
    }

    String remoteAddress = req.getRemoteAddr();
    if (isIpv6Address(remoteAddress)) {
      logger.atWarning().log(
          "IPv6 remote address: %s - trusted proxy enforcement supports only IPv4, HTTP header"
              + " rejected",
          remoteAddress);
      return false;
    }

    if (trustedProxyNetworks.contains(remoteAddress + "/32")) {
      return true;
    }

    if (trustedProxySubnets.stream().anyMatch(subnet -> subnet.isInRange(remoteAddress))) {
      return true;
    }

    logger.atWarning().log(
        "Untrusted remote address: %s - authentication via HTTP header rejected", remoteAddress);
    return false;
  }

  /**
   * Extracts username from an HTTP Basic or Digest authentication header.
   *
   * @param auth header value which is used for extracting.
   * @return username if available or null.
   */
  @Nullable
  public static String extractUsername(String auth) {
    auth = emptyToNull(auth);

    if (auth == null) {
      return null;

    } else if (auth.startsWith("Basic ")) {
      auth = auth.substring("Basic ".length());
      auth = new String(BaseEncoding.base64().decode(auth), UTF_8);
      final int c = auth.indexOf(':');
      return c > 0 ? auth.substring(0, c) : null;

    } else if (auth.startsWith("Digest ")) {
      final int u = auth.indexOf("username=\"");
      if (u <= 0) {
        return null;
      }
      auth = auth.substring(u + 10);
      final int e = auth.indexOf('"');
      return e > 0 ? auth.substring(0, e) : null;

    } else {
      return null;
    }
  }

  private static boolean isIpv6Address(String ipAddress) {
    return ipAddress.contains(":");
  }
}
