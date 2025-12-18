/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@material/web/progress/circular-progress.js';
import '@material/web/button/filled-button.js';
import '@material/web/button/text-button.js';
import '../shared/gr-icon/gr-icon';
import '../shared/gr-button/gr-button';
import '../shared/gr-formatted-text/gr-formatted-text';
import './citations-box';
import './references-dropdown';
import './message-actions';

import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';

import {changeModelToken} from '../../models/change/change-model';
import {
  filesModelToken,
  NormalizedFileInfo,
} from '../../models/change/files-model';
import {
  chatModelToken,
  CreateCommentPart,
  GeminiMessage as GeminiMessageModel,
  ResponsePartType,
  Turn,
} from '../../models/chat/chat-model';
import {commentsModelToken} from '../../models/comments/comments-model';
import {resolve} from '../../models/dependency';
import {NumericChangeId, PatchSetNumber} from '../../types/common';
import {compareComments, createNew} from '../../utils/comment-util';
import {assert} from '../../utils/common-util';
import {subscribe} from '../lit/subscription-controller';

@customElement('gemini-message')
export class GeminiMessage extends LitElement {
  @property({type: Number}) turnIndex = 0;

  @property({type: Boolean}) isLatest = false;

  /**
   * A background request is a request that is not part of an active ongoing
   * chat conversation, but just kicked off from the splash page.
   */
  @property({type: Boolean}) isBackgroundRequest = false;

  @state() turns: readonly Turn[] = [];

  @state() fileEntities: {[path: string]: NormalizedFileInfo} = {};

  @state() currentClNumber?: NumericChangeId;

  @state() showErrorDetails = false;

