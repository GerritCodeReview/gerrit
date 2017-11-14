package com.google.gerrit.server.mail.lib;

import java.time.format.DateTimeFormatter;

public class MailUtil {

  public static DateTimeFormatter rfcDateformatter =
      DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss ZZZ");
}
