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

// CGI environment and execution management portions are:
//
// ========================================================================
// Copyright (c) 2006-2009 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses.
// ========================================================================

package com.google.gerrit.httpd.gitweb;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.GitwebCgiConfig;
import com.google.gerrit.server.config.GitwebConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Invokes {@code gitweb.cgi} for the project given in {@code p}. */
@SuppressWarnings("serial")
@Singleton
class GitwebServlet extends HttpServlet {
  private static final Logger log = LoggerFactory.getLogger(GitwebServlet.class);

  private static final String PROJECT_LIST_ACTION = "project_list";

  private final Set<String> deniedActions;
  private final int bufferSize = 8192;
  private final Path gitwebCgi;
  private final URI gitwebUrl;
  private final LocalDiskRepositoryManager repoManager;
  private final ProjectCache projectCache;
  private final PermissionBackend permissionBackend;
  private final Provider<AnonymousUser> anonymousUserProvider;
  private final Provider<CurrentUser> userProvider;
  private final EnvList _env;

  @Inject
  GitwebServlet(
      GitRepositoryManager repoManager,
      ProjectCache projectCache,
      PermissionBackend permissionBackend,
      Provider<AnonymousUser> anonymousUserProvider,
      Provider<CurrentUser> userProvider,
      SitePaths site,
      @GerritServerConfig Config cfg,
      SshInfo sshInfo,
      GitwebConfig gitwebConfig,
      GitwebCgiConfig gitwebCgiConfig)
      throws IOException {
    if (!(repoManager instanceof LocalDiskRepositoryManager)) {
      throw new ProvisionException("Gitweb can only be used with LocalDiskRepositoryManager");
    }
    this.repoManager = (LocalDiskRepositoryManager) repoManager;
    this.projectCache = projectCache;
    this.permissionBackend = permissionBackend;
    this.anonymousUserProvider = anonymousUserProvider;
    this.userProvider = userProvider;
    this.gitwebCgi = gitwebCgiConfig.getGitwebCgi();
    this.deniedActions = new HashSet<>();

    final String url = gitwebConfig.getUrl();
    if ((url != null) && (!url.equals("gitweb"))) {
      URI uri = null;
      try {
        uri = new URI(url);
      } catch (URISyntaxException e) {
        log.error("Invalid gitweb.url: " + url);
      }
      gitwebUrl = uri;
    } else {
      gitwebUrl = null;
    }

    deniedActions.add("forks");
    deniedActions.add("opml");
    deniedActions.add("project_index");

    _env = new EnvList();
    makeSiteConfig(site, cfg, sshInfo);

    if (!_env.envMap.containsKey("SystemRoot")) {
      String os = System.getProperty("os.name");
      if (os != null && os.toLowerCase().contains("windows")) {
        String sysroot = System.getenv("SystemRoot");
        if (sysroot == null || sysroot.isEmpty()) {
          sysroot = "C:\\WINDOWS";
        }
        _env.set("SystemRoot", sysroot);
      }
    }

    if (!_env.envMap.containsKey("PATH")) {
      _env.set("PATH", System.getenv("PATH"));
    }
  }

