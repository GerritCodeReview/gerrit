/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../../shared/gr-comment-thread/gr-comment-thread';
import '../../shared/gr-js-api-interface/gr-js-api-interface';
import '../gr-diff/gr-diff';
import '../gr-syntax-layer/gr-syntax-layer';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-diff-host_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {rangesEqual} from '../gr-diff/gr-diff-utils';
import {appContext} from '../../../services/app-context';
import {
  getParentIndex,
  isMergeParent,
  isNumber,
} from '../../../utils/patch-set-util';
import {
  Comment,
  isDraft,
  PatchSetFile,
  sortComments,
  TwoSidesComments,
  UIComment,
} from '../gr-comment-api/gr-comment-api';
import {customElement, observe, property} from '@polymer/decorators';
import {
  CommitRange,
  CoverageRange,
  DiffLayer,
  DiffLayerListener,
} from '../../../types/types';
import {
  Base64ImageFile,
  BlameInfo,
  CommentRange,
  DiffInfo,
  DiffPreferencesInfo,
  NumericChangeId,
  PatchRange,
  PatchSetNum,
  RepoName,
} from '../../../types/common';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {JsApiService} from '../../shared/gr-js-api-interface/gr-js-api-types';
import {GrDiff, LineOfInterest} from '../gr-diff/gr-diff';
import {GrSyntaxLayer} from '../gr-syntax-layer/gr-syntax-layer';
import {
  DiffViewMode,
  IgnoreWhitespaceType,
  Side,
} from '../../../constants/constants';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {FilesWebLinks} from '../gr-patch-range-select/gr-patch-range-select';
import {LineNumber} from '../gr-diff/gr-diff-line';
import {GrCommentThread} from '../../shared/gr-comment-thread/gr-comment-thread';

const MSG_EMPTY_BLAME = 'No blame information for this diff.';

const EVENT_AGAINST_PARENT = 'diff-against-parent';
const EVENT_ZERO_REBASE = 'rebase-percent-zero';
const EVENT_NONZERO_REBASE = 'rebase-percent-nonzero';

const TimingLabel = {
  TOTAL: 'Diff Total Render',
  CONTENT: 'Diff Content Render',
  SYNTAX: 'Diff Syntax Render',
};

// Disable syntax highlighting if the overall diff is too large.
const SYNTAX_MAX_DIFF_LENGTH = 20000;

// If any line of the diff is more than the character limit, then disable
// syntax highlighting for the entire file.
const SYNTAX_MAX_LINE_LENGTH = 500;

// 120 lines is good enough threshold for full-sized window viewport
const NUM_OF_LINES_THRESHOLD_FOR_VIEWPORT = 120;

function isImageDiff(diff?: DiffInfo) {
  if (!diff) return false;

  const isA = diff.meta_a && diff.meta_a.content_type.startsWith('image/');
  const isB = diff.meta_b && diff.meta_b.content_type.startsWith('image/');

  return !!(diff.binary && (isA || isB));
}

interface LineInfo {
  beforeNumber?: LineNumber;
  afterNumber?: LineNumber;
}

// TODO(TS): Consolidate this with the CommentThread interface of comment-api.
// What is being used here is just a local object for collecting all the data
// that is needed to create a GrCommentThread component, see
// _createThreadElement().
interface CommentThread {
  comments: UIComment[];
  // In the context of a diff each thread must have a side!
  commentSide: Side;
  patchNum?: PatchSetNum;
  lineNum?: LineNumber;
  isOnParent?: boolean;
  range?: CommentRange;
}

export interface GrDiffHost {
  $: {
    restAPI: RestApiService & Element;
    jsAPI: JsApiService & Element;
    syntaxLayer: GrSyntaxLayer & Element;
    diff: GrDiff;
  };
}

/**
 * Wrapper around gr-diff.
 *
 * Webcomponent fetching diffs and related data from restAPI and passing them
 * to the presentational gr-diff for rendering. <gr-diff-host> is a Gerrit
 * specific component, while <gr-diff> is a re-usable component.
 */
