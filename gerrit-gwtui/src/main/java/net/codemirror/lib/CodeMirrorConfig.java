package net.codemirror.lib;

import com.google.gwt.i18n.client.Messages;

public interface CodeMirrorConfig extends Messages {

  String shortBlameMsg(String commitId, String date, String author);

  String detailedBlameMsg(String commitId, String author, String time, String msg);
}
