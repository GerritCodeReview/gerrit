/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Type definitions used across multiple files in Gerrit

window.Gerrit = window.Gerrit || {};

/** @enum {string} */
Gerrit.CoverageType = {
  /**
   * start_character and end_character of the range will be ignored for this
   * type.
   */
  COVERED: 'COVERED',
  /**
   * start_character and end_character of the range will be ignored for this
   * type.
   */
  NOT_COVERED: 'NOT_COVERED',
  PARTIALLY_COVERED: 'PARTIALLY_COVERED',
  /**
   * You don't have to use this. If there is no coverage information for a
   * range, then it implicitly means NOT_INSTRUMENTED. start_character and
   * end_character of the range will be ignored for this type.
   */
  NOT_INSTRUMENTED: 'NOT_INSTRUMENTED',
};

/**
 * @typedef {{
 *   start_line: number,
 *   start_character: number,
 *   end_line: number,
 *   end_character: number,
 * }}
 */
Gerrit.Range;

/**
 * @typedef {{side: string, range: Gerrit.Range, hovering: boolean}}
 */
Gerrit.HoveredRange;

/**
 * @typedef {{
 *   side: string,
 *   type: Gerrit.CoverageType,
 *   code_range: Gerrit.Range,
 * }}
 */
Gerrit.CoverageRange;

/**
 * @typedef {{
 *    basePatchNum: (string|number),
 *    patchNum: (number),
 * }}
 */
Gerrit.PatchRange;

/**
 * @typedef {{
 *   changeNum: (string|number),
 *   endpoint: string,
 *   patchNum: (string|number|null|undefined),
 *   errFn: (function(?Response, string=)|null|undefined),
 *   params: (Object|null|undefined),
 *   fetchOptions: (Object|null|undefined),
 *   anonymizedEndpoint: (string|undefined),
 *   reportEndpointAsIs: (boolean|undefined),
 * }}
 */
Gerrit.ChangeFetchRequest;

/**
 * Object to describe a request for passing into _send.
 * - method is the HTTP method to use in the request.
 * - url is the URL for the request
 * - body is a request payload.
 *     TODO (beckysiegel) remove need for number at least.
 * - errFn is a function to invoke when the request fails.
 * - cancelCondition is a function that, if provided and returns true, will
 *   cancel the response after it resolves.
 * - contentType is the content type of the body.
 * - headers is a key-value hash to describe HTTP headers for the request.
 * - parseResponse states whether the result should be parsed as a JSON
 *     object using getResponseObject.
 * @typedef {{
 *   method: string,
 *   url: string,
 *   body: (string|number|Object|null|undefined),
 *   errFn: (function(?Response, string=)|null|undefined),
 *   contentType: (string|null|undefined),
 *   headers: (Object|undefined),
 *   parseResponse: (boolean|undefined),
 *   anonymizedUrl: (string|undefined),
 *   reportUrlAsIs: (boolean|undefined),
 * }}
 */
Gerrit.SendRequest;

/**
 * @typedef {{
 *   changeNum: (string|number),
 *   method: string,
 *   patchNum: (string|number|undefined),
 *   endpoint: string,
 *   body: (string|number|Object|null|undefined),
 *   errFn: (function(?Response, string=)|null|undefined),
 *   contentType: (string|null|undefined),
 *   headers: (Object|undefined),
 *   parseResponse: (boolean|undefined),
 *   anonymizedEndpoint: (string|undefined),
 *   reportEndpointAsIs: (boolean|undefined),
 * }}
 */
Gerrit.ChangeSendRequest;

/**
 * @typedef {{
 *    url: string,
 *    fetchOptions: (Object|null|undefined),
 *    anonymizedUrl: (string|undefined),
 * }}
 */
Gerrit.FetchRequest;

/**
 * Object to describe a request for passing into fetchJSON or fetchRawJSON.
 * - url is the URL for the request (excluding get params)
 * - errFn is a function to invoke when the request fails.
 * - cancelCondition is a function that, if provided and returns true, will
 *     cancel the response after it resolves.
 * - params is a key-value hash to specify get params for the request URL.
 * @typedef {{
 *    url: string,
 *    errFn: (function(?Response, string=)|null|undefined),
 *    cancelCondition: (function()|null|undefined),
 *    params: (Object|null|undefined),
 *    fetchOptions: (Object|null|undefined),
 *    anonymizedUrl: (string|undefined),
 *    reportUrlAsIs: (boolean|undefined),
 * }}
 */
