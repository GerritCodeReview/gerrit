/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-formatted-text/gr-formatted-text';
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {CreateCommentPart} from '../../../models/chat/chat-model';
import {fontStyles} from '../../../styles/gr-font-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {resolve} from '../../../models/dependency';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {changeModelToken} from '../../../models/change/change-model';
import {subscribe} from '../../lit/subscription-controller';
import {PatchSetNumber} from '../../../api/rest-api';
import {computeDisplayLine, createNew} from '../../../utils/comment-util';
import {computeDisplayPath} from '../../../utils/path-list-util';
import {NumericChangeId, RepoName} from '../../../types/common';
import {changeViewModelToken} from '../../../models/views/change';
import {SpecialFilePath} from '../../../constants/constants';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';

@customElement('gr-diff-ai-result')
export class GrDiffAiResult extends LitElement {
  @property({attribute: false})
  result?: CreateCommentPart;

  /**
   * This is required by <gr-diff> as an identifier for this component. It will
   * be set to the commentCreationId of the create comment part.
   */
  @property({type: String})
  rootId?: string;

  @property({type: Boolean, attribute: 'show-file-path'})
  showFilePath = false;

  @property({type: Boolean, attribute: 'show-file-name'})
  showFileName = false;

  @state()
  changeNum?: NumericChangeId;

  @state()
  repoName?: RepoName;

  private latestPatchNum?: PatchSetNumber;

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getViewModel = resolve(this, changeViewModelToken);

  static override get styles() {
    return [
      sharedStyles,
      fontStyles,
      css`
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
        .comment-box {
          width: 80ch;
          max-width: 100%;
          background-color: var(--comment-background-color);
          color: var(--comment-text-color);
          box-shadow: var(--elevation-level-2);
          border-radius: var(--border-radius);
          flex-shrink: 0;
        }
        .comment-box.info {
          border-color: var(--info-foreground);
          background-color: var(--info-background);
        }
        .comment-box.info gr-icon {
          color: var(--info-foreground);
        }
        .comment-box {
          padding: var(--spacing-xs) var(--spacing-m);
          border: 1px solid var(--border-color);
        }
        .header {
          display: flex;
          white-space: nowrap;
        }
        .icon {
          margin-right: var(--spacing-s);
        }
        .name {
          font-weight: var(--font-weight-medium);
          margin-right: var(--spacing-m);
        }
        .details {
          margin-top: var(--spacing-m);
        }
        .message {
          margin-bottom: var(--spacing-m);
        }
        gr-icon {
          font-size: var(--line-height-normal);
        }
        .icon gr-icon {
          font-size: calc(var(--line-height-normal) - 4px);
          position: relative;
          top: 2px;
        }
        div.actions {
          display: flex;
          justify-content: flex-end;
          gap: var(--spacing-m);
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

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChangeModel().latestPatchNum$,
      x => (this.latestPatchNum = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().changeNum$,
      x => (this.changeNum = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().repo$,
      x => (this.repoName = x)
    );
  }

  override render() {
    if (!this.result) return;
    return html`
      ${this.renderFilePath()}
      <div id="container">
        <div class="comment-box info font-normal" tabindex="0">
          <div class="header">
            <div class="icon">
              <gr-icon icon="ai"></gr-icon>
            </div>
            <div class="name">AI Suggestion</div>
          </div>
          <div class="details">
            <div class="message">
              <gr-formatted-text
                .markdown=${true}
                .content=${this.result.comment.message}
              ></gr-formatted-text>
            </div>
            <div class="actions">
              <gr-button
                primary
                class="add-as-comment-button"
                @click=${this.onAddAsComment}
              >
                Add as Comment
              </gr-button>
            </div>
          </div>
        </div>
      </div>
    `;
  }

  renderFilePath() {
    if (!this.showFilePath) return;
    const href = this.getUrlForFileComment();
    const line = this.result?.comment
      ? computeDisplayLine(this.result.comment)
      : '';
    return html`
      ${this.renderFileName()}
      <div class="pathInfo">
        ${href ? html`<a href=${href}>${line}</a>` : html`<span>${line}</span>`}
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
          ? html`<a href=${href}>${displayPath}</a>`
          : html`<span>${displayPath}</span>`}
        <gr-copy-clipboard hideInput .text=${displayPath}></gr-copy-clipboard>
      </div>
    `;
  }

  private isPatchsetLevel() {
    return (
      this.result?.comment?.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS
    );
  }

  private getDiffUrlForPath() {
    if (!this.changeNum || !this.repoName || !this.result?.comment?.path) {
      return undefined;
    }
    const patchNum = this.result?.comment?.patch_set ?? this.latestPatchNum;
    if (!patchNum) return undefined;
    return this.getViewModel().diffUrl({
      patchNum,
      diffView: {path: this.result.comment.path},
    });
  }

  private getDisplayPath() {
    if (this.isPatchsetLevel()) return 'Patchset';
    return computeDisplayPath(this.result?.comment?.path);
  }

  private getUrlForFileComment() {
    if (!this.changeNum || !this.repoName || !this.result?.comment?.path) {
      return undefined;
    }
    const patchNum = this.result?.comment?.patch_set ?? this.latestPatchNum;
    if (!patchNum) return undefined;

    let lineNum: number | undefined = undefined;
    if (this.result?.comment) {
      const lineStr = String(computeDisplayLine(this.result.comment));
      lineNum = lineStr.startsWith('#')
        ? Number(lineStr.substring(1))
        : undefined;
    }

    return this.getViewModel().diffUrl({
      patchNum,
      diffView: {
        path: this.result.comment.path,
        lineNum,
      },
    });
  }

  private async onAddAsComment() {
    if (!this.result) return;
    const draft = {
      ...this.result.comment,
      ...createNew(this.result.comment.message ?? '', true),
    };
    if (!draft.patch_set) {
      draft.patch_set = this.latestPatchNum;
    }
    // TODO(milutin): Remove this once Gemini or backend fixes the issue.
    if (draft.range && draft.range.end_line < draft.range.start_line) {
      draft.range.end_line = draft.range.start_line;
    }
    await this.getCommentsModel().saveDraft(draft);
    this.getCommentsModel().reloadAllComments();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-ai-result': GrDiffAiResult;
  }
}
