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
  isDraft,
  isRobot,
  Comment,
  CommentThread,
  getLastComment,
  UnsavedInfo,
  isDraftOrUnsaved,
  createUnsavedComment,
  getFirstComment,
} from '../../../utils/comment-util';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  createDefaultDiffPrefs,
  SpecialFilePath,
} from '../../../constants/constants';
import {computeDisplayPath} from '../../../utils/path-list-util';
import {
  AccountDetailInfo,
  CommentRange,
  NumericChangeId,
  RepoName,
  UrlEncodedCommentId,
} from '../../../types/common';
import {GrComment} from '../gr-comment/gr-comment';
import {FILE} from '../../diff/gr-diff/gr-diff-line';
import {GrButton} from '../gr-button/gr-button';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {DiffLayer, RenderPreferences} from '../../../api/diff';
import {assertIsDefined} from '../../../utils/common-util';
import {fire, fireAlert, waitForEventOnce} from '../../../utils/event-util';
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
  disableTokenHighlighting$,
  syntaxHighlightingEnabled$,
} from '../../../services/user/user-model';
import {repeat} from 'lit/directives/repeat';
import {classMap} from 'lit/directives/class-map';
import {changeNum$, repo$} from '../../../services/change/change-model';
import {ShortcutController} from '../../lit/shortcut-controller';
import {ValueChangedEvent} from '../../../types/events';
import {appContext} from '../../../services/app-context';

const NEWLINE_PATTERN = /\n/g;

