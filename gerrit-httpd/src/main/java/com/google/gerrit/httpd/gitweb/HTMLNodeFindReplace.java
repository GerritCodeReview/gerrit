package com.google.gerrit.httpd.gitweb;

import com.google.gwtexpui.safehtml.client.RegexFindReplace;

import org.htmlparser.Node;
import org.htmlparser.Parser;
import org.htmlparser.Text;
import org.htmlparser.lexer.Lexer;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.htmlparser.util.SimpleNodeIterator;
import org.htmlparser.visitors.NodeVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class HTMLNodeFindReplace extends NodeVisitor {

  private List<RegexFindReplace> regexFindReplaceList;

  private Logger log = LoggerFactory.getLogger(HTMLNodeFindReplace.class);

  private ParserException parserException;

  public ParserException getParserException() {
    return parserException;
  }

  /**
   * @param htmlReplaceProcessor
   */
  HTMLNodeFindReplace(List<RegexFindReplace> regexFindReplaceList) {
    this.regexFindReplaceList = regexFindReplaceList;
  }

  public void visitStringNode(Text string) {

    if (regexFindReplaceList == null || regexFindReplaceList.size() == 0) {
      return;
    }

    Node parentNode = string.getParent();
    if (parentNode instanceof LinkTag) {
      try {
        replaceInAnchor((LinkTag) parentNode);
      } catch (ParserException e) {
        log.error("Error processing node replacement for " + parentNode, e);
        parserException = e;
      }
    } else {
      replaceInText(string);
    }
  }

  private void replaceInAnchor(LinkTag anchorNode) throws ParserException {

    Node parentAnchor = anchorNode.getParent();
    if (parentAnchor == null) return;

    String line = anchorNode.toHtml();
    String openAnchor = line.substring(0, line.indexOf(">") + 1);
    String closeAnchor = line.substring(line.lastIndexOf("<"));
    String origLine = line;

    try {
      for (RegexFindReplace findReplace : regexFindReplaceList) {
        line = replaceSingle(line, findReplace, openAnchor, closeAnchor);
      }

      if (line.equals(origLine)) return;

      line = cleanUpEmptyAnchorLinks(line, openAnchor, closeAnchor);
      updateAnchorChildrenFromHTMLText(anchorNode, line);

    } finally {
      if (!line.equals(origLine)) {
        log.debug("RegEx applied on Anchor: '" + origLine + "' => '" + line
            + "'");
      }
    }
  }

  private void replaceInText(Text text) {
    String origLine = text.getText();
    String line = text.getText();
    try {

      for (RegexFindReplace findReplace : regexFindReplaceList) {
        line = replaceSingle(line, findReplace);
      }

      text.setText(line);
    } finally {
      if (!line.equals(origLine)) {
        System.out.println("RegEx applied on Text: '" + origLine + "' => '"
            + line + "'");
      }
    }
  }

  private String replaceSingle(String line, RegexFindReplace findReplace) {
    return replaceSingle(line, findReplace, "", "");
  }



  public String cleanUpEmptyAnchorLinks(String line, String openAnchor,
      String closeAnchor) {
    line = line.replaceAll(openAnchor + closeAnchor, "");
    return line;
  }

  public String replaceSingle(String line, RegexFindReplace findReplace,
      String startTag, String endTag) {
    int currPos = 0;
    StringBuilder replacedLine = new StringBuilder();

    Matcher patternMatcher = Pattern.compile(findReplace.find()).matcher(line);
    while (patternMatcher.find()) {

      String replacedString =
          patternMatcher.group().replaceFirst(findReplace.find(),
              findReplace.replace());

      currPos =
          appendReplacedTextFragmentInHTML(startTag, endTag, replacedLine,
              line, currPos, replacedString, patternMatcher);
    }

    replacedLine.append(line.substring(currPos));
    line = replacedLine.toString();
    return line;
  }



  private int appendReplacedTextFragmentInHTML(String openTag, String closeTag,
      StringBuilder expandedLine, String originalLine, int currPos,
      String replaceString, Matcher patternMatcher) {

    int matchStart = patternMatcher.start();
    int matchEnd = patternMatcher.end();

    String prefixBeforeMatch = originalLine.substring(currPos, matchStart);

    if (prefixBeforeMatch.length() > 0) {
      expandedLine.append(prefixBeforeMatch);
      expandedLine.append(closeTag);
    }

    expandedLine.append(replaceString);
    expandedLine.append(openTag);

    return matchEnd;
  }

  public void updateAnchorChildrenFromHTMLText(LinkTag anchorNode, String line)
      throws ParserException {

    NodeList newChildren = new NodeList();

    NodeList children = anchorNode.getParent().getChildren();
    if (children == null) children = new NodeList();

    SimpleNodeIterator iter = children.elements();
    while (iter.hasMoreNodes()) {
      Node node = iter.nextNode();

      if (node == anchorNode) {
        newChildren.add(new Parser(new Lexer(line)).parse(null));
      } else {
        newChildren.add(node);
      }
    }

    anchorNode.getParent().setChildren(newChildren);
  }

}
