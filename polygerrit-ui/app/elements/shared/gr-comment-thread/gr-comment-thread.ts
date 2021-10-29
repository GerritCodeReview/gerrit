/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import '../../../styles/gr-a11y-styles';
import '../../../styles/shared-styles';
import '../gr-comment/gr-comment';
import '../../diff/gr-diff/gr-diff';
import '../gr-copy-clipboard/gr-copy-clipboard';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-comment-thread_html';
import {
  computeDiffFromContext,
  computeId,
  DraftInfo,
  isDraft,
  isRobot,
  sortComments,
  UIComment,
  UIDraft,
  UIRobot,
} from '../../../utils/comment-util';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {appContext} from '../../../services/app-context';
import {
  CommentSide,
  createDefaultDiffPrefs,
  Side,
  SpecialFilePath,
} from '../../../constants/constants';
import {computeDisplayPath} from '../../../utils/path-list-util';
import {customElement, observe, property} from '@polymer/decorators';
import {
  AccountDetailInfo,
  CommentRange,
  ConfigInfo,
  NumericChangeId,
  PatchSetNum,
  RepoName,
  UrlEncodedCommentId,
} from '../../../types/common';
import {GrComment} from '../gr-comment/gr-comment';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {FILE, LineNumber} from '../../diff/gr-diff/gr-diff-line';
import {GrButton} from '../gr-button/gr-button';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {DiffLayer, RenderPreferences} from '../../../api/diff';
import {
  assertIsDefined,
  check,
  queryAndAssert,
} from '../../../utils/common-util';
import {fireAlert, waitForEventOnce} from '../../../utils/event-util';
import {GrSyntaxLayer} from '../../diff/gr-syntax-layer/gr-syntax-layer';
import {TokenHighlightLayer} from '../../diff/gr-diff-builder/token-highlight-layer';
import {anyLineTooLong} from '../../diff/gr-diff/gr-diff-utils';
import {getUserName} from '../../../utils/display-name-util';
import {generateAbsoluteUrl} from '../../../utils/url-util';
import {addGlobalShortcut} from '../../../utils/dom-util';

const UNRESOLVED_EXPAND_COUNT = 5;
const NEWLINE_PATTERN = /\n/g;

export interface GrCommentThread {
  $: {
    replyBtn: GrButton;
    quoteBtn: GrButton;
  };
}

