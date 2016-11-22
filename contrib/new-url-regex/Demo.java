package com.google.gerrit.playground;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * This class demonstrates hash extraction based on the Gerrit's new URL schema.
 * It shows, that we can extract a proper sharding hash using a single RE2-based regular
 * expression.
 */
public class Demo {

  public static void main(String args[]) {
    /** 
     * This example shows how we can extract a sharding key from the URL based on different criteria.
     * For standard hosts, we extract the project name and use it for routing. An off-the shelf load-
     * balancer can use this information to assign requests to an instance group.
     * For special hosts, that receive a lot of traffic, we also extract the hostname and use host +
     * project for assigning a request to an instance group.
     * For this example, we assume that android-review and go-review are popular hosts and should be
     * sharded by host and reponame. All others should be sharded by hostname.
     */
    String[] examplePaths = {
      // - GWT UI Calls
      "/#/p/platform/system/bt/+/c/248741/",
      "/#/q/status:open",
      // - Polygerrit UI Calls
      "/p/platform/system/bt/+/c/248741/",
      "/q/status:open",
      // -- Repo name containing delimiter
      "/p/platform/system/bt/+/c/+/c/248741/",
      "/p/platform/system/bt/+/c/folder/+/c/248741/",
      // - REST API Calls
      "/changes/platform/system/bt/+/123123",
      "/changes/platform/system/bt/+/123123/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/commit",
      "/projects/platform/system/bt/+/HEAD",
      "/accounts/self/preferences",
      "/a/accounts/self/preferences",
      // add account get pref
      // -- Repo name containing delimiter
      "/changes/platform/system/bt/+/something/+/123123",
      // - Git Wire Protocol Calls
      "/platform/system/bt",
      // -- Repo name containing delimiter
      "/platform/system/bt/+",
    };

    String[] exampleHosts = {
      "https://android-review.googlesource.com",
      "https://go-review.googlesource.com",
      "https://some-other-host-review.googlesource.com",
    };

    // Assuming, that we can provide at least one regex to the load-balancer that is matched based on RE2:
    // We've added list here and concat this later for better readability.
    String[] patterns = {
      // - HTTPS
      // -- Popular Hosts
      // --- General UI
      "https://(android|go)-review.googlesource.com(?:/#)?/p/(.*)/\\+/c/",
      // --- Dashboard (Map to hostname)
      "https://(android|go)-review.googlesource.com(?:/#)?/(?:q)",
      // --- REST API: Changes and Projects
      "https://(android|go)-review.googlesource.com/(?:changes|projects)/(.*)/\\+/",
      // --- REST API: Other (Map to hostname)
      "https://(android|go)-review.googlesource.com/(?:a/)?(?:access|accounts|changes|config|groups|plugins|projects)",
      // --- Git Wire Protocol
      "https://(android|go)-review.googlesource.com/(.*)",
      // -- Non-popular hosts
      "https://([a-z-]+)-review",
      // - RPC/SSO
      // -- Non-popular hosts
      "(?:sso|rpc)://(android|go)/(.*)",
      // -- Non-popular hosts
      "https://([a-z-]+)/",
    };

    Pattern p = Pattern.compile(Arrays.stream(patterns).collect(Collectors.joining("|")));

    // The desired hash is a concatenation of all non-null matching groups.
    for (String host : exampleHosts) {
      System.out.println("\nChecking regex on " + host + ":");
      for (String path : examplePaths) {
        Matcher m = p.matcher(host + path);
        if (m.find()) {
          StringBuilder b = new StringBuilder();
          // In Java, capturing groups are indexed from left to right, starting at one.
          // Group zero denotes the entire pattern
          for (int i = 1; i <= m.groupCount(); i++) {
            if (m.group(i) == null) {
              continue;
            }
            if (b.length() > 0) {
              b.append('-');
            }
            b.append(m.group(i));
          }
          System.out.println(b);
        } else {
          System.out.println("No Match");
        }
      }
    }
  }
}