  @state() latestPatchNum?: PatchSetNumber;

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
      .suggested-comment {
        padding: 10px;
        background-color: var(--background-color-tertiary);
        border: 1px solid var(--border-color);
        border-radius: 5px;
        margin-bottom: 10px;
        overflow-x: auto;
        scrollbar-width: thin;
      }
      .thinking-indicator {
        display: flex;
        align-items: center;
      }
      .gemini-icon {
        color: #4285f4;
      }
      .thinking-spinner {
        --md-circular-progress-size: 24px;
        margin-left: 10px;
      }
      .server-error {
        display: flex;
        align-items: center;
        gap: var(--spacing-s);
        font-weight: 500;
        color: var(--error-foreground);
        margin-bottom: var(--spacing-s);
      }
      .error-icon {
        color: var(--error-foreground);
      }
      .error-message {
        margin-bottom: var(--spacing-m);
        color: var(--deemphasized-text-color);
      }
      .error-details {
        margin-top: var(--spacing-s);
        margin-bottom: var(--spacing-s);
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-small);
        white-space: pre-wrap;
        background-color: var(--background-color-tertiary);
        padding: var(--spacing-s);
        border-radius: var(--border-radius);
      }
      .error-actions {
        display: flex;
        gap: var(--spacing-m);
        margin-top: var(--spacing-s);
      }
      .user-info {
        margin-bottom: var(--spacing-m);
      }
      .text-response {
        margin-top: var(--spacing-s);
        margin-bottom: var(--spacing-xl);
      }
      references-dropdown {
        margin-bottom: var(--spacing-l);
      }
      .text-content {
        overflow-x: auto;
        scrollbar-width: thin;
      }
      .comment-path,
      .comment-line {
        display: flex;
        align-items: center;
        gap: var(--spacing-s);
        margin-bottom: var(--spacing-xs);
        color: var(--link-color);
        text-decoration: none;
      }
      .comment-path gr-icon,
      .comment-line gr-icon {
        font-size: 16px;
      }
      .suggested-comment-message {
        margin-top: var(--spacing-s);
        margin-bottom: var(--spacing-m);
      }
    `,
  ];

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChatModel().turns$,
      x => (this.turns = x ?? [])
    );
    subscribe(
      this,
      () => this.getFilesModel().files$,
      x => {
        const fileEntities: {[path: string]: NormalizedFileInfo} = {};
        for (const file of x) {
          fileEntities[file.__path] = file;
        }
        this.fileEntities = fileEntities;
      }
    );
    subscribe(
      this,
      () => this.getChangeModel().changeNum$,
      x => (this.currentClNumber = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().latestPatchNum$,
      x => (this.latestPatchNum = x)
    );
  }

  private async onAddAsComment(part: CreateCommentPart) {
    const draft = {
      ...part.comment,
      ...createNew(part.comment.message, true),
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

  private onRetry() {
    this.getChatModel().regenerateMessage(this.turnId());
  }

  private toggleShowErrorDetails() {
    this.showErrorDetails = !this.showErrorDetails;
  }

  override render() {
    if (this.turnIndex >= this.turns.length) return;
    const message = this.message();
    if (!message) return;
    const responseParts = message.responseParts;
    const textParts = responseParts.filter(
      part => part.type === ResponsePartType.TEXT
    );

    return html`
      ${when(
        !this.isBackgroundRequest,
        () => html`
          <div class="user-info">
            <gr-icon
              class="gemini-icon"
              icon="robot_2"
              .title=${message.timestamp
                ? new Date(message.timestamp).toLocaleString()
                : ''}
            ></gr-icon>
          </div>
        `
      )}
      ${when(
        message.errorMessage,
        () => html`
          <div class="server-error text-content">
            <gr-icon icon="error" class="error-icon"></gr-icon>
            Server error
          </div>
          <div class="error-message">
            We were unable to fulfill your request.
            ${when(
              this.showErrorDetails,
              () => html`<p class="error-details">${message.errorMessage}</p>`
            )}
            <div class="error-actions">
              <gr-button @click=${() => this.onRetry()} link>Retry</gr-button>
              <gr-button @click=${() => this.toggleShowErrorDetails()} link>
                ${this.showErrorDetails ? 'Hide details' : 'Show details'}
              </gr-button>
            </div>
          </div>
        `
      )}
      ${when(!message.errorMessage && responseParts.length === 0, () =>
        when(
          message.responseComplete,
          () => html`<p class="text-content">
            The server did not return any response.
          </p>`,
          () => html`<div class="thinking-indicator">
            <p class="text-content">Thinking ...</p>
            ${when(
              !this.isBackgroundRequest,
              () => html`
                <md-circular-progress
                  class="thinking-spinner"
                  indeterminate
                  size="small"
                ></md-circular-progress>
              `
            )}
          </div>`
        )
      )}
      ${when(
        !message.errorMessage && responseParts.length > 0,
        () => html`
          ${textParts.map(
            responsePart => html`
              <p class="text-content text-response">
                <gr-formatted-text
                  .markdown=${true}
                  .content=${responsePart.content}
                ></gr-formatted-text>
              </p>
            `
          )}
          ${when(!this.isBackgroundRequest, () =>
            this.sortedComments().map(
              comment => html`
                ${when(
                  comment.comment.path,
                  () => html`
                    <div class="comment-path">
                      <gr-icon icon="description"></gr-icon>
                      ${comment.comment.path}
                    </div>
                  `
                )}
                ${when(
                  comment.comment.line,
                  () => html`
                    <div class="comment-line">
                      <gr-icon icon="code"></gr-icon>
                      ${comment.comment.line}
                    </div>
                  `
                )}
                <div class="suggested-comment">
                  <p class="suggested-comment-message">
                    <gr-formatted-text
                      .markdown=${true}
                      .content=${comment.comment.message}
                    ></gr-formatted-text>
                  </p>
                  <gr-button
                    primary
                    class="add-as-comment-button"
                    @click=${() => this.onAddAsComment(comment)}
                    >Add as Comment
                  </gr-button>
                </div>
              `
            )
          )}
          ${when(
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
            `
          )}
        `
      )}
    `;
  }

  private message(): GeminiMessageModel {
    assert(this.turnIndex < this.turns.length, 'turnIndex out of bounds');
    return this.turns[this.turnIndex].geminiMessage;
  }

  private sortedComments() {
    return this.message()
      .responseParts.filter(
        part => part.type === ResponsePartType.CREATE_COMMENT
      )
      .sort((p1, p2) => {
        const c1 = {...createNew(p1.comment.message), ...p1.comment};
        const c2 = {...createNew(p2.comment.message), ...p2.comment};
        return compareComments(c1, c2);
      });
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
