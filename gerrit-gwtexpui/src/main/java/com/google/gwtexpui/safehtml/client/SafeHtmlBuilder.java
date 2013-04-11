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

package com.google.gwtexpui.safehtml.client;

import com.google.gwt.core.client.GWT;

/**
 * Safely constructs a {@link SafeHtml}, escaping user provided content.
 */
public class SafeHtmlBuilder extends SafeHtml {
  private static final Impl impl;

  static {
    if (GWT.isClient()) {
      impl = new ClientImpl();
    } else {
      impl = new ServerImpl();
    }
  }

  private final BufferDirect dBuf;
  private Buffer cb;

  private BufferSealElement sBuf;
  private AttMap att;

  public SafeHtmlBuilder() {
    cb = dBuf = new BufferDirect();
  }

  /** @return true if this builder has not had an append occur yet. */
  public boolean isEmpty() {
    return dBuf.isEmpty();
  }

  /** @return true if this builder has content appended into it. */
  public boolean hasContent() {
    return !isEmpty();
  }

  public SafeHtmlBuilder append(final boolean in) {
    cb.append(in);
    return this;
  }

  public SafeHtmlBuilder append(final char in) {
    switch (in) {
      case '&':
        cb.append("&amp;");
        break;

      case '>':
        cb.append("&gt;");
        break;

      case '<':
        cb.append("&lt;");
        break;

      case '"':
        cb.append("&quot;");
        break;

      case '\'':
        cb.append("&#39;");
        break;

      default:
        cb.append(in);
        break;
    }
    return this;
  }

  public SafeHtmlBuilder append(final int in) {
    cb.append(in);
    return this;
  }

  public SafeHtmlBuilder append(final long in) {
    cb.append(in);
    return this;
  }

  public SafeHtmlBuilder append(final float in) {
    cb.append(in);
    return this;
  }

  public SafeHtmlBuilder append(final double in) {
    cb.append(in);
    return this;
  }

  /** Append already safe HTML as-is, avoiding double escaping. */
  public SafeHtmlBuilder append(final SafeHtml in) {
    if (in != null) {
      cb.append(in.asString());
    }
    return this;
  }

  /** Append the string, escaping unsafe characters. */
  public SafeHtmlBuilder append(final String in) {
    if (in != null) {
      impl.escapeStr(this, in);
    }
    return this;
  }

  /** Append the string, escaping unsafe characters. */
  public SafeHtmlBuilder append(final StringBuilder in) {
    if (in != null) {
      append(in.toString());
    }
    return this;
  }

  /** Append the string, escaping unsafe characters. */
  public SafeHtmlBuilder append(final StringBuffer in) {
    if (in != null) {
      append(in.toString());
    }
    return this;
  }

  /** Append the result of toString(), escaping unsafe characters. */
  public SafeHtmlBuilder append(final Object in) {
    if (in != null) {
      append(in.toString());
    }
    return this;
  }

  /** Append the string, escaping unsafe characters. */
  public SafeHtmlBuilder append(final CharSequence in) {
    if (in != null) {
      escapeCS(this, in);
    }
    return this;
  }

  /**
   * Open an element, appending "<tagName>" to the buffer.
   * <p>
   * After the element is open the attributes may be manipulated until the next
   * <code>append</code>, <code>openElement</code>, <code>closeSelf</code> or
   * <code>closeElement</code> call.
   *
   * @param tagName name of the HTML element to open.
   */
  public SafeHtmlBuilder openElement(final String tagName) {
    assert isElementName(tagName);
    cb.append("<");
    cb.append(tagName);
    if (sBuf == null) {
      att = new AttMap();
      sBuf = new BufferSealElement(this);
    }
    att.reset(tagName);
    cb = sBuf;
    return this;
  }

  /**
   * Get an attribute of the last opened element.
   *
   * @param name name of the attribute to read.
   * @return the attribute value, as a string. The empty string if the attribute
   *         has not been assigned a value. The returned string is the raw
   *         (unescaped) value.
   */
  public String getAttribute(final String name) {
    assert isAttributeName(name);
    assert cb == sBuf;
    return att.get(name);
  }

  /**
   * Set an attribute of the last opened element.
   *
   * @param name name of the attribute to set.
   * @param value value to assign; any existing value is replaced. The value is
   *        escaped (if necessary) during the assignment.
   */
  public SafeHtmlBuilder setAttribute(final String name, final String value) {
    assert isAttributeName(name);
    assert cb == sBuf;
    att.set(name, value != null ? value : "");
    return this;
  }

  /**
   * Set an attribute of the last opened element.
   *
   * @param name name of the attribute to set.
   * @param value value to assign, any existing value is replaced.
   */
  public SafeHtmlBuilder setAttribute(final String name, final int value) {
    return setAttribute(name, String.valueOf(value));
  }

  /**
   * Append a new value into a whitespace delimited attribute.
   * <p>
   * If the attribute is not yet assigned, this method sets the attribute. If
   * the attribute is already assigned, the new value is appended onto the end,
   * after appending a single space to delimit the values.
   *
   * @param name name of the attribute to append onto.
   * @param value additional value to append.
   */
  public SafeHtmlBuilder appendAttribute(final String name, String value) {
    if (value != null && value.length() > 0) {
      final String e = getAttribute(name);
      return setAttribute(name, e.length() > 0 ? e + " " + value : value);
    }
    return this;
  }

