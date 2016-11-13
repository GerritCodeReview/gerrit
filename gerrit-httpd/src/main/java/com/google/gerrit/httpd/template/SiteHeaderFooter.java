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

package com.google.gerrit.httpd.template;

import static com.google.gerrit.common.FileUtil.lastModified;

import com.google.common.base.Strings;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Singleton
public class SiteHeaderFooter {
  private static final Logger log = LoggerFactory.getLogger(SiteHeaderFooter.class);

  private final boolean refreshHeaderFooter;
  private final SitePaths sitePaths;
  private volatile Template template;

  @Inject
  SiteHeaderFooter(@GerritServerConfig Config cfg, SitePaths sitePaths) {
    this.refreshHeaderFooter = cfg.getBoolean("site", "refreshHeaderFooter", true);
    this.sitePaths = sitePaths;

    try {
      Template t = new Template(sitePaths);
      t.load();
      template = t;
    } catch (IOException e) {
      log.warn("Cannot load site header or footer", e);
    }
  }

  public Document parse(Class<?> clazz, String name) throws IOException {
    Template t = template;
    if (refreshHeaderFooter && t.isStale()) {
      t = new Template(sitePaths);
      try {
        t.load();
        template = t;
      } catch (IOException e) {
        log.warn("Cannot refresh site header or footer", e);
        t = template;
      }
    }

    Document doc = HtmlDomUtil.parseFile(clazz, name);
    injectCss(doc, "gerrit_sitecss", t.css);
    injectXml(doc, "gerrit_header", t.header);
    injectXml(doc, "gerrit_footer", t.footer);
    return doc;
  }

  private void injectCss(Document doc, String id, String content) {
    Element e = HtmlDomUtil.find(doc, id);
    if (e != null) {
      if (!Strings.isNullOrEmpty(content)) {
        while (e.getFirstChild() != null) {
          e.removeChild(e.getFirstChild());
        }
        e.removeAttribute("id");
        e.appendChild(doc.createCDATASection("\n" + content + "\n"));
      } else {
        e.getParentNode().removeChild(e);
      }
    }
  }

  private void injectXml(Document doc, String id, Element d) {
    Element e = HtmlDomUtil.find(doc, id);
    if (e != null) {
      if (d != null) {
        while (e.getFirstChild() != null) {
          e.removeChild(e.getFirstChild());
        }
        e.appendChild(doc.importNode(d, true));
      } else {
        e.getParentNode().removeChild(e);
      }
    }
  }

  private static class Template {
    private final FileInfo cssFile;
    private final FileInfo headerFile;
    private final FileInfo footerFile;

    String css;
    Element header;
    Element footer;

    Template(SitePaths site) {
      cssFile = new FileInfo(site.site_css);
      headerFile = new FileInfo(site.site_header);
      footerFile = new FileInfo(site.site_footer);
    }

    void load() throws IOException {
      css = HtmlDomUtil.readFile(cssFile.path.getParent(), cssFile.path.getFileName().toString());
      header = readXml(headerFile);
      footer = readXml(footerFile);
    }

    boolean isStale() {
      return cssFile.isStale() || headerFile.isStale() || footerFile.isStale();
    }

    private static Element readXml(FileInfo src) throws IOException {
      Document d = HtmlDomUtil.parseFile(src.path);
      return d != null ? d.getDocumentElement() : null;
    }
  }

  private static class FileInfo {
    final Path path;
    final long time;

    FileInfo(Path p) {
      path = p;
      time = lastModified(p);
    }

    boolean isStale() {
      return time != lastModified(path);
    }
  }
}
