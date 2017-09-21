/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.google.gerrit.elasticsearch;

import com.google.common.base.Charsets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.elasticsearch.ElasticsearchGenerationException;
import org.elasticsearch.action.support.QuerySourceBuilder;
import org.elasticsearch.action.support.ToXContentToBytes;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.innerhits.InnerHitsBuilder;
import org.elasticsearch.search.fetch.source.FetchSourceContext;
import org.elasticsearch.search.highlight.HighlightBuilder;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.search.rescore.RescoreBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.search.suggest.SuggestBuilder;

/**
 * A search source builder allowing to easily build search source. Simple construction using {@link
 * org.elasticsearch.search.builder.SearchSourceBuilder#searchSource()}.
 *
 * @see org.elasticsearch.action.search.SearchRequest#source(SearchSourceBuilder)
 */
public class GerritElasticSearchSourceBuilder extends ToXContentToBytes {

  /** A static factory method to construct a new search source. */
  public static GerritElasticSearchSourceBuilder searchSource() {
    return new GerritElasticSearchSourceBuilder();
  }

  /** A static factory method to construct new search highlights. */
  public static HighlightBuilder highlight() {
    return new HighlightBuilder();
  }

  private QuerySourceBuilder querySourceBuilder;

  private QueryBuilder postQueryBuilder;

  private BytesReference filterBinary;

  private int from = -1;

  private int size = -1;

  private Boolean explain;

  private Boolean version;

  private List<SortBuilder> sorts;

  private boolean trackScores = false;

  private Float minScore;

  private long timeoutInMillis = -1;
  private int terminateAfter = SearchContext.DEFAULT_TERMINATE_AFTER;

  private List<String> fieldNames;
  private List<String> fieldDataFields;
  private List<ScriptField> scriptFields;
  private FetchSourceContext fetchSourceContext;

  private List<AbstractAggregationBuilder> aggregations;
  private BytesReference aggregationsBinary;

  private HighlightBuilder highlightBuilder;

  private SuggestBuilder suggestBuilder;

  private InnerHitsBuilder innerHitsBuilder;

  private List<RescoreBuilder> rescoreBuilders;
  private Integer defaultRescoreWindowSize;

  private HashMap<String, Float> indexBoost = null;

  private String[] stats;

  private boolean profile = false;

  /** Constructs a new search source builder. */
  public GerritElasticSearchSourceBuilder() {}

  /** Sets the query provided as a {@link QuerySourceBuilder} */
  public GerritElasticSearchSourceBuilder query(QuerySourceBuilder querySourceBuilder) {
    this.querySourceBuilder = querySourceBuilder;
    return this;
  }

  /**
   * Constructs a new search source builder with a search query.
   *
   * @see org.elasticsearch.index.query.QueryBuilders
   */
  public GerritElasticSearchSourceBuilder query(QueryBuilder query) {
    if (this.querySourceBuilder == null) {
      this.querySourceBuilder = new QuerySourceBuilder();
    }
    this.querySourceBuilder.setQuery(query);
    return this;
  }

  /** Constructs a new search source builder with a raw search query. */
  public GerritElasticSearchSourceBuilder query(byte[] queryBinary) {
    return query(queryBinary, 0, queryBinary.length);
  }

  /** Constructs a new search source builder with a raw search query. */
  public GerritElasticSearchSourceBuilder query(
      byte[] queryBinary, int queryBinaryOffset, int queryBinaryLength) {
    return query(new BytesArray(queryBinary, queryBinaryOffset, queryBinaryLength));
  }

  /** Constructs a new search source builder with a raw search query. */
  public GerritElasticSearchSourceBuilder query(BytesReference queryBinary) {
    if (this.querySourceBuilder == null) {
      this.querySourceBuilder = new QuerySourceBuilder();
    }
    this.querySourceBuilder.setQuery(queryBinary);
    return this;
  }

  /** Constructs a new search source builder with a raw search query. */
  public GerritElasticSearchSourceBuilder query(String queryString) {
    return query(queryString.getBytes(Charsets.UTF_8));
  }

  /** Constructs a new search source builder with a query from a builder. */
  public GerritElasticSearchSourceBuilder query(XContentBuilder query) {
    return query(query.bytes());
  }

  /** Constructs a new search source builder with a query from a map. */
  public GerritElasticSearchSourceBuilder query(Map<String, ?> query) {
    try {
      XContentBuilder builder = XContentFactory.contentBuilder(Requests.CONTENT_TYPE);
      builder.map(query);
      return query(builder);
    } catch (IOException e) {
      throw new ElasticsearchGenerationException("Failed to generate [" + query + "]", e);
    }
  }

