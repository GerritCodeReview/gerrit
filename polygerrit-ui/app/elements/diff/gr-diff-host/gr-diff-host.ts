/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-comment-thread/gr-comment-thread';
import '../../checks/gr-diff-check-result';
import '../../../embed/diff/gr-diff/gr-diff';
import {
  anyLineTooLong,
  getDiffLength,
  getLine,
  getSide,
  SYNTAX_MAX_LINE_LENGTH,
} from '../../../embed/diff/gr-diff/gr-diff-utils';
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
import {CoverageRange, DiffLayer, PatchSetFile} from '../../../types/types';
import {
  Base64ImageFile,
  BlameInfo,
  ChangeInfo,
  EDIT,
  NumericChangeId,
  PARENT,
  PatchRange,
  PatchSetNum,
  RepoName,
  RevisionPatchSetNum,
  UrlEncodedCommentId,
} from '../../../types/common';
import {
  DiffInfo,
  DiffPreferencesInfo,
  IgnoreWhitespaceType,
} from '../../../types/diff';
import {
  CreateCommentEventDetail,
  GrDiff,
} from '../../../embed/diff/gr-diff/gr-diff';
import {DiffViewMode, Side, CommentSide} from '../../../constants/constants';
import {FilesWebLinks} from '../gr-patch-range-select/gr-patch-range-select';
import {LineNumber, FILE} from '../../../embed/diff/gr-diff/gr-diff-line';
import {GrCommentThread} from '../../shared/gr-comment-thread/gr-comment-thread';
import {KnownExperimentId} from '../../../services/flags/flags';
import {
  firePageError,
  fireAlert,
  fireServerError,
  fireEvent,
  waitForEventOnce,
  fire,
} from '../../../utils/event-util';
import {assertIsDefined} from '../../../utils/common-util';
import {DiffContextExpandedEventDetail} from '../../../embed/diff/gr-diff-builder/gr-diff-builder';
import {TokenHighlightLayer} from '../../../embed/diff/gr-diff-builder/token-highlight-layer';
import {Timing, Interaction} from '../../../constants/reporting';
import {ChangeComments} from '../gr-comment-api/gr-comment-api';
import {Subscription} from 'rxjs';
import {DisplayLine, RenderPreferences} from '../../../api/diff';
import {resolve} from '../../../models/dependency';
import {browserModelToken} from '../../../models/browser/browser-model';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {checksModelToken, RunResult} from '../../../models/checks/checks-model';
import {GrDiffCheckResult} from '../../checks/gr-diff-check-result';
import {distinctUntilChanged, map} from 'rxjs/operators';
import {deepEqual} from '../../../utils/deep-util';
import {Category} from '../../../api/checks';
import {GrSyntaxLayerWorker} from '../../../embed/diff/gr-syntax-layer/gr-syntax-layer-worker';
import {
  CODE_MAX_LINES,
  highlightServiceToken,
} from '../../../services/highlight/highlight-service';
import {html, LitElement, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {ValueChangedEvent} from '../../../types/events';
import {
  debounceP,
  DelayedPromise,
  DELAYED_CANCELLATION,
  noAwait,
} from '../../../utils/async-util';
import {subscribe} from '../../lit/subscription-controller';
import {GeneratedWebLink} from '../../../utils/weblink-util';
import {userModelToken} from '../../../models/user/user-model';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';

const EMPTY_BLAME = 'No blame information for this diff.';

const EVENT_AGAINST_PARENT = 'diff-against-parent';
const EVENT_ZERO_REBASE = 'rebase-percent-zero';
const EVENT_NONZERO_REBASE = 'rebase-percent-nonzero';

function isImageDiff(diff?: DiffInfo) {
  if (!diff) return false;

  const isA = diff.meta_a && diff.meta_a.content_type.startsWith('image/');
  const isB = diff.meta_b && diff.meta_b.content_type.startsWith('image/');

  return !!(diff.binary && (isA || isB));
}

// visible for testing
export interface LineInfo {
  beforeNumber?: LineNumber;
  afterNumber?: LineNumber;
}

declare global {
  interface HTMLElementEventMap {
    /* prettier-ignore */
    'render': CustomEvent;
    'diff-context-expanded': CustomEvent<DiffContextExpandedEventDetail>;
    'create-comment': CustomEvent<CreateCommentEventDetail>;
    'is-blame-loaded-changed': ValueChangedEvent<boolean>;
    'diff-changed': ValueChangedEvent<DiffInfo | undefined>;
    'edit-weblinks-changed': ValueChangedEvent<GeneratedWebLink[] | undefined>;
    'files-weblinks-changed': ValueChangedEvent<FilesWebLinks | undefined>;
    'is-image-diff-changed': ValueChangedEvent<boolean>;
    // Fired when the user selects a line (See gr-diff).
    'line-selected': CustomEvent;
    // Fired if being logged in is required.
    'show-auth-required': void;
  }
}

/**
 * Wrapper around gr-diff.
 *
 * Webcomponent fetching diffs and related data from restAPI and passing them
 * to the presentational gr-diff for rendering. <gr-diff-host> is a Gerrit
 * specific component, while <gr-diff> is a re-usable component.
 */
@customElement('gr-diff-host')
export class GrDiffHost extends LitElement {
  @query('#diff')
  diffElement?: GrDiff;

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

  @state()
  private _isImageDiff = false;

  get isImageDiff() {
    return this._isImageDiff;
  }

  set isImageDiff(isImageDiff: boolean) {
    if (this._isImageDiff === isImageDiff) return;
    this._isImageDiff = isImageDiff;
    fire(this, 'is-image-diff-changed', {value: isImageDiff});
  }

  @state()
  private _editWeblinks?: GeneratedWebLink[];

  get editWeblinks() {
    return this._editWeblinks;
  }

  set editWeblinks(editWeblinks: GeneratedWebLink[] | undefined) {
    if (this._editWeblinks === editWeblinks) return;
    this._editWeblinks = editWeblinks;
    fire(this, 'edit-weblinks-changed', {value: editWeblinks});
  }

  @state()
  private _filesWeblinks?: FilesWebLinks;

  get filesWeblinks() {
    return this._filesWeblinks;
  }

  set filesWeblinks(filesWeblinks: FilesWebLinks | undefined) {
    if (this._filesWeblinks === filesWeblinks) return;
    this._filesWeblinks = filesWeblinks;
    fire(this, 'files-weblinks-changed', {value: filesWeblinks});
  }

  @property({type: Boolean, reflect: true})
  override hidden = false;

  @property({type: Boolean})
  noRenderOnPrefsChange = false;

  // Private but used in tests.
  @state()
  threads: CommentThread[] = [];

  @property({type: Boolean})
  lineWrapping = false;

  @property({type: Object})
  lineOfInterest?: DisplayLine;

  @property({type: String})
  viewMode = DiffViewMode.SIDE_BY_SIDE;

  @property({type: Boolean})
  showLoadFailure?: boolean;

  @state()
  private loggedIn = false;

  // Private but used in tests.
  @state()
  errorMessage: string | null = null;

  @state()
  private baseImage?: Base64ImageFile;

  @state()
  private revisionImage?: Base64ImageFile;

  // Do not use, use diff instead through the getters and setters.
  // This is not a regular @state because we need to also send the
  // 'diff-changed' event when it is changed. And if we rely on @state
  // then the name to look for in willUpdate/update/updated is '_diff'.
  private _diff?: DiffInfo;

  get diff() {
    return this._diff;
  }

  set diff(diff: DiffInfo | undefined) {
    if (this._diff === diff) return;
    const oldDiff = this._diff;
    this._diff = diff;
    this.isImageDiff = isImageDiff(this._diff);
    fire(this, 'diff-changed', {value: this._diff});
    this.requestUpdate('diff', oldDiff);
  }

  @state()
  private changeComments?: ChangeComments;

  @state()
  private fetchDiffPromise: Promise<DiffInfo> | null = null;

  // Do not use, use blame instead through the getters and setters. This is not
  // a regular @state because we need to also send the
  // 'is-blame-loading-changed' event when it is changed. And if we rely on
  // @state then the name to look for in willUpdate/update/updated is '_blame'.
  private _blame: BlameInfo[] | null = null;

  @state()
  get blame() {
    return this._blame;
  }

  set blame(blame: BlameInfo[] | null) {
    if (this._blame === blame) return;
    const oldBlame = this._blame;
    this._blame = blame;
    fire(this, 'is-blame-loaded-changed', {value: !!this._blame});
    this.requestUpdate('blame', oldBlame);
  }

  @state()
  private coverageRanges: CoverageRange[] = [];

  @state()
  private loadedWhitespaceLevel?: IgnoreWhitespaceType;

  @state()
  private layers: DiffLayer[] = [];

  @state()
  private renderPrefs: RenderPreferences = {
    num_lines_rendered_at_once: 128,
  };

  // Debounces across multiple reload calls and ensures that waiters can
  // wait on it whenever a reload is requested.  If more than one reload is
  // requested within a given time-frame, the first one is canceled but will
  // still be resolved when the second one is resolved. (and inductively, any
  // further ones that were requested within a animation-frame).
  private reloadPromise?: DelayedPromise<void>;

  private readonly getBrowserModel = resolve(this, browserModelToken);

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly getChecksModel = resolve(this, checksModelToken);

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

  // visible for testing
  readonly reporting = getAppContext().reportingService;

  private readonly flags = getAppContext().flagsService;

  private readonly restApiService = getAppContext().restApiService;

  // visible for testing
  readonly getUserModel = resolve(this, userModelToken);

  // visible for testing
  readonly syntaxLayer: GrSyntaxLayerWorker;

  private checksSubscription?: Subscription;

  // for DIFF_AUTOCLOSE logging purposes only
  readonly uid = performance.now().toString(36) + Math.random().toString(36);

  constructor() {
    super();
    this.syntaxLayer = new GrSyntaxLayerWorker(
      resolve(this, highlightServiceToken),
      () => getAppContext().reportingService
    );
    this.renderPrefs = {
      ...this.renderPrefs,
      use_lit_components: true,
    };
    this.addEventListener(
      // These are named inconsistently for a reason:
      // The create-comment event is fired to indicate that we should
      // create a comment.
      // The comment-* events are just notifying that the comments did already
      // change in some way, and that we should update any models we may want
      // to keep in sync.
      'create-comment',
      e => this.handleCreateThread(e)
    );
    this.addEventListener('diff-context-expanded', event =>
      this.handleDiffContextExpanded(event)
    );
    subscribe(
      this,
      () => this.getBrowserModel().diffViewMode$,
      diffView => (this.viewMode = diffView)
    );
    subscribe(
      this,
      () => this.getUserModel().loggedIn$,
      loggedIn => (this.loggedIn = loggedIn)
    );
    subscribe(
      this,
      () => this.getCommentsModel().changeComments$,
      changeComments => {
        this.changeComments = changeComments;
      }
    );
    subscribe(
      this,
      () => this.getUserModel().diffPreferences$,
      diffPreferences => {
        this.prefs = diffPreferences;
      }
    );
    this.logForDiffAutoClose();
  }

  // for DIFF_AUTOCLOSE logging purposes only
  private logForDiffAutoClose() {
    this.reporting.reportInteraction(
      Interaction.DIFF_AUTOCLOSE_DIFF_HOST_CREATED,
      {uid: this.uid}
    );
    setTimeout(() => {
      if (!this.hasReloadBeenCalledOnce) {
        this.reporting.reportInteraction(
          Interaction.DIFF_AUTOCLOSE_DIFF_HOST_NOT_RENDERING,
          {uid: this.uid}
        );
      }
    }, /* 10 seconds */ 10000);
  }

  override connectedCallback() {
    super.connectedCallback();
    this.subscribeToChecks();
  }

  override disconnectedCallback() {
    if (this.reloadPromise) {
      this.reloadPromise.cancel();
      this.reloadPromise = undefined;
    }
    if (this.checksSubscription) {
      this.checksSubscription.unsubscribe();
      this.checksSubscription = undefined;
    }
    this.clear();
    super.disconnectedCallback();
  }

  protected override willUpdate(changedProperties: PropertyValues) {
    // Important to call as this will call render, see LitElement.
    super.willUpdate(changedProperties);
    if (changedProperties.has('diff')) {
      this.isImageDiff = isImageDiff(this.diff);
    }
    if (
      changedProperties.has('changeComments') ||
      changedProperties.has('patchRange') ||
      changedProperties.has('file')
    ) {
      this.threads = this.computeFileThreads(
        this.changeComments,
        this.patchRange,
        this.file
      );
    }
    if (
      changedProperties.has('noRenderOnPrefsChange') ||
      changedProperties.has('prefs') ||
      changedProperties.has('path') ||
      changedProperties.has('changeNum')
    ) {
      this.syntaxHighlightingChanged(
        this.noRenderOnPrefsChange,
        changedProperties.get('prefs'),
        this.prefs,
        this.path,
        this.changeNum
      );
    }
    if (
      changedProperties.has('prefs') ||
      changedProperties.has('loadedWhitespaceLevel') ||
      changedProperties.has('noRenderOnPrefsChange') ||
      changedProperties.has('path') ||
      changedProperties.has('changeNum')
    ) {
      this.whitespaceChanged(
        this.prefs?.ignore_whitespace,
        this.loadedWhitespaceLevel,
        this.noRenderOnPrefsChange,
        this.path,
        this.changeNum
      );
    }
  }

  protected override updated(changedProperties: PropertyValues) {
    super.updated(changedProperties);
    // This needs to happen in updated() because it has to happen post-render as
    // this method calls getThreadEls which inspects the DOM. Also <gr-diff>
    // only starts observing nodes (for thread element changes) after rendering
    // is done.
    if (changedProperties.has('threads')) {
      this.threadsChanged(this.threads);
    }
  }

  async waitForReloadToRender(): Promise<void> {
    await this.updateComplete;
    if (this.reloadPromise) {
      try {
        // If we are reloading, wait for the reload to finish and then ensure
        // that any changes are captured in another update.
        await this.reloadPromise;
      } catch (e: unknown) {
        // TODO: Consider moving this logic to a helper method.
        if (e === DELAYED_CANCELLATION) {
          // Do nothing.
        } else if (e instanceof Error) {
          this.reporting.error('GrDiffHost Reload:', e);
        } else {
          this.reporting.error(
            'GrDiffHost Reload:',
            new Error('reloadPromise error'),
            e
          );
        }
      }
      await this.updateComplete;
    }
  }

  override render() {
    const showNewlineWarningLeft =
      this.hasTrailingNewlines(this.diff, true) === false;
    const showNewlineWarningRight =
      this.hasTrailingNewlines(this.diff, false) === false;
    const useNewImageDiffUi = this.flags.isEnabled(
      KnownExperimentId.NEW_IMAGE_DIFF_UI
    );

    return html` <gr-diff
      id="diff"
      ?hidden=${this.hidden}
      .noAutoRender=${this.noAutoRender}
      .path=${this.path}
      .prefs=${this.prefs}
      .displayLine=${this.displayLine}
      .isImageDiff=${this.isImageDiff}
      .noRenderOnPrefsChange=${this.noRenderOnPrefsChange}
      .renderPrefs=${this.renderPrefs}
      .lineWrapping=${this.lineWrapping}
      .viewMode=${this.viewMode}
      .lineOfInterest=${this.lineOfInterest}
      .loggedIn=${this.loggedIn}
      .errorMessage=${this.errorMessage}
      .baseImage=${this.baseImage}
      .revisionImage=${this.revisionImage}
      .coverageRanges=${this.coverageRanges}
      .blame=${this.blame}
      .layers=${this.layers}
      .diff=${this.diff}
      .showNewlineWarningLeft=${showNewlineWarningLeft}
      .showNewlineWarningRight=${showNewlineWarningRight}
      .useNewImageDiffUi=${useNewImageDiffUi}
    ></gr-diff>`;
  }

  async initLayers() {
    const preferencesPromise = this.restApiService.getPreferences();
    const prefs = await preferencesPromise;
    const enableTokenHighlight = !prefs?.disable_token_highlighting;

    assertIsDefined(this.path, 'path');
    this.layers = this.getLayers(enableTokenHighlight);
    this.coverageRanges = [];
    // We kick off fetching the data here, but we don't return the promise,
    // so awaiting initLayers() will not wait for coverage data to be
    // completely loaded.
    noAwait(this.getCoverageData());
  }

  /**
   * @param shouldReportMetric indicate a new Diff Page. This is a
   * signal to report metrics event that started on location change.
   */
  reload(shouldReportMetric?: boolean): Promise<void> {
    this.reloadPromise = debounceP(
      this.reloadPromise,
      async () => {
        try {
          await this.reloadInternal(shouldReportMetric);
          return;
        } catch (e: unknown) {
          if (e instanceof Error) {
            this.reporting.error('GrDiffHost Reload:', e);
          } else {
            this.reporting.error(
              'GrDiffHost Reload:',
              new Error('reloadInternal error'),
              e
            );
          }
        } finally {
          this.reloadPromise = undefined;
        }
      },
      0
    );
    return this.reloadPromise;
  }

  // for DIFF_AUTOCLOSE logging purposes only
  private reloadOngoing = false;

  // for DIFF_AUTOCLOSE logging purposes only
  private hasReloadBeenCalledOnce = false;

  async reloadInternal(shouldReportMetric?: boolean) {
    this.hasReloadBeenCalledOnce = true;
    this.reporting.time(Timing.DIFF_TOTAL);
    this.reporting.time(Timing.DIFF_LOAD);
    // TODO: Find better names for these 3 clear/cancel methods. Ideally the
    // <gr-diff-host> should not re-used at all for another diff rendering pass.
    this.clear();
    this.cancel();
    this.clearDiffContent();
    assertIsDefined(this.path, 'path');
    assertIsDefined(this.changeNum, 'changeNum');
    this.diff = undefined;
    this.errorMessage = null;
    const whitespaceLevel = this.getIgnoreWhitespace();
    if (this.reloadOngoing) {
      this.reporting.reportInteraction(Interaction.DIFF_AUTOCLOSE_DIFF_ONGOING);
    }
    this.reloadOngoing = true;

    try {
      // We are carefully orchestrating operations that have to wait for another
      // and operations that can be run in parallel. Plugins may provide layers,
      // so we have to wait on plugins being loaded before we can initialize
      // layers and proceed to rendering. OTOH we want to fetch diffs and diff
      // assets in parallel.
      const layerPromise = this.initLayers();
      const diff = await this.getDiff();
      if (diff === undefined) {
        this.reporting.reportInteraction(
          Interaction.DIFF_AUTOCLOSE_DIFF_UNDEFINED
        );
      }
      this.loadedWhitespaceLevel = whitespaceLevel;
      this.reportDiff(diff);

      await this.loadDiffAssets(diff);
      // Only now we are awaiting layers (and plugin loading), which was kicked
      // off above.
      await layerPromise;

      // Not waiting for coverage ranges intentionally as
      // plugin loading should not block the content rendering

      this.editWeblinks = this.getEditWeblinks(diff);
      this.filesWeblinks = this.getFilesWeblinks(diff);
      this.diff = diff;
      this.reporting.timeEnd(Timing.DIFF_LOAD, this.timingDetails());

      this.reporting.time(Timing.DIFF_CONTENT);
      this.syntaxLayer.setEnabled(this.isSyntaxHighlightingEnabled());
      const syntaxLayerPromise = this.syntaxLayer.process(diff);
      await waitForEventOnce(this, 'render');
      this.subscribeToChecks();
      this.reporting.timeEnd(Timing.DIFF_CONTENT, this.timingDetails());

      if (shouldReportMetric) {
        // We report diffViewContentDisplayed only on reload caused
        // by params changed - expected only on Diff Page.
        this.reporting.diffViewContentDisplayed();
      }

      this.reporting.time(Timing.DIFF_SYNTAX);
      await syntaxLayerPromise;
      this.reporting.timeEnd(Timing.DIFF_SYNTAX, this.timingDetails());
    } catch (e: unknown) {
      if (e instanceof Response) {
        this.handleGetDiffError(e);
      } else if (e instanceof Error) {
        this.reporting.error('GrDiffHost Reload:', e);
      } else {
        this.reporting.error(
          'GrDiffHost Reload:',
          new Error('reload error'),
          e
        );
      }
    } finally {
      this.reporting.timeEnd(Timing.DIFF_TOTAL, this.timingDetails());
      this.reloadOngoing = false;
    }
  }

  /**
   * Produces an event detail object for reporting.
   */
  private timingDetails() {
    if (!this.diff) return {};
    const metaLines =
      (this.diff.meta_a?.lines ?? 0) + (this.diff.meta_b?.lines ?? 0);

    let contentLines = 0;
    let contentChanged = 0;
    let contentUnchanged = 0;
    for (const chunk of this.diff.content) {
      const ab = chunk.ab?.length ?? 0;
      const a = chunk.a?.length ?? 0;
      const b = chunk.b?.length ?? 0;
      contentLines += ab + ab + a + b;
      contentChanged += a + b;
      contentUnchanged += ab + ab;
    }
    return {
      metaLines,
      contentLines,
      contentUnchanged,
      contentChanged,
      height:
        this.diffElement?.shadowRoot?.querySelector('.diffContainer')
          ?.clientHeight,
    };
  }

  private getLayers(enableTokenHighlight: boolean): DiffLayer[] {
    const layers = [];
    if (enableTokenHighlight) {
      layers.push(new TokenHighlightLayer(this));
    }
    layers.push(this.syntaxLayer);
    return layers;
  }

  clear() {
    this.layers = [];
  }

  /**
   * This should be called when either `path` or `patchRange` has changed.
   * We will then subscribe to the checks model and filter the relevant
   * check results for this diff. Path and patchset must match, and a code
   * pointer must be included.
   */
  private subscribeToChecks() {
    if (this.checksSubscription) {
      this.checksSubscription.unsubscribe();
      this.checksSubscription = undefined;
      this.checksChanged([]);
    }

    const path = this.path;
    const patchNum = this.patchRange?.patchNum;
    if (!path || !patchNum || patchNum === EDIT) return;
    this.checksSubscription = this.getChecksModel()
      .allResults$.pipe(
        map(results =>
          results.filter(result => {
            if (result.patchset !== patchNum) return false;
            if (result.category === Category.SUCCESS) return false;
            // Only one code pointer is supported. See API docs.
            const pointer = result.codePointers?.[0];
            return pointer?.path === this.path && !!pointer?.range;
          })
        ),
        distinctUntilChanged(deepEqual)
      )
      .subscribe(results => this.checksChanged(results));
  }

  /**
   * Similar to threadsChanged(), but a bit simpler. We compare the elements
   * that are already in <gr-diff> with the current results emitted from the
   * model. Exists? Update. New? Create and attach. Old? Remove.
   */
  private checksChanged(checks: RunResult[]) {
    const idToEl = new Map<string, GrDiffCheckResult>();
    const checkEls = this.getCheckEls();
    const dontRemove = new Set<GrDiffCheckResult>();
    let createdCount = 0;
    let updatedCount = 0;
    let removedCount = 0;
    const checksCount = checks.length;
    const checkElsCount = checkEls.length;
    if (checksCount === 0 && checkElsCount === 0) return;
    for (const el of checkEls) {
      const id = el.result?.internalResultId;
      assertIsDefined(id, 'result.internalResultId of gr-diff-check-result');
      idToEl.set(id, el);
    }
    for (const check of checks) {
      const id = check.internalResultId;
      const existingEl = idToEl.get(id);
      if (existingEl) {
        existingEl.result = check;
        dontRemove.add(existingEl);
        updatedCount++;
      } else {
        const newEl = this.createCheckEl(check);
        dontRemove.add(newEl);
        createdCount++;
      }
    }
    // Remove all check els that don't have a matching check anymore.
    for (const el of checkEls) {
      if (dontRemove.has(el)) continue;
      el.remove();
      removedCount++;
    }
    this.reporting.reportInteraction(
      Interaction.COMMENTS_AUTOCLOSE_CHECKS_UPDATED,
      {createdCount, updatedCount, removedCount, checksCount, checkElsCount}
    );
  }

  /**
   * This is very similar to createThreadElement(). It creates a new
   * <gr-diff-check-result> element, sets its props/attributes and adds it to
   * <gr-diff>.
   */
  // Visible for testing
  createCheckEl(check: RunResult) {
    const pointer = check.codePointers?.[0];
    assertIsDefined(pointer, 'code pointer of check result in diff');
    const line: LineNumber =
      pointer.range?.end_line || pointer.range?.start_line || 'FILE';
    const el = document.createElement('gr-diff-check-result');
    // This is what gr-diff expects, even though this is a check, not a comment.
    el.className = 'comment-thread';
    el.rootId = check.internalResultId;
    el.result = check;
    // These attributes are the "interface" between comments/checks and gr-diff.
    // <gr-comment-thread> does not care about them and is not affected by them.
    el.setAttribute('slot', `${Side.RIGHT}-${line}`);
    el.setAttribute('diff-side', `${Side.RIGHT}`);
    el.setAttribute('line-num', `${line}`);
    if (
      pointer.range?.start_line > 0 &&
      pointer.range?.end_line > 0 &&
      pointer.range?.start_character >= 0 &&
      pointer.range?.end_character >= 0
    ) {
      el.setAttribute('range', `${JSON.stringify(pointer.range)}`);
    }
    assertIsDefined(this.diffElement);
    this.diffElement.appendChild(el);
    return el;
  }

  private async getCoverageData() {
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
    // We are simply waiting here for all plugins to be loaded. Ideally we would
    // just react to state changes, but plugins are loaded quickly once at app
    // startup, and coordinating incoming coverage providers with the reloading
    // process seems to be complex enough to avoid it for the time being.
    await this.getPluginLoader().awaitPluginsLoaded();
    const plugins =
      this.getPluginLoader().pluginsModel.getState().coveragePlugins;
    const providers = plugins.map(p => p.provider);
    for (const provider of providers) {
      try {
        const coverageRanges = await provider(
          changeNum,
          path,
          basePatchNum,
          patchNum,
          change
        );
        assertIsDefined(this.patchRange, 'patchRange');
        if (
          !coverageRanges ||
          changeNum !== this.changeNum ||
          change !== this.change ||
          path !== this.path ||
          basePatchNum !== toNumberOnly(this.patchRange.basePatchNum) ||
          patchNum !== toNumberOnly(this.patchRange.patchNum)
        ) {
          continue;
        }
        this.coverageRanges = coverageRanges;
      } catch (e) {
        if (e instanceof Error) this.reporting.error('GrDiffHost Coverage', e);
      }
    }
  }

  private computeFileThreads(
    changeComments?: ChangeComments,
    patchRange?: PatchRange,
    file?: PatchSetFile
  ) {
    if (!changeComments || !patchRange || !file) return this.threads;
    return changeComments.getThreadsBySideForFile(file, patchRange);
  }

  private getEditWeblinks(diff: DiffInfo) {
    return diff?.edit_web_links ?? [];
  }

  private getFilesWeblinks(diff: DiffInfo) {
    return {
      meta_a: diff?.meta_a?.web_links ?? [],
      meta_b: diff?.meta_b?.web_links ?? [],
    };
  }

  /** Cancel any remaining diff builder rendering work. */
  cancel() {
    this.diffElement?.cancel();
  }

  getCursorStops() {
    assertIsDefined(this.diffElement);
    return this.diffElement.getCursorStops();
  }

  isRangeSelected() {
    assertIsDefined(this.diffElement);
    return this.diffElement.isRangeSelected();
  }

  createRangeComment() {
    assertIsDefined(this.diffElement);
    this.diffElement.createRangeComment();
  }

  toggleLeftDiff() {
    assertIsDefined(this.diffElement);
    this.diffElement.toggleLeftDiff();
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

        this.blame = blame;
        return blame;
      });
  }

  clearBlame() {
    this.blame = null;
  }

  getThreadEls(): GrCommentThread[] {
    assertIsDefined(this.diffElement);
    return Array.from(this.diffElement.querySelectorAll('gr-comment-thread'));
  }

  getCheckEls(): GrDiffCheckResult[] {
    return Array.from(
      this.diffElement?.querySelectorAll('gr-diff-check-result') ?? []
    );
  }

  addDraftAtLine(el: Element) {
    assertIsDefined(this.diffElement);
    this.diffElement.addDraftAtLine(el);
  }

  clearDiffContent() {
    this.diffElement?.clearDiffContent();
  }

  toggleAllContext() {
    assertIsDefined(this.diffElement);
    this.diffElement.toggleAllContext();
  }

  // TODO(milutin): Use rest-api with fetchCacheURL instead of this.
  prefetchDiff() {
    if (
      !!this.changeNum &&
      !!this.patchRange &&
      !!this.path &&
      this.fetchDiffPromise === null
    ) {
      this.fetchDiffPromise = this.getDiff();
    }
  }

  // Private but used in tests.
  getDiff(): Promise<DiffInfo> {
    if (this.fetchDiffPromise !== null) {
      const fetchDiffPromise = this.fetchDiffPromise;
      this.fetchDiffPromise = null;
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
          this.getIgnoreWhitespace(),
          reject
        )
        .then(diff => resolve(diff!)); // reject is called in case of error, so we can't get undefined here
    });
  }

  // Private but used in tests.
  handleGetDiffError(response: Response) {
    // Loading the diff may respond with 409 if the file is too large. In this
    // case, use a toast error..
    if (response.status === 409) {
      fireServerError(response);
      return;
    }

    if (this.showLoadFailure) {
      this.errorMessage = [
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
   *
   * Private but used in tests.
   */
  reportDiff(diff?: DiffInfo) {
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
    if (this.patchRange.basePatchNum === PARENT) {
      this.reporting.reportInteraction(EVENT_AGAINST_PARENT);
    } else if (percentRebaseDelta === 0) {
      this.reporting.reportInteraction(EVENT_ZERO_REBASE);
    } else {
      this.reporting.reportInteraction(EVENT_NONZERO_REBASE, {
        percentRebaseDelta,
      });
    }
  }

  private loadDiffAssets(diff?: DiffInfo) {
    if (isImageDiff(diff)) {
      // diff! is justified, because isImageDiff() returns false otherwise
      return this.getImages(diff!).then(images => {
        this.baseImage = images.baseImage ?? undefined;
        this.revisionImage = images.revisionImage ?? undefined;
      });
    } else {
      this.baseImage = undefined;
      this.revisionImage = undefined;
      return Promise.resolve();
    }
  }

  private threadsChanged(threads: CommentThread[]) {
    const rootIdToThreadEl = new Map<UrlEncodedCommentId, GrCommentThread>();
    const unsavedThreadEls: GrCommentThread[] = [];
    const threadEls = this.getThreadEls();
    for (const threadEl of threadEls) {
      if (threadEl.rootId) {
        rootIdToThreadEl.set(threadEl.rootId, threadEl);
      } else {
        // Unsaved thread els must have editing:true, just being defensive here.
        if (threadEl.editing) unsavedThreadEls.push(threadEl);
      }
    }
    const dontRemove = new Set<GrCommentThread>();
    let createdCount = 0;
    let updatedCount = 0;
    let removedCount = 0;
    const threadCount = threads.length;
    const threadElCount = threadEls.length;
    if (threadCount === 0 && threadElCount === 0) return;

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
      // There is a case possible where the rootIds match but the locations
      // are different. Such as when a thread was originally attached on the
      // right side of the diff but now should be attached on the left side of
      // the diff.
      // There is another case possible where the original thread element was
      // associated with a ported thread, hence had the LineNum set to LOST.
      // In this case we cannot reuse the thread element if the same thread
      // now is being attached in it's proper location since the LineNum needs
      // to be updated hence create a new thread element.
      if (
        existingThreadEl &&
        existingThreadEl.getAttribute('diff-side') ===
          this.getDiffSide(thread) &&
        existingThreadEl.thread!.ported === thread.ported
      ) {
        existingThreadEl.thread = thread;
        dontRemove.add(existingThreadEl);
        updatedCount++;
      } else {
        const threadEl = this.createThreadElement(thread);
        this.attachThreadElement(threadEl);
        dontRemove.add(threadEl);
        createdCount++;
      }
    }
    // Remove all threads that are no longer existing.
    for (const threadEl of this.getThreadEls()) {
      if (dontRemove.has(threadEl)) continue;
      // The user may have opened a couple of comment boxes for editing. They
      // might be unsaved and thus not be reflected in `threads` yet, so let's
      // keep them open.
      if (threadEl.editing && threadEl.thread?.comments.length === 0) continue;
      removedCount++;
      threadEl.remove();
    }
    this.reporting.reportInteraction(
      Interaction.COMMENTS_AUTOCLOSE_THREADS_UPDATED,
      {createdCount, updatedCount, removedCount, threadCount, threadElCount}
    );
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

  private getImages(diff: DiffInfo) {
    assertIsDefined(this.changeNum, 'changeNum');
    assertIsDefined(this.patchRange, 'patchRange');
    return this.restApiService.getImagesForDiff(
      this.changeNum,
      diff,
      this.patchRange
    );
  }

  handleCreateThread(e: CustomEvent<CreateCommentEventDetail>) {
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

    const parentIndex = this.computeParentIndex();
    const newThread: CommentThread = {
      rootId: undefined,
      comments: [],
      patchNum: patchNum as RevisionPatchSetNum,
      commentSide,
      // TODO: Maybe just compute from patchRange.base on the fly?
      mergeParentNum: parentIndex ?? undefined,
      path,
      line: lineNum,
      range,
    };
    const el = this.createThreadElement(newThread);
    this.attachThreadElement(el);
  }

  private canCommentOnPatchSetNum(patchNum: PatchSetNum) {
    if (!this.loggedIn) {
      fireEvent(this, 'show-auth-required');
      return false;
    }
    if (!this.patchRange) {
      fireAlert(this, 'Cannot create comment. patchRange undefined.');
      return false;
    }

    const isEdit = patchNum === EDIT;
    const isEditBase = patchNum === PARENT && this.patchRange.patchNum === EDIT;

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

  private attachThreadElement(threadEl: Element) {
    assertIsDefined(this.diffElement);
    this.diffElement.appendChild(threadEl);
  }

  private getDiffSide(thread: CommentThread) {
    let diffSide: Side;
    assertIsDefined(this.patchRange, 'patchRange');
    const commentProps = {
      patch_set: thread.patchNum,
      side: thread.commentSide,
      parent: thread.mergeParentNum,
    };
    if (isInBaseOfPatchRange(commentProps, this.patchRange)) {
      diffSide = Side.LEFT;
    } else if (isInRevisionOfPatchRange(commentProps, this.patchRange)) {
      diffSide = Side.RIGHT;
    } else {
      const propsStr = JSON.stringify(commentProps);
      const rangeStr = JSON.stringify(this.patchRange);
      throw new Error(`comment ${propsStr} not in range ${rangeStr}`);
    }
    return diffSide;
  }

  private createThreadElement(thread: CommentThread) {
    const diffSide = this.getDiffSide(thread);

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

  // Private but used in tests.
  filterThreadElsForLocation(
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

  private getIgnoreWhitespace(): IgnoreWhitespaceType {
    if (!this.prefs || !this.prefs.ignore_whitespace) {
      return 'IGNORE_NONE';
    }
    return this.prefs.ignore_whitespace;
  }

  private whitespaceChanged(
    preferredWhitespaceLevel: IgnoreWhitespaceType | undefined,
    loadedWhitespaceLevel: IgnoreWhitespaceType | undefined,
    noRenderOnPrefsChange: boolean | undefined,
    path: string | undefined,
    changeNum: NumericChangeId | undefined
  ): void | Promise<void> {
    if (preferredWhitespaceLevel === undefined) return;
    if (loadedWhitespaceLevel === undefined) return;
    if (noRenderOnPrefsChange === undefined) return;
    if (path === undefined) return;
    if (changeNum === undefined) return;

    this.fetchDiffPromise = null;
    if (
      preferredWhitespaceLevel !== loadedWhitespaceLevel &&
      !noRenderOnPrefsChange
    ) {
      this.reporting.reportInteraction(
        Interaction.DIFF_AUTOCLOSE_RELOAD_ON_WHITESPACE
      );
      return this.reload();
    }
  }

  private syntaxHighlightingChanged(
    noRenderOnPrefsChange: boolean | undefined,
    oldPrefs: DiffPreferencesInfo | undefined,
    prefs: DiffPreferencesInfo | undefined,
    path: string | undefined,
    changeNum: NumericChangeId | undefined
  ): void | Promise<void> {
    if (noRenderOnPrefsChange === undefined) return;
    if (prefs === undefined) return;
    if (path === undefined) return;
    if (changeNum === undefined) return;
    if (oldPrefs?.syntax_highlighting === prefs.syntax_highlighting) return;

    if (!noRenderOnPrefsChange) {
      this.reporting.reportInteraction(
        Interaction.DIFF_AUTOCLOSE_RELOAD_ON_SYNTAX
      );
      return this.reload();
    }
  }

  private computeParentIndex() {
    if (!this.patchRange) return null;
    return isMergeParent(this.patchRange.basePatchNum)
      ? getParentIndex(this.patchRange.basePatchNum)
      : null;
  }

  private isSyntaxHighlightingEnabled() {
    if (!this.prefs?.syntax_highlighting || !this.diff) {
      return false;
    }
    if (anyLineTooLong(this.diff)) {
      fireAlert(
        this,
        `Files with line longer than ${SYNTAX_MAX_LINE_LENGTH} characters` +
          '  will not be syntax highlighted.'
      );
      return false;
    }
    assertIsDefined(this.diffElement);
    if (getDiffLength(this.diff) > CODE_MAX_LINES) {
      fireAlert(
        this,
        `Files with more than ${CODE_MAX_LINES} lines` +
          '  will not be syntax highlighted.'
      );
      return false;
    }
    return true;
  }

  private handleDiffContextExpanded(
    e: CustomEvent<DiffContextExpandedEventDetail>
  ) {
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
   *
   * Private but used in tests.
   */
  lastChunkForSide(diff: DiffInfo | undefined, leftSide: boolean) {
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
   *
   * Private but used in tests.
   */
  hasTrailingNewlines(diff: DiffInfo | undefined, leftSide: boolean) {
    const chunk = this.lastChunkForSide(diff, leftSide);
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
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-host': GrDiffHost;
  }
}
