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
import '../../shared/gr-comment-thread/gr-comment-thread';
import '../gr-diff/gr-diff';
import {htmlTemplate} from './gr-diff-host_html';
import {
  GerritNav,
  GeneratedWebLink,
} from '../../core/gr-navigation/gr-navigation';
import {
  anyLineTooLong,
  getLine,
  getSide,
  SYNTAX_MAX_LINE_LENGTH,
} from '../gr-diff/gr-diff-utils';
import {getAppContext} from '../../../services/app-context';
import {
  getParentIndex,
  isAParent,
  isMergeParent,
  isNumber,
} from '../../../utils/patch-set-util';
import {
  CommentThread,
  equalLocation,
  isInBaseOfPatchRange,
  isInRevisionOfPatchRange,
} from '../../../utils/comment-util';
import {customElement, observe, property} from '@polymer/decorators';
import {
  CommitRange,
  CoverageRange,
  DiffLayer,
  DiffLayerListener,
  PatchSetFile,
} from '../../../types/types';
import {
  Base64ImageFile,
  BlameInfo,
  ChangeInfo,
  EditPatchSetNum,
  NumericChangeId,
  ParentPatchSetNum,
  PatchRange,
  PatchSetNum,
  RepoName,
  UrlEncodedCommentId,
} from '../../../types/common';
import {
  DiffInfo,
  DiffPreferencesInfo,
  IgnoreWhitespaceType,
} from '../../../types/diff';
import {CreateCommentEventDetail, GrDiff} from '../gr-diff/gr-diff';
import {GrSyntaxLayer} from '../gr-syntax-layer/gr-syntax-layer';
import {DiffViewMode, Side, CommentSide} from '../../../constants/constants';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {FilesWebLinks} from '../gr-patch-range-select/gr-patch-range-select';
import {LineNumber, FILE} from '../gr-diff/gr-diff-line';
import {GrCommentThread} from '../../shared/gr-comment-thread/gr-comment-thread';
import {KnownExperimentId} from '../../../services/flags/flags';
import {
  firePageError,
  fireAlert,
  fireServerError,
  fireEvent,
  waitForEventOnce,
} from '../../../utils/event-util';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {assertIsDefined} from '../../../utils/common-util';
import {DiffContextExpandedEventDetail} from '../gr-diff-builder/gr-diff-builder';
import {TokenHighlightLayer} from '../gr-diff-builder/token-highlight-layer';
import {Timing} from '../../../constants/reporting';
import {ChangeComments} from '../gr-comment-api/gr-comment-api';
import {Subscription} from 'rxjs';
import {DisplayLine, RenderPreferences} from '../../../api/diff';
import {resolve, DIPolymerElement} from '../../../services/dependency';
import {browserModelToken} from '../../../models/browser/browser-model';
import {commentsModelToken} from '../../../models/comments/comments-model';

const EMPTY_BLAME = 'No blame information for this diff.';

const EVENT_AGAINST_PARENT = 'diff-against-parent';
const EVENT_ZERO_REBASE = 'rebase-percent-zero';
const EVENT_NONZERO_REBASE = 'rebase-percent-nonzero';

// Disable syntax highlighting if the overall diff is too large.
const SYNTAX_MAX_DIFF_LENGTH = 20000;

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

export interface GrDiffHost {
  $: {
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
export class GrDiffHost extends DIPolymerElement {
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

  @property({type: Number})
  changeNum?: NumericChangeId;

  @property({type: Object})
  change?: ChangeInfo;

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
  editWeblinks?: GeneratedWebLink[];

  @property({type: Object, notify: true})
  filesWeblinks: FilesWebLinks | {} = {};

  @property({type: Boolean, reflectToAttribute: true})
  override hidden = false;

  @property({type: Boolean})
  noRenderOnPrefsChange = false;

  @property({type: Object, observer: '_threadsChanged'})
  threads?: CommentThread[];

  @property({type: Boolean})
  lineWrapping = false;

  @property({type: Object})
  lineOfInterest?: DisplayLine;

  @property({type: String})
  viewMode = DiffViewMode.SIDE_BY_SIDE;

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

  @property({type: String})
  _errorMessage: string | null = null;

  @property({type: Object})
  _baseImage: Base64ImageFile | null = null;

  @property({type: Object})
  _revisionImage: Base64ImageFile | null = null;

  @property({type: Object, notify: true, observer: 'diffChanged'})
  diff?: DiffInfo;

  @property({type: Object})
  changeComments?: ChangeComments;

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
    observer: '_syntaxHighlightingEnabledChanged',
  })
  _syntaxHighlightingEnabled?: boolean;

