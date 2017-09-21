// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

public final class SocketUtil {
  /** True if this InetAddress is a raw IPv6 in dotted quad notation. */
  public static boolean isIPv6(InetAddress ip) {
    return ip instanceof Inet6Address && ip.getHostName().equals(ip.getHostAddress());
  }

  /** Get the name or IP address, or {@code *} if this address is a wildcard IP. */
  public static String hostname(InetSocketAddress addr) {
    if (addr.getAddress() != null) {
      if (addr.getAddress().isAnyLocalAddress()) {
        return "*";
      }
      return addr.getAddress().getHostName();
    }
    return addr.getHostName();
  }

  /** Format an address string into {@code host:port} or {@code *:port} syntax. */
  public static String format(SocketAddress s, int defaultPort) {
    if (s instanceof InetSocketAddress) {
      final InetSocketAddress addr = (InetSocketAddress) s;
      if (addr.getPort() == defaultPort) {
        return safeHostname(hostname(addr));
      }
      return format(hostname(addr), addr.getPort());
    }
    return s.toString();
  }

  /** Format an address string into {@code host:port} or {@code *:port} syntax. */
  public static String format(String hostname, int port) {
    return safeHostname(hostname) + ":" + port;
  }

  private static String safeHostname(String hostname) {
    if (0 <= hostname.indexOf(':')) {
      hostname = "[" + hostname + "]";
    }
    return hostname;
  }

  /** Parse an address string such as {@code host:port} or {@code *:port}. */
  public static InetSocketAddress parse(String desc, int defaultPort) {
    String hostStr;
    String portStr;

    if (desc.startsWith("[")) {
      // IPv6, as a raw IP address.
      //
      final int hostEnd = desc.indexOf(']');
      if (hostEnd < 0) {
        throw new IllegalArgumentException("invalid IPv6: " + desc);
      }

      hostStr = desc.substring(1, hostEnd);
      portStr = desc.substring(hostEnd + 1);
    } else {
      // IPv4, or a host name.
      //
      final int hostEnd = desc.indexOf(':');
      hostStr = 0 <= hostEnd ? desc.substring(0, hostEnd) : desc;
      portStr = 0 <= hostEnd ? desc.substring(hostEnd) : "";
    }

    if ("".equals(hostStr)) {
      hostStr = "*";
    }
    if (portStr.startsWith(":")) {
      portStr = portStr.substring(1);
    }

    final int port;
    if (portStr.length() > 0) {
      try {
        port = Integer.parseInt(portStr);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("invalid port: " + desc);
      }
    } else {
      port = defaultPort;
    }

    if ("*".equals(hostStr)) {
      return new InetSocketAddress(port);
    }
    return InetSocketAddress.createUnresolved(hostStr, port);
  }

  /** Parse and resolve an address string, looking up the IP address. */
  public static InetSocketAddress resolve(String desc, int defaultPort) {
    final InetSocketAddress addr = parse(desc, defaultPort);
    if (addr.getAddress() != null && addr.getAddress().isAnyLocalAddress()) {
      return addr;
    }
    try {
      final InetAddress host = InetAddress.getByName(addr.getHostName());
      return new InetSocketAddress(host, addr.getPort());
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("unknown host: " + desc, e);
    }
  }

  private SocketUtil() {}
}