  /**
   * Sets a filter that will be executed after the query has been executed and only has affect on
   * the search hits (not aggregations). This filter is always executed as last filtering mechanism.
   */
  public GerritElasticSearchSourceBuilder postFilter(QueryBuilder postFilter) {
    this.postQueryBuilder = postFilter;
    return this;
  }

  /**
   * Sets a filter on the query executed that only applies to the search query (and not aggs for
   * example).
   */
  public GerritElasticSearchSourceBuilder postFilter(String postFilterString) {
    return postFilter(postFilterString.getBytes(Charsets.UTF_8));
  }

  /**
   * Sets a filter on the query executed that only applies to the search query (and not aggs for
   * example).
   */
  public GerritElasticSearchSourceBuilder postFilter(byte[] postFilter) {
    return postFilter(postFilter, 0, postFilter.length);
  }

  /**
   * Sets a filter on the query executed that only applies to the search query (and not aggs for
   * example).
   */
  public GerritElasticSearchSourceBuilder postFilter(
      byte[] postFilterBinary, int postFilterBinaryOffset, int postFilterBinaryLength) {
    return postFilter(
        new BytesArray(postFilterBinary, postFilterBinaryOffset, postFilterBinaryLength));
  }

  /**
   * Sets a filter on the query executed that only applies to the search query (and not aggs for
   * example).
   */
  public GerritElasticSearchSourceBuilder postFilter(BytesReference postFilterBinary) {
    this.filterBinary = postFilterBinary;
    return this;
  }

  /** Constructs a new search source builder with a query from a builder. */
  public GerritElasticSearchSourceBuilder postFilter(XContentBuilder postFilter) {
    return postFilter(postFilter.bytes());
  }

  /** Constructs a new search source builder with a query from a map. */
  public GerritElasticSearchSourceBuilder postFilter(Map<String, ?> postFilter) {
    try {
      XContentBuilder builder = XContentFactory.contentBuilder(Requests.CONTENT_TYPE);
      builder.map(postFilter);
      return postFilter(builder);
    } catch (IOException e) {
      throw new ElasticsearchGenerationException("Failed to generate [" + postFilter + "]", e);
    }
  }

  /** From index to start the search from. Defaults to <tt>0</tt>. */
  public GerritElasticSearchSourceBuilder from(int from) {
    this.from = from;
    return this;
  }

  /** The number of search hits to return. Defaults to <tt>10</tt>. */
  public GerritElasticSearchSourceBuilder size(int size) {
    this.size = size;
    return this;
  }

  /** Sets the minimum score below which docs will be filtered out. */
  public GerritElasticSearchSourceBuilder minScore(float minScore) {
    this.minScore = minScore;
    return this;
  }

  /**
   * Should each {@link org.elasticsearch.search.SearchHit} be returned with an explanation of the
   * hit (ranking).
   */
  public GerritElasticSearchSourceBuilder explain(Boolean explain) {
    this.explain = explain;
    return this;
  }

  /**
   * Should each {@link org.elasticsearch.search.SearchHit} be returned with a version associated
   * with it.
   */
  public GerritElasticSearchSourceBuilder version(Boolean version) {
    this.version = version;
    return this;
  }

  /** An optional timeout to control how long search is allowed to take. */
  public GerritElasticSearchSourceBuilder timeout(TimeValue timeout) {
    this.timeoutInMillis = timeout.millis();
    return this;
  }

  /** An optional timeout to control how long search is allowed to take. */
  public GerritElasticSearchSourceBuilder timeout(String timeout) {
    this.timeoutInMillis =
        TimeValue.parseTimeValue(timeout, null, getClass().getSimpleName() + ".timeout").millis();
    return this;
  }

  /**
   * An optional terminate_after to terminate the search after collecting <code>terminateAfter
   * </code> documents
   */
  public GerritElasticSearchSourceBuilder terminateAfter(int terminateAfter) {
    if (terminateAfter <= 0) {
      throw new IllegalArgumentException("terminateAfter must be > 0");
    }
    this.terminateAfter = terminateAfter;
    return this;
  }

  /**
   * Adds a sort against the given field name and the sort ordering.
   *
   * @param name The name of the field
   * @param order The sort ordering
   */
  public GerritElasticSearchSourceBuilder sort(String name, SortOrder order) {
    return sort(SortBuilders.fieldSort(name).order(order));
  }

  /**
   * Add a sort against the given field name.
   *
   * @param name The name of the field to sort by
   */
  public GerritElasticSearchSourceBuilder sort(String name) {
    return sort(SortBuilders.fieldSort(name));
  }