  private void makeSiteConfig(SitePaths site, Config cfg, SshInfo sshInfo) throws IOException {
    if (!Files.exists(site.tmp_dir)) {
      Files.createDirectories(site.tmp_dir);
    }
    Path myconf = Files.createTempFile(site.tmp_dir, "gitweb_config", ".perl");

    // To make our configuration file only readable or writable by us;
    // this reduces the chances of someone tampering with the file.
    //
    // TODO(dborowitz): Is there a portable way to do this with NIO?
    File myconfFile = myconf.toFile();
    myconfFile.setWritable(false, false /* all */);
    myconfFile.setReadable(false, false /* all */);
    myconfFile.setExecutable(false, false /* all */);

    myconfFile.setWritable(true, true /* owner only */);
    myconfFile.setReadable(true, true /* owner only */);

    myconfFile.deleteOnExit();

    _env.set("GIT_DIR", ".");
    _env.set("GITWEB_CONFIG", myconf.toAbsolutePath().toString());

    try (PrintWriter p = new PrintWriter(Files.newBufferedWriter(myconf, UTF_8))) {
      p.print("# Autogenerated by Gerrit Code Review \n");
      p.print("# DO NOT EDIT\n");
      p.print("\n");

      // We are mounted at the same level in the context as the main
      // UI, so we can include the same header and footer scheme.
      //
      Path hdr = site.site_header;
      if (Files.isRegularFile(hdr)) {
        p.print("$site_header = " + quoteForPerl(hdr) + ";\n");
      }
      Path ftr = site.site_footer;
      if (Files.isRegularFile(ftr)) {
        p.print("$site_footer = " + quoteForPerl(ftr) + ";\n");
      }

      // Top level should return to Gerrit's UI.
      //
      p.print("$home_link = $ENV{'GERRIT_CONTEXT_PATH'};\n");
      p.print("$home_link_str = 'Code Review';\n");

      p.print("$favicon = 'favicon.ico';\n");
      p.print("$logo = 'gitweb-logo.png';\n");
      p.print("$javascript = 'gitweb.js';\n");
      p.print("@stylesheets = ('gitweb-default.css');\n");
      Path css = site.site_css;
      if (Files.isRegularFile(css)) {
        p.print("push @stylesheets, 'gitweb-site.css';\n");
      }

      // Try to make the title match Gerrit's normal window title
      // scheme of host followed by 'Code Review'.
      //
      p.print("$site_name = $home_link_str;\n");
      p.print("$site_name = qq{$1 $site_name} if ");
      p.print("$ENV{'SERVER_NAME'} =~ m,^([^.]+(?:\\.[^.]+)?)(?:\\.|$),;\n");

      // Assume by default that XSS is a problem, and try to prevent it.
      //
      p.print("$prevent_xss = 1;\n");

      // Generate URLs using smart http://
      //
      p.print("{\n");
      p.print("  my $secure = $ENV{'HTTPS'} =~ /^ON$/i;\n");
      p.print("  my $http_url = $secure ? 'https://' : 'http://';\n");
      p.print("  $http_url .= qq{$ENV{'GERRIT_USER_NAME'}@}\n");
      p.print("    unless $ENV{'GERRIT_ANONYMOUS_READ'};\n");
      p.print("  $http_url .= $ENV{'SERVER_NAME'};\n");
      p.print("  $http_url .= qq{:$ENV{'SERVER_PORT'}}\n");
      p.print("    if (( $secure && $ENV{'SERVER_PORT'} != 443)\n");
      p.print("     || (!$secure && $ENV{'SERVER_PORT'} != 80)\n");
      p.print("    );\n");
      p.print("  my $context = $ENV{'GERRIT_CONTEXT_PATH'};\n");
      p.print("  chop($context);\n");
      p.print("  $http_url .= qq{$context};\n");
      p.print("  $http_url .= qq{/a}\n");
      p.print("    unless $ENV{'GERRIT_ANONYMOUS_READ'};\n");
      p.print("  push @git_base_url_list, $http_url;\n");
      p.print("}\n");

      // Generate URLs using anonymous git://
      //
      String url = cfg.getString("gerrit", null, "canonicalGitUrl");
      if (url != null) {
        if (url.endsWith("/")) {
          url = url.substring(0, url.length() - 1);
        }
        p.print("if ($ENV{'GERRIT_ANONYMOUS_READ'}) {\n");
        p.print("  push @git_base_url_list, ");
        p.print(quoteForPerl(url));
        p.print(";\n");
        p.print("}\n");
      }

      // Generate URLs using authenticated ssh://
      //
      if (sshInfo != null && !sshInfo.getHostKeys().isEmpty()) {
        String sshAddr = sshInfo.getHostKeys().get(0).getHost();
        p.print("if ($ENV{'GERRIT_USER_NAME'}) {\n");
        p.print("  push @git_base_url_list, join('', 'ssh://'");
        p.print(", $ENV{'GERRIT_USER_NAME'}");
        p.print(", '@'");
        if (sshAddr.startsWith("*:") || "".equals(sshAddr)) {
          p.print(", $ENV{'SERVER_NAME'}");
        }
        if (sshAddr.startsWith("*")) {
          sshAddr = sshAddr.substring(1);
        }
        p.print(", " + quoteForPerl(sshAddr));
        p.print(");\n");
        p.print("}\n");
      }

      // Link back to Gerrit (when possible, to matching review record).
      // Supported gitweb's hash values are:
      // - (missing),
      // - HEAD,
      // - refs/heads/<branch>,
      // - refs/changes/*/<change>/*,
      // - <revision>.
      //
      p.print("sub add_review_link {\n");
      p.print("  my $h = shift;\n");
      p.print("  my $q;\n");
      p.print("  if (!$h || $h eq 'HEAD') {\n");
      p.print("    $q = qq{#/q/project:$ENV{'GERRIT_PROJECT_NAME'}};\n");
      p.print("  } elsif ($h =~ /^refs\\/heads\\/([-\\w]+)$/) {\n");
      p.print("    $q = qq{#/q/project:$ENV{'GERRIT_PROJECT_NAME'}");
      p.print("+branch:$1};\n"); // wrapped
      p.print("  } elsif ($h =~ /^refs\\/changes\\/\\d{2}\\/(\\d+)\\/\\d+$/) ");
      p.print("{\n"); // wrapped
      p.print("    $q = qq{#/c/$1};\n");
      p.print("  } else {\n");
      p.print("    $q = qq{#/q/$h};\n");
      p.print("  }\n");
      p.print("  my $r = qq{$ENV{'GERRIT_CONTEXT_PATH'}$q};\n");
      p.print("  push @{$feature{'actions'}{'default'}},\n");
      p.print("      ('review',$r,'commitdiff');\n");
      p.print("}\n");
      p.print("if ($cgi->param('hb')) {\n");
      p.print("  add_review_link($cgi->param('hb'));\n");
      p.print("} elsif ($cgi->param('h')) {\n");
      p.print("  add_review_link($cgi->param('h'));\n");
      p.print("} else {\n");
      p.print("  add_review_link();\n");
      p.print("}\n");

      // If the administrator has created a site-specific gitweb_config,
      // load that before we perform any final overrides.
      //
      Path sitecfg = site.site_gitweb;
      if (Files.isRegularFile(sitecfg)) {
        p.print("$GITWEB_CONFIG = " + quoteForPerl(sitecfg) + ";\n");
        p.print("if (-e $GITWEB_CONFIG) {\n");
        p.print("  do " + quoteForPerl(sitecfg) + ";\n");
        p.print("}\n");
      }

      p.print("$projectroot = $ENV{'GITWEB_PROJECTROOT'};\n");

      // Permit exporting only the project we were started for.
      // We use the name under $projectroot in case symlinks
      // were involved in the path.
      //
      p.print("$export_auth_hook = sub {\n");
      p.print("    my $dir = shift;\n");
      p.print("    my $name = $ENV{'GERRIT_PROJECT_NAME'};\n");
      p.print("    my $allow = qq{$projectroot/$name.git};\n");
      p.print("    return $dir eq $allow;\n");
      p.print("  };\n");

      // Do not allow the administrator to enable path info, its
      // not a URL format we currently support.
      //
      p.print("$feature{'pathinfo'}{'override'} = 0;\n");
      p.print("$feature{'pathinfo'}{'default'} = [0];\n");

      // We don't do forking, so don't allow it to be enabled.
      //
      p.print("$feature{'forks'}{'override'} = 0;\n");
      p.print("$feature{'forks'}{'default'} = [0];\n");
    }

    myconfFile.setReadOnly();
  }