declare global {
  interface HTMLElementEventMap {
    'comment-thread-editing-changed': ValueChangedEvent<boolean>;
  }
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
@customElement('gr-comment-thread')
export class GrCommentThread extends LitElement {
  @query('#replyBtn')
  replyBtn?: GrButton;

  @query('#quoteBtn')
  quoteBtn?: GrButton;

  @query('.comment-box')
  commentBox?: HTMLElement;

  @queryAll('gr-comment')
  commentElements?: NodeList;

  /** Required to be set by parent. */
  @property()
  thread?: CommentThread;

  /**
   * Id of the first comment and thus must not change. Will be derived from
   * the `thread` property in the first willUpdate() cycle.
   *
   * The `rootId` property is also used in gr-diff for maintaining lists and
   * maps of threads and their associated elements.
   *
   * Only stays `undefined` for new threads that only have an unsaved comment.
   */
  @property({type: String})
  rootId?: UrlEncodedCommentId;

  // TODO: Is this attribute needed for querySelector() or css rules?
  // We don't need this internally for the component.
  @property({type: Boolean, reflect: true, attribute: 'has-draft'})
  hasDraft?: boolean;

  /** Will be inspected on firstUpdated() only. */
  @property({type: Boolean, attribute: 'should-scroll-into-view'})
  shouldScrollIntoView = false;

  /**
   * Should the file path and line number be rendered above the comment thread
   * widget? Typically true in <gr-thread-list> and false in <gr-diff>.
   */
  @property({type: Boolean, attribute: 'show-file-path'})
  showFilePath = false;

  /**
   * Only relevant when `showFilePath` is set.
   * If false, then only the line number is rendered.
   */
  @property({type: Boolean, attribute: 'show-file-name'})
  showFileName = false;

  @property({type: Boolean, attribute: 'show-ported-comment'})
  showPortedComment = false;

  /** This is set to false by <gr-diff>. */
  @property({type: Boolean, attribute: false})
  showPatchset = true;

  @property({type: Boolean, attribute: 'show-comment-context'})
  showCommentContext = false;

  /**
   * We are reflecting the editing state of the draft comment here. This is not
   * an input property, but can be inspected from the parent component.
   *
   * Changes to this property are fired as 'comment-thread-editing-changed'
   * events.
   */
  @property({type: Boolean, attribute: 'false'})
  editing = false;

  /**
   * If set, then `thread.comments` may not contain a draft.
   */
  @state()
  unsavedComment?: UnsavedInfo;

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

  /**
   * Reflects the *dirty* state of whether the thread is currently unresolved.
   * We are listening on the <gr-comment> of the draft, so we even know when the
   * checkbox is checked, even if not yet saved.
   */
  @state()
  unresolved = true;

  /**
   * Normally drafts are saved within the <gr-comment> child component and we
   * don't care about that. But when creating 'Done.' replies we are actually
   * saving from this component. True while the REST API call is inflight.
   */
  @state()
  saving = false;

  private readonly commentsService = appContext.commentsService;

  private readonly shortcuts = new ShortcutController(this);

  private readonly syntaxLayer = new GrSyntaxLayer();

  constructor() {
    super();
    console.log('gr-comment-thread.constrcutor');
    subscribe(this, changeNum$, x => (this.changeNum = x));
    subscribe(this, account$, x => (this.account = x));
    subscribe(this, repo$, x => (this.repoName = x));
    subscribe(this, syntaxHighlightingEnabled$, x =>
      this.syntaxLayer.setEnabled(x)
    );
    subscribe(this, disableTokenHighlighting$, disable => {
      const layers: DiffLayer[] = [this.syntaxLayer];
      if (!disable) {
        layers.push(new TokenHighlightLayer(this));
      }
      this.layers = layers;
    });
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
        .comment-box.saving #actionsContainer {
          opacity: 0.5;
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
    if (!this.thread) return;
    console.log(`gr-comment-thread.render ${this.rootId}`);
    const dynamicBoxClasses = {
      robotComment: this.isRobotComment(),
      unresolved: this.unresolved,
      saving: this.saving,
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
    const href = this.getUrlForComment();
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
    assertIsDefined(this.thread, 'thread');
    const robotButtonDisabled = !this.account || this.isDraftOrUnsaved();
    const comments: Comment[] = [...this.thread.comments];
    if (this.unsavedComment && !this.isDraft()) {
      comments.push(this.unsavedComment);
    }
    return repeat(
      comments,
      // We want to reuse <gr-comment> when unsaved changes to draft.
      comment => (isDraftOrUnsaved(comment) ? 'unsaved' : comment.id),
      comment => html`
        <gr-comment
          .comment="${comment}"
          .comments="${this.thread!.comments}"
          .patchNum="${this.thread?.patchNum}"
          ?initially-collapsed="${!this.unresolved}"
          ?robot-button-disabled="${robotButtonDisabled}"
          ?show-patchset="${this.showPatchset}"
          ?show-ported-comment="${this.showPortedComment &&
          comment.id === this.rootId}"
          @create-fix-comment="${this.handleCommentFix}"
          @copy-comment-link="${this.handleCopyLink}"
          @comment-editing-changed="${(e: CustomEvent) => {
            if (isDraftOrUnsaved(comment)) this.editing = e.detail;
          }}"
          @comment-resolved-changed="${(e: CustomEvent) => {
            if (isDraftOrUnsaved(comment)) this.unresolved = !e.detail;
          }}"
        ></gr-comment>
      `
    );
  }

  renderActions() {
    if (!this.account || this.isDraftOrUnsaved() || this.isRobotComment())
      return;
    return html`
      <div id="actionsContainer">
        <span id="unresolvedLabel">${
          this.unresolved ? 'Unresolved' : 'Resolved'
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
              ?disabled="${this.saving}"
              @click="${() => this.handleCommentReply(false)}"
          >Reply</gr-button
          >
          <gr-button
              id="quoteBtn"
              link
              class="action quote"
              ?disabled="${this.saving}"
              @click="${() => this.handleCommentReply(true)}"
          >Quote</gr-button
          >
          ${
            this.unresolved
              ? html`
                  <gr-button
                    id="ackBtn"
                    link
                    class="action ack"
                    ?disabled="${this.saving}"
                    @click="${this.handleCommentAck}"
                    >Ack</gr-button
                  >
                  <gr-button
                    id="doneBtn"
                    link
                    class="action done"
                    ?disabled="${this.saving}"
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
    if (!this.thread?.path) return;
    const href = this.getUrlForComment();
    return html`
      <div class="diff-container">
        <gr-diff
          id="diff"
          .changeNum="${this.changeNum}"
          .diff="${this.diff}"
          .layers="${this.layers}"
          .path="${this.thread.path}"
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

  private hasBeenUpdatedOnce = false;

  override willUpdate(changed: PropertyValues) {
    console.log(
      `gr-comment-thread.willUpdate ${this.rootId} ${JSON.stringify([
        ...changed.keys(),
      ])}`
    );
    if (changed.has('thread')) {
      if (!this.isDraftOrUnsaved()) {
        // We can only do this for threads without draft, because otherwise we
        // are relying on the <gr-comment> component for the draft to fire
        // events about the *dirty* `unresolved` state.
        this.unresolved = this.getLastComment()?.unresolved ?? true;
      }
      this.hasDraft = this.isDraftOrUnsaved();
      this.rootId = this.getFirstComment()?.id;
      if (this.isDraft()) {
        console.log(`gr-comment-thread wiping unsaved comment ${this.rootId}`);
        this.unsavedComment = undefined;
      }
    }
    if (changed.has('editing')) {
      console.log(`gr-comment-thread.willUpdate.editing ${this.editing}`);
      if (!this.editing && this.unsavedComment) {
        this.remove();
      }
      fire(this, 'comment-thread-editing-changed', {value: this.editing});
    }
    if (!this.hasBeenUpdatedOnce && this.thread) {
      console.log(`gr-comment-thread.willUpdate.firstTime ${this.rootId}`);
      this.hasBeenUpdatedOnce = true;
      if (this.getFirstComment() === undefined) {
        console.log(`gr-comment-thread.willUpdate.unsaved ${this.rootId}`);
        this.unsavedComment = createUnsavedComment(this.thread, '', true);
      }
      this.unresolved = this.getLastComment()?.unresolved ?? true;
      this.diff = this.computeDiff();
      this.highlightRange = this.computeHighlightRange();
    }
  }

  override firstUpdated() {
    if (this.shouldScrollIntoView) {
      this.commentBox?.focus();
      this.scrollIntoView();
    }
  }

  private isDraft() {
    return isDraft(this.getLastComment());
  }

  private isDraftOrUnsaved(): boolean {
    return this.isDraft() || this.isUnsaved();
  }

  private isUnsaved(): boolean {
    return !!this.unsavedComment || this.thread?.comments.length === 0;
  }

  private isPatchsetLevel() {
    return this.thread?.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS;
  }

  private computeDiff() {
    console.log(`gr-comment-thread computeDiff ${this.showCommentContext}
    ${this.thread?.path}`);
    if (!this.showCommentContext) return;
    if (!this.thread?.path) return;
    const firstComment = this.getFirstComment();
    if (!firstComment?.context_lines?.length) return;
    const diff = computeDiffFromContext(
      firstComment.context_lines,
      this.thread?.path,
      firstComment.source_content_type
    );
    console.log(`gr-comment-thread computed diff ${JSON.stringify(diff)}`);
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

  private getDiffUrlForPath() {
    if (!this.changeNum || !this.repoName || !this.thread?.path) {
      return undefined;
    }
    if (this.isUnsaved()) return undefined;
    if (this.isDraft()) {
      return GerritNav.getUrlForDiffById(
        this.changeNum,
        this.repoName,
        this.thread.path,
        this.thread.patchNum
      );
    }
    return this.getUrlForComment();
  }

  private computeHighlightRange() {
    const comment = this.getFirstComment();
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

  private getUrlForComment() {
    if (!this.repoName || !this.changeNum || !this.thread?.path) {
      return undefined;
    }
    if (this.isUnsaved()) return undefined;
    assertIsDefined(this.rootId, 'rootId of comment thread');
    return GerritNav.getUrlForComment(
      this.changeNum,
      this.repoName,
      this.rootId
    );
  }

  private handleCopyLink() {
    const url = this.getUrlForComment();
    assertIsDefined(url, 'url for comment');
    navigator.clipboard.writeText(generateAbsoluteUrl(url)).then(() => {
      fireAlert(this, 'Link copied to clipboard');
    });
  }

  private getDisplayPath() {
    if (this.isPatchsetLevel()) return 'Patchset';
    return computeDisplayPath(this.thread?.path);
  }

  private computeDisplayLine() {
    assertIsDefined(this.thread, 'thread');
    if (this.thread.line === FILE) return this.isPatchsetLevel() ? '' : FILE;
    if (this.thread.line) return `#${this.thread.line}`;
    // If range is set, then lineNum equals the end line of the range.
    if (this.thread.range) return `#${this.thread.range.end_line}`;
    return '';
  }

  private isRobotComment() {
    return isRobot(this.getLastComment());
  }

  private getFirstComment() {
    assertIsDefined(this.thread);
    return getFirstComment(this.thread);
  }

  private getLastComment() {
    assertIsDefined(this.thread);
    return getLastComment(this.thread);
  }

  private handleExpandShortcut() {
    this.expandCollapseComments(false);
  }

  private handleCollapseShortcut() {
    this.expandCollapseComments(true);
  }

  private expandCollapseComments(actionIsCollapse: boolean) {
    for (const comment of this.commentElements ?? []) {
      (comment as GrComment).collapsed = actionIsCollapse;
    }
  }

  private async createReplyComment(
    content: string,
    userWantsToEdit: boolean,
    unresolved: boolean
  ) {
    const id = this.getLastComment()?.id;
    assertIsDefined(this.thread, 'thread');
    assertIsDefined(id, 'id of comment that the user wants to reply to');
    if (isDraft(this.getLastComment())) {
      throw new Error('cannot reply to draft');
    }
    const unsaved = createUnsavedComment(this.thread, content, unresolved, id);
    if (userWantsToEdit) {
      this.unsavedComment = unsaved;
    } else {
      this.saving = true;
      this.commentsService
        .saveDraft(unsaved)
        .finally(() => (this.saving = false));
    }

    // await waitUntil(() => this.getDraftElement() !== undefined);
    // const draftEl = this.getDraftElement();
    // assertIsDefined(draftEl, 'draft gr-comment element');
    // await draftEl.updateComplete;
    // draftEl.save();
  }

  // private getDraftElement(): GrComment | undefined {
  //   if (!this.commentElements) return;
  //   const elements = [...this.commentElements] as GrComment[];
  //   return elements.find(c => isDraft(c.comment));
  // }

  private handleCommentReply(quote: boolean) {
    const comment = this.getLastComment();
    if (!comment) throw new Error('Failed to find last comment.');
    let content = '';
    if (quote) {
      const msg = comment.message;
      if (!msg) throw new Error('Quoting empty comment.');
      content = '> ' + msg.replace(NEWLINE_PATTERN, '\n> ') + '\n\n';
    }
    this.createReplyComment(content, true, comment.unresolved ?? true);
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

  private computeAriaHeading() {
    const author = this.getFirstComment()?.author ?? this.account;
    const status = [
      this.unresolved ? 'Unresolved' : '',
      this.isDraftOrUnsaved() ? 'Draft' : '',
    ].join(' ');
    return `${status} Comment thread by ${getUserName(undefined, author)}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-comment-thread': GrCommentThread;
  }
}