  /** Adds a sort builder. */
  public GerritElasticSearchSourceBuilder sort(SortBuilder sort) {
    if (sorts == null) {
      sorts = new ArrayList<>();
    }
    sorts.add(sort);
    return this;
  }

  /**
   * Applies when sorting, and controls if scores will be tracked as well. Defaults to
   * <tt>false</tt>.
   */
  public GerritElasticSearchSourceBuilder trackScores(boolean trackScores) {
    this.trackScores = trackScores;
    return this;
  }

  /** Add an get to perform as part of the search. */
  public GerritElasticSearchSourceBuilder aggregation(AbstractAggregationBuilder aggregation) {
    if (aggregations == null) {
      aggregations = new ArrayList<>();
    }
    aggregations.add(aggregation);
    return this;
  }

  /** Sets a raw (xcontent / json) addAggregation. */
  public GerritElasticSearchSourceBuilder aggregations(byte[] aggregationsBinary) {
    return aggregations(aggregationsBinary, 0, aggregationsBinary.length);
  }

  /** Sets a raw (xcontent / json) addAggregation. */
  public GerritElasticSearchSourceBuilder aggregations(
      byte[] aggregationsBinary, int aggregationsBinaryOffset, int aggregationsBinaryLength) {
    return aggregations(
        new BytesArray(aggregationsBinary, aggregationsBinaryOffset, aggregationsBinaryLength));
  }

  /** Sets a raw (xcontent / json) addAggregation. */
  public GerritElasticSearchSourceBuilder aggregations(BytesReference aggregationsBinary) {
    this.aggregationsBinary = aggregationsBinary;
    return this;
  }

  /** Sets a raw (xcontent / json) addAggregation. */
  public GerritElasticSearchSourceBuilder aggregations(XContentBuilder aggs) {
    return aggregations(aggs.bytes());
  }

  /**
   * Set the rescore window size for rescores that don't specify their window.
   *
   * @deprecated use {@link RescoreBuilder#windowSize(int)} instead.
   */
  @Deprecated
  public GerritElasticSearchSourceBuilder defaultRescoreWindowSize(int defaultRescoreWindowSize) {
    this.defaultRescoreWindowSize = defaultRescoreWindowSize;
    return this;
  }

  /** Sets a raw (xcontent / json) addAggregation. */
  public GerritElasticSearchSourceBuilder aggregations(Map<String, ?> aggregations) {
    try {
      XContentBuilder builder = XContentFactory.contentBuilder(Requests.CONTENT_TYPE);
      builder.map(aggregations);
      return aggregations(builder);
    } catch (IOException e) {
      throw new ElasticsearchGenerationException("Failed to generate [" + aggregations + "]", e);
    }
  }

  public HighlightBuilder highlighter() {
    if (highlightBuilder == null) {
      highlightBuilder = new HighlightBuilder();
    }
    return highlightBuilder;
  }

  /** Adds highlight to perform as part of the search. */
  public GerritElasticSearchSourceBuilder highlight(HighlightBuilder highlightBuilder) {
    this.highlightBuilder = highlightBuilder;
    return this;
  }

  public InnerHitsBuilder innerHitsBuilder() {
    if (innerHitsBuilder == null) {
      innerHitsBuilder = new InnerHitsBuilder();
    }
    return innerHitsBuilder;
  }

  public SuggestBuilder suggest() {
    if (suggestBuilder == null) {
      suggestBuilder = new SuggestBuilder("suggest");
    }
    return suggestBuilder;
  }

  public GerritElasticSearchSourceBuilder addRescorer(RescoreBuilder rescoreBuilder) {
    if (rescoreBuilders == null) {
      rescoreBuilders = new ArrayList<>();
    }
    rescoreBuilders.add(rescoreBuilder);
    return this;
  }

  public GerritElasticSearchSourceBuilder clearRescorers() {
    rescoreBuilders = null;
    return this;
  }

  /** Should the query be profiled. Defaults to <tt>false</tt> */
  public GerritElasticSearchSourceBuilder profile(boolean profile) {
    this.profile = profile;
    return this;
  }

  /** Return whether to profile query execution, or {@code null} if unspecified. */
  public boolean profile() {
    return profile;
  }

  /** Indicates whether the response should contain the stored _source for every hit */
  public GerritElasticSearchSourceBuilder fetchSource(boolean fetch) {
    if (this.fetchSourceContext == null) {
      this.fetchSourceContext = new FetchSourceContext(fetch);
    } else {
      this.fetchSourceContext.fetchSource(fetch);
    }
    return this;
  }

