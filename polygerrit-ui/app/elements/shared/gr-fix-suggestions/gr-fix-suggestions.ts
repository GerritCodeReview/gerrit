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
import {
  Comment,
  FixSuggestionInfo,
  isDraft,
  PatchSetNumber,
} from '../../../types/common';
import {commentModelToken} from '../gr-comment-model/gr-comment-model';

declare global {
  interface HTMLElementEventMap {
    'open-fix-suggestions-preview': CustomEvent<{}>;
  }
}

@customElement('gr-fix-suggestions')
export class GrFixSuggestions extends LitElement {
  @query('gr-suggestion-diff-preview')
  suggestionDiffPreview?: GrSuggestionDiffPreview;

  @property({type: Object})
  fixSuggestions?: FixSuggestionInfo[];

  @state() private docsBaseUrl = '';

  @state() private applyingFix = false;

  @state() latestPatchNum?: PatchSetNumber;

  @state() comment?: Comment;

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
        .fixReplacementInfos=${this.fixSuggestions?.[0].replacements}
      ></gr-suggestion-diff-preview>`;
  }

  handleShowFix() {
    if (!this.fixSuggestions) return;
    fire(this, 'open-fix-suggestions-preview', {});
  }

  async handleApplyFix() {
    if (!this.fixSuggestions) return;
    this.applyingFix = true;
    await this.suggestionDiffPreview?.applyFixSuggestions();
    this.applyingFix = false;
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