  private static String quoteForPerl(Path value) {
    return quoteForPerl(value.toAbsolutePath().toString());
  }

  private static String quoteForPerl(String value) {
    if (value == null || value.isEmpty()) {
      return "''";
    }
    if (!value.contains("'")) {
      return "'" + value + "'";
    }
    if (!value.contains("{") && !value.contains("}")) {
      return "q{" + value + "}";
    }
    throw new IllegalArgumentException("Cannot quote in Perl: " + value);
  }

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    if (req.getQueryString() == null || req.getQueryString().isEmpty()) {
      // No query string? They want the project list, which we don't
      // currently support. Return to Gerrit's own web UI.
      //
      rsp.sendRedirect(req.getContextPath() + "/");
      return;
    }

    final Map<String, String> params = getParameters(req);
    String a = params.get("a");
    if (a != null) {
      if (deniedActions.contains(a)) {
        rsp.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;
      }

      if (a.equals(PROJECT_LIST_ACTION)) {
        rsp.sendRedirect(
            req.getContextPath()
                + "/#"
                + PageLinks.ADMIN_PROJECTS
                + "?filter="
                + Url.encode(params.get("pf") + "/"));
        return;
      }
    }

    String name = params.get("p");
    if (name == null) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    if (name.endsWith(".git")) {
      name = name.substring(0, name.length() - 4);
    }

