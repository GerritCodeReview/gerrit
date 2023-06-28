// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.documentation;

import static com.vladsch.flexmark.parser.PegdownExtensions.ALL;
import static com.vladsch.flexmark.parser.PegdownExtensions.HARDWRAPS;
import static com.vladsch.flexmark.parser.PegdownExtensions.SUPPRESS_ALL_HTML;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.util.TextCollectingVisitor;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.profile.pegdown.PegdownOptionsAdapter;
import com.vladsch.flexmark.util.ast.Block;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.TemporaryBuffer;

public class MarkdownFormatter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String defaultCss;

  static {
    AtomicBoolean file = new AtomicBoolean();
    String src;
    try {
      src = readFlexMarkJavaCss(file);
    } catch (IOException err) {
      logger.atWarning().withCause(err).log("Cannot load flexmark-java.css");
      src = "";
    }
    defaultCss = file.get() ? null : src;
  }

  private static String readCSS() {
    if (defaultCss != null) {
      return defaultCss;
    }
    try {
      return readFlexMarkJavaCss(new AtomicBoolean());
    } catch (IOException err) {
      logger.atWarning().withCause(err).log("Cannot load flexmark-java.css");
      return "";
    }
  }

  private boolean suppressHtml;
  private String css;

  public MarkdownFormatter suppressHtml() {
    suppressHtml = true;
    return this;
  }

  public MarkdownFormatter setCss(String css) {
    this.css = StringEscapeUtils.escapeHtml4(css);
    return this;
  }

  private MutableDataHolder markDownOptions() {
    int options = ALL & ~HARDWRAPS;
    if (suppressHtml) {
      options |= SUPPRESS_ALL_HTML;
    }

    MutableDataHolder optionsExt =
        PegdownOptionsAdapter.flexmarkOptions(
                options, MarkdownFormatterHeader.HeadingExtension.create())
            .toMutable();

    return optionsExt;
  }

  public byte[] markdownToDocHtml(String md, String charEnc) throws UnsupportedEncodingException {
    Node root = parseMarkdown(md);
    HtmlRenderer renderer = HtmlRenderer.builder(markDownOptions()).build();
    String title = findTitle(root);

    StringBuilder html = new StringBuilder();
    html.append("<html>");
    html.append("<head>");
    if (!Strings.isNullOrEmpty(title)) {
      html.append("<title>").append(title).append("</title>");
    }
    html.append("<style type=\"text/css\">\n");
    if (css != null) {
      html.append(css);
    } else {
      html.append(readCSS());
    }
    html.append("\n</style>");
    html.append("</head>");
    html.append("<body>\n");
    html.append(renderer.render(root));
    html.append("\n</body></html>");
    return html.toString().getBytes(charEnc);
  }

  public String extractTitleFromMarkdown(byte[] data, String charEnc) {
    String md = RawParseUtils.decode(Charset.forName(charEnc), data);
    return findTitle(parseMarkdown(md));
  }

  @Nullable
  private String findTitle(Node root) {
    if (root instanceof Heading) {
      Heading h = (Heading) root;
      if (h.getLevel() == 1 && h.hasChildren()) {
        TextCollectingVisitor collectingVisitor = new TextCollectingVisitor();
        return collectingVisitor.collectAndGetText(h);
      }
    }

    if (root instanceof Block && root.hasChildren()) {
      Node child = root.getFirstChild();
      while (child != null) {
        String title = findTitle(child);
        if (title != null) {
          return title;
        }
        child = child.getNext();
      }
    }

    return null;
  }

  private Node parseMarkdown(String md) {
    Parser parser = Parser.builder(markDownOptions()).build();
    Node document = parser.parse(md);
    return document;
  }

  private static String readFlexMarkJavaCss(AtomicBoolean file) throws IOException {
    String name = "flexmark-java.css";
    URL url = MarkdownFormatter.class.getResource(name);
    if (url == null) {
      throw new FileNotFoundException("Resource " + name);
    }
    file.set("file".equals(url.getProtocol()));
    try (InputStream in = url.openStream();
        TemporaryBuffer.Heap tmp = new TemporaryBuffer.Heap(128 * 1024)) {
      tmp.copy(in);
      return new String(tmp.toByteArray(), UTF_8);
    }
  }
}
