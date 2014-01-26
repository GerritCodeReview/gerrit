// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.httpd.raw;

import com.google.common.base.Strings;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.Weigher;
import com.google.common.collect.Maps;
import com.google.gerrit.httpd.raw.ProjectDocResourceKey.DiffMode;
import com.google.gerrit.httpd.resources.Resource;
import com.google.gerrit.httpd.resources.SmallResource;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.documentation.MarkdownFormatter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.ssh.SshAddressesModule;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.outerj.daisy.diff.HtmlCleaner;
import org.outerj.daisy.diff.XslFilter;
import org.outerj.daisy.diff.html.HTMLDiffer;
import org.outerj.daisy.diff.html.HtmlSaxDiffOutput;
import org.outerj.daisy.diff.html.TextNodeComparator;
import org.outerj.daisy.diff.html.dom.DomTreeBuilder;
import org.owasp.validator.html.AntiSamy;
import org.owasp.validator.html.Policy;
import org.owasp.validator.html.PolicyException;
import org.owasp.validator.html.ScanException;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

@Singleton
public class ProjectDocLoader extends CacheLoader<ProjectDocResourceKey, Resource> {
  public static enum AntiSamyPolicy {
    STRICT, MODEST, LAX, CUSTOM, DISABLED
  }

  private final GitRepositoryManager repoManager;
  private final Provider<String> webUrl;
  private final boolean suppressHtml;
  private final Policy policy;
  private final String sshHost;
  private final int sshPort;

  @Inject
  ProjectDocLoader(GitRepositoryManager repoManager,
      @CanonicalWebUrl Provider<String> webUrl, SitePaths sitePaths,
      @GerritServerConfig Config cfg, SshInfo sshInfo) throws PolicyException {
    this.repoManager = repoManager;
    this.webUrl = webUrl;
    this.suppressHtml = cfg.getBoolean("site", "suppressHtml", true);
    this.policy = loadPolicy(sitePaths, cfg);

    String sshHost = "review.example.com";
    int sshPort = SshAddressesModule.DEFAULT_PORT;
    if (!sshInfo.getHostKeys().isEmpty()) {
      String host = sshInfo.getHostKeys().get(0).getHost();
      int c = host.lastIndexOf(':');
      if (0 <= c) {
        sshHost = host.substring(0, c);
        sshPort = Integer.parseInt(host.substring(c+1));
      } else {
        sshHost = host;
        sshPort = SshAddressesModule.IANA_SSH_PORT;
      }
    }
    this.sshHost = sshHost;
    this.sshPort = sshPort;
  }

  private static Policy loadPolicy(SitePaths sitePaths, Config cfg)
      throws PolicyException {
    AntiSamyPolicy policy =
        cfg.getEnum("site", null, "antiSamyPolicy", AntiSamyPolicy.MODEST);
    if (AntiSamyPolicy.DISABLED.equals(policy)) {
      return null;
    }
    return AntiSamyPolicy.CUSTOM.equals(policy)
        ? Policy.getInstance(new File(sitePaths.etc_dir, "antisamy.xml"))
        : Policy.getInstance(ProjectDocLoader.class.getResourceAsStream(
            "antisamy-" + policy.name().toLowerCase() + ".xml"));
  }

  @Override
  public Resource load(ProjectDocResourceKey key) throws Exception {
    Repository repo = repoManager.openRepository(key.getProject());
    try {
      RevWalk rw = new RevWalk(repo);
      try {
        RevCommit commit = rw.parseCommit(key.getRevId());
        byte[] html = loadHtml(repo, rw, commit, key.getResource());
        if (html == null) {
          if (key.getRevIdB() == null) {
            return Resource.NOT_FOUND;
          } else {
            html = new byte[] {};
          }
        }

        if (key.getRevIdB() != null && !key.getRevId().equals(key.getRevIdB())) {
          RevCommit commitB = rw.parseCommit(key.getRevIdB());
          byte[] htmlB = loadHtml(repo, rw, commitB, key.getResource());
          if (htmlB == null) {
            htmlB = new byte[] {};
          }
          html = diffHtml(html, htmlB, key.getDiffMode());
        }

        return new SmallResource(html)
            .setContentType("text/html")
            .setCharacterEncoding("UTF-8")
            .setLastModified(commit.getCommitTime());
      } finally {
        rw.release();
      }
    } finally {
      repo.close();
    }
  }

  private byte[] loadHtml(Repository repo, RevWalk rw, RevCommit commit,
      String resource) throws IOException, PolicyException, ScanException {
    RevTree tree = commit.getTree();
    TreeWalk tw = new TreeWalk(repo);
    try {
      tw.addTree(tree);
      tw.setRecursive(true);
      tw.setFilter(PathFilter.create(resource));
      if (!tw.next()) {
        return null;
      }
      ObjectId objectId = tw.getObjectId(0);
      ObjectLoader loader = repo.open(objectId);
      byte[] md = loader.getBytes(Integer.MAX_VALUE);
      return getMarkdownAsHtml(md);
    } finally {
      tw.release();
    }
  }

