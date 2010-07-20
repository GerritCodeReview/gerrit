// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.common.Version;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.events.ChangeAttribute;
import com.google.gerrit.server.events.EventFactory;
import com.google.gerrit.server.events.QueryStats;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gson.Gson;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;

import javax.annotation.Nullable;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

public class QueryProcessor {
  private static final Logger log =
      LoggerFactory.getLogger(QueryProcessor.class);

  public static enum OutputFormat {
    TEXT, JSON, RSS;
  }

  private final Gson gson = new Gson();
  private final SimpleDateFormat sdf =
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");

  private final EventFactory eventFactory;
  private final ChangeQueryBuilder queryBuilder;
  private final ChangeQueryRewriter queryRewriter;
  private final Provider<ReviewDb> db;
  private final Provider<String> urlProvider;
  private final AccountCache accountCache;
  private final SimpleDateFormat gmt;

  private int defaultLimit = 500;
  private OutputFormat outputFormat = OutputFormat.TEXT;
  private boolean includePatchSets;
  private boolean includeCurrentPatchSet;

  private OutputStream outputStream = DisabledOutputStream.INSTANCE;
  private PrintWriter out;
  private TransformerHandler xml;

  @Inject
  QueryProcessor(EventFactory eventFactory,
      ChangeQueryBuilder.Factory queryBuilder, CurrentUser currentUser,
      ChangeQueryRewriter queryRewriter, Provider<ReviewDb> db,
      @Nullable @CanonicalWebUrl Provider<String> urlProvider,
      AccountCache accountCache) {
    this.eventFactory = eventFactory;
    this.queryBuilder = queryBuilder.create(currentUser);
    this.queryRewriter = queryRewriter;
    this.urlProvider = urlProvider;
    this.db = db;
    this.accountCache = accountCache;

    gmt = new SimpleDateFormat("ddd, dd mmm yyyy HH:mm:ss zzz");
    gmt.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  public void setDefaultLimit(int limit) {
    defaultLimit = limit;
  }

  public void setIncludePatchSets(boolean on) {
    includePatchSets = on;
  }

  public void setIncludeCurrentPatchSet(boolean on) {
    includeCurrentPatchSet = on;
  }

  public void setOutput(OutputStream out, OutputFormat fmt) {
    this.outputStream = out;
    this.outputFormat = fmt;
  }

  public void query(String queryString) throws IOException {
    out = new PrintWriter( //
        new BufferedWriter( //
            new OutputStreamWriter(outputStream, "UTF-8")));

    switch (outputFormat) {
      case RSS: {
        StreamResult streamResult = new StreamResult(out);
        SAXTransformerFactory tf =
            (SAXTransformerFactory) SAXTransformerFactory.newInstance();
        try {
          xml = tf.newTransformerHandler();
        } catch (TransformerConfigurationException e) {
          throw new IOException("Cannot start XML document", e);
        }
        Transformer serializer = xml.getTransformer();
        serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        serializer.setOutputProperty(OutputKeys.INDENT, "yes");
        xml.setResult(streamResult);
        try {
          xml.startDocument();

          AttributesImpl atts = new AttributesImpl();
          atts.addAttribute("", "", "version", "CDATA", "2.0");
          xml.startElement("", "", "rss", atts);

          atts.clear();
          xml.startElement("", "", "channel", atts);

          xmlString("title", "Gerrit Code Review");
          if (urlProvider.get() != null) {
            xmlString("link", urlProvider.get() + "r/"
                + URLEncoder.encode(queryString, "UTF-8"));
          }

          xmlString("description", "Changes matching " + queryString);
          xmlString("lastBuildDate", gmt.format(new Date()));
          xmlString("language", "en-us");
          xmlString("generator", "Gerrit Code Review/" + Version.getVersion());
        } catch (SAXException e) {
          throw new IOException("Cannot start XML document", e);
        }
        break;
      }
    }

    try {
      try {
        final QueryStats stats = new QueryStats();
        stats.runTimeMilliseconds = System.currentTimeMillis();

        final Predicate<ChangeData> visibleToMe = queryBuilder.is_visible();
        Predicate<ChangeData> s = compileQuery(queryString, visibleToMe);
        List<ChangeData> results = new ArrayList<ChangeData>();
        HashSet<Change.Id> want = new HashSet<Change.Id>();
        for (ChangeData d : ((ChangeDataSource) s).read()) {
          if (d.hasChange()) {
            // Checking visibleToMe here should be unnecessary, the
            // query should have already performed it. But we don't
            // want to trust the query rewriter that much yet.
            //
            if (visibleToMe.match(d)) {
              results.add(d);
            }
          } else {
            want.add(d.getId());
          }
        }

        if (!want.isEmpty()) {
          for (Change c : db.get().changes().get(want)) {
            ChangeData d = new ChangeData(c);
            if (visibleToMe.match(d)) {
              results.add(d);
            }
          }
        }

        Collections.sort(results, new Comparator<ChangeData>() {
          @Override
          public int compare(ChangeData a, ChangeData b) {
            return b.getChange().getSortKey().compareTo(
                a.getChange().getSortKey());
          }
        });

        int limit = limit(s);
        if (limit < results.size()) {
          results = results.subList(0, limit);
        }

        switch (outputFormat) {
          case RSS:
            try {
              for (ChangeData d : results) {
                asRssItem(d);
              }
            } catch (SAXException e) {
              throw new IOException("Cannot format RSS", e);
            }
            break;

          default:
            for (ChangeData d : results) {
              ChangeAttribute c = eventFactory.asChangeAttribute(d.getChange());
              eventFactory.extend(c, d.getChange());
              eventFactory.addTrackingIds(c, d.trackingIds(db));

              if (includePatchSets) {
                eventFactory.addPatchSets(c, d.patches(db));
              }

              if (includeCurrentPatchSet) {
                PatchSet current = d.currentPatchSet(db);
                if (current != null) {
                  c.currentPatchSet = eventFactory.asPatchSetAttribute(current);
                  eventFactory.addApprovals(c.currentPatchSet, //
                      d.approvalsFor(db, current.getId()));
                }
              }

              show(c);
            }
            stats.rowCount = results.size();
            stats.runTimeMilliseconds =
                System.currentTimeMillis() - stats.runTimeMilliseconds;
            show(stats);
            break;
        }
      } catch (OrmException err) {
        log.error("Cannot execute query: " + queryString, err);

        ErrorMessage m = new ErrorMessage();
        m.message = "cannot query database";
        show(m);

      } catch (QueryParseException e) {
        ErrorMessage m = new ErrorMessage();
        m.message = e.getMessage();
        show(m);
      }
    } finally {
      switch (outputFormat) {
        case RSS:
          try {
            xml.endElement("", "", "channel");
            xml.endElement("", "", "rss");
            xml.endDocument();
          } catch (SAXException e) {
            throw new IOException("Cannot end RSS feed", e);
          }
          break;

        default:
          try {
            out.flush();
          } finally {
            out = null;
          }
          break;
      }
    }
  }

  private void asRssItem(ChangeData d) throws SAXException, IOException {
    Change c = d.getChange();
    String url = eventFactory.getChangeUrl(c);

    xml.startElement("", "", "item", new AttributesImpl());
    xmlString("title", c.getSubject());
    if (url != null) {
      xmlString("link", url);
      xmlString("guid", url);
    }
    xmlString("pubDate", gmt.format(c.getLastUpdatedOn()));

    Account owner = accountCache.get(c.getOwner()).getAccount();
    if (owner.getPreferredEmail() != null) {
      xmlString("author", owner.getPreferredEmail());
    }
    xmlString("category", c.getProject().get());

    StringBuilder buf = new StringBuilder();
    switch (c.getStatus()) {
      case MERGED:
        buf.append("Merged Into ");
        break;
      case ABANDONED:
        buf.append("Abandoned (But previously for ");
        break;
    }
    buf.append("Project ");
    buf.append(c.getProject().get());
    buf.append(" - Branch ");
    buf.append(c.getDest().getShortName());
    if (c.getTopic() != null) {
      buf.append(" - Topic ");
      buf.append(c.getTopic());
    }
    switch (c.getStatus()) {
      case ABANDONED:
        buf.append(")");
        break;
    }
    xmlString("description", buf.toString());
    xml.endElement("", "", "item");
  }

  private void xmlString(String elementName, String value) throws IOException {
    try {
      char[] ch = value.toCharArray();
      AttributesImpl atts = new AttributesImpl();
      xml.startElement("", "", elementName, atts);
      xml.characters(ch, 0, ch.length);
      xml.endElement("", "", elementName);
    } catch (SAXException e) {
      throw new IOException("Cannot write <" + elementName + ">" + value, e);
    }
  }

  private int limit(Predicate<ChangeData> s) {
    return queryBuilder.hasLimit(s) ? queryBuilder.getLimit(s) : defaultLimit;
  }

  @SuppressWarnings("unchecked")
  private Predicate<ChangeData> compileQuery(String queryString,
      final Predicate<ChangeData> visibleToMe) throws QueryParseException {

    Predicate<ChangeData> q = queryBuilder.parse(queryString);
    if (!queryBuilder.hasLimit(q)) {
      q = Predicate.and(q, queryBuilder.limit(defaultLimit));
    }
    if (!queryBuilder.hasSortKey(q)) {
      q = Predicate.and(q, queryBuilder.sortkey_before("z"));
    }
    q = Predicate.and(q, visibleToMe);

    Predicate<ChangeData> s = queryRewriter.rewrite(q);
    if (!(s instanceof ChangeDataSource)) {
      s = queryRewriter.rewrite(Predicate.and(queryBuilder.status_open(), q));
    }

    if (!(s instanceof ChangeDataSource)) {
      throw new QueryParseException("cannot execute query: " + s);
    }

    return s;
  }

  private void show(Object data) {
    switch (outputFormat) {
      default:
      case TEXT:
        if (data instanceof ChangeAttribute) {
          out.print("change ");
          out.print(((ChangeAttribute) data).id);
          out.print("\n");
          showText(data, 1);
        } else {
          showText(data, 0);
        }
        out.print('\n');
        break;

      case JSON:
        out.print(gson.toJson(data));
        out.print('\n');
        break;

      case RSS:
        // We don't format objects in RSS.
        break;
    }
  }

  private void showText(Object data, int depth) {
    for (Field f : fieldsOf(data.getClass())) {
      Object val;
      try {
        val = f.get(data);
      } catch (IllegalArgumentException err) {
        continue;
      } catch (IllegalAccessException err) {
        continue;
      }
      if (val == null) {
        continue;
      }

      indent(depth);
      out.print(f.getName());
      out.print(":");

      if (val instanceof Long && isDateField(f.getName())) {
        out.print(' ');
        out.print(sdf.format(new Date(((Long) val) * 1000L)));
        out.print('\n');
      } else {
        showTextValue(val, depth);
      }
    }
  }

  private void indent(int depth) {
    for (int i = 0; i < depth; i++) {
      out.print("  ");
    }
  }

  @SuppressWarnings( {"cast", "unchecked"})
  private void showTextValue(Object value, int depth) {
    if (isPrimitive(value)) {
      out.print(' ');
      out.print(value);
      out.print('\n');

    } else if (value instanceof Collection) {
      out.print('\n');
      for (Object thing : ((Collection) value)) {
        if (isPrimitive(thing)) {
          out.print(' ');
          out.print(value);
          out.print('\n');
        } else {
          showText(thing, depth + 1);
          out.print('\n');
        }
      }
    } else {
      out.print('\n');
      showText(value, depth + 1);
    }
  }

  @SuppressWarnings("unchecked")
  private static boolean isPrimitive(Object value) {
    return value instanceof String //
        || value instanceof Number //
        || value instanceof Boolean //
        || value instanceof Enum;
  }

  private static boolean isDateField(String name) {
    return "lastUpdated".equals(name) //
        || "grantedOn".equals(name);
  }

  private List<Field> fieldsOf(Class<?> type) {
    List<Field> r = new ArrayList<Field>();
    if (type.getSuperclass() != null) {
      r.addAll(fieldsOf(type.getSuperclass()));
    }
    r.addAll(Arrays.asList(type.getDeclaredFields()));
    return r;
  }

  static class ErrorMessage {
    public final String type = "error";
    public String message;
  }
}
