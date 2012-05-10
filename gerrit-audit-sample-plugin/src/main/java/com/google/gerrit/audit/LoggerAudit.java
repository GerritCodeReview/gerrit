package com.google.gerrit.audit;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.gerrit.audit.AuditEvent;
import com.google.gerrit.audit.AuditListener;
import com.google.gerrit.audit.AuditEvent.UUID;
import com.google.gerrit.common.auth.userpass.LoginResult;
import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Singleton;

@Listen
@Singleton
public class LoggerAudit implements AuditListener {
  private static final Logger log = LoggerFactory.getLogger(LoggerAudit.class);
  private final SimpleDateFormat dateFmt = new SimpleDateFormat(
      "yyyy/MM/dd hh:mm:ss.SSSS");
  @SuppressWarnings("serial")
  private static final Map<Class<?>, AuditFormatter> AUDIT_FORMATTERS =
      Collections.unmodifiableMap(new HashMap<Class<?>, AuditFormatter>() {
        {
          put(LoginResultFormat.CLASS, new LoginResultFormat());
        }
      });

  static {
    log.info("EventId | EventTS | SessionId | User | Action | Parameters | Result | StartTS | Elapsed");
  }

  @Override
  public void onAuditableAction(AuditEvent action) {
    log.info(getFormattedAudit(action));
  }

  private String getFormattedAudit(AuditEvent action) {
    return String.format(
        "%1$s | %2$s | %3$s | %4$s | %5$s | %6$s | %7$s | %8$s | %9$s",
        action.uuid.get(), getFormattedTS(action.when), action.sessionId,
        getFormattedAudit(action.who), action.what,
        getFormattedAuditList(action.params), getFormattedAudit(action.result),
        getFormattedTS(action.timeAtStart), action.elapsed);
  }

  private Object getFormattedAuditList(List<?> params) {
    if (params == null || params.size() == 0) return "[]";

    Iterator<?> iterator = params.iterator();
    StringBuilder formattedOut =
        new StringBuilder("[" + getFormattedAudit(iterator.next()));
    while (iterator.hasNext()) {
      formattedOut.append(',');
      formattedOut.append(getFormattedAudit(iterator.next()));
    }
    formattedOut.append(']');

    return formattedOut.toString();
  }

  private String getFormattedAudit(Object result) {
    if (result == null) return "";

    AuditFormatter fmt = AUDIT_FORMATTERS.get(result.getClass());
    if (fmt == null) return result.toString();

    return fmt.format(result);
  }

  private synchronized String getFormattedTS(long when) {
    return dateFmt.format(new Date(when));
  }

}
