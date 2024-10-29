/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '../gr-suggestion-diff-preview/gr-suggestion-diff-preview';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, state, query} from 'lit/decorators.js';
import {fire} from '../../../utils/event-util';
import {getDocUrl} from '../../../utils/url-util';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {GrSuggestionDiffPreview} from '../gr-suggestion-diff-preview/gr-suggestion-diff-preview';
import {changeModelToken} from '../../../models/change/change-model';
import {Comment, PatchSetNumber} from '../../../types/common';
import {commentModelToken} from '../gr-comment-model/gr-comment-model';
import {createUserFixSuggestion} from '../../../utils/comment-util';

declare global {
  interface HTMLElementEventMap {
    'open-user-suggest-preview': OpenUserSuggestionPreviewEvent;
  }
}

export type OpenUserSuggestionPreviewEvent =
  CustomEvent<OpenUserSuggestionPreviewEventDetail>;
export interface OpenUserSuggestionPreviewEventDetail {
  code: string;
}

@customElement('gr-user-suggestion-fix')
export class GrUserSuggestionsFix extends LitElement {
  @query('gr-suggestion-diff-preview')
  suggestionDiffPreview?: GrSuggestionDiffPreview;

  @state() private docsBaseUrl = '';

  @state() private applyingFix = false;

  @state() latestPatchNum?: PatchSetNumber;

  @state() comment?: Comment;

  @state() private previewLoaded = false;

  @state() commentedText?: string;

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getCommentModel = resolve(this, commentModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().docsBaseUrl$,
      docsBaseUrl => (this.docsBaseUrl = docsBaseUrl)
    );
    subscribe(
      this,
      () => this.getChangeModel().latestPatchNum$,
      x => (this.latestPatchNum = x)
    );
    subscribe(
      this,
      () => this.getCommentModel().comment$,
      comment => (this.comment = comment)
    );
    subscribe(
      this,
      () => this.getCommentModel().commentedText$,
      commentedText => (this.commentedText = commentedText)
    );
  }

  static override get styles() {
    return [
      css`
        .header {
          background-color: var(--background-color-primary);
          border: 1px solid var(--border-color);
          padding: var(--spacing-xs) var(--spacing-xl);
          display: flex;
          align-items: center;
          border-top-left-radius: var(--border-radius);
          border-top-right-radius: var(--border-radius);
        }
        .header .title {
          flex: 1;
        }
        .copyButton {
          margin-right: var(--spacing-l);
        }
      `,
    ];
  }

  override render() {
    if (!this.textContent || !this.comment || !this.commentedText)
      return nothing;
    const code = this.textContent;
    const fixSuggestions = createUserFixSuggestion(
      this.comment,
      this.commentedText,
      code
    );
    return html`<div class="header">
        <div class="title">
          <span>Suggested edit</span>
          <a
            href=${getDocUrl(this.docsBaseUrl, 'user-suggest-edits.html')}
            target="_blank"
            rel="noopener noreferrer"
            ><gr-icon icon="help" title="read documentation"></gr-icon
          ></a>
        </div>
        <div class="copyButton">
          <gr-copy-clipboard
            hideInput=""
            text=${code}
            multiline
            copyTargetName="Suggested edit"
            buttonTitle="Copy Suggested edit to clipboard"
          ></gr-copy-clipboard>
        </div>
        <div>
          <gr-button
            secondary
            flatten
            class="action show-fix"
            @click=${this.handleShowFix}
          >
            Show edit
          </gr-button>
          <gr-button
            secondary
            flatten
            .loading=${this.applyingFix}
            .disabled=${this.isApplyEditDisabled()}
            class="action show-fix"
            @click=${this.handleApplyFix}
            .title=${this.computeApplyEditTooltip()}
          >
            Apply edit
          </gr-button>
        </div>
      </div>
      <gr-suggestion-diff-preview
        .fixSuggestionInfo=${fixSuggestions[0]}
        .codeText=${code}
        @preview-loaded=${() => (this.previewLoaded = true)}
      ></gr-suggestion-diff-preview>`;
  }

  override updated(changedProperties: PropertyValues) {
    super.updated(changedProperties);
    if (changedProperties.has('commentedText') && this.commentedText) {
      this.previewLoaded = false;
    }
  }

  handleShowFix() {
    if (!this.textContent) return;
    fire(this, 'open-user-suggest-preview', {code: this.textContent});
  }

  async handleApplyFix() {
    if (!this.textContent) return;
    this.applyingFix = true;
    await this.suggestionDiffPreview?.applyFix();
    this.applyingFix = false;
  }

  private isApplyEditDisabled() {
    if (this.comment?.patch_set === undefined) return true;
    return !this.previewLoaded;
  }

  private computeApplyEditTooltip() {
    if (this.comment?.patch_set === undefined) return '';
    if (!this.previewLoaded) return 'Fix is still loading ...';
    return '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-user-suggestion-fix': GrUserSuggestionsFix;
  }
}