@customElement('gr-comment-thread')
export class GrCommentThread extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * gr-comment-thread exposes the following attributes that allow a
   * diff widget like gr-diff to show the thread in the right location:
   *
   * line-num:
   *     1-based line number or 'FILE' if it refers to the entire file.
   *
   * diff-side:
   *     "left" or "right". These indicate which of the two diffed versions
   *     the comment relates to. In the case of unified diff, the left
   *     version is the one whose line number column is further to the left.
   *
   * range:
   *     The range of text that the comment refers to (start_line,
   *     start_character, end_line, end_character), serialized as JSON. If
   *     set, range's end_line will have the same value as line-num. Line
   *     numbers are 1-based, char numbers are 0-based. The start position
   *     (start_line, start_character) is inclusive, and the end position
   *     (end_line, end_character) is exclusive.
   */
  @property({type: Number})
  changeNum?: NumericChangeId;

  @property({type: Array})
  comments: UIComment[] = [];

  @property({type: Object, reflectToAttribute: true})
  range?: CommentRange;

  @property({type: String, reflectToAttribute: true})
  diffSide?: Side;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: String})
  path: string | undefined;

  @property({type: String, observer: '_projectNameChanged'})
  projectName?: RepoName;

  @property({type: Boolean, notify: true, reflectToAttribute: true})
  hasDraft?: boolean;

  @property({type: Boolean})
  isOnParent = false;

  @property({type: Number})
  parentIndex: number | null = null;

  @property({
    type: String,
    notify: true,
    computed: '_computeRootId(comments.*)',
  })
  rootId?: UrlEncodedCommentId;

  @property({type: Boolean, observer: 'handleShouldScrollIntoViewChanged'})
  shouldScrollIntoView = false;

  @property({type: Boolean})
  showFilePath = false;

  @property({type: Object, reflectToAttribute: true})
  lineNum?: LineNumber;

  @property({type: Boolean, notify: true, reflectToAttribute: true})
  unresolved?: boolean;

  @property({type: Boolean})
  _showActions?: boolean;

  @property({type: Object})
  _lastComment?: UIComment;

  @property({type: Array})
  _orderedComments: UIComment[] = [];

  @property({type: Object})
  _projectConfig?: ConfigInfo;

  @property({type: Object})
  _prefs: DiffPreferencesInfo = createDefaultDiffPrefs();

  @property({type: Object})
  _renderPrefs: RenderPreferences = {
    hide_left_side: true,
    disable_context_control_buttons: true,
    show_file_comment_button: false,
    hide_line_length_indicator: true,
  };

  @property({type: Boolean, reflectToAttribute: true})
  isRobotComment = false;

  @property({type: Boolean})
  showFileName = true;

  @property({type: Boolean})
  showPortedComment = false;

  @property({type: Boolean})
  showPatchset = true;

  @property({type: Boolean})
  showCommentContext = false;

  @property({type: Object})
  _selfAccount?: AccountDetailInfo;

  @property({type: Array})
  layers: DiffLayer[] = [];

  @property({type: Object, computed: 'computeDiff(comments, path)'})
  _diff?: DiffInfo;

  /** Called in disconnectedCallback. */
  private cleanups: (() => void)[] = [];

  private readonly reporting = appContext.reportingService;

  private readonly commentsService = appContext.commentsService;

  readonly storage = appContext.storageService;

  private readonly syntaxLayer = new GrSyntaxLayer();

  readonly restApiService = appContext.restApiService;

  private readonly shortcuts = appContext.shortcutsService;

  constructor() {
    super();
    this.addEventListener('comment-update', e =>
      this._handleCommentUpdate(e as CustomEvent)
    );
    appContext.restApiService.getPreferences().then(prefs => {
      this._initLayers(!!prefs?.disable_token_highlighting);
    });
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    for (const cleanup of this.cleanups) cleanup();
    this.cleanups = [];
  }

  override connectedCallback() {
    super.connectedCallback();
    this.cleanups.push(
      addGlobalShortcut({key: 'e'}, e => this.handleExpandShortcut(e))
    );
    this.cleanups.push(
      addGlobalShortcut({key: 'E'}, e => this.handleCollapseShortcut(e))
    );
    this._getLoggedIn().then(loggedIn => {
      this._showActions = loggedIn;
    });
    this.restApiService.getDiffPreferences().then(prefs => {
      if (!prefs) return;
      this._prefs = {
        ...prefs,
        // set line_wrapping to true so that the context can take all the
        // remaining space after comment card has rendered
        line_wrapping: true,
      };
      this.syntaxLayer.setEnabled(!!prefs.syntax_highlighting);
    });
    this.restApiService.getAccount().then(account => {
      this._selfAccount = account;
    });
    this._setInitialExpandedState();
  }

  computeDiff(comments?: UIComment[], path?: string) {
    if (comments === undefined || path === undefined) return undefined;
    if (!comments[0]?.context_lines?.length) return undefined;
    const diff = computeDiffFromContext(
      comments[0].context_lines,
      path,
      comments[0].source_content_type
    );
    // Do we really have to re-compute (and re-render) the diff?
    if (this._diff && JSON.stringify(this._diff) === JSON.stringify(diff)) {
      return this._diff;
    }

    if (!anyLineTooLong(diff)) {
      this.syntaxLayer.init(diff);
      waitForEventOnce(this, 'render').then(() => {
        this.syntaxLayer.process();
      });
    }
    return diff;
  }

  handleShouldScrollIntoViewChanged(shouldScrollIntoView?: boolean) {
    // Wait for comment to be rendered before scrolling to it
    if (shouldScrollIntoView) {
      const resizeObserver = new ResizeObserver(
        (_entries: ResizeObserverEntry[], observer: ResizeObserver) => {
          if (this.offsetHeight > 0) {
            queryAndAssert<HTMLDivElement>(this, '.comment-box').focus();
            this.scrollIntoView();
          }
          observer.unobserve(this);
        }
      );
      resizeObserver.observe(this);
    }
  }

  _shouldShowCommentContext(
    changeNum?: NumericChangeId,
    showCommentContext?: boolean,
    diff?: DiffInfo
  ) {
    return changeNum && showCommentContext && !!diff;
  }

  addOrEditDraft(lineNum?: LineNumber, rangeParam?: CommentRange) {
    const lastComment = this.comments[this.comments.length - 1] || {};
    if (isDraft(lastComment)) {
      const commentEl = this._commentElWithDraftID(
        lastComment.id || lastComment.__draftID
      );
      if (!commentEl) throw new Error('Failed to find draft.');
      commentEl.editing = true;

      // If the comment was collapsed, re-open it to make it clear which
      // actions are available.
      commentEl.collapsed = false;
    } else {
      const range = rangeParam
        ? rangeParam
        : lastComment
        ? lastComment.range
        : undefined;
      const unresolved = lastComment ? lastComment.unresolved : undefined;
      this.addDraft(lineNum, range, unresolved);
    }
  }

  addDraft(lineNum?: LineNumber, range?: CommentRange, unresolved?: boolean) {
    const draft = this._newDraft(lineNum, range);
    draft.unresolved = unresolved === false ? unresolved : true;
    this.commentsService.addDraft(draft);
  }

  _getDiffUrlForPath(
    projectName?: RepoName,
    changeNum?: NumericChangeId,
    path?: string,
    patchNum?: PatchSetNum
  ) {
    if (!changeNum || !projectName || !path) return undefined;
    if (isDraft(this.comments[0])) {
      return GerritNav.getUrlForDiffById(
        changeNum,
        projectName,
        path,
        patchNum
      );
    }
    const id = this.comments[0].id;
    if (!id) throw new Error('A published comment is missing the id.');
    return GerritNav.getUrlForComment(changeNum, projectName, id);
  }

  /** The parameter is for triggering re-computation only. */
  getHighlightRange(_: unknown) {
    const comment = this.comments?.[0];
    if (!comment) return undefined;
    if (comment.range) return comment.range;
    if (comment.line) {
      return {
        start_line: comment.line,
        start_character: 0,
        end_line: comment.line,
        end_character: 0,
      };
    }
    return undefined;
  }

  _initLayers(disableTokenHighlighting: boolean) {
    if (!disableTokenHighlighting) {
      this.layers.push(new TokenHighlightLayer(this));
    }
    this.layers.push(this.syntaxLayer);
  }

  _getUrlForViewDiff(
    comments: UIComment[],
    changeNum?: NumericChangeId,
    projectName?: RepoName
  ): string {
    if (!changeNum) return '';
    if (!projectName) return '';
    check(comments.length > 0, 'comment not found');
    return GerritNav.getUrlForComment(changeNum, projectName, comments[0].id!);
  }

  _getDiffUrlForComment(
    projectName?: RepoName,
    changeNum?: NumericChangeId,
    path?: string,
    patchNum?: PatchSetNum
  ) {
    if (!projectName || !changeNum || !path) return undefined;
    if (
      (this.comments.length && this.comments[0].side === 'PARENT') ||
      isDraft(this.comments[0])
    ) {
      if (this.lineNum === 'LOST') throw new Error('invalid lineNum lost');
      return GerritNav.getUrlForDiffById(
        changeNum,
        projectName,
        path,
        patchNum,
        undefined,
        this.lineNum === FILE ? undefined : this.lineNum
      );
    }
    const id = this.comments[0].id;
    if (!id) throw new Error('A published comment is missing the id.');
    return GerritNav.getUrlForComment(changeNum, projectName, id);
  }

  handleCopyLink() {
    assertIsDefined(this.changeNum, 'changeNum');
    assertIsDefined(this.projectName, 'projectName');
    const url = generateAbsoluteUrl(
      GerritNav.getUrlForCommentsTab(
        this.changeNum,
        this.projectName,
        this.comments[0].id!
      )
    );
    navigator.clipboard.writeText(url).then(() => {
      fireAlert(this, 'Link copied to clipboard');
    });
  }

  _isPatchsetLevelComment(path?: string) {
    return path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS;
  }

  _computeShowPortedComment(comment: UIComment) {
    if (this._orderedComments.length === 0) return false;
    return this.showPortedComment && comment.id === this._orderedComments[0].id;
  }

  _computeDisplayPath(path?: string) {
    const displayPath = computeDisplayPath(path);
    if (displayPath === SpecialFilePath.PATCHSET_LEVEL_COMMENTS) {
      return 'Patchset';
    }
    return displayPath;
  }

  _computeDisplayLine(lineNum?: LineNumber, range?: CommentRange) {
    if (lineNum === FILE) {
      if (this.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS) {
        return '';
      }
      return FILE;
    }
    if (lineNum) return `#${lineNum}`;
    // If range is set, then lineNum equals the end line of the range.
    if (range) return `#${range.end_line}`;
    return '';
  }

  _getLoggedIn() {
    return this.restApiService.getLoggedIn();
  }

  _getUnresolvedLabel(unresolved?: boolean) {
    return unresolved ? 'Unresolved' : 'Resolved';
  }

  @observe('comments.*')
  _commentsChanged() {
    this._orderedComments = sortComments(this.comments);
    this.updateThreadProperties();
  }

  updateThreadProperties() {
    if (this._orderedComments.length) {
      this._lastComment = this._getLastComment();
      this.unresolved = this._lastComment.unresolved;
      this.hasDraft = isDraft(this._lastComment);
      this.isRobotComment = isRobot(this._lastComment);
    }
  }

  _shouldDisableAction(_showActions?: boolean, _lastComment?: UIComment) {
    return !_showActions || !_lastComment || isDraft(_lastComment);
  }

  _hideActions(_showActions?: boolean, _lastComment?: UIComment) {
    return (
      this._shouldDisableAction(_showActions, _lastComment) ||
      isRobot(_lastComment)
    );
  }

  _getLastComment() {
    return this._orderedComments[this._orderedComments.length - 1] || {};
  }

  private handleExpandShortcut(e: KeyboardEvent) {
    if (this.shortcuts.shouldSuppress(e)) return;
    this._expandCollapseComments(false);
  }

  private handleCollapseShortcut(e: KeyboardEvent) {
    if (this.shortcuts.shouldSuppress(e)) return;
    this._expandCollapseComments(true);
  }

  _expandCollapseComments(actionIsCollapse: boolean) {
    const comments = this.root?.querySelectorAll('gr-comment');
    if (!comments) return;
    for (const comment of comments) {
      comment.collapsed = actionIsCollapse;
    }
  }

  /**
   * Sets the initial state of the comment thread.
   * Expands the thread if one of the following is true:
   * - last {UNRESOLVED_EXPAND_COUNT} comments expanded by default if the
   * thread is unresolved,
   * - it's a robot comment.
   * - it's a draft
   */
  _setInitialExpandedState() {
    if (this._orderedComments) {
      for (let i = 0; i < this._orderedComments.length; i++) {
        const comment = this._orderedComments[i];
        if (isDraft(comment)) {
          comment.collapsed = false;
          continue;
        }
        const isRobotComment = !!(comment as UIRobot).robot_id;
        // False if it's an unresolved comment under UNRESOLVED_EXPAND_COUNT.
        const resolvedThread =
          !this.unresolved ||
          this._orderedComments.length - i - 1 >= UNRESOLVED_EXPAND_COUNT;
        if (comment.collapsed === undefined) {
          comment.collapsed = !isRobotComment && resolvedThread;
        }
      }
    }
  }

  _createReplyComment(
    content?: string,
    isEditing?: boolean,
    unresolved?: boolean
  ) {
    this.reporting.recordDraftInteraction();
    const id = this._orderedComments[this._orderedComments.length - 1].id;
    if (!id) throw new Error('Cannot reply to comment without id.');
    const reply = this._newReply(id, content, unresolved);

    if (isEditing) {
      this.commentsService.addDraft(reply);
    } else {
      assertIsDefined(this.changeNum, 'changeNum');
      assertIsDefined(this.patchNum, 'patchNum');
      this.restApiService
        .saveDiffDraft(this.changeNum, this.patchNum, reply)
        .then(result => {
          if (!result.ok) {
            fireAlert(document, 'Unable to restore draft');
            return;
          }
          this.restApiService.getResponseObject(result).then(obj => {
            const resComment = obj as unknown as DraftInfo;
            resComment.patch_set = reply.patch_set;
            this.commentsService.addDraft(resComment);
          });
        });
    }
  }

  _isDraft(comment: UIComment) {
    return isDraft(comment);
  }

  _processCommentReply(quote?: boolean) {
    const comment = this._lastComment;
    if (!comment) throw new Error('Failed to find last comment.');
    let content = undefined;
    if (quote) {
      const msg = comment.message;
      if (!msg) throw new Error('Quoting empty comment.');
      content = '> ' + msg.replace(NEWLINE_PATTERN, '\n> ') + '\n\n';
    }
    this._createReplyComment(content, true, comment.unresolved);
  }

  _handleCommentReply() {
    this._processCommentReply();
  }

  _handleCommentQuote() {
    this._processCommentReply(true);
  }

  _handleCommentAck() {
    this._createReplyComment('Ack', false, false);
  }

  _handleCommentDone() {
    this._createReplyComment('Done', false, false);
  }

  _handleCommentFix(e: CustomEvent) {
    const comment = e.detail.comment;
    const msg = comment.message;
    const quoted = msg.replace(NEWLINE_PATTERN, '\n> ') as string;
    const quoteStr = '> ' + quoted + '\n\n';
    const response = quoteStr + 'Please fix.';
    this._createReplyComment(response, false, true);
  }

  _commentElWithDraftID(id?: string): GrComment | null {
    if (!id) return null;
    const els = this.root?.querySelectorAll('gr-comment');
    if (!els) return null;
    for (const el of els) {
      const c = el.comment;
      if (isRobot(c)) continue;
      if (c?.id === id || (isDraft(c) && c?.__draftID === id)) return el;
    }
    return null;
  }

  _newReply(
    inReplyTo: UrlEncodedCommentId,
    message?: string,
    unresolved?: boolean
  ) {
    const d = this._newDraft();
    d.in_reply_to = inReplyTo;
    if (message !== undefined) {
      d.message = message;
    }
    if (unresolved !== undefined) {
      d.unresolved = unresolved;
    }
    return d;
  }

  _newDraft(lineNum?: LineNumber, range?: CommentRange) {
    const d: UIDraft = {
      __draft: true,
      __draftID: 'draft__' + Math.random().toString(36),
      __date: new Date(),
    };
    if (lineNum === 'LOST') throw new Error('invalid lineNum lost');
    // For replies, always use same meta info as root.
    if (this.comments && this.comments.length >= 1) {
      const rootComment = this.comments[0];
      if (rootComment.path !== undefined) d.path = rootComment.path;
      if (rootComment.patch_set !== undefined)
        d.patch_set = rootComment.patch_set;
      if (rootComment.side !== undefined) d.side = rootComment.side;
      if (rootComment.line !== undefined) d.line = rootComment.line;
      if (rootComment.range !== undefined) d.range = rootComment.range;
      if (rootComment.parent !== undefined) d.parent = rootComment.parent;
    } else {
      // Set meta info for root comment.
      d.path = this.path;
      d.patch_set = this.patchNum;
      d.side = this._getSide(this.isOnParent);

      if (lineNum && lineNum !== FILE) {
        d.line = lineNum;
      }
      if (range) {
        d.range = range;
      }
      if (this.parentIndex) {
        d.parent = this.parentIndex;
      }
    }
    return d;
  }

  _getSide(isOnParent: boolean): CommentSide {
    return isOnParent ? CommentSide.PARENT : CommentSide.REVISION;
  }

  _computeRootId(comments: PolymerDeepPropertyChange<UIComment[], unknown>) {
    // Keep the root ID even if the comment was removed, so that notification
    // to sync will know which thread to remove.
    if (!comments.base.length) {
      return this.rootId;
    }
    return computeId(comments.base[0]);
  }

  _handleCommentUpdate(e: CustomEvent) {
    const comment = e.detail.comment;
    const index = this._indexOf(comment, this.comments);
    if (index === -1) {
      // This should never happen: comment belongs to another thread.
      this.reporting.error(
        new Error(`Comment update for another comment thread: ${comment}`)
      );
      return;
    }
    this.set(['comments', index], comment);
    // Because of the way we pass these comment objects around by-ref, in
    // combination with the fact that Polymer does dirty checking in
    // observers, the this.set() call above will not cause a thread update in
    // some situations.
    this.updateThreadProperties();
  }

  _indexOf(comment: UIComment | undefined, arr: UIComment[]) {
    if (!comment) return -1;
    for (let i = 0; i < arr.length; i++) {
      const c = arr[i];
      if (
        (isDraft(c) && isDraft(comment) && c.__draftID === comment.__draftID) ||
        (c.id && c.id === comment.id)
      ) {
        return i;
      }
    }
    return -1;
  }

  /** 2nd parameter is for triggering re-computation only. */
  _computeHostClass(unresolved?: boolean, _?: unknown) {
    if (this.isRobotComment) {
      return 'robotComment';
    }
    return unresolved ? 'unresolved' : '';
  }

  /**
   * Load the project config when a project name has been provided.
   *
   * @param name The project name.
   */
  _projectNameChanged(name?: RepoName) {
    if (!name) {
      return;
    }
    this.restApiService.getProjectConfig(name).then(config => {
      this._projectConfig = config;
    });
  }

  _computeAriaHeading(_orderedComments: UIComment[]) {
    const firstComment = _orderedComments[0];
    const author = firstComment?.author ?? this._selfAccount;
    const lastComment = _orderedComments[_orderedComments.length - 1] || {};
    const status = [
      lastComment.unresolved ? 'Unresolved' : '',
      isDraft(lastComment) ? 'Draft' : '',
    ].join(' ');
    return `${status} Comment thread by ${getUserName(undefined, author)}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-comment-thread': GrCommentThread;
  }
}
