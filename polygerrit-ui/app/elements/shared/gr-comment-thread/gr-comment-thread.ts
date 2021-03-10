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
import '../../../styles/shared-styles';
import '../gr-storage/gr-storage';
import '../gr-comment/gr-comment';
import '../../diff/gr-diff/gr-diff';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-comment-thread_html';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {
  computeDiffFromContext,
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
import {computed, customElement, observe, property} from '@polymer/decorators';
import {
  CommentRange,
  ConfigInfo,
  NumericChangeId,
  PatchSetNum,
  RepoName,
  UrlEncodedCommentId,
} from '../../../types/common';
import {GrComment} from '../gr-comment/gr-comment';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {GrStorage, StorageLocation} from '../gr-storage/gr-storage';
import {CustomKeyboardEvent} from '../../../types/events';
import {LineNumber, FILE} from '../../diff/gr-diff/gr-diff-line';
import {GrButton} from '../gr-button/gr-button';
import {KnownExperimentId} from '../../../services/flags/flags';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {RenderPreferences} from '../../../api/diff';
import {check, assertIsDefined} from '../../../utils/common-util';
import {waitForEventOnce} from '../../../utils/event-util';
import {GrSyntaxLayer} from '../../diff/gr-syntax-layer/gr-syntax-layer';

const UNRESOLVED_EXPAND_COUNT = 5;
const NEWLINE_PATTERN = /\n/g;

export interface GrCommentThread {
  $: {
    replyBtn: GrButton;
    quoteBtn: GrButton;
  };
}

