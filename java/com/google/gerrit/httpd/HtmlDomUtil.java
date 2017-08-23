// Copyright (C) 2008 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Utility functions to deal with HTML using W3C DOM operations. */
public class HtmlDomUtil {
  /** Standard character encoding we prefer (UTF-8). */
  public static final Charset ENC = UTF_8;

  /** DOCTYPE for a standards mode HTML document. */
  public static final String HTML_STRICT =
      "-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd";

  /** Convert a document to a UTF-8 byte sequence. */
  public static byte[] toUTF8(Document hostDoc) throws IOException {
    return toString(hostDoc).getBytes(ENC);
  }

  /** Compress the document. */
  public static byte[] compress(byte[] raw) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    GZIPOutputStream gz = new GZIPOutputStream(out);
    gz.write(raw);
    gz.finish();
    gz.flush();
    return out.toByteArray();
  }

  /** Convert a document to a String, assuming later encoding to UTF-8. */
  public static String toString(Document hostDoc) throws IOException {
    try {
      StringWriter out = new StringWriter();
      DOMSource domSource = new DOMSource(hostDoc);
      StreamResult streamResult = new StreamResult(out);
      TransformerFactory tf = TransformerFactory.newInstance();
      Transformer serializer = tf.newTransformer();
      serializer.setOutputProperty(OutputKeys.ENCODING, ENC.name());
      serializer.setOutputProperty(OutputKeys.METHOD, "html");
      serializer.setOutputProperty(OutputKeys.INDENT, "no");
      serializer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, HtmlDomUtil.HTML_STRICT);
      serializer.transform(domSource, streamResult);
      return out.toString();
    } catch (TransformerException e) {
      IOException r = new IOException("Error transforming page");
      r.initCause(e);
      throw r;
    }
  }

  /** Find an element by its "id" attribute; null if no element is found. */
  public static Element find(Node parent, String name) {
    NodeList list = parent.getChildNodes();
    for (int i = 0; i < list.getLength(); i++) {
      Node n = list.item(i);
      if (n instanceof Element) {
        Element e = (Element) n;
        if (name.equals(e.getAttribute("id"))) {
          return e;
        }
      }
      Element r = find(n, name);
      if (r != null) {
        return r;
      }
    }
    return null;
  }

  /** Append an HTML &lt;input type="hidden"&gt; to the form. */
  public static void addHidden(Element form, String name, String value) {
    Element in = form.getOwnerDocument().createElement("input");
    in.setAttribute("type", "hidden");
    in.setAttribute("name", name);
    in.setAttribute("value", value);
    form.appendChild(in);
  }

  /** Construct a new empty document. */
  public static Document newDocument() {
    try {
      return newBuilder().newDocument();
    } catch (ParserConfigurationException e) {
      throw new RuntimeException("Cannot create new document", e);
    }
  }

  /** Clone a document so it can be safely modified on a per-request basis. */
  public static Document clone(Document doc) throws IOException {
    Document d;
    try {
      d = newBuilder().newDocument();
    } catch (ParserConfigurationException e) {
      throw new IOException("Cannot clone document");
    }
    Node n = d.importNode(doc.getDocumentElement(), true);
    d.appendChild(n);
    return d;
  }

  /** Parse an XHTML file from our CLASSPATH and return the instance. */
  public static Document parseFile(Class<?> context, String name) throws IOException {
    try (InputStream in = context.getResourceAsStream(name)) {
      if (in == null) {
        return null;
      }
      Document doc = newBuilder().parse(in);
      compact(doc);
      return doc;
    } catch (SAXException | ParserConfigurationException | IOException e) {
      throw new IOException("Error reading " + name, e);
    }
  }

  private static void compact(Document doc) {
    try {
      String expr = "//text()[normalize-space(.) = '']";
      XPathFactory xp = XPathFactory.newInstance();
      XPathExpression e = xp.newXPath().compile(expr);
      NodeList empty = (NodeList) e.evaluate(doc, XPathConstants.NODESET);
      for (int i = 0; i < empty.getLength(); i++) {
        Node node = empty.item(i);
        node.getParentNode().removeChild(node);
      }
    } catch (XPathExpressionException e) {
      // Don't do the whitespace removal.
    }
  }

  /** Read a Read a UTF-8 text file from our CLASSPATH and return it. */
  public static String readFile(Class<?> context, String name) throws IOException {
    try (InputStream in = context.getResourceAsStream(name)) {
      if (in == null) {
        return null;
      }
      return new String(ByteStreams.toByteArray(in), ENC);
    } catch (IOException e) {
      throw new IOException("Error reading " + name, e);
    }
  }

  /** Parse an XHTML file from the local drive and return the instance. */
  public static Document parseFile(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
      Document doc = newBuilder().parse(in);
      compact(doc);
      return doc;
    } catch (NoSuchFileException e) {
      return null;
    } catch (SAXException | ParserConfigurationException | IOException e) {
      throw new IOException("Error reading " + path, e);
    }
  }

  /** Read a UTF-8 text file from the local drive. */
  public static String readFile(Path parentDir, String name) throws IOException {
    if (parentDir == null) {
      return null;
    }
    Path path = parentDir.resolve(name);
    try (InputStream in = Files.newInputStream(path)) {
      return new String(ByteStreams.toByteArray(in), ENC);
    } catch (NoSuchFileException e) {
      return null;
    } catch (IOException e) {
      throw new IOException("Error reading " + path, e);
    }
  }

  private static DocumentBuilder newBuilder() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setValidating(false);
    factory.setExpandEntityReferences(false);
    factory.setIgnoringComments(true);
    factory.setCoalescing(true);
    return factory.newDocumentBuilder();
  }
}
