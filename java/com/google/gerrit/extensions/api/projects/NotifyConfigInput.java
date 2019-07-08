package com.google.gerrit.extensions.api.projects;

import com.google.gerrit.extensions.api.projects.NotifyConfigInfo.Header;
import java.util.HashSet;
import java.util.Set;

public class NotifyConfigInput implements Comparable<NotifyConfigInput> {

  public String name;
  public Boolean notifyNewChanges;
  public Boolean notifyNewPatchSets;
  public Boolean notifyAllComments;
  public Boolean notifySubmittedChanges;
  public Boolean notifyAbandonedChanges;
  public String filter;
  public Header header = Header.BCC;
  public Set<String> addresses = new HashSet<>();
  public Set<String> groupsIds = new HashSet<>();

  public void addEmail(String address) {
    addresses.add(address);
  }

  public void addGroup(String groupId) {
    groupsIds.add(groupId);
  }

  @Override
  public int compareTo(NotifyConfigInput o) {
    return name.compareTo(o.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof NotifyConfigInput) {
      return compareTo((NotifyConfigInput) obj) == 0;
    }
    return false;
  }

  @Override
  public String toString() {
    return "NotifyConfigInput[" + name + " = " + addresses + " + " + groupsIds + "]";
  }
}
