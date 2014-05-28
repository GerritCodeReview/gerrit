package com.google.gerrit.extensions.events;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

import java.sql.Timestamp;
import java.util.List;

/** Notified when usage data is published */
@ExtensionPoint
public interface UsageDataPublishedListener {

  public interface Event {
    MetaData getMetaData();
    Timestamp getInstant();
    List<Data> getData();
  }

  public interface Data {
    long getValue();
    String getProjectName();
  }

  public interface MetaData {
    public String getName();
    public String getUnitName();
    public String getUnitSymbol();
    public String getDescription();
  }

  void onUsageDataPublished(Event event);
}
