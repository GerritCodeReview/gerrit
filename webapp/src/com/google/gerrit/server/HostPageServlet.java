// Copyright 2008 Google Inc.
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

package com.google.gerrit.server;

import com.google.gwt.user.server.rpc.RPCServletUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** Sends the Gerrit host page to clients. */
public class HostPageServlet extends HttpServlet {
  private static final long MAX_AGE = 5 * 60 * 1000L/* milliseconds */;
  private static final String CACHE_CTRL =
      "public, max-age=" + (MAX_AGE / 1000L);
  private static final String CT_ENC = "UTF-8";
  private static final String HTML_STRICT =
      "-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/REC-html40/strict.dtd";

  private byte[] hostPageRaw;
  private byte[] hostPageCompressed;
  private long lastModified;

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);

    final String hostPageName = "com/google/gerrit/public/Gerrit.html";
    final Document hostDoc = parseFile(hostPageName);
    if (hostDoc == null) {
      throw new ServletException("No " + hostPageName + " in CLASSPATH");
    }
    injectCssFile(hostDoc, "gerrit_sitecss", "GerritSite.css");
    injectXmlFile(hostDoc, "gerrit_header", "GerritSiteHeader.html");
    injectXmlFile(hostDoc, "gerrit_footer", "GerritSiteFooter.html");
    hostPageRaw = toUTF8(hostDoc);
    hostPageCompressed = compress(hostPageRaw);
    lastModified = System.currentTimeMillis();
  }

  private Document parseFile(final String name) throws ServletException {
    final InputStream in;

    in = getClass().getClassLoader().getResourceAsStream(name);
    if (in == null) {
      return null;
    }
    try {
      try {
        try {
          final DocumentBuilderFactory factory =
              DocumentBuilderFactory.newInstance();
          factory.setValidating(false);
          factory.setExpandEntityReferences(false);
          factory.setIgnoringComments(true);
          final DocumentBuilder parser = factory.newDocumentBuilder();
          return parser.parse(in);
        } catch (SAXException e) {
          throw new ServletException("Error reading " + name, e);
        } catch (ParserConfigurationException e) {
          throw new ServletException("Error reading " + name, e);
        }
      } finally {
        in.close();
      }
    } catch (IOException e) {
      throw new ServletException("Error reading " + name, e);
    }
  }

  private void injectXmlFile(final Document hostDoc, final String id,
      final String fileName) throws ServletException {
    final Element banner = find(hostDoc, id);
    if (banner == null) {
      return;
    }

    while (banner.getFirstChild() != null) {
      banner.removeChild(banner.getFirstChild());
    }

    final Document bannerHTML = parseFile(fileName);
    if (bannerHTML == null) {
      banner.getParentNode().removeChild(banner);
      return;
    }

    final Element content = bannerHTML.getDocumentElement();
    banner.appendChild(hostDoc.importNode(content, true));
  }

  private void injectCssFile(final Document hostDoc, final String id,
      final String fileName) throws ServletException {
    final Element banner = find(hostDoc, id);
    if (banner == null) {
      return;
    }

    while (banner.getFirstChild() != null) {
      banner.removeChild(banner.getFirstChild());
    }

    InputStream in = getClass().getClassLoader().getResourceAsStream(fileName);
    if (in == null) {
      banner.getParentNode().removeChild(banner);
      return;
    }

    final StringWriter w = new StringWriter();
    w.write('\n');
    try {
      try {
        final InputStreamReader r = new InputStreamReader(in, CT_ENC);
        final char[] buf = new char[512];
        int n;
        while ((n = r.read(buf)) > 0) {
          w.write(buf, 0, n);
        }
      } finally {
        in.close();
      }
    } catch (IOException e) {
      throw new ServletException("Error reading " + fileName, e);
    }
    w.write('\n');

    banner.removeAttribute("id");
    banner.appendChild(hostDoc.createCDATASection(w.toString()));
  }

  private static Element find(final Node parent, final String name) {
    final NodeList list = parent.getChildNodes();
    for (int i = 0; i < list.getLength(); i++) {
      final Node n = list.item(i);
      if (n instanceof Element) {
        final Element e = (Element) n;
        if (name.equals(e.getAttribute("id"))) {
          return e;
        }
      }
      final Element r = find(n, name);
      if (r != null) {
        return r;
      }
    }
    return null;
  }

  private byte[] toUTF8(final Document hostDoc) throws ServletException {
    try {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      final DOMSource domSource = new DOMSource(hostDoc);
      final StreamResult streamResult = new StreamResult(out);
      final TransformerFactory tf = TransformerFactory.newInstance();
      final Transformer serializer = tf.newTransformer();
      serializer.setOutputProperty(OutputKeys.ENCODING, CT_ENC);
      serializer.setOutputProperty(OutputKeys.METHOD, "html");
      serializer.setOutputProperty(OutputKeys.INDENT, "no");
      serializer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, HTML_STRICT);
      serializer.transform(domSource, streamResult);
      return out.toByteArray();
    } catch (TransformerConfigurationException e) {
      e.printStackTrace();
      throw new ServletException("Error transforming host page", e);
    } catch (TransformerException e) {
      e.printStackTrace();
      throw new ServletException("Error transforming host page", e);
    }
  }

  private byte[] compress(final byte[] raw) throws ServletException {
    try {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      final GZIPOutputStream gz = new GZIPOutputStream(out);
      gz.write(raw);
      gz.finish();
      gz.flush();
      return out.toByteArray();
    } catch (IOException e) {
      throw new ServletException("Cannot compress host page", e);
    }
  }

  @Override
  protected long getLastModified(final HttpServletRequest req) {
    return lastModified;
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    final byte[] tosend;
    if (RPCServletUtils.acceptsGzipEncoding(req)) {
      rsp.setHeader("Content-Encoding", "gzip");
      tosend = hostPageCompressed;
    } else {
      tosend = hostPageRaw;
    }

    rsp.setHeader("Cache-Control", CACHE_CTRL);
    rsp.setDateHeader("Expires", System.currentTimeMillis() + MAX_AGE);
    rsp.setDateHeader("Last-Modified", lastModified);
    rsp.setContentType("text/html");
    rsp.setCharacterEncoding(CT_ENC);
    rsp.setContentLength(tosend.length);
    final OutputStream out = rsp.getOutputStream();
    try {
      out.write(tosend);
    } finally {
      out.close();
    }
  }
}
