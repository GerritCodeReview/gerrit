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
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, query, queryAll, state} from 'lit/decorators';
import {
  computeDiffFromContext,
  computeId,
  isDraft,
  isRobot,
  sortComments,
  Comment,
  DraftInfo,
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
import {
  AccountDetailInfo,
  CommentRange,
  NumericChangeId,
  PatchSetNum,
  RepoName,
  UrlEncodedCommentId,
} from '../../../types/common';
import {GrComment} from '../gr-comment/gr-comment';
import {FILE, LineNumber} from '../../diff/gr-diff/gr-diff-line';
import {GrButton} from '../gr-button/gr-button';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {DiffLayer, RenderPreferences} from '../../../api/diff';
import {assertIsDefined, check} from '../../../utils/common-util';
import {fireAlert, waitForEventOnce} from '../../../utils/event-util';
import {GrSyntaxLayer} from '../../diff/gr-syntax-layer/gr-syntax-layer';
import {TokenHighlightLayer} from '../../diff/gr-diff-builder/token-highlight-layer';
import {anyLineTooLong} from '../../diff/gr-diff/gr-diff-utils';
import {getUserName} from '../../../utils/display-name-util';
import {generateAbsoluteUrl} from '../../../utils/url-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {a11yStyles} from '../../../styles/gr-a11y-styles';
import {subscribe} from '../../lit/subscription-controller';
import {
  account$,
  diffPreferences$,
  syntaxHighlightingEnabled$,
} from '../../../services/user/user-model';
import {repeat} from 'lit/directives/repeat';
import {classMap} from 'lit/directives/class-map';
import {changeNum$, repo$} from '../../../services/change/change-model';
import {ShortcutController} from '../../lit/shortcut-controller';

const NEWLINE_PATTERN = /\n/g;

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
@customElement('gr-comment-thread')
export class GrCommentThread extends LitElement {
  @query('#replyBtn')
  replyBtn?: GrButton;

  @query('#quoteBtn')
  quoteBtn?: GrButton;

  @query('.comment-box')
  commentBox?: HTMLElement;

  @queryAll('gr-comment')
  commentElements?: GrComment[];

  @property({type: Array})
  comments: Comment[] = [];

  /** Ordered version of `comments`. Updated in willUpdate(). */
  @state()
  orderedComments: Comment[] = [];

  /**
   * Id of the first comment and thus must not change. Will be derived from
   * `comments` property in the first willUpdate() cycle.
   *
   * The `rootId` property is also used in gr-diff for maintaining lists and
   * maps of threads and their associated elements.
   *
   * TODO: How does this behave for new threads when the first comment is a
   * draft and then gets saved?
   */
  @property()
  rootId?: UrlEncodedCommentId;

  @property({type: Object, reflect: true})
  range?: CommentRange;

  @property({type: String, reflect: true, attribute: 'diff-side'})
  diffSide?: Side;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: String})
  path: string | undefined;

  @property({type: Boolean})
  isOnParent = false;

  @property({type: Number})
  parentIndex: number | null = null;

  /** Will be inspected on firstUpdated() only. */
  @property({type: Boolean, attribute: 'should-scroll-into-view'})
  shouldScrollIntoView = false;

  @property({type: Boolean, attribute: 'show-file-path'})
  showFilePath = false;

  @property({type: Boolean, attribute: 'show-file-name'})
  showFileName = true;

  @property({type: Boolean, attribute: 'show-ported-comment'})
  showPortedComment = false;

  @property({type: Boolean, attribute: 'show-patchset'})
  showPatchset = true;

  @property({type: Boolean, attribute: 'show-comment-context'})
  showCommentContext = false;

  @property({type: Object, reflect: true, attribute: 'line-num'})
  lineNum?: LineNumber;

  /** This attribute is only used by css filtering rules in <gr-thread-list>. */
  @property({type: Boolean, reflect: true, attribute: 'has-draft'})
  hasDraft?: boolean;

  /** This attribute is only used by css filtering rules in <gr-thread-list>. */
  @property({type: Boolean, reflect: true})
  unresolved?: boolean;

  @state()
  changeNum?: NumericChangeId;

  @state()
  prefs: DiffPreferencesInfo = createDefaultDiffPrefs();

  @state()
  renderPrefs: RenderPreferences = {
    hide_left_side: true,
    disable_context_control_buttons: true,
    show_file_comment_button: false,
    hide_line_length_indicator: true,
  };

  @state()
  repoName?: RepoName;

  @state()
  account?: AccountDetailInfo;

  @state()
  layers: DiffLayer[] = [];

  /** Computed during willUpdate(). */
  @state()
  diff?: DiffInfo;

  /** Computed during willUpdate(). */
  @state()
  highlightRange?: CommentRange;

  private readonly reporting = appContext.reportingService;

  private readonly commentsService = appContext.commentsService;

  private readonly restApiService = appContext.restApiService;

  private readonly shortcuts = new ShortcutController(this);

  private readonly syntaxLayer = new GrSyntaxLayer();

  constructor() {
    super();
    subscribe(this, changeNum$, x => (this.changeNum = x));
    subscribe(this, account$, x => (this.account = x));
    subscribe(this, repo$, x => (this.repoName = x));
    subscribe(this, syntaxHighlightingEnabled$, x =>
      this.syntaxLayer.setEnabled(x)
    );
    subscribe(this, diffPreferences$, prefs => {
      this.prefs = {
        ...prefs,
        // set line_wrapping to true so that the context can take all the
        // remaining space after comment card has rendered
        line_wrapping: true,
      };
    });
    this.shortcuts.addGlobal({key: 'e'}, () => this.handleExpandShortcut());
    this.shortcuts.addGlobal({key: 'E'}, () => this.handleCollapseShortcut());
    this.addEventListener('comment-update', e =>
      this.handleCommentUpdate(e as CustomEvent)
    );
    appContext.restApiService.getPreferences().then(prefs => {
      this.initLayers(!!prefs?.disable_token_highlighting);
    });
  }

  static override get styles() {
    return [
      a11yStyles,
      sharedStyles,
      css`
        :host {
          font-family: var(--font-family);
          font-size: var(--font-size-normal);
          font-weight: var(--font-weight-normal);
          line-height: var(--line-height-normal);
          /* Explicitly set the background color of the diff. We
           * cannot use the diff content type ab because of the skip chunk preceding
           * it, diff processor assumes the chunk of type skip/ab can be collapsed
           * and hides our diff behind context control buttons.
           *  */
          --dark-add-highlight-color: var(--background-color-primary);
        }
        gr-button {
          margin-left: var(--spacing-m);
        }
        gr-comment {
          border-bottom: 1px solid var(--comment-separator-color);
        }
        #actions {
          margin-left: auto;
          padding: var(--spacing-s) var(--spacing-m);
        }
        .comment-box {
          width: 80ch;
          max-width: 100%;
          background-color: var(--comment-background-color);
          color: var(--comment-text-color);
          box-shadow: var(--elevation-level-2);
          border-radius: var(--border-radius);
          flex-shrink: 0;
        }
        #container {
          display: var(--gr-comment-thread-display, flex);
          align-items: flex-start;
          margin: 0 var(--spacing-s) var(--spacing-s);
          white-space: normal;
          /** This is required for firefox to continue the inheritance */
          -webkit-user-select: inherit;
          -moz-user-select: inherit;
          -ms-user-select: inherit;
          user-select: inherit;
        }
        .comment-box.unresolved {
          background-color: var(--unresolved-comment-background-color);
        }
        .comment-box.robotComment {
          background-color: var(--robot-comment-background-color);
        }
        #actionsContainer {
          display: flex;
        }
        #unresolvedLabel {
          font-family: var(--font-family);
          margin: auto 0;
          padding: var(--spacing-m);
        }
        .pathInfo {
          display: flex;
          align-items: baseline;
          justify-content: space-between;
          padding: 0 var(--spacing-s) var(--spacing-s);
        }
        .fileName {
          padding: var(--spacing-m) var(--spacing-s) var(--spacing-m);
        }
        @media only screen and (max-width: 1200px) {
          .diff-container {
            display: none;
          }
        }
        .diff-container {
          margin-left: var(--spacing-l);
          border: 1px solid var(--border-color);
          flex-grow: 1;
          flex-shrink: 1;
          max-width: 1200px;
        }
        .view-diff-button {
          margin: var(--spacing-s) var(--spacing-m);
        }
        .view-diff-container {
          border-top: 1px solid var(--border-color);
          background-color: var(--background-color-primary);
        }

        /* In saved state the "reply" and "quote" buttons are 28px height.
         * top:4px  positions the 20px icon vertically centered.
         * Currently in draft state the "save" and "cancel" buttons are 20px
         * height, so the link icon does not need a top:4px in gr-comment_html.
         */
        .link-icon {
          position: relative;
          top: 4px;
          cursor: pointer;
        }
        .fileName gr-copy-clipboard {
          display: inline-block;
          visibility: hidden;
          vertical-align: top;
          --gr-button-padding: 0px;
        }
        .fileName:focus-within gr-copy-clipboard,
        .fileName:hover gr-copy-clipboard {
          visibility: visible;
        }
      `,
    ];
  }

  override render() {
    if (this.isEmpty()) return;
    if (!this.path || !this.patchNum) return;
    const dynamicBoxClasses = {
      robotComment: this.isRobotComment(),
      unresolved: this.isUnresolved(),
    };
    return html`
      ${this.renderFilePath()}
      <div id="container">
        <h3 class="assistive-tech-only">${this.computeAriaHeading()}</h3>
        <div class="comment-box ${classMap(dynamicBoxClasses)}" tabindex="0">
          ${this.renderComments()} ${this.renderActions()}
        </div>
        ${this.renderContextualDiff()}
      </div>
    `;
  }

  renderFilePath() {
    if (!this.showFilePath) return;
    const href = this.getDiffUrlForComment();
    console.log(`renderFilePath: ${href}`);
    const line = this.computeDisplayLine();
    return html`
      ${this.renderFileName()}
      <div class="pathInfo">
        ${href
          ? html`<a href="${href}">${line}</a>`
          : html`<span>${line}</span>`}
      </div>
    `;
  }

  renderFileName() {
    if (!this.showFileName) return;
    if (this.isPatchsetLevel()) {
      return html`<div class="fileName"><span>Patchset</span></div>`;
    }
    const href = this.getDiffUrlForPath();
    const displayPath = this.getDisplayPath();
    return html`
      <div class="fileName">
        ${href
          ? html`<a href="${href}">${displayPath}</a>`
          : html`<span>${displayPath}</span>`}
        <gr-copy-clipboard hideInput .text="${displayPath}"></gr-copy-clipboard>
      </div>
    `;
  }

  renderComments() {
    if (this.isEmpty()) return;
    const robotButtonDisabled = !this.account || this.isDraft();
    return repeat(
      this.orderedComments,
      comment => comment.id,
      comment => html`
        <gr-comment
          .comment="${comment}"
          .comments="${this.comments}"
          .patchNum="${this.patchNum}"
          ?robot-button-disabled="${robotButtonDisabled}"
          ?show-patchset="${this.showPatchset}"
          ?show-ported-comment="${this.showPortedComment &&
          comment.id === this.rootId}"
          @create-fix-comment="${this.handleCommentFix}"
          @copy-comment-link="${this.handleCopyLink}"
        ></gr-comment>
      `
    );
  }

  renderActions() {
    if (!this.account || this.isDraft() || this.isRobotComment()) return;
    return html`
      <div id="actionsContainer">
        <span id="unresolvedLabel">${
          this.isUnresolved() ? 'Unresolved' : 'Resolved'
        }</span>
        <div id="actions">
          <iron-icon
              class="link-icon"
              @click="${this.handleCopyLink}"
              class="copy"
              title="Copy link to this comment"
              icon="gr-icons:link"
              role="button"
              tabindex="0"
          >
          </iron-icon>
          <gr-button
              id="replyBtn"
              link
              class="action reply"
              @click="${() => this.handleCommentReply(false)}"
          >Reply</gr-button
          >
          <gr-button
              id="quoteBtn"
              link
              class="action quote"
              @click="${() => this.handleCommentReply(true)}"
          >Quote</gr-button
          >
          ${
            this.isUnresolved()
              ? html`
                  <gr-button
                    id="ackBtn"
                    link
                    class="action ack"
                    @click="${this.handleCommentAck}"
                    >Ack</gr-button
                  >
                  <gr-button
                    id="doneBtn"
                    link
                    class="action done"
                    @click="${this.handleCommentDone}"
                    >Done</gr-button
                  >
                `
              : ''
          }
        </div>
      </div>
      </div>
    `;
  }

  renderContextualDiff() {
    if (!this.changeNum || !this.showCommentContext || !this.diff) return;
    const href = this.getUrlForViewDiff();
    return html`
      <div class="diff-container">
        <gr-diff
          id="diff"
          .changeNum="${this.changeNum}"
          .diff="${this.diff}"
          .layers="${this.layers}"
          .path="${this.path}"
          .prefs="${this.prefs}"
          .renderPrefs="${this.renderPrefs}"
          .highlightRange="${this.highlightRange}"
        >
        </gr-diff>
        <div class="view-diff-container">
          <a href="${href}">
            <gr-button link class="view-diff-button">View Diff</gr-button>
          </a>
        </div>
      </div>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    this.hasDraft = this.isDraft();
    if (changedProperties.has('comments') || changedProperties.has('path')) {
      this.diff = this.computeDiff();
    }
    if (changedProperties.has('comments')) {
      this.orderedComments = sortComments(this.comments);
      if (!this.rootId) {
        this.rootId = computeId(this.getFirstComment());
      }
      this.unresolved = this.getLastComment().unresolved;
      this.highlightRange = this.computeHighlightRange();
    }
  }

  override firstUpdated() {
    if (this.shouldScrollIntoView) {
      this.commentBox?.focus();
      this.scrollIntoView();
    }
  }

  private isEmpty() {
    return !this.comments || this.comments.length === 0;
  }

  private isDraft() {
    return isDraft(this.getLastComment());
  }

  private isUnresolved() {
    return this.getLastComment()?.unresolved ?? false;
  }

  private isPatchsetLevel() {
    return this.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS;
  }

  private computeDiff() {
    if (!this.comments || !this.path || !this.showCommentContext) return;
    if (!this.comments[0]?.context_lines?.length) return;
    const diff = computeDiffFromContext(
      this.comments[0].context_lines,
      this.path,
      this.comments[0].source_content_type
    );
    // Do we really have to re-compute (and re-render) the diff?
    if (this.diff && JSON.stringify(this.diff) === JSON.stringify(diff)) {
      return this.diff;
    }

    if (!anyLineTooLong(diff)) {
      this.syntaxLayer.init(diff);
      waitForEventOnce(this, 'render').then(() => {
        this.syntaxLayer.process();
      });
    }
    return diff;
  }

  /** Is being called by the gr-diff-host. */
  addOrEditDraft(lineNum?: LineNumber, rangeParam?: CommentRange) {
    const lastComment = this.getLastComment() || {};
    if (isDraft(lastComment)) {
      const commentEl = this.commentElWithDraftID(
        lastComment.id || lastComment.__draftID
      );
      if (!commentEl) throw new Error('Failed to find draft.');
      commentEl.edit();
    } else {
      const range = rangeParam
        ? rangeParam
        : lastComment
        ? lastComment.range
        : undefined;
      const unresolved = lastComment ? lastComment.unresolved : undefined;
      const draft = this.newDraft(lineNum, range);
      draft.unresolved = unresolved === false ? unresolved : true;
      this.commentsService.addDraft(draft);
    }
  }

  private getDiffUrlForPath() {
    if (!this.changeNum || !this.repoName || !this.path) return undefined;
    if (isDraft(this.comments[0])) {
      return GerritNav.getUrlForDiffById(
        this.changeNum,
        this.repoName,
        this.path,
        this.patchNum
      );
    }
    const id = this.comments[0].id;
    if (!id) throw new Error('A published comment is missing the id.');
    return GerritNav.getUrlForComment(this.changeNum, this.repoName, id);
  }

  private computeHighlightRange() {
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

  private initLayers(disableTokenHighlighting: boolean) {
    if (!disableTokenHighlighting) {
      this.layers.push(new TokenHighlightLayer(this));
    }
    this.layers.push(this.syntaxLayer);
  }

  private getUrlForViewDiff(): string {
    if (!this.changeNum) return '';
    if (!this.repoName) return '';
    check(this.comments.length > 0, 'comment not found');
    return GerritNav.getUrlForComment(
      this.changeNum,
      this.repoName,
      this.comments[0].id!
    );
  }

  private getDiffUrlForComment() {
    console.log(
      `getDiffUrlForComment: ${this.repoName} ${this.changeNum} ${this.path}`
    );
    if (!this.repoName || !this.changeNum || !this.path) return undefined;
    if (
      (this.comments.length && this.comments[0].side === 'PARENT') ||
      isDraft(this.comments[0])
    ) {
      if (this.lineNum === 'LOST') throw new Error('invalid lineNum lost');
      return GerritNav.getUrlForDiffById(
        this.changeNum,
        this.repoName,
        this.path,
        this.patchNum,
        undefined,
        this.lineNum === FILE ? undefined : this.lineNum
      );
    }
    const id = this.comments[0].id;
    if (!id) throw new Error('A published comment is missing the id.');
    return GerritNav.getUrlForComment(this.changeNum, this.repoName, id);
  }

  private handleCopyLink() {
    assertIsDefined(this.changeNum, 'changeNum');
    assertIsDefined(this.repoName, 'repoName');
    const url = generateAbsoluteUrl(
      GerritNav.getUrlForCommentsTab(
        this.changeNum,
        this.repoName,
        this.comments[0].id!
      )
    );
    navigator.clipboard.writeText(url).then(() => {
      fireAlert(this, 'Link copied to clipboard');
    });
  }

  private getDisplayPath() {
    if (this.isPatchsetLevel()) return 'Patchset';
    return computeDisplayPath(this.path);
  }

  private computeDisplayLine() {
    if (this.lineNum === FILE) return this.isPatchsetLevel() ? '' : FILE;
    if (this.lineNum) return `#${this.lineNum}`;
    // If range is set, then lineNum equals the end line of the range.
    if (this.range) return `#${this.range.end_line}`;
    return '';
  }

  private isRobotComment() {
    return isRobot(this.getLastComment());
  }

  private getFirstComment() {
    return this.orderedComments[0];
  }

  private getLastComment() {
    return this.orderedComments[this.orderedComments.length - 1];
  }

  private handleExpandShortcut() {
    this.expandCollapseComments(false);
  }

  private handleCollapseShortcut() {
    this.expandCollapseComments(true);
  }

  private expandCollapseComments(actionIsCollapse: boolean) {
    for (const comment of this.commentElements ?? []) {
      comment.collapsed = actionIsCollapse;
    }
  }

  private createReplyComment(
    content?: string,
    isEditing?: boolean,
    unresolved?: boolean
  ) {
    this.reporting.recordDraftInteraction();
    const id = this.orderedComments[this.orderedComments.length - 1].id;
    if (!id) throw new Error('Cannot reply to comment without id.');
    const reply = this.newReply(id, content, unresolved);

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

  private handleCommentReply(quote: boolean) {
    const comment = this.getLastComment();
    if (!comment) throw new Error('Failed to find last comment.');
    let content = undefined;
    if (quote) {
      const msg = comment.message;
      if (!msg) throw new Error('Quoting empty comment.');
      content = '> ' + msg.replace(NEWLINE_PATTERN, '\n> ') + '\n\n';
    }
    this.createReplyComment(content, true, comment.unresolved);
  }

  private handleCommentAck() {
    this.createReplyComment('Ack', false, false);
  }

  private handleCommentDone() {
    this.createReplyComment('Done', false, false);
  }

  private handleCommentFix(e: CustomEvent) {
    const comment = e.detail.comment;
    const msg = comment.message;
    const quoted = msg.replace(NEWLINE_PATTERN, '\n> ') as string;
    const quoteStr = '> ' + quoted + '\n\n';
    const response = quoteStr + 'Please fix.';
    this.createReplyComment(response, false, true);
  }

  private commentElWithDraftID(id?: string): GrComment | null {
    if (!id) return null;
    for (const el of this.commentElements ?? []) {
      const c = el.comment;
      if (isRobot(c)) continue;
      if (c?.id === id || (isDraft(c) && c?.__draftID === id)) return el;
    }
    return null;
  }

  private newReply(
    inReplyTo: UrlEncodedCommentId,
    message?: string,
    unresolved?: boolean
  ) {
    const d = this.newDraft();
    d.in_reply_to = inReplyTo;
    if (message !== undefined) {
      d.message = message;
    }
    if (unresolved !== undefined) {
      d.unresolved = unresolved;
    }
    return d;
  }

  private newDraft(lineNum?: LineNumber, range?: CommentRange) {
    const randomString = Math.random().toString(36);
    const d: DraftInfo = {
      __draft: true,
      __draftID: `draft__${randomString}` as UrlEncodedCommentId,
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
      d.side = this.isOnParent ? CommentSide.PARENT : CommentSide.REVISION;

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

  private handleCommentUpdate(e: CustomEvent) {
    const comment = e.detail.comment;
    const index = this.indexOf(comment, this.comments);
    if (index === -1) {
      // This should never happen: comment belongs to another thread.
      this.reporting.error(
        new Error(`Comment update for another comment thread: ${comment}`)
      );
      return;
    }
    // TODO: this.set('comments') was being used here. Move into model/service.
    this.requestUpdate();
  }

  private indexOf(comment: Comment | undefined, arr: Comment[]) {
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

  private computeAriaHeading() {
    const author = this.getFirstComment().author ?? this.account;
    const status = [
      this.getLastComment().unresolved ? 'Unresolved' : '',
      this.isDraft() ? 'Draft' : '',
    ].join(' ');
    return `${status} Comment thread by ${getUserName(undefined, author)}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-comment-thread': GrCommentThread;
  }
}
