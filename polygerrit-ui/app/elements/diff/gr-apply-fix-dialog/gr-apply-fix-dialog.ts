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
import '@polymer/iron-icon/iron-icon';
import '../../../styles/shared-styles';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-overlay/gr-overlay';
import '../gr-diff/gr-diff';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-apply-fix-dialog_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property} from '@polymer/decorators';
import {
  NumericChangeId,
  EditPatchSetNum,
  FixId,
  FixSuggestionInfo,
  PatchSetNum,
  RobotId,
  BasePatchSetNum,
} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {isRobot} from '../../../utils/comment-util';
import {OpenFixPreviewEvent} from '../../../types/events';
import {getAppContext} from '../../../services/app-context';
import {fireCloseFixPreview, fireEvent} from '../../../utils/event-util';
import {DiffLayer, ParsedChangeInfo} from '../../../types/types';
import {GrButton} from '../../shared/gr-button/gr-button';
import {TokenHighlightLayer} from '../gr-diff-builder/token-highlight-layer';

export interface GrApplyFixDialog {
  $: {
    applyFixOverlay: GrOverlay;
    nextFix: GrButton;
  };
}

interface FilePreview {
  filepath: string;
  preview: DiffInfo;
}

@customElement('gr-apply-fix-dialog')
export class GrApplyFixDialog extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  prefs?: DiffPreferencesInfo;

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: String})
  changeNum?: NumericChangeId;

  @property({type: Number})
  _patchNum?: PatchSetNum;

  @property({type: String})
  _robotId?: RobotId;

  @property({type: Object})
  _currentFix?: FixSuggestionInfo;

  @property({type: Array})
  _currentPreviews: FilePreview[] = [];

  @property({type: Array})
  _fixSuggestions?: FixSuggestionInfo[];

  @property({type: Boolean})
  _isApplyFixLoading = false;

  @property({type: Number})
  _selectedFixIdx = 0;

  @property({
    type: Boolean,
    computed:
      '_computeDisableApplyFixButton(_isApplyFixLoading, change, ' +
      '_patchNum)',
  })
  _disableApplyFixButton = false;

  @property({type: Array})
  layers: DiffLayer[] = [];

  private refitOverlay?: () => void;

  private readonly restApiService = getAppContext().restApiService;

  constructor() {
    super();
    this.restApiService.getPreferences().then(prefs => {
      if (!prefs?.disable_token_highlighting) {
        this.layers = [new TokenHighlightLayer(this)];
      }
    });
  }

  /**
   * Given robot comment CustomEvent object, fetch diffs associated
   * with first robot comment suggested fix and open dialog.
   *
   * @param e to be passed from gr-comment with robot comment detail.
   * @return Promise that resolves either when all
   * preview diffs are fetched or no fix suggestions in custom event detail.
   */
  open(e: OpenFixPreviewEvent) {
    const detail = e.detail;
    const comment = detail.comment;

    if (comment?.message?.includes('```suggestion')) {
      const start =
        comment.message.indexOf('```suggestion\n') + '```suggestion\n'.length;
      const end = comment.message.indexOf('\n```', start);
      const replacement = comment.message.substring(start, end);
      this._fixSuggestions = [
        {
          fix_id: 'test' as FixId,
          description: 'User suggestion',
          replacements: [
            {
              path: comment.path!,
              range: {
                start_line: comment.line!,
                start_character: 0,
                end_line: comment.line!,
                end_character: 100,
              },
              replacement,
            },
          ],
        },
      ];
    } else {
      if (!detail.patchNum || !comment || !isRobot(comment)) {
        return Promise.resolve();
      }
      this._fixSuggestions = comment.fix_suggestions;
      this._robotId = comment.robot_id;
    }
    this._patchNum = detail.patchNum;
    if (!this._fixSuggestions || !this._fixSuggestions.length) {
      return Promise.resolve();
    }
    this._selectedFixIdx = 0;
    const promises = [];
    promises.push(
      this._showSelectedFixSuggestion(this._fixSuggestions[0]),
      this.$.applyFixOverlay.open()
    );
    return Promise.all(promises).then(() => {
      // ensures gr-overlay repositions overlay in center
      fireEvent(this.$.applyFixOverlay, 'iron-resize');
    });
  }

  override connectedCallback() {
    super.connectedCallback();
    this.refitOverlay = () => {
      // re-center the dialog as content changed
      fireEvent(this.$.applyFixOverlay, 'iron-resize');
    };
    this.addEventListener('diff-context-expanded', this.refitOverlay);
  }

  override disconnectedCallback() {
    if (this.refitOverlay) {
      this.removeEventListener('diff-context-expanded', this.refitOverlay);
    }
    super.disconnectedCallback();
  }

  _showSelectedFixSuggestion(fixSuggestion: FixSuggestionInfo) {
    this._currentFix = fixSuggestion;
    return this._fetchFixPreview(fixSuggestion.fix_id);
  }

  _fetchFixPreview(fixId: FixId) {
    if (!this.changeNum || !this._patchNum) {
      return Promise.reject(
        new Error('Both _patchNum and changeNum must be set')
      );
    }
    if (fixId === 'test') {
      this._currentPreviews = [
        {
          filepath: this._fixSuggestions![0].replacements[0].path,
          preview: {
            "meta_a": {
                "name": "polygerrit-ui/app/elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper.ts",
                "content_type": "application/typescript",
                "lines": 526,
                "web_links": [
                    {
                        "name": "browse",
                        "url": "https://gerrit.googlesource.com/gerrit/+/c99705db41c22330a5c30bee5902def9281a812f/polygerrit-ui/app/elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper.ts",
                        "target": "_blank"
                    }
                ]
            },
            "meta_b": {
                "name": "polygerrit-ui/app/elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper.ts",
                "content_type": "application/typescript",
                "lines": 523,
                "web_links": [
                    {
                        "name": "browse",
                        "url": "https://gerrit.googlesource.com/gerrit/+/refs/changes/26/316526/1/polygerrit-ui/app/elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper.ts",
                        "target": "_blank"
                    }
                ]
            },
            "intraline_status": "OK",
            "change_type": "MODIFIED",
            "diff_header": [
                "diff --git a/polygerrit-ui/app/elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper.ts b/polygerrit-ui/app/elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper.ts",
                "index 8db6606..90cfcfd 100644",
                "--- a/polygerrit-ui/app/elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper.ts",
                "+++ b/polygerrit-ui/app/elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper.ts"
            ],
            "content": [
                {
                    "ab": [
                        "/**",
                        " * @license",
                        " * Copyright (C) 2019 The Android Open Source Project",
                        " *",
                        " * Licensed under the Apache License, Version 2.0 (the \"License\");",
                        " * you may not use this file except in compliance with the License.",
                        " * You may obtain a copy of the License at",
                        " *",
                        " * http://www.apache.org/licenses/LICENSE-2.0",
                        " *",
                        " * Unless required by applicable law or agreed to in writing, software",
                        " * distributed under the License is distributed on an \"AS IS\" BASIS,",
                        " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.",
                        " * See the License for the specific language governing permissions and",
                        " * limitations under the License.",
                        " */",
                        "import {getBaseUrl} from '../../../../utils/url-util';",
                        "import {CancelConditionCallback} from '../../../../services/gr-rest-api/gr-rest-api';",
                        "import {",
                        "  AuthRequestInit,",
                        "  AuthService,",
                        "} from '../../../../services/gr-auth/gr-auth';",
                        "import {",
                        "  AccountDetailInfo,",
                        "  EmailInfo,",
                        "  ParsedJSON,",
                        "  RequestPayload,",
                        "} from '../../../../types/common';",
                        "import {HttpMethod} from '../../../../constants/constants';",
                        "import {RpcLogEventDetail} from '../../../../types/events';",
                        "import {fireNetworkError, fireServerError} from '../../../../utils/event-util';",
                        "import {FetchRequest} from '../../../../types/types';",
                        "import {ErrorCallback} from '../../../../api/rest';",
                        "",
                        "export const JSON_PREFIX = \")]}'\";",
                        "",
                        "export interface ResponsePayload {",
                        "  // TODO(TS): readResponsePayload can assign null to the parsed property if",
                        "  // it can't parse input data. However polygerrit assumes in many places",
                        "  // that the parsed property can't be null. We should update",
                        "  // readResponsePayload method and reject a promise instead of assigning",
                        "  // null to the parsed property",
                        "  parsed: ParsedJSON; // Can be null!!! See comment above",
                        "  raw: string;",
                        "}",
                        "",
                        "export function readResponsePayload(",
                        "  response: Response",
                        "): Promise<ResponsePayload> {",
                        "  return response.text().then(text => {",
                        "    let result;",
                        "    try {",
                        "      result = parsePrefixedJSON(text);"
                    ]
                },
                {
                    "a": [
                        "    } catch {"
                    ],
                    "b": [
                        "    } catch { // use new ecma6"
                    ],
                    "edit_a": [
                        [
                            12,
                            4
                        ]
                    ],
                    "edit_b": []
                },
                {
                    "ab": [
                        "      result = null;",
                        "    }",
                        "    // TODO(TS): readResponsePayload can assign null to the parsed property if",
                        "    // it can't parse input data. However polygerrit assumes in many places",
                        "    // that the parsed property can't be null. We should update",
                        "    // readResponsePayload method and reject a promise instead of assigning",
                        "    // null to the parsed property",
                        "    return {parsed: result!, raw: text};",
                        "  });",
                        "}",
                        "",
                        "export function parsePrefixedJSON(jsonWithPrefix: string): ParsedJSON {",
                        "  return JSON.parse(jsonWithPrefix.substring(JSON_PREFIX.length)) as ParsedJSON;",
                        "}",
                        "",
                        "/**",
                        " * Wrapper around Map for caching server responses. Site-based so that",
                        " * changes to CANONICAL_PATH will result in a different cache going into",
                        " * effect.",
                        " */",
                        "export class SiteBasedCache {",
                        "  // TODO(TS): Type looks unusual. Fix it.",
                        "  // Container of per-canonical-path caches.",
                        "  private readonly data = new Map<",
                        "    string | undefined,",
                        "    unknown | Map<string, ParsedJSON | null>",
                        "  >();",
                        "",
                        "  constructor() {",
                        "    if (window.INITIAL_DATA) {",
                        "      // Put all data shipped with index.html into the cache. This makes it",
                        "      // so that we spare more round trips to the server when the app loads",
                        "      // initially.",
                        "      Object.entries(window.INITIAL_DATA).forEach(e =>",
                        "        this._cache().set(e[0], e[1] as unknown as ParsedJSON)",
                        "      );",
                        "    }",
                        "  }",
                        "",
                        "  // Returns the cache for the current canonical path.",
                        "  _cache(): Map<string, unknown> {",
                        "    if (!this.data.has(window.CANONICAL_PATH)) {",
                        "      this.data.set(",
                        "        window.CANONICAL_PATH,",
                        "        new Map<string, ParsedJSON | null>()",
                        "      );",
                        "    }",
                        "    return this.data.get(window.CANONICAL_PATH) as Map<",
                        "      string,",
                        "      ParsedJSON | null",
                        "    >;",
                        "  }",
                        "",
                        "  has(key: string) {",
                        "    return this._cache().has(key);",
                        "  }",
                        "",
                        "  get(key: '/accounts/self/emails'): EmailInfo[] | null;",
                        "",
                        "  get(key: '/accounts/self/detail'): AccountDetailInfo[] | null;",
                        "",
                        "  get(key: string): ParsedJSON | null;",
                        "",
                        "  get(key: string): unknown {",
                        "    return this._cache().get(key);",
                        "  }",
                        "",
                        "  set(key: '/accounts/self/emails', value: EmailInfo[]): void;",
                        "",
                        "  set(key: '/accounts/self/detail', value: AccountDetailInfo[]): void;",
                        "",
                        "  set(key: string, value: ParsedJSON | null): void;",
                        "",
                        "  set(key: string, value: unknown) {",
                        "    this._cache().set(key, value);",
                        "  }",
                        "",
                        "  delete(key: string) {",
                        "    this._cache().delete(key);",
                        "  }",
                        "",
                        "  invalidatePrefix(prefix: string) {",
                        "    const newMap = new Map<string, unknown>();",
                        "    for (const [key, value] of this._cache().entries()) {",
                        "      if (!key.startsWith(prefix)) {",
                        "        newMap.set(key, value);",
                        "      }",
                        "    }",
                        "    this.data.set(window.CANONICAL_PATH, newMap);",
                        "  }",
                        "}",
                        "",
                        "type FetchPromisesCacheData = {",
                        "  [url: string]: Promise<ParsedJSON | undefined> | undefined;",
                        "};",
                        "",
                        "export class FetchPromisesCache {",
                        "  private data: FetchPromisesCacheData;",
                        "",
                        "  constructor() {",
                        "    this.data = {};",
                        "  }",
                        "",
                        "  public testOnlyGetData() {",
                        "    return this.data;",
                        "  }",
                        "",
                        "  /**",
                        "   * @return true only if a value for a key sets and it is not undefined",
                        "   */",
                        "  has(key: string): boolean {",
                        "    return !!this.data[key];",
                        "  }",
                        "",
                        "  get(key: string) {",
                        "    return this.data[key];",
                        "  }",
                        "",
                        "  /**",
                        "   * @param value a Promise to store in the cache. Pass undefined value to",
                        "   *     mark key as deleted.",
                        "   */",
                        "  set(key: string, value: Promise<ParsedJSON | undefined> | undefined) {",
                        "    this.data[key] = value;",
                        "  }",
                        "",
                        "  invalidatePrefix(prefix: string) {"
                    ]
                },
                {
                    "a": [
                        "    const newData: FetchPromisesCacheData = {};",
                        "    Object.entries(this.data).forEach(([key, value]) => {",
                        "      if (!key.startsWith(prefix)) {",
                        "        newData[key] = value;",
                        "      }",
                        "    });",
                        "    this.data = newData;"
                    ],
                    "b": [
                        "    const withPrefix = Object.entries(this.data).filter(([key, _]) => ",
                        "      key.startsWith(prefix)",
                        "    );",
                        "    this.data = Object.fromEntries(withPrefix);"
                    ],
                    "edit_a": [
                        [
                            10,
                            41
                        ],
                        [
                            28,
                            6
                        ],
                        [
                            8,
                            5
                        ],
                        [
                            6,
                            1
                        ],
                        [
                            7,
                            5
                        ],
                        [
                            22,
                            41
                        ],
                        [
                            5,
                            1
                        ],
                        [
                            19,
                            7
                        ]
                    ],
                    "edit_b": [
                        [
                            10,
                            12
                        ],
                        [
                            28,
                            5
                        ],
                        [
                            8,
                            1
                        ],
                        [
                            59,
                            30
                        ]
                    ]
                },
                {
                    "ab": [
                        "  }",
                        "}",
                        "export type FetchParams = {",
                        "  [name: string]: string[] | string | number | boolean | undefined | null;",
                        "};",
                        "",
                        "interface SendRequestBase {",
                        "  method: HttpMethod | undefined;",
                        "  body?: RequestPayload;",
                        "  contentType?: string;",
                        "  headers?: Record<string, string>;",
                        "  url: string;",
                        "  reportUrlAsIs?: boolean;",
                        "  anonymizedUrl?: string;",
                        "  errFn?: ErrorCallback;",
                        "}",
                        "",
                        "export interface SendRawRequest extends SendRequestBase {",
                        "  parseResponse?: false | null;",
                        "}",
                        "",
                        "export interface SendJSONRequest extends SendRequestBase {",
                        "  parseResponse: true;",
                        "}",
                        "",
                        "export type SendRequest = SendRawRequest | SendJSONRequest;",
                        "",
                        "export interface FetchJSONRequest extends FetchRequest {",
                        "  reportUrlAsIs?: boolean;",
                        "  params?: FetchParams;",
                        "  cancelCondition?: CancelConditionCallback;",
                        "  errFn?: ErrorCallback;",
                        "}",
                        "",
                        "// export function isRequestWithCancel<T extends FetchJSONRequest>(",
                        "//   x: T",
                        "// ): x is T & RequestWithCancel {",
                        "//   return !!(x as RequestWithCancel).cancelCondition;",
                        "// }",
                        "//",
                        "// export function isRequestWithErrFn<T extends FetchJSONRequest>(",
                        "//   x: T",
                        "// ): x is T & RequestWithErrFn {",
                        "//   return !!(x as RequestWithErrFn).errFn;",
                        "// }",
                        "",
                        "export class GrRestApiHelper {",
                        "  constructor(",
                        "    private readonly _cache: SiteBasedCache,",
                        "    private readonly _auth: AuthService,",
                        "    private readonly _fetchPromisesCache: FetchPromisesCache",
                        "  ) {}",
                        "",
                        "  /**",
                        "   * Wraps calls to the underlying authenticated fetch function (_auth.fetch)",
                        "   * with timing and logging.",
                        "s   */",
                        "  fetch(req: FetchRequest): Promise<Response> {",
                        "    const start = Date.now();",
                        "    const xhr = this._auth.fetch(req.url, req.fetchOptions);",
                        "",
                        "    // Log the call after it completes.",
                        "    xhr.then(res => this._logCall(req, start, res ? res.status : null));",
                        "",
                        "    // Return the XHR directly (without the log).",
                        "    return xhr;",
                        "  }",
                        "",
                        "  /**",
                        "   * Log information about a REST call. Because the elapsed time is determined",
                        "   * by this method, it should be called immediately after the request",
                        "   * finishes.",
                        "   *",
                        "   * @param startTime the time that the request was started.",
                        "   * @param status the HTTP status of the response. The status value",
                        "   *     is used here rather than the response object so there is no way this",
                        "   *     method can read the body stream.",
                        "   */",
                        "  private _logCall(",
                        "    req: FetchRequest,",
                        "    startTime: number,",
                        "    status: number | null",
                        "  ) {",
                        "    const method =",
                        "      req.fetchOptions && req.fetchOptions.method",
                        "        ? req.fetchOptions.method",
                        "        : 'GET';",
                        "    const endTime = Date.now();",
                        "    const elapsed = endTime - startTime;",
                        "    const startAt = new Date(startTime);",
                        "    const endAt = new Date(endTime);",
                        "    console.info(",
                        "      [",
                        "        'HTTP',",
                        "        status,",
                        "        method,",
                        "        `${elapsed}ms`,",
                        "        req.anonymizedUrl || req.url,",
                        "        `(${startAt.toISOString()}, ${endAt.toISOString()})`,",
                        "      ].join(' ')",
                        "    );",
                        "    if (req.anonymizedUrl) {",
                        "      const detail: RpcLogEventDetail = {",
                        "        status,",
                        "        method,",
                        "        elapsed,",
                        "        anonymizedUrl: req.anonymizedUrl,",
                        "      };",
                        "      document.dispatchEvent(",
                        "        new CustomEvent('gr-rpc-log', {",
                        "          detail,",
                        "          composed: true,",
                        "          bubbles: true,",
                        "        })",
                        "      );",
                        "    }",
                        "  }",
                        "",
                        "  /**",
                        "   * Fetch JSON from url provided.",
                        "   * Returns a Promise that resolves to a native Response.",
                        "   * Doesn't do error checking. Supports cancel condition. Performs auth.",
                        "   * Validates auth expiry errors.",
                        "   *",
                        "   * @return Promise which resolves to undefined if cancelCondition returns true",
                        "   *     and resolves to Response otherwise",
                        "   */",
                        "  fetchRawJSON(req: FetchJSONRequest): Promise<Response | undefined> {",
                        "    const urlWithParams = this.urlWithParams(req.url, req.params);",
                        "    const fetchReq: FetchRequest = {",
                        "      url: urlWithParams,",
                        "      fetchOptions: req.fetchOptions,",
                        "      anonymizedUrl: req.reportUrlAsIs ? urlWithParams : req.anonymizedUrl,",
                        "    };",
                        "    return this.fetch(fetchReq)",
                        "      .then((res: Response) => {",
                        "        if (req.cancelCondition && req.cancelCondition()) {",
                        "          if (res.body) {",
                        "            res.body.cancel();",
                        "          }",
                        "          return;",
                        "        }",
                        "        return res;",
                        "      })",
                        "      .catch(err => {",
                        "        if (req.errFn) {",
                        "          req.errFn.call(undefined, null, err);",
                        "        } else {",
                        "          fireNetworkError(err);",
                        "        }",
                        "        throw err;",
                        "      });",
                        "  }",
                        "",
                        "  /**",
                        "   * Fetch JSON from url provided.",
                        "   * Returns a Promise that resolves to a parsed response.",
                        "   * Same as {@link fetchRawJSON}, plus error handling.",
                        "   *",
                        "   * @param noAcceptHeader - don't add default accept json header",
                        "   */",
                        "  fetchJSON(",
                        "    req: FetchJSONRequest,",
                        "    noAcceptHeader?: boolean",
                        "  ): Promise<ParsedJSON | undefined> {",
                        "    if (!noAcceptHeader) {",
                        "      req = this.addAcceptJsonHeader(req);",
                        "    }",
                        "    return this.fetchRawJSON(req).then(response => {",
                        "      if (!response) {",
                        "        return;",
                        "      }",
                        "      if (!response.ok) {",
                        "        if (req.errFn) {",
                        "          req.errFn.call(null, response);",
                        "          return;",
                        "        }",
                        "        fireServerError(response, req);",
                        "        return;",
                        "      }",
                        "      return this.getResponseObject(response);",
                        "    });",
                        "  }",
                        "",
                        "  urlWithParams(url: string, fetchParams?: FetchParams): string {",
                        "    if (!fetchParams) {",
                        "      return getBaseUrl() + url;",
                        "    }",
                        "",
                        "    const params: Array<string | number | boolean> = [];",
                        "    for (const [p, paramValue] of Object.entries(fetchParams)) {",
                        "      // TODO(TS): Replace == null with === and check for null and undefined",
                        "      // eslint-disable-next-line eqeqeq",
                        "      if (paramValue == null) {",
                        "        params.push(this.encodeRFC5987(p));",
                        "        continue;",
                        "      }",
                        "      // TODO(TS): Unclear, why do we need the following code.",
                        "      // If paramValue can be array - we should either fix FetchParams type",
                        "      // or convert the array to a string before calling urlWithParams method.",
                        "      const paramValueAsArray = ([] as Array<string | number | boolean>).concat(",
                        "        paramValue",
                        "      );",
                        "      for (const value of paramValueAsArray) {",
                        "        params.push(`${this.encodeRFC5987(p)}=${this.encodeRFC5987(value)}`);",
                        "      }",
                        "    }",
                        "    return getBaseUrl() + url + '?' + params.join('&');",
                        "  }",
                        "",
                        "  // Backend encode url in RFC5987 and frontend needs to do same to match",
                        "  // queries for preloading queries",
                        "  encodeRFC5987(uri: string | number | boolean) {",
                        "    return encodeURIComponent(uri).replace(",
                        "      /['()*]/g,",
                        "      c => '%' + c.charCodeAt(0).toString(16)",
                        "    );",
                        "  }",
                        "",
                        "  getResponseObject(response: Response): Promise<ParsedJSON> {",
                        "    return readResponsePayload(response).then(payload => payload.parsed);",
                        "  }",
                        "",
                        "  addAcceptJsonHeader(req: FetchJSONRequest) {",
                        "    if (!req.fetchOptions) req.fetchOptions = {};",
                        "    if (!req.fetchOptions.headers) req.fetchOptions.headers = new Headers();",
                        "    if (!req.fetchOptions.headers.has('Accept')) {",
                        "      req.fetchOptions.headers.append('Accept', 'application/json');",
                        "    }",
                        "    return req;",
                        "  }",
                        "",
                        "  fetchCacheURL(req: FetchJSONRequest): Promise<ParsedJSON | undefined> {",
                        "    if (this._fetchPromisesCache.has(req.url)) {",
                        "      return this._fetchPromisesCache.get(req.url)!;",
                        "    }",
                        "    // TODO(andybons): Periodic cache invalidation.",
                        "    if (this._cache.has(req.url)) {",
                        "      return Promise.resolve(this._cache.get(req.url)!);",
                        "    }",
                        "    this._fetchPromisesCache.set(",
                        "      req.url,",
                        "      this.fetchJSON(req)",
                        "        .then(response => {",
                        "          if (response !== undefined) {",
                        "            this._cache.set(req.url, response);",
                        "          }",
                        "          this._fetchPromisesCache.set(req.url, undefined);",
                        "          return response;",
                        "        })",
                        "        .catch(err => {",
                        "          this._fetchPromisesCache.set(req.url, undefined);",
                        "          throw err;",
                        "        })",
                        "    );",
                        "    return this._fetchPromisesCache.get(req.url)!;",
                        "  }",
                        "",
                        "  // if errFn is not set, then only Response possible",
                        "  send(req: SendRawRequest & {errFn?: undefined}): Promise<Response>;",
                        "",
                        "  send(req: SendRawRequest): Promise<Response | undefined>;",
                        "",
                        "  send(req: SendJSONRequest): Promise<ParsedJSON>;",
                        "",
                        "  send(req: SendRequest): Promise<Response | ParsedJSON | undefined>;",
                        "",
                        "  /**",
                        "   * Send an XHR.",
                        "   *",
                        "   * @return Promise resolves to Response/ParsedJSON only if the request is successful",
                        "   *     (i.e. no exception and response.ok is true). If response fails then",
                        "   *     promise resolves either to void if errFn is set or rejects if errFn",
                        "   *     is not set   */",
                        "  send(req: SendRequest): Promise<Response | ParsedJSON | undefined> {",
                        "    const options: AuthRequestInit = {method: req.method};",
                        "    if (req.body) {",
                        "      options.headers = new Headers();",
                        "      options.headers.set(",
                        "        'Content-Type',",
                        "        req.contentType || 'application/json'",
                        "      );",
                        "      options.body =",
                        "        typeof req.body === 'string' ? req.body : JSON.stringify(req.body);",
                        "    }",
                        "    if (req.headers) {",
                        "      if (!options.headers) {",
                        "        options.headers = new Headers();",
                        "      }",
                        "      for (const [name, value] of Object.entries(req.headers)) {",
                        "        options.headers.set(name, value);",
                        "      }",
                        "    }",
                        "    const url = req.url.startsWith('http') ? req.url : getBaseUrl() + req.url;",
                        "    const fetchReq: FetchRequest = {",
                        "      url,",
                        "      fetchOptions: options,",
                        "      anonymizedUrl: req.reportUrlAsIs ? url : req.anonymizedUrl,",
                        "    };",
                        "    const xhr = this.fetch(fetchReq)",
                        "      .catch(err => {",
                        "        fireNetworkError(err);",
                        "        if (req.errFn) {",
                        "          return req.errFn.call(undefined, null, err);",
                        "        } else {",
                        "          throw err;",
                        "        }",
                        "      })",
                        "      .then(response => {",
                        "        if (response && !response.ok) {",
                        "          if (req.errFn) {",
                        "            req.errFn.call(undefined, response);",
                        "            return;",
                        "          }",
                        "          fireServerError(response, fetchReq);",
                        "        }",
                        "        return response;",
                        "      });",
                        "",
                        "    if (req.parseResponse) {",
                        "      // TODO(TS): remove as Response and fix error.",
                        "      // Javascript code allows returning of a Response object from errFn.",
                        "      // This can be a mistake and we should add check here or it can be used",
                        "      // somewhere - in this case we should fix it carefully (define",
                        "      // different type of callback if parseResponse is true, etc...).",
                        "      return xhr.then(res => this.getResponseObject(res as Response));",
                        "    }",
                        "    // The actual xhr type is Promise<Response|undefined|void> because of the",
                        "    // catch callback",
                        "    return xhr as Promise<Response | undefined>;",
                        "  }",
                        "",
                        "  invalidateFetchPromisesPrefix(prefix: string) {",
                        "    this._fetchPromisesCache.invalidatePrefix(prefix);",
                        "    this._cache.invalidatePrefix(prefix);",
                        "  }",
                        "}",
                        ""
                    ]
                }
            ]
        },
        },
      ];
      return Promise.resolve();
    } else {
      return this.restApiService
        .getRobotCommentFixPreview(this.changeNum, this._patchNum, fixId)
        .then(res => {
          if (res) {
            this._currentPreviews = Object.keys(res).map(key => {
              return {filepath: key, preview: res[key]};
            });
          }
        })
        .catch(err => {
          this._close(false);
          throw err;
        });
    }
  }

  hasSingleFix(_fixSuggestions?: FixSuggestionInfo[]) {
    return (_fixSuggestions || []).length === 1;
  }

  overridePartialPrefs(prefs?: DiffPreferencesInfo) {
    if (!prefs) return undefined;
    // generate a smaller gr-diff than fullscreen for dialog
    return {...prefs, line_length: 50};
  }

  onCancel(e: Event) {
    if (e) {
      e.stopPropagation();
    }
    this._close(false);
  }

  addOneTo(_selectedFixIdx: number) {
    return _selectedFixIdx + 1;
  }

  _onPrevFixClick(e: Event) {
    if (e) e.stopPropagation();
    if (this._selectedFixIdx >= 1 && this._fixSuggestions) {
      this._selectedFixIdx -= 1;
      this._showSelectedFixSuggestion(
        this._fixSuggestions[this._selectedFixIdx]
      );
    }
  }

  _onNextFixClick(e: Event) {
    if (e) e.stopPropagation();
    if (
      this._fixSuggestions &&
      this._selectedFixIdx < this._fixSuggestions.length
    ) {
      this._selectedFixIdx += 1;
      this._showSelectedFixSuggestion(
        this._fixSuggestions[this._selectedFixIdx]
      );
    }
  }

  _noPrevFix(_selectedFixIdx: number) {
    return _selectedFixIdx === 0;
  }

  _noNextFix(_selectedFixIdx: number, fixSuggestions?: FixSuggestionInfo[]) {
    if (!fixSuggestions) return true;
    return _selectedFixIdx === fixSuggestions.length - 1;
  }

  _close(fixApplied: boolean) {
    this._currentFix = undefined;
    this._currentPreviews = [];
    this._isApplyFixLoading = false;

    fireCloseFixPreview(this, fixApplied);
    this.$.applyFixOverlay.close();
  }

  _getApplyFixButtonLabel(isLoading: boolean) {
    return isLoading ? 'Saving...' : 'Apply Fix';
  }

  _computeTooltip(change?: ParsedChangeInfo, patchNum?: PatchSetNum) {
    if (!change || !patchNum) return '';
    const latestPatchNum = change.revisions[change.current_revision]._number;
    return latestPatchNum !== patchNum
      ? 'Fix can only be applied to the latest patchset'
      : '';
  }

  _computeDisableApplyFixButton(
    isApplyFixLoading: boolean,
    change?: ParsedChangeInfo,
    patchNum?: PatchSetNum
  ) {
    if (!change || isApplyFixLoading === undefined || patchNum === undefined) {
      return true;
    }
    const currentPatchNum = change.revisions[change.current_revision]._number;
    if (patchNum !== currentPatchNum) {
      return true;
    }
    return isApplyFixLoading;
  }

  _handleApplyFix(e: Event) {
    if (e) {
      e.stopPropagation();
    }

    const changeNum = this.changeNum;
    const patchNum = this._patchNum;
    const change = this.change;
    if (!changeNum || !patchNum || !change || !this._currentFix) {
      return Promise.reject(new Error('Not all required properties are set.'));
    }
    this._isApplyFixLoading = true;
    return this.restApiService
      .applyFixSuggestion(changeNum, patchNum, this._currentFix.fix_id)
      .then(res => {
        if (res && res.ok) {
          GerritNav.navigateToChange(change, {
            patchNum: EditPatchSetNum,
            basePatchNum: patchNum as BasePatchSetNum,
          });
          this._close(true);
        }
        this._isApplyFixLoading = false;
      });
  }

  getFixDescription(currentFix?: FixSuggestionInfo) {
    return currentFix && currentFix.description ? currentFix.description : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-apply-fix-dialog': GrApplyFixDialog;
  }
}
