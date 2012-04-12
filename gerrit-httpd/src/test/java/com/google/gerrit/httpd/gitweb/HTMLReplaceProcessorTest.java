package com.google.gerrit.httpd.gitweb;

import static org.junit.Assert.*;

import com.google.gerrit.common.data.GerritConfig;
import com.google.gwtexpui.safehtml.client.RegexFindReplace;

import org.htmlparser.Parser;
import org.htmlparser.lexer.Lexer;
import org.htmlparser.lexer.Page;
import org.htmlparser.util.ParserException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.Arrays;

import static org.mockito.Mockito.*;

public class HTMLReplaceProcessorTest {

  private static final String A_HREF_HTTP_YAHOO_COM_LINK_A =
      "<a href=\"http://yahoo.com\">Link</a>";

  private static final String TEST_HTML_DATA =
      "<HTML><HEAD><title>This is my page</title><BODY><p>This is my test my only one</p></BODY></HTML>";
  private static final String TEST_HTML_DATA_REPLACED1 =
      "<HTML><HEAD><title>This is my page</title><BODY><p>This is my test2 my only one</p></BODY></HTML>";
  private static final String TEST_HTML_DATA_REPLACED2 =
      "<HTML><HEAD><title>That is my page</title><BODY><p>That is my test2 my only one</p></BODY></HTML>";
  private static final String TEST_HTML_DATA_REPLACED_MULTIPLE_TIMES =
      "<HTML><HEAD><title>This is your page</title><BODY><p>This is your test your only one</p></BODY></HTML>";
  private static final String TEST_HTML_DATA_REPLACED_WITH_ANCHOR =
      "<HTML><HEAD><title>This" + A_HREF_HTTP_YAHOO_COM_LINK_A
          + " my page</title><BODY><p>This" + A_HREF_HTTP_YAHOO_COM_LINK_A
          + " my test my only one</p></BODY></HTML>";

  private static final String TEST_HTML_WITH_ANCHOR =
      "<HTML><BODY><a href=\"http://google.com\">Link to google to</a></BODY></HTML>";
  private static final String TEST_HTML_WITH_ANCHOR_REPLACED_OUTSIDE_ANCHOR =
      "<HTML><BODY>" + A_HREF_HTTP_YAHOO_COM_LINK_A
          + "<a href=\"http://google.com\"> to google to</a></BODY></HTML>";

  private static final String TEST_HTML_WITH_ANCHOR_REPLACED_OUTSIDE_ANCHOR_TWICE =
      "<HTML><BODY><a href=\"http://google.com\">Link </a>"
          + A_HREF_HTTP_YAHOO_COM_LINK_A
          + "<a href=\"http://google.com\"> google </a>"
          + A_HREF_HTTP_YAHOO_COM_LINK_A + "</BODY></HTML>";

  private static final String UTF8 = "UTF-8";


  private ByteArrayInputStream srcIn;
  private ByteArrayOutputStream dstOut;
  private HTMLReplaceProcessor htmlProcessor;
  GerritConfig config = mock(GerritConfig.class);

  @Before
  public void setUp() throws Exception {
    dstOut = new ByteArrayOutputStream();
    htmlProcessor = new HTMLReplaceProcessor(config);
  }

  @After
  public void tearDown() throws Exception {
  }

  private void setConfigRegexList(RegexFindReplace[] regexList) {
    when(config.getCommentLinks()).thenReturn(Arrays.asList(regexList));
  }

  private void setInput(String input) {
    srcIn = new ByteArrayInputStream(input.getBytes());
  }


  private String getOutput() {
    return new String(dstOut.toByteArray());
  }


  private void processStreams() throws IOException {
    htmlProcessor.processStreams(srcIn, dstOut, UTF8);
  }


  private void assertEqualsHTML(String srcHTML, String dstHTML)
      throws ParserException {

    String normalisedSrc =
        new Parser(new Lexer(new Page(srcHTML))).parse(null).toHtml();
    String normalisedDst =
        new Parser(new Lexer(new Page(dstHTML))).parse(null).toHtml();

    assertEquals(normalisedSrc, normalisedDst);
  }

  @Test
  public void testProcessStreamsDoesntChangeHTMLWithEmptyReplacePattern()
      throws IOException, ParserException {
    setInput(TEST_HTML_DATA);
    setConfigRegexList(new RegexFindReplace[] {});
    processStreams();
    assertEqualsHTML(TEST_HTML_DATA, getOutput());
  }

  @Test
  public void testProcessStreamsWithUniqueStringReplacement()
      throws IOException, ParserException {
    setInput(TEST_HTML_DATA);
    setConfigRegexList(new RegexFindReplace[] {new RegexFindReplace("test",
        "test2")});
    processStreams();
    assertEqualsHTML(TEST_HTML_DATA_REPLACED1, getOutput());
  }

  @Test
  public void testProcessStreamsWithMultipleStringReplacements()
      throws IOException, ParserException {
    setInput(TEST_HTML_DATA);
    setConfigRegexList(new RegexFindReplace[] {
        new RegexFindReplace("test", "test2"),
        new RegexFindReplace("This", "That")});
    processStreams();
    assertEqualsHTML(TEST_HTML_DATA_REPLACED2, getOutput());
  }

  @Test
  public void testProcessStreamsWithOneStringReplacementsMultipleTimes()
      throws IOException, ParserException {
    setInput(TEST_HTML_DATA);
    setConfigRegexList(new RegexFindReplace[] {new RegexFindReplace("my",
        "your")});
    processStreams();
    assertEqualsHTML(TEST_HTML_DATA_REPLACED_MULTIPLE_TIMES, getOutput());
  }

  @Test
  public void testProcessStreamsWithOneStringToAnchorReplacementsMultipleTimes()
      throws IOException, ParserException {
    setInput(TEST_HTML_DATA);
    setConfigRegexList(new RegexFindReplace[] {new RegexFindReplace(" is",
        A_HREF_HTTP_YAHOO_COM_LINK_A)});
    processStreams();
    assertEqualsHTML(TEST_HTML_DATA_REPLACED_WITH_ANCHOR, getOutput());
  }

  @Test
  public void testProcessStreamsWithAnchorExpandedOutsideAnchor()
      throws IOException, ParserException {
    setInput(TEST_HTML_WITH_ANCHOR);
    setConfigRegexList(new RegexFindReplace[] {new RegexFindReplace("Link",
        A_HREF_HTTP_YAHOO_COM_LINK_A)});
    processStreams();
    assertEqualsHTML(TEST_HTML_WITH_ANCHOR_REPLACED_OUTSIDE_ANCHOR, getOutput());
  }

  @Test
  public void testProcessStreamsWithAnchorExpandedOutsideAnchorTwice()
      throws IOException, ParserException {
    setInput(TEST_HTML_WITH_ANCHOR);
    setConfigRegexList(new RegexFindReplace[] {new RegexFindReplace("to",
        A_HREF_HTTP_YAHOO_COM_LINK_A)});
    processStreams();
    assertEqualsHTML(TEST_HTML_WITH_ANCHOR_REPLACED_OUTSIDE_ANCHOR_TWICE,
        getOutput());
  }


}