  /**
   * Indicate that _source should be returned with every hit, with an "include" and/or "exclude" set
   * which can include simple wildcard elements.
   *
   * @param include An optional include (optionally wildcarded) pattern to filter the returned
   *     _source
   * @param exclude An optional exclude (optionally wildcarded) pattern to filter the returned
   *     _source
   */
  public GerritElasticSearchSourceBuilder fetchSource(
      @Nullable String include, @Nullable String exclude) {
    return fetchSource(
        include == null ? Strings.EMPTY_ARRAY : new String[] {include},
        exclude == null ? Strings.EMPTY_ARRAY : new String[] {exclude});
  }

  /**
   * Indicate that _source should be returned with every hit, with an "include" and/or "exclude" set
   * which can include simple wildcard elements.
   *
   * @param includes An optional list of include (optionally wildcarded) pattern to filter the
   *     returned _source
   * @param excludes An optional list of exclude (optionally wildcarded) pattern to filter the
   *     returned _source
   */
  public GerritElasticSearchSourceBuilder fetchSource(
      @Nullable String[] includes, @Nullable String[] excludes) {
    fetchSourceContext = new FetchSourceContext(includes, excludes);
    return this;
  }

  /** Indicate how the _source should be fetched. */
  public GerritElasticSearchSourceBuilder fetchSource(
      @Nullable FetchSourceContext fetchSourceContext) {
    this.fetchSourceContext = fetchSourceContext;
    return this;
  }

  /** Sets no fields to be loaded, resulting in only id and type to be returned per field. */
  public GerritElasticSearchSourceBuilder noFields() {
    this.fieldNames = Collections.emptyList();
    return this;
  }

  /**
   * Sets the fields to load and return as part of the search request. If none are specified, the
   * source of the document will be returned.
   */
  public GerritElasticSearchSourceBuilder fields(List<String> fields) {
    this.fieldNames = fields;
    return this;
  }

  /**
   * Adds the fields to load and return as part of the search request. If none are specified, the
   * source of the document will be returned.
   */
  public GerritElasticSearchSourceBuilder fields(String... fields) {
    if (fieldNames == null) {
      fieldNames = new ArrayList<>();
    }
    Collections.addAll(fieldNames, fields);
    return this;
  }

  /**
   * Adds a field to load and return (note, it must be stored) as part of the search request. If
   * none are specified, the source of the document will be return.
   */
  public GerritElasticSearchSourceBuilder field(String name) {
    if (fieldNames == null) {
      fieldNames = new ArrayList<>();
    }
    fieldNames.add(name);
    return this;
  }

  /** Adds a field to load from the field data cache and return as part of the search request. */
  public GerritElasticSearchSourceBuilder fieldDataField(String name) {
    if (fieldDataFields == null) {
      fieldDataFields = new ArrayList<>();
    }
    fieldDataFields.add(name);
    return this;
  }

  /**
   * Adds a script field under the given name with the provided script.
   *
   * @param name The name of the field
   * @param script The script
   */
  public GerritElasticSearchSourceBuilder scriptField(String name, Script script) {
    if (scriptFields == null) {
      scriptFields = new ArrayList<>();
    }
    scriptFields.add(new ScriptField(name, script));
    return this;
  }

  /**
   * Sets the boost a specific index will receive when the query is executeed against it.
   *
   * @param index The index to apply the boost against
   * @param indexBoost The boost to apply to the index
   */
  public GerritElasticSearchSourceBuilder indexBoost(String index, float indexBoost) {
    if (this.indexBoost == null) {
      this.indexBoost = new HashMap<>();
    }
    this.indexBoost.put(index, indexBoost);
    return this;
  }

  /** The stats groups this request will be aggregated under. */
  public GerritElasticSearchSourceBuilder stats(String... statsGroups) {
    this.stats = statsGroups;
    return this;
  }