Gerrit.FetchJSONRequest;

/**
 * @typedef {{
 *    message: string,
 *    icon: string,
 *    class: string,
 *  }}
 */
Gerrit.PushCertificateValidation;

/**
 * Object containing layout values to be used in rendering size-bars.
 * `max{Inserted,Deleted}` represent the largest values of the
 * `lines_inserted` and `lines_deleted` fields of the files respectively. The
 * `max{Addition,Deletion}Width` represent the width of the graphic allocated
 * to the insertion or deletion side respectively. Finally, the
 * `deletionOffset` value represents the x-position for the deletion bar.
 *
 * @typedef {{
 *    maxInserted: number,
 *    maxDeleted: number,
 *    maxAdditionWidth: number,
 *    maxDeletionWidth: number,
 *    deletionOffset: number,
 * }}
 */
Gerrit.LayoutStats;

/**
 * @typedef {{
 *    changeNum: number,
 *    path: string,
 *    patchRange: !Gerrit.PatchRange,
 *    projectConfig: (Object|undefined),
 * }}
 */
Gerrit.CommentMeta;

/**
 * @typedef {{
 *    meta: !Gerrit.CommentMeta,
 *    left: !Array,
 *    right: !Array,
 * }}
 */
Gerrit.CommentsBySide;

/**
 * The DiffIntralineInfo entity contains information about intraline edits in a
 * file.
 *
 * The information consists of a list of <skip length, mark length> pairs, where
 * the skip length is the number of characters between the end of the previous
 * edit and the start of this edit, and the mark length is the number of edited
 * characters following the skip. The start of the edits is from the beginning
 * of the related diff content lines.
 *
 * Note that the implied newline character at the end of each line is included
 * in the length calculation, and thus it is possible for the edits to span
 * newlines.
 * @typedef {!Array<number>}
 */
Gerrit.IntralineInfo;

/**
 * A portion of the diff that is treated the same.
 *
 * Called `DiffContent` in the API, see
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#diff-content
 *
 * @typedef {{
 *  ab: ?Array<!string>,
 *  a: ?Array<!string>,
 *  b: ?Array<!string>,
 *  skip: ?number,
 *  edit_a: ?Array<!Gerrit.IntralineInfo>,
 *  edit_b: ?Array<!Gerrit.IntralineInfo>,
 *  due_to_rebase: ?boolean,
 *  common: ?boolean
 * }}
 */
Gerrit.DiffChunk;

/**
 * Special line number which should not be collapsed into a shared region.
 *
 * @typedef {{
 *  number: number,
 *  leftSide: boolean
 * }}
 */
Gerrit.LineOfInterest;

/**
 * @typedef {{
 *    html: Node,
 *    position: number,
 *    length: number,
 * }}
 */
Gerrit.CommentLinkItem;

/**
 * @typedef {{
 *   name: string,
 *   value: Object,
 * }}
 */
Gerrit.GrSuggestionItem;

/**
 * @typedef {{
 *    getSuggestions: function(string): Promise<Array<Object>>,
 *    makeSuggestionItem: function(Object): Gerrit.GrSuggestionItem,
 * }}
 */
Gerrit.GrSuggestionsProvider;

/**
 * @typedef {{
 *  patch_set: ?number,
 *  id: ?string,
 *  path: ?Object,
 *  side: ?string,
 *  parent: ?number,
 *  line: ?Object,
 *  in_reply_to: ?string,
 *  message: ?Object,
 *  updated: ?string,
 *  author: ?Object,
 *  tag: ?Object,
 *  unresolved: ?boolean,
 *  robot_id: ?string,
 *  robot_run_id: ?string,
 *  url: ?string,
 *  properties: ?Object,
 *  fix_suggestions: ?Object,
 *  }}
 */
Gerrit.Comment;