  /** Set the height attribute of the current element. */
  public SafeHtmlBuilder setHeight(final int height) {
    return setAttribute("height", height);
  }

  /** Set the width attribute of the current element. */
  public SafeHtmlBuilder setWidth(final int width) {
    return setAttribute("width", width);
  }

  /** Set the CSS class name for this element. */
  public SafeHtmlBuilder setStyleName(final String style) {
    assert isCssName(style);
    return setAttribute("class", style);
  }

  /**
   * Add an additional CSS class name to this element.
   *<p>
   * If no CSS class name has been specified yet, this method initializes it to
   * the single name.
   */
  public SafeHtmlBuilder addStyleName(final String style) {
    assert isCssName(style);
    return appendAttribute("class", style);
  }

  private void sealElement0() {
    assert cb == sBuf;
    cb = dBuf;
    att.onto(cb, this);
  }

  Buffer sealElement() {
    sealElement0();
    cb.append(">");
    return cb;
  }

  /** Close the current element with a self closing suffix ("/ &gt;"). */
  public SafeHtmlBuilder closeSelf() {
    sealElement0();
    cb.append(" />");
    return this;
  }

  /** Append a closing tag for the named element. */
  public SafeHtmlBuilder closeElement(final String name) {
    assert isElementName(name);
    cb.append("</");
    cb.append(name);
    cb.append(">");
    return this;
  }

  /** Append "&amp;nbsp;" - a non-breaking space, useful in empty table cells. */
  public SafeHtmlBuilder nbsp() {
    cb.append("&nbsp;");
    return this;
  }

  /** Append "&lt;br /&gt;" - a line break with no attributes */
  public SafeHtmlBuilder br() {
    cb.append("<br />");
    return this;
  }

  /** Append "&lt;tr&gt;"; attributes may be set if needed */
  public SafeHtmlBuilder openTr() {
    return openElement("tr");
  }

  /** Append "&lt;/tr&gt;" */
  public SafeHtmlBuilder closeTr() {
    return closeElement("tr");
  }

  /** Append "&lt;td&gt;"; attributes may be set if needed */
  public SafeHtmlBuilder openTd() {
    return openElement("td");
  }

  /** Append "&lt;/td&gt;" */
  public SafeHtmlBuilder closeTd() {
    return closeElement("td");
  }

  /** Append "&lt;div&gt;"; attributes may be set if needed */
  public SafeHtmlBuilder openDiv() {
    return openElement("div");
  }

  /** Append "&lt;/div&gt;" */
  public SafeHtmlBuilder closeDiv() {
    return closeElement("div");
  }

  /** Append "&lt;span&gt;"; attributes may be set if needed */
  public SafeHtmlBuilder openSpan() {
    return openElement("span");
  }

  /** Append "&lt;/span&gt;" */
  public SafeHtmlBuilder closeSpan() {
    return closeElement("span");
  }

  /** Append "&lt;a&gt;"; attributes may be set if needed */
  public SafeHtmlBuilder openAnchor() {
    return openElement("a");
  }

  /** Append "&lt;/a&gt;" */
  public SafeHtmlBuilder closeAnchor() {
    return closeElement("a");
  }

  /** Append "&lt;param name=... value=... /&gt;". */
  public SafeHtmlBuilder paramElement(final String name, final String value) {
    openElement("param");
    setAttribute("name", name);
    setAttribute("value", value);
    return closeSelf();
  }

  /** @return an immutable {@link SafeHtml} representation of the buffer. */
  public SafeHtml toSafeHtml() {
    return new SafeHtmlString(asString());
  }

  @Override
  public String asString() {
    return cb.toString();
  }

  private static void escapeCS(final SafeHtmlBuilder b, final CharSequence in) {
    for (int i = 0; i < in.length(); i++) {
      b.append(in.charAt(i));
    }
  }

  private static boolean isElementName(final String name) {
    return name.matches("^[a-zA-Z][a-zA-Z0-9_-]*$");
  }

  private static boolean isAttributeName(final String name) {
    return isElementName(name);
  }

  private static boolean isCssName(final String name) {
    return isElementName(name);
  }

  private static abstract class Impl {
    abstract void escapeStr(SafeHtmlBuilder b, String in);
  }

  private static class ServerImpl extends Impl {
    @Override
    void escapeStr(final SafeHtmlBuilder b, final String in) {
      SafeHtmlBuilder.escapeCS(b, in);
    }
  }

  private static class ClientImpl extends Impl {
    @Override
    void escapeStr(final SafeHtmlBuilder b, final String in) {
      b.cb.append(escape(in));
    }

    private static native String escape(String src)
    /*-{ return src.replace(/&/g,'&amp;')
                   .replace(/>/g,'&gt;')
                   .replace(/</g,'&lt;')
                   .replace(/"/g,'&quot;')
                   .replace(/'/g,'&#39;');
     }-*/;
  }
}