  @property({type: Array})
  _layers: DiffLayer[] = [];

  @property({type: Object})
  _renderPrefs: RenderPreferences = {
    num_lines_rendered_at_once: 128,
  };

  private readonly getBrowserModel = resolve(this, browserModelToken);

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly reporting = getAppContext().reportingService;

  private readonly flags = getAppContext().flagsService;

  private readonly restApiService = getAppContext().restApiService;

  private readonly jsAPI = getAppContext().jsApiService;

  private readonly syntaxLayer = new GrSyntaxLayer();

  private subscriptions: Subscription[] = [];

  constructor() {
    super();
    this.addEventListener(
      // These are named inconsistently for a reason:
      // The create-comment event is fired to indicate that we should
      // create a comment.
      // The comment-* events are just notifying that the comments did already
      // change in some way, and that we should update any models we may want
      // to keep in sync.
      'create-comment',
      e => this._handleCreateThread(e)
    );
    this.addEventListener('render-start', () => this._handleRenderStart());
    this.addEventListener('render-content', () => this._handleRenderContent());
    this.addEventListener('normalize-range', event =>
      this._handleNormalizeRange(event)
    );
    this.addEventListener('diff-context-expanded', event =>
      this._handleDiffContextExpanded(event)
    );
  }

  override ready() {
    super.ready();
    if (this._canReload()) {
      this.reload();
    }
  }