  private byte[] getMarkdownAsHtml(byte[] md) throws IOException,
      PolicyException, ScanException {
    return cleanHtml(new MarkdownFormatter()
        .markdownToDocHtml(replaceMacros(new String(md, "UTF-8")),
            "UTF-8", suppressHtml));
  }

  private String replaceMacros(String md) {
    Map<String, String> macros = Maps.newHashMap();
    macros.put("SSH_HOST", sshHost);
    macros.put("SSH_PORT", "" + sshPort);
    String url = webUrl.get();
    if (Strings.isNullOrEmpty(url)) {
      url = "http://review.example.com/";
    }
    macros.put("URL", url);

    Matcher m = Pattern.compile("(\\\\)?@([A-Z_]+)@").matcher(md);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String key = m.group(2);
      String val = macros.get(key);
      if (m.group(1) != null || val == null) {
        m.appendReplacement(sb, "@" + key + "@");
      } else {
        m.appendReplacement(sb, val);
      }
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private byte[] cleanHtml(byte[] html) throws UnsupportedEncodingException,
      PolicyException, ScanException {
    if (policy == null) {
      return html;
    }
    return new AntiSamy().scan(new String(html, "UTF-8"), policy)
        .getCleanHTML().getBytes("UTF-8");
  }

  private byte[] diffHtml(byte[] htmlA, byte[] htmlB, DiffMode diffMode)
      throws TransformerConfigurationException, IOException, SAXException {
    ByteArrayOutputStream htmlDiff = new ByteArrayOutputStream();

    SAXTransformerFactory tf =
        (SAXTransformerFactory) TransformerFactory.newInstance();
    TransformerHandler result = tf.newTransformerHandler();
    result.setResult(new StreamResult(htmlDiff));
    XslFilter filter = new XslFilter();
    String htmlHeader;
    switch (diffMode) {
      case SIDEBYSIDE_A:
        htmlHeader = "com/google/gerrit/httpd/raw/diff/htmlheader-sidebyside-a.xsl";
        break;
      case SIDEBYSIDE_B:
        htmlHeader = "com/google/gerrit/httpd/raw/diff/htmlheader-sidebyside-b.xsl";
        break;
      default:
        htmlHeader = "com/google/gerrit/httpd/raw/diff/htmlheader.xsl";
    }
    ContentHandler postProcess = filter.xsl(result, htmlHeader);

    HtmlCleaner cleaner = new HtmlCleaner();

    InputSource oldSource = new InputSource(new ByteArrayInputStream(htmlA));
    DomTreeBuilder oldHandler = new DomTreeBuilder();
    cleaner.cleanAndParse(oldSource, oldHandler);
    TextNodeComparator leftComparator = new TextNodeComparator(
            oldHandler, Locale.getDefault());

    InputSource newSource = new InputSource(new ByteArrayInputStream(htmlB));
    DomTreeBuilder newHandler = new DomTreeBuilder();
    cleaner.cleanAndParse(newSource, newHandler);
    System.out.print(".");
    TextNodeComparator rightComparator = new TextNodeComparator(
            newHandler, Locale.getDefault());

    postProcess.startDocument();
    postProcess.startElement("", "diffreport", "diffreport",
            new AttributesImpl());
    postProcess.startElement("", "diff", "diff",
            new AttributesImpl());
    HtmlSaxDiffOutput output = new HtmlSaxDiffOutput(postProcess, "diff");

    HTMLDiffer differ = new HTMLDiffer(output);
    differ.diff(leftComparator, rightComparator);
    postProcess.endElement("", "diff", "diff");
    postProcess.endElement("", "diffreport", "diffreport");
    postProcess.endDocument();

    return htmlDiff.toString("UTF-8").getBytes("UTF-8");
  }

  public static class Module extends CacheModule {
    static final String PROJECT_DOC_RESOURCES = "project_doc_resources";

    @Override
    protected void configure() {
      install(new CacheModule() {
        @Override
        protected void configure() {
          persist(PROJECT_DOC_RESOURCES, ProjectDocResourceKey.class, Resource.class)
            .maximumWeight(2 << 20)
            .weigher(ProjectDocResourceWeigher.class)
            .loader(ProjectDocLoader.class);
        }
      });
    }
  }

  private static class ProjectDocResourceWeigher implements
      Weigher<ProjectDocResourceKey, Resource> {
    @Override
    public int weigh(ProjectDocResourceKey key, Resource value) {
      return key.weigh() + value.weigh();
    }
  }
}