@customElement('gr-diff-host')
export class GrDiffHost extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the user selects a line.
   *
   * @event line-selected
   */

  /**
   * Fired if being logged in is required.
   *
   * @event show-auth-required
   */

  /**
   * Fired when a comment is saved or discarded
   *
   * @event diff-comments-modified
   */

  @property({type: Number})
  changeNum?: NumericChangeId;

  @property({type: Boolean})
  noAutoRender = false;

  @property({type: Object})
  patchRange?: PatchRange;

  @property({type: Object})
  file?: PatchSetFile;

  @property({type: String})
  path?: string;

  @property({type: Object})
  prefs?: DiffPreferencesInfo;

  @property({type: String})
  projectName?: RepoName;

  @property({type: Boolean})
  displayLine = false;

  @property({
    type: Boolean,
    computed: '_computeIsImageDiff(diff)',
    notify: true,
  })
  isImageDiff?: boolean;

  @property({type: Object})
  commitRange?: CommitRange;

  @property({type: Object, notify: true})
  filesWeblinks: FilesWebLinks | {} = {};

  @property({type: Boolean, reflectToAttribute: true})
  hidden = false;

  @property({type: Boolean})
  noRenderOnPrefsChange = false;

  @property({type: Object, observer: '_commentsChanged'})
  comments?: TwoSidesComments;

  @property({type: Boolean})
  lineWrapping = false;

  @property({type: String})
  viewMode = DiffViewMode.SIDE_BY_SIDE;

  @property({type: Object})
  lineOfInterest?: LineOfInterest;

  @property({type: Boolean})
  showLoadFailure?: boolean;

  @property({
    type: Boolean,
    notify: true,
    computed: '_computeIsBlameLoaded(_blame)',
  })
  isBlameLoaded?: boolean;

  @property({type: Boolean})
  _loggedIn = false;

  @property({type: Boolean})
  _loading = false;

  @property({type: String})
  _errorMessage: string | null = null;

  @property({type: Object})
  _baseImage: Base64ImageFile | null = null;

  @property({type: Object})
  _revisionImage: Base64ImageFile | null = null;

  @property({type: Object, notify: true})
  diff?: DiffInfo;

  @property({type: Object})
  _fetchDiffPromise: Promise<DiffInfo> | null = null;

  @property({type: Object})
  _blame: BlameInfo[] | null = null;

  @property({type: Array})
  _coverageRanges: CoverageRange[] = [];

  @property({type: String})
  _loadedWhitespaceLevel?: IgnoreWhitespaceType;

  @property({type: Number, computed: '_computeParentIndex(patchRange.*)'})
  _parentIndex: number | null = null;

  @property({
    type: Boolean,
    computed: '_isSyntaxHighlightingEnabled(prefs.*, diff)',
  })
  _syntaxHighlightingEnabled?: boolean;

  @property({type: Array})
  _layers: DiffLayer[] = [];

  private readonly reporting = appContext.reportingService;

  /** @override */
  created() {
    super.created();
    this.addEventListener(
      // These are named inconsistently for a reason:
      // The create-comment event is fired to indicate that we should
      // create a comment.
      // The comment-* events are just notifying that the comments did already
      // change in some way, and that we should update any models we may want
      // to keep in sync.
      'create-comment',
      e => this._handleCreateComment(e)
    );
    this.addEventListener('comment-discard', e =>
      this._handleCommentDiscard(e)
    );
    this.addEventListener('comment-update', e => this._handleCommentUpdate(e));
    this.addEventListener('comment-save', e => this._handleCommentSave(e));
    this.addEventListener('render-start', () => this._handleRenderStart());
    this.addEventListener('render-content', () => this._handleRenderContent());
    this.addEventListener('normalize-range', event =>
      this._handleNormalizeRange(event)
    );
    this.addEventListener('diff-context-expanded', event =>
      this._handleDiffContextExpanded(event)
    );
  }

  /** @override */
  ready() {
    super.ready();
    if (this._canReload()) {
      this.reload();
    }
  }

  /** @override */
  attached() {
    super.attached();
    this._getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
    });
  }

  /** @override */
  detached() {
    super.detached();
    this.clear();
  }

  /**
   * @param shouldReportMetric indicate a new Diff Page. This is a
   * signal to report metrics event that started on location change.
   * @return
   */
  reload(shouldReportMetric?: boolean) {
    this.clear();
    if (!this.path) throw new Error('Missing required "path" property.');
    if (!this.changeNum) throw new Error('Missing required "changeNum" prop.');
    this._loading = true;
    this._errorMessage = null;
    const whitespaceLevel = this._getIgnoreWhitespace();

    const layers: DiffLayer[] = [this.$.syntaxLayer];
    // Get layers from plugins (if any).
    for (const pluginLayer of this.$.jsAPI.getDiffLayers(
      this.path,
      this.changeNum
    )) {
      layers.push(pluginLayer);
    }
    this._layers = layers;

    if (shouldReportMetric) {
      // We listen on render viewport only on DiffPage (on paramsChanged)
      this._listenToViewportRender();
    }

    this._coverageRanges = [];
    this._getCoverageData();
    const diffRequest = this._getDiff()
      .then(diff => {
        this._loadedWhitespaceLevel = whitespaceLevel;
        this._reportDiff(diff);
        return diff;
      })
      .catch(e => {
        this._handleGetDiffError(e);
        return null;
      });

    const assetRequest = diffRequest.then(diff => {
      // If the diff is null, then it's failed to load.
      if (!diff) {
        return null;
      }

      return this._loadDiffAssets(diff);
    });

    // Not waiting for coverage ranges intentionally as
    // plugin loading should not block the content rendering
    return Promise.all([diffRequest, assetRequest])
      .then(results => {
        const diff = results[0];
        if (!diff) {
          return Promise.resolve();
        }
        this.filesWeblinks = this._getFilesWeblinks(diff);
        return new Promise(resolve => {
          const callback = (event: CustomEvent) => {
            const needsSyntaxHighlighting =
              event.detail && event.detail.contentRendered;
            if (needsSyntaxHighlighting) {
              this.reporting.time(TimingLabel.SYNTAX);
              this.$.syntaxLayer.process().finally(() => {
                this.reporting.timeEnd(TimingLabel.SYNTAX);
                this.reporting.timeEnd(TimingLabel.TOTAL);
                resolve();
              });
            } else {
              this.reporting.timeEnd(TimingLabel.TOTAL);
              resolve();
            }
            this.removeEventListener('render', callback);
            if (shouldReportMetric) {
              // We report diffViewContentDisplayed only on reload caused
              // by params changed - expected only on Diff Page.
              this.reporting.diffViewContentDisplayed();
            }
          };
          this.addEventListener('render', callback);
          this.diff = diff;
        });
      })
      .catch(err => {
        console.warn('Error encountered loading diff:', err);
      })
      .then(() => {
        this._loading = false;
      });
  }

  clear() {
    if (this.path) this.$.jsAPI.disposeDiffLayers(this.path);
    this._layers = [];
  }

  _getCoverageData() {
    if (!this.changeNum) throw new Error('Missing required "changeNum" prop.');
    if (!this.path) throw new Error('Missing required "path" prop.');
    if (!this.patchRange) throw new Error('Missing required "patchRange".');
    const changeNum = this.changeNum;
    const path = this.path;
    // Coverage providers do not provide data for EDIT and PARENT patch sets.
    const basePatchNum = isNumber(this.patchRange.basePatchNum)
      ? this.patchRange.basePatchNum
      : undefined;
    const patchNum = isNumber(this.patchRange.patchNum)
      ? this.patchRange.patchNum
      : undefined;
    this.$.jsAPI
      .getCoverageAnnotationApi()
      .then(coverageAnnotationApi => {
        if (!coverageAnnotationApi) return;
        const provider = coverageAnnotationApi.getCoverageProvider();
        if (!provider) return;
        return provider(changeNum, path, basePatchNum, patchNum).then(
          coverageRanges => {
            if (!this.patchRange) throw new Error('Missing "patchRange".');
            if (
              !coverageRanges ||
              changeNum !== this.changeNum ||
              path !== this.path ||
              basePatchNum !== this.patchRange.basePatchNum ||
              patchNum !== this.patchRange.patchNum
            ) {
              return;
            }

            const existingCoverageRanges = this._coverageRanges;
            this._coverageRanges = coverageRanges;

            // Notify with existing coverage ranges
            // in case there is some existing coverage data that needs to be removed
            existingCoverageRanges.forEach(range => {
              coverageAnnotationApi.notify(
                path,
                range.code_range.start_line,
                range.code_range.end_line,
                range.side
              );
            });

            // Notify with new coverage data
            coverageRanges.forEach(range => {
              coverageAnnotationApi.notify(
                path,
                range.code_range.start_line,
                range.code_range.end_line,
                range.side
              );
            });
          }
        );
      })
      .catch(err => {
        console.warn('Loading coverage ranges failed: ', err);
      });
  }

  _getFilesWeblinks(diff: DiffInfo) {
    if (!this.projectName || !this.commitRange || !this.path) return {};
    return {
      meta_a: GerritNav.getFileWebLinks(
        this.projectName,
        this.commitRange.baseCommit,
        this.path,
        {weblinks: diff && diff.meta_a && diff.meta_a.web_links}
      ),
      meta_b: GerritNav.getFileWebLinks(
        this.projectName,
        this.commitRange.commit,
        this.path,
        {weblinks: diff && diff.meta_b && diff.meta_b.web_links}
      ),
    };
  }

  /** Cancel any remaining diff builder rendering work. */
  cancel() {
    this.$.diff.cancel();
    this.$.syntaxLayer.cancel();
  }

  getCursorStops() {
    return this.$.diff.getCursorStops();
  }

  isRangeSelected() {
    return this.$.diff.isRangeSelected();
  }

  createRangeComment() {
    return this.$.diff.createRangeComment();
  }

  toggleLeftDiff() {
    this.$.diff.toggleLeftDiff();
  }

  /**
   * Load and display blame information for the base of the diff.
   */
  loadBlame(): Promise<BlameInfo[]> {
    if (!this.changeNum) throw new Error('Missing required "changeNum" prop.');
    if (!this.patchRange) throw new Error('Missing required "patchRange".');
    if (!this.path) throw new Error('Missing required "path" property.');
    return this.$.restAPI
      .getBlame(this.changeNum, this.patchRange.patchNum, this.path, true)
      .then(blame => {
        if (!blame || !blame.length) {
          this.dispatchEvent(
            new CustomEvent('show-alert', {
              detail: {message: MSG_EMPTY_BLAME},
              composed: true,
              bubbles: true,
            })
          );
          return Promise.reject(MSG_EMPTY_BLAME);
        }

        this._blame = blame;
        return blame;
      });
  }

  clearBlame() {
    this._blame = null;
  }

  getThreadEls(): GrCommentThread[] {
    return Array.from(this.$.diff.querySelectorAll('.comment-thread'));
  }

  addDraftAtLine(el: Element) {
    this.$.diff.addDraftAtLine(el);
  }

  clearDiffContent() {
    this.$.diff.clearDiffContent();
  }

  expandAllContext() {
    this.$.diff.expandAllContext();
  }

  _getLoggedIn() {
    return this.$.restAPI.getLoggedIn();
  }

  _canReload() {
    return (
      !!this.changeNum && !!this.patchRange && !!this.path && !this.noAutoRender
    );
  }

  // TODO(milutin): Use rest-api with fetchCacheURL instead of this.
  prefetchDiff() {
    if (
      !!this.changeNum &&
      !!this.patchRange &&
      !!this.path &&
      this._fetchDiffPromise === null
    ) {
      this._fetchDiffPromise = this._getDiff();
    }
  }

  _getDiff(): Promise<DiffInfo> {
    if (this._fetchDiffPromise !== null) {
      const fetchDiffPromise = this._fetchDiffPromise;
      this._fetchDiffPromise = null;
      return fetchDiffPromise;
    }
    // Wrap the diff request in a new promise so that the error handler
    // rejects the promise, allowing the error to be handled in the .catch.
    return new Promise((resolve, reject) => {
      if (!this.changeNum) throw new Error('Missing required "changeNum".');
      if (!this.patchRange) throw new Error('Missing required "patchRange".');
      if (!this.path) throw new Error('Missing required "path" property.');
      this.$.restAPI
        .getDiff(
          this.changeNum,
          this.patchRange.basePatchNum,
          this.patchRange.patchNum,
          this.path,
          this._getIgnoreWhitespace(),
          reject
        )
        .then(resolve);
    });
  }

  _handleGetDiffError(response: Response) {
    // Loading the diff may respond with 409 if the file is too large. In this
    // case, use a toast error..
    if (response.status === 409) {
      this.dispatchEvent(
        new CustomEvent('server-error', {
          detail: {response},
          composed: true,
          bubbles: true,
        })
      );
      return;
    }

    if (this.showLoadFailure) {
      this._errorMessage = [
        'Encountered error when loading the diff:',
        response.status,
        response.statusText,
      ].join(' ');
      return;
    }

    this.dispatchEvent(
      new CustomEvent('page-error', {
        detail: {response},
        composed: true,
        bubbles: true,
      })
    );
  }

  /**
   * Report info about the diff response.
   */
  _reportDiff(diff?: DiffInfo) {
    if (!diff || !diff.content) return;

    // Count the delta lines stemming from normal deltas, and from
    // due_to_rebase deltas.
    let nonRebaseDelta = 0;
    let rebaseDelta = 0;
    diff.content.forEach(chunk => {
      if (chunk.ab) {
        return;
      }
      const deltaSize = Math.max(
        chunk.a ? chunk.a.length : 0,
        chunk.b ? chunk.b.length : 0
      );
      if (chunk.due_to_rebase) {
        rebaseDelta += deltaSize;
      } else {
        nonRebaseDelta += deltaSize;
      }
    });

    // Find the percent of the delta from due_to_rebase chunks rounded to two
    // digits. Diffs with no delta are considered 0%.
    const totalDelta = rebaseDelta + nonRebaseDelta;
    const percentRebaseDelta = !totalDelta
      ? 0
      : Math.round((100 * rebaseDelta) / totalDelta);

    // Report the due_to_rebase percentage in the "diff" category when
    // applicable.
    if (!this.patchRange) throw new Error('Missing required "patchRange".');
    if (this.patchRange.basePatchNum === 'PARENT') {
      this.reporting.reportInteraction(EVENT_AGAINST_PARENT);
    } else if (percentRebaseDelta === 0) {
      this.reporting.reportInteraction(EVENT_ZERO_REBASE);
    } else {
      this.reporting.reportInteraction(EVENT_NONZERO_REBASE, {
        percentRebaseDelta,
      });
    }
  }

  _loadDiffAssets(diff?: DiffInfo) {
    if (isImageDiff(diff)) {
      // diff! is justified, because isImageDiff() returns false otherwise
      return this._getImages(diff!).then(images => {
        this._baseImage = images.baseImage;
        this._revisionImage = images.revisionImage;
      });
    } else {
      this._baseImage = null;
      this._revisionImage = null;
      return Promise.resolve();
    }
  }

  _computeIsImageDiff(diff?: DiffInfo) {
    return isImageDiff(diff);
  }

  _commentsChanged(newComments: TwoSidesComments) {
    const allComments = [];
    for (const side of [Side.LEFT, Side.RIGHT]) {
      // This is needed by the threading.
      for (const comment of newComments[side]) {
        comment.__commentSide = side;
      }
      allComments.push(...newComments[side]);
    }
    // Currently, the only way this is ever changed here is when the initial
    // comments are loaded, so it's okay performance wise to clear the threads
    // and recreate them. If this changes in future, we might want to reuse
    // some DOM nodes here.
    this._clearThreads();
    const threads = this._createThreads(allComments);
    for (const thread of threads) {
      const threadEl = this._createThreadElement(thread);
      this._attachThreadElement(threadEl);
    }
  }

  _createThreads(comments: UIComment[]): CommentThread[] {
    const sortedComments = sortComments(comments);
    const threads = [];
    for (const comment of sortedComments) {
      // If the comment is in reply to another comment, find that comment's
      // thread and append to it.
      if (comment.in_reply_to) {
        const thread = threads.find(thread =>
          thread.comments.some(c => c.id === comment.in_reply_to)
        );
        if (thread) {
          thread.comments.push(comment);
          continue;
        }
      }

      // Otherwise, this comment starts its own thread.
      if (!comment.__commentSide) throw new Error('Missing "__commentSide".');
      const newThread: CommentThread = {
        comments: [comment],
        commentSide: comment.__commentSide,
        patchNum: comment.patch_set,
        lineNum: comment.line,
        isOnParent: comment.side === 'PARENT',
      };
      if (comment.range) {
        newThread.range = {...comment.range};
      }
      threads.push(newThread);
    }
    return threads;
  }

  _computeIsBlameLoaded(blame: BlameInfo[] | null) {
    return !!blame;
  }

  _getImages(diff: DiffInfo) {
    if (!this.changeNum) throw new Error('Missing required "changeNum" prop.');
    if (!this.patchRange) throw new Error('Missing required "patchRange".');
    return this.$.restAPI.getImagesForDiff(
      this.changeNum,
      diff,
      this.patchRange
    );
  }

  _handleCreateComment(e: CustomEvent) {
    const {lineNum, side, patchNum, isOnParent, range} = e.detail;
    const threadEl = this._getOrCreateThread(
      patchNum,
      lineNum,
      side,
      range,
      isOnParent
    );
    threadEl.addOrEditDraft(lineNum, range);

    this.reporting.recordDraftInteraction();
  }

  /**
   * Gets or creates a comment thread at a given location.
   * May provide a range, to get/create a range comment.
   */
  _getOrCreateThread(
    patchNum: PatchSetNum,
    lineNum: LineNumber | undefined,
    commentSide: Side,
    range?: CommentRange,
    isOnParent?: boolean
  ): GrCommentThread {
    let threadEl = this._getThreadEl(lineNum, commentSide, range);
    if (!threadEl) {
      threadEl = this._createThreadElement({
        comments: [],
        commentSide,
        patchNum,
        lineNum,
        range,
        isOnParent,
      });
      this._attachThreadElement(threadEl);
    }
    return threadEl;
  }

  _attachThreadElement(threadEl: Element) {
    this.$.diff.appendChild(threadEl);
  }

  _clearThreads() {
    for (const threadEl of this.getThreadEls()) {
      const parent = threadEl.parentNode;
      if (parent) parent.removeChild(threadEl);
    }
  }

  _createThreadElement(thread: CommentThread) {
    const threadEl = document.createElement('gr-comment-thread');
    threadEl.className = 'comment-thread';
    threadEl.setAttribute('slot', `${thread.commentSide}-${thread.lineNum}`);
    threadEl.comments = thread.comments;
    threadEl.commentSide = thread.commentSide;
    threadEl.isOnParent = !!thread.isOnParent;
    threadEl.parentIndex = this._parentIndex;
    // Use path before renmaing when comment added on the left when comparing
    // two patch sets (not against base)
    if (
      this.file &&
      this.file.basePath &&
      thread.commentSide === Side.LEFT &&
      !thread.isOnParent
    ) {
      threadEl.path = this.file.basePath;
    } else {
      threadEl.path = this.path;
    }
    threadEl.changeNum = this.changeNum;
    threadEl.patchNum = thread.patchNum;
    threadEl.showPatchset = false;
    // GrCommentThread does not understand 'FILE', but requires undefined.
    threadEl.lineNum = thread.lineNum !== 'FILE' ? thread.lineNum : undefined;
    threadEl.projectName = this.projectName;
    threadEl.range = thread.range;
    const threadDiscardListener = (e: Event) => {
      const threadEl = e.currentTarget as Element;
      const parent = threadEl.parentNode;
      if (parent) parent.removeChild(threadEl);
      threadEl.removeEventListener('thread-discard', threadDiscardListener);
    };
    threadEl.addEventListener('thread-discard', threadDiscardListener);
    return threadEl;
  }

  /**
   * Gets a comment thread element at a given location.
   * May provide a range, to get a range comment.
   */
  _getThreadEl(
    lineNum: LineNumber | undefined,
    commentSide: Side,
    range?: CommentRange
  ): GrCommentThread | null {
    let line: LineInfo;
    if (commentSide === Side.LEFT) {
      line = {beforeNumber: lineNum};
    } else if (commentSide === Side.RIGHT) {
      line = {afterNumber: lineNum};
    } else {
      throw new Error(`Unknown side: ${commentSide}`);
    }
    function matchesRange(threadEl: GrCommentThread) {
      const rangeAtt = threadEl.getAttribute('range');
      const threadRange = rangeAtt
        ? (JSON.parse(rangeAtt) as CommentRange)
        : undefined;
      return rangesEqual(threadRange, range);
    }

    const filteredThreadEls = this._filterThreadElsForLocation(
      this.getThreadEls(),
      line,
      commentSide
    ).filter(matchesRange);
    return filteredThreadEls.length ? filteredThreadEls[0] : null;
  }

  _filterThreadElsForLocation(
    threadEls: GrCommentThread[],
    lineInfo: LineInfo,
    side: Side
  ) {
    function matchesLeftLine(threadEl: GrCommentThread) {
      return (
        threadEl.getAttribute('comment-side') === Side.LEFT &&
        threadEl.getAttribute('line-num') === String(lineInfo.beforeNumber)
      );
    }
    function matchesRightLine(threadEl: GrCommentThread) {
      return (
        threadEl.getAttribute('comment-side') === Side.RIGHT &&
        threadEl.getAttribute('line-num') === String(lineInfo.afterNumber)
      );
    }
    function matchesFileComment(threadEl: GrCommentThread) {
      return (
        threadEl.getAttribute('comment-side') === side &&
        // line/range comments have 1-based line set, if line is falsy it's
        // a file comment
        !threadEl.getAttribute('line-num')
      );
    }

    // Select the appropriate matchers for the desired side and line
    // If side is BOTH, we want both the left and right matcher.
    const matchers: ((thread: GrCommentThread) => boolean)[] = [];
    if (side !== Side.RIGHT) {
      matchers.push(matchesLeftLine);
    }
    if (side !== Side.LEFT) {
      matchers.push(matchesRightLine);
    }
    if (lineInfo.afterNumber === 'FILE' || lineInfo.beforeNumber === 'FILE') {
      matchers.push(matchesFileComment);
    }
    return threadEls.filter(threadEl =>
      matchers.some(matcher => matcher(threadEl))
    );
  }

  _getIgnoreWhitespace(): IgnoreWhitespaceType {
    if (!this.prefs || !this.prefs.ignore_whitespace) {
      return IgnoreWhitespaceType.IGNORE_NONE;
    }
    return this.prefs.ignore_whitespace;
  }

  @observe(
    'prefs.ignore_whitespace',
    '_loadedWhitespaceLevel',
    'noRenderOnPrefsChange'
  )
  _whitespaceChanged(
    preferredWhitespaceLevel?: IgnoreWhitespaceType,
    loadedWhitespaceLevel?: IgnoreWhitespaceType,
    noRenderOnPrefsChange?: boolean
  ) {
    if (preferredWhitespaceLevel === undefined) return;
    if (loadedWhitespaceLevel === undefined) return;
    if (noRenderOnPrefsChange === undefined) return;

    this._fetchDiffPromise = null;
    if (
      preferredWhitespaceLevel !== loadedWhitespaceLevel &&
      !noRenderOnPrefsChange
    ) {
      this.reload();
    }
  }

  @observe('noRenderOnPrefsChange', 'prefs.*')
  _syntaxHighlightingChanged(
    noRenderOnPrefsChange?: boolean,
    prefsChangeRecord?: PolymerDeepPropertyChange<
      DiffPreferencesInfo,
      DiffPreferencesInfo
    >
  ) {
    if (noRenderOnPrefsChange === undefined) return;
    if (prefsChangeRecord === undefined) return;
    if (prefsChangeRecord.path !== 'prefs.syntax_highlighting') return;

    if (!noRenderOnPrefsChange) this.reload();
  }

  _computeParentIndex(
    patchRangeRecord: PolymerDeepPropertyChange<PatchRange, PatchRange>
  ) {
    if (!patchRangeRecord.base) return null;
    return isMergeParent(patchRangeRecord.base.basePatchNum)
      ? getParentIndex(patchRangeRecord.base.basePatchNum)
      : null;
  }

  _handleCommentSave(e: CustomEvent) {
    const comment = e.detail.comment;
    const side = e.detail.comment.__commentSide;
    const idx = this._findDraftIndex(comment, side);
    this.set(['comments', side, idx], comment);
    this._handleCommentSaveOrDiscard();
  }

  _handleCommentDiscard(e: CustomEvent) {
    const comment = e.detail.comment;
    this._removeComment(comment);
    this._handleCommentSaveOrDiscard();
  }

  _handleCommentUpdate(e: CustomEvent) {
    const comment = e.detail.comment;
    const side = e.detail.comment.__commentSide;
    let idx = this._findCommentIndex(comment, side);
    if (idx === -1) {
      idx = this._findDraftIndex(comment, side);
    }
    if (idx !== -1) {
      // Update draft or comment.
      this.set(['comments', side, idx], comment);
    } else {
      // Create new draft.
      this.push(['comments', side], comment);
    }
  }

  _handleCommentSaveOrDiscard() {
    this.dispatchEvent(
      new CustomEvent('diff-comments-modified', {bubbles: true, composed: true})
    );
  }

  _removeComment(comment: UIComment) {
    const side = comment.__commentSide;
    if (!side) throw new Error('Missing required "side" in comment.');
    this._removeCommentFromSide(comment, side);
  }

  _removeCommentFromSide(comment: Comment, side: Side) {
    let idx = this._findCommentIndex(comment, side);
    if (idx === -1) {
      idx = this._findDraftIndex(comment, side);
    }
    if (idx !== -1) {
      this.splice('comments.' + side, idx, 1);
    }
  }

  _findCommentIndex(comment: Comment, side: Side) {
    if (!comment.id || !this.comments || !this.comments[side]) {
      return -1;
    }
    return this.comments[side].findIndex(item => item.id === comment.id);
  }

  _findDraftIndex(comment: Comment, side: Side) {
    if (
      !isDraft(comment) ||
      !comment.__draftID ||
      !this.comments ||
      !this.comments[side]
    ) {
      return -1;
    }
    return this.comments[side].findIndex(
      item => isDraft(item) && item.__draftID === comment.__draftID
    );
  }

  _isSyntaxHighlightingEnabled(
    preferenceChangeRecord?: PolymerDeepPropertyChange<
      DiffPreferencesInfo,
      DiffPreferencesInfo
    >,
    diff?: DiffInfo
  ) {
    if (
      !preferenceChangeRecord ||
      !preferenceChangeRecord.base ||
      !preferenceChangeRecord.base.syntax_highlighting ||
      !diff
    ) {
      return false;
    }
    return (
      !this._anyLineTooLong(diff) &&
      this.$.diff.getDiffLength(diff) <= SYNTAX_MAX_DIFF_LENGTH
    );
  }

  /**
   * @return whether any of the lines in diff are longer
   * than SYNTAX_MAX_LINE_LENGTH.
   */
  _anyLineTooLong(diff?: DiffInfo) {
    if (!diff) return false;
    return diff.content.some(section => {
      const lines = section.ab
        ? section.ab
        : (section.a || []).concat(section.b || []);
      return lines.some(line => line.length >= SYNTAX_MAX_LINE_LENGTH);
    });
  }

  _listenToViewportRender() {
    const renderUpdateListener: DiffLayerListener = start => {
      if (start > NUM_OF_LINES_THRESHOLD_FOR_VIEWPORT) {
        this.reporting.diffViewDisplayed();
        this.$.syntaxLayer.removeListener(renderUpdateListener);
      }
    };

    this.$.syntaxLayer.addListener(renderUpdateListener);
  }

  _handleRenderStart() {
    this.reporting.time(TimingLabel.TOTAL);
    this.reporting.time(TimingLabel.CONTENT);
  }

  _handleRenderContent() {
    this.reporting.timeEnd(TimingLabel.CONTENT);
  }

  _handleNormalizeRange(event: CustomEvent) {
    this.reporting.reportInteraction('normalize-range', {
      side: event.detail.side,
      lineNum: event.detail.lineNum,
    });
  }

  _handleDiffContextExpanded(event: CustomEvent) {
    this.reporting.reportInteraction('diff-context-expanded', {
      numLines: event.detail.numLines,
    });
  }

  /**
   * Find the last chunk for the given side.
   *
   * @param leftSide true if checking the base of the diff,
   * false if testing the revision.
   * @return returns the chunk object or null if there was
   * no chunk for that side.
   */
  _lastChunkForSide(diff: DiffInfo | undefined, leftSide: boolean) {
    if (!diff?.content.length) {
      return null;
    }

    let chunkIndex = diff.content.length;
    let chunk;

    // Walk backwards until we find a chunk for the given side.
    do {
      chunkIndex--;
      chunk = diff.content[chunkIndex];
    } while (
      // We haven't reached the beginning.
      chunkIndex >= 0 &&
      // The chunk doesn't have both sides.
      !chunk.ab &&
      // The chunk doesn't have the given side.
      ((leftSide && (!chunk.a || !chunk.a.length)) ||
        (!leftSide && (!chunk.b || !chunk.b.length)))
    );

    // If we reached the beginning of the diff and failed to find a chunk
    // with the given side, return null.
    if (chunkIndex === -1) {
      return null;
    }

    return chunk;
  }

  /**
   * Check whether the specified side of the diff has a trailing newline.
   *
   * @param leftSide true if checking the base of the diff,
   * false if testing the revision.
   * @return Return true if the side has a trailing newline.
   * Return false if it doesn't. Return null if not applicable (for
   * example, if the diff has no content on the specified side).
   */
  _hasTrailingNewlines(diff: DiffInfo | undefined, leftSide: boolean) {
    const chunk = this._lastChunkForSide(diff, leftSide);
    if (!chunk) return null;
    let lines;
    if (chunk.ab) {
      lines = chunk.ab;
    } else {
      lines = leftSide ? chunk.a : chunk.b;
    }
    if (!lines) return null;
    return lines[lines.length - 1] === '';
  }

  _showNewlineWarningLeft(diff?: DiffInfo) {
    return this._hasTrailingNewlines(diff, true) === false;
  }

  _showNewlineWarningRight(diff?: DiffInfo) {
    return this._hasTrailingNewlines(diff, false) === false;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-host': GrDiffHost;
  }
}

// TODO(TS): Be more specific than CustomEvent, which has detail:any.
declare global {
  interface HTMLElementEventMap {
    render: CustomEvent;
    'normalize-range': CustomEvent;
    'diff-context-expanded': CustomEvent;
    'create-comment': CustomEvent;
    'comment-discard': CustomEvent;
    'comment-update': CustomEvent;
    'comment-save': CustomEvent;
    'root-id-changed': CustomEvent;
  }
}
