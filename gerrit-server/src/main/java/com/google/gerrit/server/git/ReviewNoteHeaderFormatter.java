package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class ReviewNoteHeaderFormatter {

  private final DateFormat rfc2822DateFormatter;
  private final StringBuilder sb = new StringBuilder();

  public ReviewNoteHeaderFormatter() {
    rfc2822DateFormatter =
        new SimpleDateFormat("dd MMM yyyy HH:mm:ss Z", Locale.US);
    rfc2822DateFormatter.setCalendar(Calendar.getInstance(
        TimeZone.getTimeZone("GMT"), Locale.US));
  }

  public void appendChangeId(Change.Id changeId) {
    sb.append("Change-Id: ").append(changeId).append("\n");
  }

  public void appendApproval(ApprovalCategory category,
      short value, Account user) {
    sb.append(category.getName());
    sb.append(value < 0 ? "-" : "+").append(value).append(": ");
    appendUserData(user);
    sb.append("\n");
  }

  private void appendUserData(Account user) {
    sb.append(user.getFullName()).append(" <").append(user.getPreferredEmail())
        .append(">");
  }

  public void appendBranch(Project project, Branch.NameKey branch) {
    sb.append("Branch: ").append(project.getName()).append(" ")
        .append(branch.get()).append("\n");
  }

  public void appendSubmittedBy(Account user) {
    sb.append("Submitted-by: ");
    appendUserData(user);
    sb.append("\n");
  }

  public void appendSubmittedOn(Date date) {
    sb.append("Submitted-on: ").append(rfc2822DateFormatter.format(date))
        .append("\n");
  }

  @Override
  public String toString() {
    return sb.toString();
  }
}