    Project.NameKey nameKey = new Project.NameKey(name);
    try {
      if (projectCache.checkedGet(nameKey) == null) {
        notFound(req, rsp);
        return;
      }
      permissionBackend.user(userProvider).project(nameKey).check(ProjectPermission.READ);
    } catch (AuthException e) {
      notFound(req, rsp);
      return;
    } catch (IOException | PermissionBackendException err) {
      log.error("cannot load " + name, err);
      rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    try (Repository repo = repoManager.openRepository(nameKey)) {
      CacheHeaders.setNotCacheable(rsp);
      exec(req, rsp, nameKey);
    } catch (RepositoryNotFoundException e) {
      getServletContext().log("Cannot open repository", e);
      rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void notFound(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    if (userProvider.get().isIdentifiedUser()) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
    } else {
      rsp.sendRedirect(getLoginRedirectUrl(req));
    }
  }

  private static String getLoginRedirectUrl(HttpServletRequest req) {
    String contextPath = req.getContextPath();
    String loginUrl = contextPath + "/login/";
    String token = req.getRequestURI();
    if (!contextPath.isEmpty()) {
      token = token.substring(contextPath.length());
    }

    String queryString = req.getQueryString();
    if (queryString != null && !queryString.isEmpty()) {
      token = token.concat("?" + queryString);
    }
    return (loginUrl + Url.encode(token));
  }

  private static Map<String, String> getParameters(HttpServletRequest req) {
    final Map<String, String> params = new HashMap<>();
    for (String pair : req.getQueryString().split("[&;]")) {
      final int eq = pair.indexOf('=');
      if (0 < eq) {
        String name = pair.substring(0, eq);
        String value = pair.substring(eq + 1);

        name = Url.decode(name);
        value = Url.decode(value);
        params.put(name, value);
      }
    }
    return params;
  }

  private void exec(HttpServletRequest req, HttpServletResponse rsp, Project.NameKey project)
      throws IOException {
    final Process proc =
        Runtime.getRuntime()
            .exec(
                new String[] {gitwebCgi.toAbsolutePath().toString()},
                makeEnv(req, project),
                gitwebCgi.toAbsolutePath().getParent().toFile());

    copyStderrToLog(proc.getErrorStream());
    if (0 < req.getContentLength()) {
      copyContentToCGI(req, proc.getOutputStream());
    } else {
      proc.getOutputStream().close();
    }

    try (InputStream in = new BufferedInputStream(proc.getInputStream(), bufferSize)) {
      readCgiHeaders(rsp, in);

      try (OutputStream out = rsp.getOutputStream()) {
        final byte[] buf = new byte[bufferSize];
        int n;
        while ((n = in.read(buf)) > 0) {
          out.write(buf, 0, n);
        }
      }
    } catch (IOException e) {
      // The browser has probably closed its input stream. We don't
      // want to continue executing this request.
      //
      proc.destroy();
      return;
    }

    try {
      proc.waitFor();

      final int status = proc.exitValue();
      if (0 != status) {
        log.error("Non-zero exit status (" + status + ") from " + gitwebCgi);
        if (!rsp.isCommitted()) {
          rsp.sendError(500);
        }
      }
    } catch (InterruptedException ie) {
      log.debug("CGI: interrupted waiting for CGI to terminate");
    }
  }

  private String[] makeEnv(HttpServletRequest req, Project.NameKey nameKey) {
    final EnvList env = new EnvList(_env);
    final int contentLength = Math.max(0, req.getContentLength());

    // These ones are from "The WWW Common Gateway Interface Version 1.1"
    //
    env.set("AUTH_TYPE", req.getAuthType());
    env.set("CONTENT_LENGTH", Integer.toString(contentLength));
    env.set("CONTENT_TYPE", req.getContentType());
    env.set("GATEWAY_INTERFACE", "CGI/1.1");
    env.set("PATH_INFO", req.getPathInfo());
    env.set("PATH_TRANSLATED", null);
    env.set("QUERY_STRING", req.getQueryString());
    env.set("REMOTE_ADDR", req.getRemoteAddr());
    env.set("REMOTE_HOST", req.getRemoteHost());
    env.set("HTTPS", req.isSecure() ? "ON" : "OFF");

    // The identity information reported about the connection by a
    // RFC 1413 [11] request to the remote agent, if
    // available. Servers MAY choose not to support this feature, or
    // not to request the data for efficiency reasons.
    // "REMOTE_IDENT" => "NYI"
    //
    env.set("REQUEST_METHOD", req.getMethod());
    env.set("SCRIPT_NAME", req.getContextPath() + req.getServletPath());
    env.set("SCRIPT_FILENAME", gitwebCgi.toAbsolutePath().toString());
    env.set("SERVER_NAME", req.getServerName());
    env.set("SERVER_PORT", Integer.toString(req.getServerPort()));
    env.set("SERVER_PROTOCOL", req.getProtocol());
    env.set("SERVER_SOFTWARE", getServletContext().getServerInfo());

    final Enumeration<String> hdrs = enumerateHeaderNames(req);
    while (hdrs.hasMoreElements()) {
      final String name = hdrs.nextElement();
      final String value = req.getHeader(name);
      env.set("HTTP_" + name.toUpperCase().replace('-', '_'), value);
    }

    env.set("GERRIT_CONTEXT_PATH", req.getContextPath() + "/");
    env.set("GERRIT_PROJECT_NAME", nameKey.get());

    env.set("GITWEB_PROJECTROOT", repoManager.getBasePath(nameKey).toAbsolutePath().toString());

    if (permissionBackend
        .user(anonymousUserProvider)
        .project(nameKey)
        .testOrFalse(ProjectPermission.READ)) {
      env.set("GERRIT_ANONYMOUS_READ", "1");
    }

    String remoteUser = null;
    if (userProvider.get().isIdentifiedUser()) {
      IdentifiedUser u = userProvider.get().asIdentifiedUser();
      String user = u.getUserName();
      env.set("GERRIT_USER_NAME", user);
      if (user != null && !user.isEmpty()) {
        remoteUser = user;
      } else {
        remoteUser = "account-" + u.getAccountId();
      }
    }
    env.set("REMOTE_USER", remoteUser);

    // Override CGI settings using alternative URI provided by gitweb.url.
    // This is required to trick gitweb into thinking that it's served under
    // different URL. Setting just $my_uri on the perl's side isn't enough,
    // because few actions (atom, blobdiff_plain, commitdiff_plain) rely on
    // URL returned by $cgi->self_url().
    //
    if (gitwebUrl != null) {
      int schemePort = -1;

      if (gitwebUrl.getScheme() != null) {
        if (gitwebUrl.getScheme().equals("http")) {
          env.set("HTTPS", "OFF");
          schemePort = 80;
        } else {
          env.set("HTTPS", "ON");
          schemePort = 443;
        }
      }

      if (gitwebUrl.getHost() != null) {
        env.set("SERVER_NAME", gitwebUrl.getHost());
        env.set("HTTP_HOST", gitwebUrl.getHost());
      }

      if (gitwebUrl.getPort() != -1) {
        env.set("SERVER_PORT", Integer.toString(gitwebUrl.getPort()));
      } else if (schemePort != -1) {
        env.set("SERVER_PORT", Integer.toString(schemePort));
      }

      if (gitwebUrl.getPath() != null) {
        env.set("SCRIPT_NAME", gitwebUrl.getPath().isEmpty() ? "/" : gitwebUrl.getPath());
      }
    }

    return env.getEnvArray();
  }

  private void copyContentToCGI(HttpServletRequest req, OutputStream dst) throws IOException {
    final int contentLength = req.getContentLength();
    final InputStream src = req.getInputStream();
    new Thread(
            () -> {
              try {
                try {
                  final byte[] buf = new byte[bufferSize];
                  int remaining = contentLength;
                  while (0 < remaining) {
                    final int max = Math.max(buf.length, remaining);
                    final int n = src.read(buf, 0, max);
                    if (n < 0) {
                      throw new EOFException("Expected " + remaining + " more bytes");
                    }
                    dst.write(buf, 0, n);
                    remaining -= n;
                  }
                } finally {
                  dst.close();
                }
              } catch (IOException e) {
                log.debug("Unexpected error copying input to CGI", e);
              }
            },
            "Gitweb-InputFeeder")
        .start();
  }

  private void copyStderrToLog(InputStream in) {
    new Thread(
            () -> {
              try (BufferedReader br =
                  new BufferedReader(new InputStreamReader(in, ISO_8859_1.name()))) {
                String line;
                while ((line = br.readLine()) != null) {
                  log.error("CGI: " + line);
                }
              } catch (IOException e) {
                log.debug("Unexpected error copying stderr from CGI", e);
              }
            },
            "Gitweb-ErrorLogger")
        .start();
  }

  private static Enumeration<String> enumerateHeaderNames(HttpServletRequest req) {
    return req.getHeaderNames();
  }

  private void readCgiHeaders(HttpServletResponse res, InputStream in) throws IOException {
    String line;
    while (!(line = readLine(in)).isEmpty()) {
      if (line.startsWith("HTTP")) {
        // CGI believes it is a non-parsed-header CGI. We refuse
        // to support that here so abort.
        //
        throw new IOException("NPH CGI not supported: " + line);
      }

      final int sep = line.indexOf(':');
      if (sep < 0) {
        throw new IOException("CGI returned invalid header: " + line);
      }

      final String key = line.substring(0, sep).trim();
      final String value = line.substring(sep + 1).trim();
      if ("Location".equalsIgnoreCase(key)) {
        res.sendRedirect(value);

      } else if ("Status".equalsIgnoreCase(key)) {
        final String[] token = value.split(" ");
        final int status = Integer.parseInt(token[0]);
        res.setStatus(status);

      } else {
        res.addHeader(key, value);
      }
    }
  }

  private String readLine(InputStream in) throws IOException {
    final StringBuilder buf = new StringBuilder();
    int b;
    while ((b = in.read()) != -1 && b != '\n') {
      buf.append((char) b);
    }
    return buf.toString().trim();
  }

  /** private utility class that manages the Environment passed to exec. */
  private static class EnvList {
    private Map<String, String> envMap;

    EnvList() {
      envMap = new HashMap<>();
    }

    EnvList(EnvList l) {
      envMap = new HashMap<>(l.envMap);
    }

    /** Set a name/value pair, null values will be treated as an empty String */
    public void set(String name, String value) {
      if (value == null) {
        value = "";
      }
      envMap.put(name, name + "=" + value);
    }

    /** Get representation suitable for passing to exec. */
    public String[] getEnvArray() {
      return envMap.values().toArray(new String[envMap.size()]);
    }

    @Override
    public String toString() {
      return envMap.toString();
    }
  }
}