  @Override
  public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
    builder.startObject();
    innerToXContent(builder, params);
    builder.endObject();
    return builder;
  }

  public void innerToXContent(XContentBuilder builder, Params params) throws IOException {
    if (from != -1) {
      builder.field("from", from);
    }
    if (size != -1) {
      builder.field("size", size);
    }

    if (timeoutInMillis != -1) {
      builder.field("timeout", timeoutInMillis);
    }

    if (terminateAfter != SearchContext.DEFAULT_TERMINATE_AFTER) {
      builder.field("terminate_after", terminateAfter);
    }

    if (querySourceBuilder != null) {
      querySourceBuilder.innerToXContent(builder, params);
    }

    if (postQueryBuilder != null) {
      builder.field("post_filter");
      postQueryBuilder.toXContent(builder, params);
    }

    if (filterBinary != null) {
      if (XContentFactory.xContentType(filterBinary) == builder.contentType()) {
        builder.rawField("filter", filterBinary);
      } else {
        builder.field("filter_binary", filterBinary);
      }
    }

    if (minScore != null) {
      builder.field("min_score", minScore);
    }

    if (version != null) {
      builder.field("version", version);
    }

    if (explain != null) {
      builder.field("explain", explain);
    }

    if (profile) {
      builder.field("profile", true);
    }

    if (fetchSourceContext != null) {
      if (!fetchSourceContext.fetchSource()) {
        builder.field("_source", false);
      } else {
        builder.startObject("_source");
        builder.array("includes", fetchSourceContext.includes());
        builder.array("excludes", fetchSourceContext.excludes());
        builder.endObject();
      }
    }

    if (fieldNames != null) {
      if (fieldNames.size() == 1) {
        builder.field("stored_fields", fieldNames.get(0));
      } else {
        builder.startArray("stored_fields");
        for (String fieldName : fieldNames) {
          builder.value(fieldName);
        }
        builder.endArray();
      }
    }

    if (fieldDataFields != null) {
      builder.startArray("fielddata_fields");
      for (String fieldName : fieldDataFields) {
        builder.value(fieldName);
      }
      builder.endArray();
    }

    if (scriptFields != null) {
      builder.startObject("script_fields");
      for (ScriptField scriptField : scriptFields) {
        builder.startObject(scriptField.fieldName());
        builder.field("script", scriptField.script());
        builder.endObject();
      }
      builder.endObject();
    }

    if (sorts != null) {
      builder.startArray("sort");
      for (SortBuilder sort : sorts) {
        builder.startObject();
        sort.toXContent(builder, params);
        builder.endObject();
      }
      builder.endArray();
    }

    if (trackScores) {
      builder.field("track_scores", true);
    }

    if (indexBoost != null) {
      builder.startObject("indices_boost");
      assert !indexBoost.containsKey(null);
      for (Entry<String, Float> entry : indexBoost.entrySet()) {
        if (entry.getKey() != null) {
          builder.field(entry.getKey(), entry.getValue());
        }
      }
      builder.endObject();
    }

    if (aggregations != null) {
      builder.field("aggregations");
      builder.startObject();
      for (AbstractAggregationBuilder aggregation : aggregations) {
        aggregation.toXContent(builder, params);
      }
      builder.endObject();
    }

    if (aggregationsBinary != null) {
      if (XContentFactory.xContentType(aggregationsBinary) == builder.contentType()) {
        builder.rawField("aggregations", aggregationsBinary);
      } else {
        builder.field("aggregations_binary", aggregationsBinary);
      }
    }

    if (highlightBuilder != null) {
      highlightBuilder.toXContent(builder, params);
    }

    if (innerHitsBuilder != null) {
      innerHitsBuilder.toXContent(builder, params);
    }

    if (suggestBuilder != null) {
      suggestBuilder.toXContent(builder, params);
    }

    if (rescoreBuilders != null) {
      // Strip empty rescoreBuilders from the request
      Iterator<RescoreBuilder> itr = rescoreBuilders.iterator();
      while (itr.hasNext()) {
        if (itr.next().isEmpty()) {
          itr.remove();
        }
      }

      // Now build the request taking care to skip empty lists and only send the object form
      // if there is just one builder.
      if (rescoreBuilders.size() == 1) {
        builder.startObject("rescore");
        rescoreBuilders.get(0).toXContent(builder, params);
        if (rescoreBuilders.get(0).windowSize() == null && defaultRescoreWindowSize != null) {
          builder.field("window_size", defaultRescoreWindowSize);
        }
        builder.endObject();
      } else if (!rescoreBuilders.isEmpty()) {
        builder.startArray("rescore");
        for (RescoreBuilder rescoreBuilder : rescoreBuilders) {
          builder.startObject();
          rescoreBuilder.toXContent(builder, params);
          if (rescoreBuilder.windowSize() == null && defaultRescoreWindowSize != null) {
            builder.field("window_size", defaultRescoreWindowSize);
          }
          builder.endObject();
        }
        builder.endArray();
      }
    }

    if (stats != null) {
      builder.startArray("stats");
      for (String stat : stats) {
        builder.value(stat);
      }
      builder.endArray();
    }
  }

  private static class ScriptField {
    private final String fieldName;
    private final Script script;

    private ScriptField(String fieldName, Script script) {
      this.fieldName = fieldName;
      this.script = script;
    }

    public String fieldName() {
      return fieldName;
    }

    public Script script() {
      return script;
    }
  }
}
