package com.google.gerrit.extensions.common;


public class UserPreferences {

  public EditSection edit;

  public UserPreferences() {
    edit = new EditSection();
  }

  public static class EditSection {
    public Integer lineLength;
    public Integer tabSize;
    public Boolean showLineEndings;
    public Boolean showTabs;
    public Boolean syntaxHighlighting;
    public Boolean hideLineNumbers;
    public Theme theme;

    public EditSection() {
      lineLength = 120;
      tabSize = 2;
      showLineEndings = true;
      showTabs = true;
      syntaxHighlighting = true;
      hideLineNumbers = false;
      theme = Theme.ECLIPSE;
    }
  }
}
