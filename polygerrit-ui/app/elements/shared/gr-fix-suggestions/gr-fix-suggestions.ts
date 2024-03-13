/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '../gr-suggestion-diff-preview/gr-suggestion-diff-preview';
import {css, html, LitElement} from 'lit';
import {customElement, state, query, property} from 'lit/decorators.js';
import {fire} from '../../../utils/event-util';
import {getDocUrl} from '../../../utils/url-util';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {GrSuggestionDiffPreview} from '../gr-suggestion-diff-preview/gr-suggestion-diff-preview';
import {changeModelToken} from '../../../models/change/change-model';
import {Comment, isDraft, PatchSetNumber} from '../../../types/common';
import {OpenFixPreviewEventDetail} from '../../../types/events';

/**
 * gr-fix-suggestions is UI for comment.fix_suggestions.
 * gr-fix-suggestions is wrapper for gr-suggestion-diff-preview with buttons
 * to preview and apply fix and for giving a context about suggestion.
 */
@customElement('gr-fix-suggestions')
export class GrFixSuggestions extends LitElement {
  @query('gr-suggestion-diff-preview')
  suggestionDiffPreview?: GrSuggestionDiffPreview;

  @property({type: Object})
  comment?: Comment;

  @state() private docsBaseUrl = '';

  @state() private applyingFix = false;

  @state() latestPatchNum?: PatchSetNumber;

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

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
        .fixSuggestionInfo=${this.comment?.fix_suggestions?.[0]}
      ></gr-suggestion-diff-preview>`;
  }

  handleShowFix() {
    if (!this.comment?.fix_suggestions || !this.comment?.patch_set) return;
    const eventDetail: OpenFixPreviewEventDetail = {
      fixSuggestions: this.comment.fix_suggestions.map(s => {
        return {
          ...s,
          description: 'Suggested Edit from comment',
        };
      }),
      patchNum: this.comment.patch_set,
      onCloseFixPreviewCallbacks: [],
    };
    fire(this, 'open-fix-preview', eventDetail);
  }

  async handleApplyFix() {
    if (!this.comment?.fix_suggestions) return;
    this.applyingFix = true;
    try {
      await this.suggestionDiffPreview?.applyFixSuggestion();
    } finally {
      this.applyingFix = false;
    }
  }

  private isApplyEditDisabled() {
    if (this.comment?.patch_set === undefined) return true;
    if (isDraft(this.comment)) return true;
    return this.comment.patch_set !== this.latestPatchNum;
  }

  private computeApplyEditTooltip() {
    if (this.comment?.patch_set === undefined) return '';
    return this.comment.patch_set !== this.latestPatchNum
      ? 'You cannot apply this fix because it is from a previous patchset'
      : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-fix-suggestions': GrFixSuggestions;
  }
}
