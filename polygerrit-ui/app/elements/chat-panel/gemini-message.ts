/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';

import {changeModelToken} from '../../models/change/change-model';
import {filesModelToken, NormalizedFileInfo} from '../../models/change/files-model';
import {chatModelToken, CreateCommentPart, GeminiMessage as GeminiMessageModel, ResponsePartType, Turn} from '../../models/chat/chat-model';
import {commentsModelToken} from '../../models/comments/comments-model';
import {resolve} from '../../models/dependency';
import {CommentRange, NumericChangeId, PatchSetNumber} from '../../types/common';
import {createNew} from '../../utils/comment-util';
import {assert, assertIsDefined} from '../../utils/common-util';
import {subscribe} from '../lit/subscription-controller';

function compareComments(a: CreateCommentPart, b: CreateCommentPart) {
  if (!a.filePath || !b.filePath) {
    if (!a.filePath && !b.filePath) {
      return a.id - b.id;
    }
    if (!a.filePath) {
      return -1;
    }
    return 1;
  }
  if (a.filePath !== b.filePath) {
    return a.filePath.localeCompare(b.filePath);
  }
  const aLine = a.range?.start_line || 0;
  const bLine = b.range?.start_line || 0;
  if (aLine !== bLine) {
    return aLine - bLine;
  }

  return a.id - b.id;
}

@customElement('gemini-message')
export class GeminiMessage extends LitElement {
  @property({type: Number}) turnIndex = 0;

  @property({type: Boolean}) isLatest = false;

  @property({type: Boolean}) isBackgroundRequest = false;

  @state() turns: readonly Turn[] = [];

  @state() fileEntities: {[path: string]: NormalizedFileInfo} = {};

  @state() currentClNumber?: NumericChangeId;

  @state() showErrorDetails = false;

  private readonly getChatModel = resolve(this, chatModelToken);

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getFilesModel = resolve(this, filesModelToken);

  static override styles = [
    css`
      :host {
        display: block;
        padding-top: var(--spacing-s);
        padding-bottom: var(--spacing-s);
      }
      .material-icon {
        vertical-align: middle;
      }
      .suggested-comment-container {
        padding: 10px;
        background-color: var(--chat-panel-container-bg-color);
        border-radius: 5px;
        margin-bottom: 10px;
      }
      .thinking-indicator {
        display: flex;
        align-items: center;
      }
      .gemini-icon {
        color: #4285f4;
      }
      .thinking-spinner {
        margin-left: 10px;
      }
      .server-error {
        font-weight: bold;
      }
      .text-response {
        margin-top: 0px;
      }
      .text-response p:first-child {
        margin-top: var(--spacing-s);
        margin-bottom: var(--spacing-s);
      }
      .text-response,
      .suggested-comment-container {
        /* styling of the innerHtml of the text response. */
      }
      .text-response ul,
      .suggested-comment-container ul,
      .text-response ol,
      .suggested-comment-container ol {
        padding-inline-start: 18px;
      }
      .text-response p,
      .suggested-comment-container p,
      .text-response ul,
      .suggested-comment-container ul,
      .text-response ol,
      .suggested-comment-container ol {
        line-height: 20px;
      }
      .text-response code,
      .suggested-comment-container code {
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-mono);
        line-height: var(--line-height-mono);
        background-color: var(--chat-panel-code-bg-color);
        border-radius: 4px;
        padding: 2px;
      }
      .text-response code:has(*),
      .suggested-comment-container code:has(*) {
        white-space: pre-wrap;
        overflow-wrap: break-word;
        border-radius: 12px;
        border: 1px solid transparent;
        display: inline-block;
        padding: var(--spacing-l) var(--spacing-m);
        width: calc(100% - 2 * var(--spacing-m) - /* border width */ 2px);
      }
      .dark-mode .text-response code:has(*),
      .dark-mode .suggested-comment-container code:has(*) {
        /* @import 'third_party/javascript/highlightjs/styles/a11y-dark'; */
        /* TODO: Add styles for dark mode */
      }
      .light-mode .text-response code:has(*),
      .light-mode .suggested-comment-container code:has(*) {
        /* @import 'third_party/javascript/highlightjs/styles/googlecode'; */
        /* TODO: Add styles for light mode */
      }
      references-dropdown {
        margin-bottom: var(--spacing-l);
      }
      .text-content,
      .suggested-comment-container {
        overflow-x: auto;
        scrollbar-width: thin;
      }
    `,
  ];

  constructor() {
    super();
    subscribe(
        this, () => this.getChatModel().turns$, x => (this.turns = x ?? []));
    subscribe(this, () => this.getFilesModel().files$, x => {
      const fileEntities: {[path: string]: NormalizedFileInfo} = {};
      for (const file of x) {
        fileEntities[file.__path] = file;
      }
      this.fileEntities = fileEntities;
    });
    subscribe(
        this, () => this.getChangeModel().changeNum$,
        x => (this.currentClNumber = x));
  }