@customElement('gr-comment-thread')
export class GrCommentThread extends KeyboardShortcutMixin(
  LegacyElementMixin(PolymerElement)
) {
  // KeyboardShortcutMixin Not used in this element rather other elements tests

  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the thread should be discarded.
   *
   * @event thread-discard
   */

  /**
   * Fired when a comment in the thread is permanently modified.
   *
   * @event thread-changed
   */

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

  @property({type: Object})
  keyEventTarget: HTMLElement = document.body;

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

  get keyBindings() {
    return {
      'e shift+e': '_handleEKey',
    };
  }

  reporting = appContext.reportingService;

  flagsService = appContext.flagsService;

  readonly storage = new GrStorage();

  private readonly syntaxLayer = new GrSyntaxLayer();

  private isCommentContextExperimentEnabled = this.flagsService.isEnabled(
    KnownExperimentId.COMMENT_CONTEXT
  );

  readonly restApiService = appContext.restApiService;

  /** @override */
  created() {
    super.created();
    this.addEventListener('comment-update', e =>
      this._handleCommentUpdate(e as CustomEvent)
    );
  }

  /** @override */
  connectedCallback() {
    super.connectedCallback();
    this._getLoggedIn().then(loggedIn => {
      this._showActions = loggedIn;
    });
    this.restApiService.getDiffPreferences().then(prefs => {
      if (!prefs) return;
      this._prefs = {
        ...prefs,
        // override explicitly so that diff doesn't take too much width
        // compared to the context
        line_wrapping: false,
      };
    });
    this._setInitialExpandedState();
  }

  @computed('comments', 'path')
  get _diff() {
    if (this.comments === undefined || this.path === undefined) return;
    if (!this.comments[0]?.context_lines?.length) return;
    waitForEventOnce(this, 'render').then(() => {
      this.syntaxLayer.process();
    });
    const diff = computeDiffFromContext(
      this.comments[0].context_lines,
      this.path,
      this.comments[0].source_content_type
    );
    this.syntaxLayer.init(diff);
    return diff;
  }

  _shouldShowCommentContext(diff?: DiffInfo) {
    return (
      this.isCommentContextExperimentEnabled &&
      this.showCommentContext &&
      !!diff
    );
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
    draft.__editing = true;
    draft.unresolved = unresolved === false ? unresolved : true;
    this.push('comments', draft);
  }

  fireRemoveSelf() {
    this.dispatchEvent(
      new CustomEvent('thread-discard', {
        detail: {rootId: this.rootId},
        bubbles: false,
      })
    );
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

  getHighlightRange() {
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

  _getLayers(diff?: DiffInfo) {
    if (!diff) return [];
    return [this.syntaxLayer];
  }

  _getUrlForViewDiff(comments: UIComment[]) {
    assertIsDefined(this.changeNum, 'changeNum');
    assertIsDefined(this.projectName, 'projectName');
    check(comments.length > 0, 'comment not found');
    return GerritNav.getUrlForComment(
      this.changeNum,
      this.projectName,
      comments[0].id!
    );
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

  _isPatchsetLevelComment(path: string) {
    return path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS;
  }

  _computeShowPortedComment(comment: UIComment) {
    if (this._orderedComments.length === 0) return false;
    return this.showPortedComment && comment.id === this._orderedComments[0].id;
  }

  _computeDisplayPath(path: string) {
    const displayPath = computeDisplayPath(path);
    if (displayPath === SpecialFilePath.PATCHSET_LEVEL_COMMENTS) {
      return 'Patchset';
    }
    return displayPath;
  }

  _computeDisplayLine() {
    if (this.lineNum === FILE) {
      if (this.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS) {
        return '';
      }
      return FILE;
    }
    if (this.lineNum) return `#${this.lineNum}`;
    // If range is set, then lineNum equals the end line of the range.
    if (this.range) return `#${this.range.end_line}`;
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

  _handleEKey(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) {
      return;
    }

    // Don’t preventDefault in this case because it will render the event
    // useless for other handlers (other gr-comment-thread elements).
    if (e.detail.keyboardEvent?.shiftKey) {
      this._expandCollapseComments(true);
    } else {
      if (this.modifierPressed(e)) {
        return;
      }
      this._expandCollapseComments(false);
    }
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
   */
  _setInitialExpandedState() {
    if (this._orderedComments) {
      for (let i = 0; i < this._orderedComments.length; i++) {
        const comment = this._orderedComments[i];
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
      reply.__editing = true;
    }

    this.push('comments', reply);

    if (!isEditing) {
      // Allow the reply to render in the dom-repeat.
      setTimeout(() => {
        const commentEl = this._commentElWithDraftID(reply.__draftID);
        if (commentEl) commentEl.save();
      }, 1);
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
      __draftID: Math.random().toString(36),
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
    const rootComment = comments.base[0];
    if (rootComment.id) return rootComment.id;
    if (isDraft(rootComment)) return rootComment.__draftID;
    throw new Error('Missing id in root comment.');
  }

  _handleCommentDiscard(e: Event) {
    assertIsDefined(this.changeNum, 'changeNum');
    assertIsDefined(this.patchNum, 'patchNum');
    const diffCommentEl = (dom(e) as EventApi).rootTarget as GrComment;
    const comment = diffCommentEl.comment;
    const idx = this._indexOf(comment, this.comments);
    if (idx === -1) {
      throw new Error(
        'Cannot find comment ' + JSON.stringify(diffCommentEl.comment)
      );
    }
    this.splice('comments', idx, 1);
    if (this.comments.length === 0) {
      this.fireRemoveSelf();
    }
    this._handleCommentSavedOrDiscarded();

    // Check to see if there are any other open comments getting edited and
    // set the local storage value to its message value.
    for (const changeComment of this.comments) {
      if (isDraft(changeComment) && changeComment.__editing) {
        const commentLocation: StorageLocation = {
          changeNum: this.changeNum,
          patchNum: this.patchNum,
          path: changeComment.path,
          line: changeComment.line,
        };
        this.storage.setDraftComment(
          commentLocation,
          changeComment.message ?? ''
        );
      }
    }
  }

  _handleCommentSavedOrDiscarded() {
    this.dispatchEvent(
      new CustomEvent('thread-changed', {
        detail: {rootId: this.rootId, path: this.path},
        bubbles: false,
      })
    );
  }

  _handleCommentUpdate(e: CustomEvent) {
    const comment = e.detail.comment;
    const index = this._indexOf(comment, this.comments);
    if (index === -1) {
      // This should never happen: comment belongs to another thread.
      console.warn('Comment update for another comment thread.');
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

  _computeHostClass(unresolved?: boolean) {
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
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-comment-thread': GrCommentThread;
  }
}