  override connectedCallback() {
    super.connectedCallback();
    this.subscriptions.push(
      this.getBrowserModel().diffViewMode$.subscribe(
        diffView => (this.viewMode = diffView)
      )
    );
    this._getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
    });
    this.subscriptions.push(
      this.getCommentsModel().changeComments$.subscribe(changeComments => {
        this.changeComments = changeComments;
      })
    );
  }

  override disconnectedCallback() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
    this.clear();
    super.disconnectedCallback();
  }

  async initLayers() {
    const preferencesPromise = this.restApiService.getPreferences();
    await getPluginLoader().awaitPluginsLoaded();
    const prefs = await preferencesPromise;
    const enableTokenHighlight = !prefs?.disable_token_highlighting;

    assertIsDefined(this.path, 'path');
    this._layers = this.getLayers(this.path, enableTokenHighlight);
    this._coverageRanges = [];
    // We kick off fetching the data here, but we don't return the promise,
    // so awaiting initLayers() will not wait for coverage data to be
    // completely loaded.
    this._getCoverageData();
  }

  diffChanged(diff?: DiffInfo) {
    this.syntaxLayer.init(diff);
  }

  /**
   * @param shouldReportMetric indicate a new Diff Page. This is a
   * signal to report metrics event that started on location change.
   */
  async reload(shouldReportMetric?: boolean) {
    this.clear();
    assertIsDefined(this.path, 'path');
    assertIsDefined(this.changeNum, 'changeNum');
    this.diff = undefined;
    this._errorMessage = null;
    const whitespaceLevel = this._getIgnoreWhitespace();

    if (shouldReportMetric) {
      // We listen on render viewport only on DiffPage (on paramsChanged)
      this._listenToViewportRender();
    }

    try {
      // We are carefully orchestrating operations that have to wait for another
      // and operations that can be run in parallel. Plugins may provide layers,
      // so we have to wait on plugins being loaded before we can initialize
      // layers and proceed to rendering. OTOH we want to fetch diffs and diff
      // assets in parallel.
      const layerPromise = this.initLayers();
      const diff = await this._getDiff();
      this._loadedWhitespaceLevel = whitespaceLevel;
      this._reportDiff(diff);

      await this._loadDiffAssets(diff);
      // Only now we are awaiting layers (and plugin loading), which was kicked
      // off above.
      await layerPromise;

      // Not waiting for coverage ranges intentionally as
      // plugin loading should not block the content rendering

      this.editWeblinks = this._getEditWeblinks(diff);
      this.filesWeblinks = this._getFilesWeblinks(diff);
      this.diff = diff;
      const event = (await waitForEventOnce(this, 'render')) as CustomEvent;
      if (shouldReportMetric) {
        // We report diffViewContentDisplayed only on reload caused
        // by params changed - expected only on Diff Page.
        this.reporting.diffViewContentDisplayed();
      }
      const needsSyntaxHighlighting = !!event.detail?.contentRendered;
      if (needsSyntaxHighlighting) {
        this.reporting.time(Timing.DIFF_SYNTAX);
        try {
          await this.syntaxLayer.process();
        } finally {
          this.reporting.timeEnd(Timing.DIFF_SYNTAX);
        }
      }
    } catch (e) {
      if (e instanceof Response) {
        this._handleGetDiffError(e);
      } else {
        this.reporting.error(e);
      }
    } finally {
      this.reporting.timeEnd(Timing.DIFF_TOTAL);
    }
  }

  private getLayers(path: string, enableTokenHighlight: boolean): DiffLayer[] {
    const layers = [];
    if (enableTokenHighlight) {
      layers.push(new TokenHighlightLayer(this));
    }
    layers.push(this.syntaxLayer);
    // Get layers from plugins (if any).
    layers.push(...this.jsAPI.getDiffLayers(path));
    return layers;
  }

  clear() {
    if (this.path) this.jsAPI.disposeDiffLayers(this.path);
    this._layers = [];
  }

  _getCoverageData() {
    assertIsDefined(this.changeNum, 'changeNum');
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.path, 'path');
    assertIsDefined(this.patchRange, 'patchRange');
    const changeNum = this.changeNum;
    const change = this.change;
    const path = this.path;
    // Coverage providers do not provide data for EDIT and PARENT patch sets.

    const toNumberOnly = (patchNum: PatchSetNum) =>
      isNumber(patchNum) ? patchNum : undefined;

    const basePatchNum = toNumberOnly(this.patchRange.basePatchNum);
    const patchNum = toNumberOnly(this.patchRange.patchNum);
    this.jsAPI
      .getCoverageAnnotationApis()
      .then(coverageAnnotationApis => {
        coverageAnnotationApis.forEach(coverageAnnotationApi => {
          const provider = coverageAnnotationApi.getCoverageProvider();
          if (!provider) return;
          provider(changeNum, path, basePatchNum, patchNum, change)
            .then(coverageRanges => {
              assertIsDefined(this.patchRange, 'patchRange');
              if (
                !coverageRanges ||
                changeNum !== this.changeNum ||
                change !== this.change ||
                path !== this.path ||
                basePatchNum !== toNumberOnly(this.patchRange.basePatchNum) ||
                patchNum !== toNumberOnly(this.patchRange.patchNum)
              ) {
                return;
              }

              const existingCoverageRanges = this._coverageRanges;
              this._coverageRanges = coverageRanges;

              // Notify with existing coverage ranges in case there is some
              // existing coverage data that needs to be removed
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
            })
            .catch(err => {
              this.reporting.error(err);
            });
        });
      })
      .catch(err => {
        this.reporting.error(err);
      });
  }

  _getEditWeblinks(diff: DiffInfo) {
    if (!this.projectName || !this.commitRange || !this.path) return undefined;
    return GerritNav.getEditWebLinks(
      this.projectName,
      this.commitRange.baseCommit,
      this.path,
      {weblinks: diff?.edit_web_links}
    );
  }

  @observe('changeComments', 'patchRange', 'file')
  computeFileThreads(
    changeComments?: ChangeComments,
    patchRange?: PatchRange,
    file?: PatchSetFile
  ) {
    if (!changeComments || !patchRange || !file) return;
    this.threads = changeComments.getThreadsBySideForFile(file, patchRange);
  }

  _getFilesWeblinks(diff: DiffInfo) {
    if (!this.projectName || !this.commitRange || !this.path) return {};
    return {
      meta_a: GerritNav.getFileWebLinks(
        this.projectName,
        this.commitRange.baseCommit,
        this.path,
        {weblinks: diff?.meta_a?.web_links}
      ),
      meta_b: GerritNav.getFileWebLinks(
        this.projectName,
        this.commitRange.commit,
        this.path,
        {weblinks: diff?.meta_b?.web_links}
      ),
    };
  }

  /** Cancel any remaining diff builder rendering work. */
  cancel() {
    this.$.diff.cancel();
    this.syntaxLayer.cancel();
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
    assertIsDefined(this.changeNum, 'changeNum');
    assertIsDefined(this.patchRange, 'patchRange');
    assertIsDefined(this.path, 'path');
    return this.restApiService
      .getBlame(this.changeNum, this.patchRange.patchNum, this.path, true)
      .then(blame => {
        if (!blame || !blame.length) {
          fireAlert(this, EMPTY_BLAME);
          return Promise.reject(EMPTY_BLAME);
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

  toggleAllContext() {
    this.$.diff.toggleAllContext();
  }

  _getLoggedIn() {
    return this.restApiService.getLoggedIn();
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
      assertIsDefined(this.changeNum, 'changeNum');
      assertIsDefined(this.patchRange, 'patchRange');
      assertIsDefined(this.path, 'path');
      this.restApiService
        .getDiff(
          this.changeNum,
          this.patchRange.basePatchNum,
          this.patchRange.patchNum,
          this.path,
          this._getIgnoreWhitespace(),
          reject
        )
        .then(diff => resolve(diff!)); // reject is called in case of error, so we can't get undefined here
    });
  }

  _handleGetDiffError(response: Response) {
    // Loading the diff may respond with 409 if the file is too large. In this
    // case, use a toast error..
    if (response.status === 409) {
      fireServerError(response);
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

    firePageError(response);
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
    assertIsDefined(this.patchRange, 'patchRange');
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

  _threadsChanged(threads: CommentThread[]) {
    const rootIdToThreadEl = new Map<UrlEncodedCommentId, GrCommentThread>();
    const unsavedThreadEls: GrCommentThread[] = [];
    for (const threadEl of this.getThreadEls()) {
      if (threadEl.rootId) {
        rootIdToThreadEl.set(threadEl.rootId, threadEl);
      } else {
        // Unsaved thread els must have editing:true, just being defensive here.
        if (threadEl.editing) unsavedThreadEls.push(threadEl);
      }
    }
    const dontRemove = new Set<GrCommentThread>();
    for (const thread of threads) {
      // Let's find an existing DOM element matching the thread. Normally this
      // is as simple as matching the rootIds.
      let existingThreadEl =
        thread.rootId && rootIdToThreadEl.get(thread.rootId);
      // But unsaved threads don't have rootIds. The incoming thread might be
      // the saved version of the unsaved thread element. To verify that we
      // check that the thread only has one comment and that their location is
      // identical.
      // TODO(brohlfs): This matching is not perfect. You could quickly create
      // two new threads on the same line/range. Then this code just makes a
      // random guess.
      if (!existingThreadEl && thread.comments?.length === 1) {
        for (const unsavedThreadEl of unsavedThreadEls) {
          if (equalLocation(unsavedThreadEl.thread, thread)) {
            existingThreadEl = unsavedThreadEl;
            break;
          }
        }
      }
      if (existingThreadEl) {
        existingThreadEl.thread = thread;
        dontRemove.add(existingThreadEl);
      } else {
        const threadEl = this._createThreadElement(thread);
        this._attachThreadElement(threadEl);
        dontRemove.add(threadEl);
      }
    }
    // Remove all threads that are no longer existing.
    for (const threadEl of this.getThreadEls()) {
      if (dontRemove.has(threadEl)) continue;
      // The user may have opened a couple of comment boxes for editing. They
      // might be unsaved and thus not be reflected in `threads` yet, so let's
      // keep them open.
      if (threadEl.editing && threadEl.thread?.comments.length === 0) continue;
      threadEl.remove();
    }
    const portedThreadsCount = threads.filter(thread => thread.ported).length;
    const portedThreadsWithoutRange = threads.filter(
      thread => thread.ported && thread.rangeInfoLost
    ).length;
    if (portedThreadsCount > 0) {
      this.reporting.reportInteraction('ported-threads-shown', {
        ported: portedThreadsCount,
        portedThreadsWithoutRange,
      });
    }
  }

  _computeIsBlameLoaded(blame: BlameInfo[] | null) {
    return !!blame;
  }

  _getImages(diff: DiffInfo) {
    assertIsDefined(this.changeNum, 'changeNum');
    assertIsDefined(this.patchRange, 'patchRange');
    return this.restApiService.getImagesForDiff(
      this.changeNum,
      diff,
      this.patchRange
    );
  }

  _handleCreateThread(e: CustomEvent<CreateCommentEventDetail>) {
    if (!this.patchRange) throw Error('patch range not set');

    const {lineNum, side, range} = e.detail;

    // Usually, the comment is stored on the patchset shown on the side the
    // user added the comment on, and the commentSide will be REVISION.
    // However, if the comment is added on the left side of the diff and the
    // version shown there is not a patchset that is part the change, but
    // instead a base (a PARENT or a merge parent commit), the comment is
    // stored on the patchset shown on the right, and commentSide=PARENT
    // indicates that the comment should still be shown on the left side.
    const patchNum =
      side === Side.LEFT && !isAParent(this.patchRange.basePatchNum)
        ? this.patchRange.basePatchNum
        : this.patchRange.patchNum;
    const commentSide =
      side === Side.LEFT && isAParent(this.patchRange.basePatchNum)
        ? CommentSide.PARENT
        : CommentSide.REVISION;
    if (!this.canCommentOnPatchSetNum(patchNum)) return;
    const path =
      this.file?.basePath &&
      side === Side.LEFT &&
      commentSide === CommentSide.REVISION
        ? this.file?.basePath
        : this.path;
    assertIsDefined(path, 'path');

    const newThread: CommentThread = {
      rootId: undefined,
      comments: [],
      patchNum,
      commentSide,
      // TODO: Maybe just compute from patchRange.base on the fly?
      mergeParentNum: this._parentIndex ?? undefined,
      path,
      line: lineNum,
      range,
    };
    const el = this._createThreadElement(newThread);
    this._attachThreadElement(el);
  }

  private canCommentOnPatchSetNum(patchNum: PatchSetNum) {
    if (!this._loggedIn) {
      fireEvent(this, 'show-auth-required');
      return false;
    }
    if (!this.patchRange) {
      fireAlert(this, 'Cannot create comment. patchRange undefined.');
      return false;
    }

    const isEdit = patchNum === EditPatchSetNum;
    const isEditBase =
      patchNum === ParentPatchSetNum &&
      this.patchRange.patchNum === EditPatchSetNum;

    if (isEdit) {
      fireAlert(this, 'You cannot comment on an edit.');
      return false;
    }
    if (isEditBase) {
      fireAlert(this, 'You cannot comment on the base patchset of an edit.');
      return false;
    }
    return true;
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
    assertIsDefined(this.patchRange, 'patchRange');
    const commentProps = {
      patch_set: thread.patchNum,
      side: thread.commentSide,
      parent: thread.mergeParentNum,
    };
    let diffSide: Side;
    if (isInBaseOfPatchRange(commentProps, this.patchRange)) {
      diffSide = Side.LEFT;
    } else if (isInRevisionOfPatchRange(commentProps, this.patchRange)) {
      diffSide = Side.RIGHT;
    } else {
      const propsStr = JSON.stringify(commentProps);
      const rangeStr = JSON.stringify(this.patchRange);
      throw new Error(`comment ${propsStr} not in range ${rangeStr}`);
    }

    const threadEl = document.createElement('gr-comment-thread');
    threadEl.className = 'comment-thread';
    threadEl.rootId = thread.rootId;
    threadEl.thread = thread;
    threadEl.showPatchset = false;
    threadEl.showPortedComment = !!thread.ported;
    // These attributes are the "interface" between comment threads and gr-diff.
    // <gr-comment-thread> does not care about them and is not affected by them.
    threadEl.setAttribute('slot', `${diffSide}-${thread.line || 'LOST'}`);
    threadEl.setAttribute('diff-side', `${diffSide}`);
    threadEl.setAttribute('line-num', `${thread.line || 'LOST'}`);
    if (thread.range) {
      threadEl.setAttribute('range', `${JSON.stringify(thread.range)}`);
    }
    return threadEl;
  }

  _filterThreadElsForLocation(
    threadEls: GrCommentThread[],
    lineInfo: LineInfo,
    side: Side
  ) {
    function matchesLeftLine(threadEl: GrCommentThread) {
      return (
        getSide(threadEl) === Side.LEFT &&
        getLine(threadEl) === lineInfo.beforeNumber
      );
    }
    function matchesRightLine(threadEl: GrCommentThread) {
      return (
        getSide(threadEl) === Side.RIGHT &&
        getLine(threadEl) === lineInfo.afterNumber
      );
    }
    function matchesFileComment(threadEl: GrCommentThread) {
      return getSide(threadEl) === side && getLine(threadEl) === FILE;
    }

    // Select the appropriate matchers for the desired side and line
    const matchers: ((thread: GrCommentThread) => boolean)[] = [];
    if (side === Side.LEFT) {
      matchers.push(matchesLeftLine);
    }
    if (side === Side.RIGHT) {
      matchers.push(matchesRightLine);
    }
    if (lineInfo.afterNumber === FILE || lineInfo.beforeNumber === FILE) {
      matchers.push(matchesFileComment);
    }
    return threadEls.filter(threadEl =>
      matchers.some(matcher => matcher(threadEl))
    );
  }

  _getIgnoreWhitespace(): IgnoreWhitespaceType {
    if (!this.prefs || !this.prefs.ignore_whitespace) {
      return 'IGNORE_NONE';
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

  _syntaxHighlightingEnabledChanged(_syntaxHighlightingEnabled: boolean) {
    this.syntaxLayer.setEnabled(_syntaxHighlightingEnabled);
  }

  _isSyntaxHighlightingEnabled(
    preferenceChangeRecord?: PolymerDeepPropertyChange<
      DiffPreferencesInfo,
      DiffPreferencesInfo
    >,
    diff?: DiffInfo
  ) {
    if (!preferenceChangeRecord?.base?.syntax_highlighting || !diff) {
      return false;
    }
    if (anyLineTooLong(diff)) {
      fireAlert(
        this,
        `Files with line longer than ${SYNTAX_MAX_LINE_LENGTH} characters` +
          '  will not be syntax highlighted.'
      );
      return false;
    }
    if (this.$.diff.getDiffLength(diff) > SYNTAX_MAX_DIFF_LENGTH) {
      fireAlert(
        this,
        `Files with more than ${SYNTAX_MAX_DIFF_LENGTH} lines` +
          '  will not be syntax highlighted.'
      );
      return false;
    }
    return true;
  }

  _listenToViewportRender() {
    const renderUpdateListener: DiffLayerListener = start => {
      if (start > NUM_OF_LINES_THRESHOLD_FOR_VIEWPORT) {
        this.reporting.diffViewDisplayed();
        this.syntaxLayer.removeListener(renderUpdateListener);
      }
    };

    this.syntaxLayer.addListener(renderUpdateListener);
  }

  _handleRenderStart() {
    this.reporting.time(Timing.DIFF_TOTAL);
    this.reporting.time(Timing.DIFF_CONTENT);
  }

  _handleRenderContent() {
    this.reporting.timeEnd(Timing.DIFF_CONTENT);
  }

  _handleNormalizeRange(event: CustomEvent) {
    this.reporting.reportInteraction('normalize-range', {
      side: event.detail.side,
      lineNum: event.detail.lineNum,
    });
  }

  _handleDiffContextExpanded(e: CustomEvent<DiffContextExpandedEventDetail>) {
    this.reporting.reportInteraction('diff-context-expanded', {
      numLines: e.detail.numLines,
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

  _useNewImageDiffUi() {
    return this.flags.isEnabled(KnownExperimentId.NEW_IMAGE_DIFF_UI);
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
    /* prettier-ignore */
    'render': CustomEvent;
    'normalize-range': CustomEvent;
    'diff-context-expanded': CustomEvent<DiffContextExpandedEventDetail>;
    'create-comment': CustomEvent;
    'root-id-changed': CustomEvent;
  }
}