  onAddAsComment(comment: CreateCommentPart) {
    const {content, filePath, range, patchsetNumber} = comment;
    assertIsDefined(filePath, 'filePath');
    assertIsDefined(patchsetNumber, 'patchsetNumber');

    const draft = {
      ...createNew(content, true),
      path: filePath,
      patch_set: patchsetNumber as PatchSetNumber,
      range: range as CommentRange | undefined,
    };
    this.getCommentsModel().saveDraft(draft);
  }

  toggleShowErrorDetails() {
    this.showErrorDetails = !this.showErrorDetails;
  }

  locationForComment(comment: CreateCommentPart) {
    if (!comment.filePath || !comment.patchsetNumber) {
      return {filePath: undefined};
    }
    return {
      filePath: comment.filePath,
      patchsetNumber: comment.patchsetNumber,
      line: comment.range?.start_line || undefined,
      side: comment.side,
    };
  }

  trackByCommentId(unusedIndex: number, comment: CreateCommentPart) {
    return comment.id;
  }

  override render() {
    const message = this.message();
    if (!message) return;
    const responseParts = message.responseParts;
    const textParts =
        responseParts.filter(part => part.type === ResponsePartType.TEXT);

    return html`
      ${
        when(
            !this.isBackgroundRequest,
            () => html`
          <div class="user-info">
            <gr-icon
              class="gemini-icon"
              icon="ai-mark"
              .title=${
                message.timestamp ?
                    new Date(message.timestamp).toLocaleString() :
                    ''}
            ></gr-icon>
          </div>
        `)}
      ${
        when(
            message.errorMessage,
            () => html`
          <p class="server-error text-content">Server issue.</p>
          <p class="error-message">
            We were unable to fulfill your request for this due to a server
            issue. Please reload the webpage to try again.
          </p>
          <md-text-button
            @click=${() => this.toggleShowErrorDetails()}
            class="error-details-button"
          >
            <gr-icon icon=${
                this.showErrorDetails ? 'expand_less' :
                                        'expand_more'}></gr-icon>
            Details
          </md-text-button>
          ${
                when(
                    this.showErrorDetails,
                    () => html`<p class="error-details">${
                        message.errorMessage}</p>`)}
        `)}
      ${
        when(
            !message.errorMessage && responseParts.length === 0,
            () => when(
                message.responseComplete, () => html`<p class="text-content">
              The server did not return any response.
            </p>`,
                () => html`<div class="thinking-indicator">
              <p class="text-content">Thinking ...</p>
              ${when(!this.isBackgroundRequest, () => html`<md-circular-progress
                    class="thinking-spinner"
                    indeterminate
                    size="small"
                  ></md-circular-progress>`)}
            </div>`))}
      ${
        when(
            !message.errorMessage && responseParts.length > 0,
            () => html`
          ${textParts.map(responsePart => html`
              <p class="text-content text-response">
                <gr-formatted-text
                  .markdown=${true}
                  .text=${responsePart.content}
                ></gr-formatted-text>
              </p>
            `)}
          ${
                when(
                    !this.isBackgroundRequest,
                    () => this.sortedComments().map(comment => html`
                <div class="suggested-comment-container">
                  <p class="suggested-comment">
                    <gr-formatted-text
                      .markdown=${true}
                      .text=${comment.content}
                    ></gr-formatted-text>
                  </p>
                  <md-filled-button
                    class="add-as-comment-button"
                    @click=${() => this.onAddAsComment(comment)}
                    >Add as Comment
                  </md-filled-button>
                </div>
            `))}
          ${
                when(
                    message.responseComplete && !this.isBackgroundRequest,
                    () => html`
              <citations-box .turnIndex=${this.turnIndex}></citations-box>
              <references-dropdown
                .turnIndex=${this.turnIndex}
              ></references-dropdown>
              <message-actions
                .turnId=${this.turnId()}
                .isLatest=${this.isLatest}
              ></message-actions>
            `)}
        `)}
    `;
  }

  private message(): GeminiMessageModel {
    assert(this.turnIndex < this.turns.length, 'turnIndex out of bounds');
    return this.turns[this.turnIndex].geminiMessage;
  }

  private sortedComments() {
    return this.message()
        .responseParts
        .filter(part => part.type === ResponsePartType.CREATE_COMMENT)
        .sort(compareComments);
  }

  private turnId() {
    return {
      turnIndex: this.turnIndex,
      regenerationIndex: this.message().regenerationIndex,
    };
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gemini-message': GeminiMessage;
  }
}
