/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../shared/gr-suggestion-diff-preview/gr-suggestion-diff-preview';
import {GrSuggestionDiffPreview} from '../shared/gr-suggestion-diff-preview/gr-suggestion-diff-preview';
import {css, html, LitElement, nothing} from 'lit';
import {customElement, query, property, state} from 'lit/decorators.js';
import {BasePatchSetNum, RepoName} from '../../types/common';
import {resolve} from '../../models/dependency';
import {
  ChangeStatus,
  FixSuggestionInfo,
  NumericChangeId,
  PatchSetNumber,
} from '../../api/rest-api';
import {changeModelToken} from '../../models/change/change-model';
import {subscribe} from '../lit/subscription-controller';
import {fire} from '../../utils/event-util';
import {OpenFixPreviewEventDetail} from '../../types/events';
import {userModelToken} from '../../models/user/user-model';

/**
 * There is a certain overlap with `GrUserSuggestionsFix` which wraps
 * `GrSuggestionDiffPreview` and has the header that we also need.
 * But it is very targeted to be used for user suggestions and inside comments.
 *
 * So there is certainly an opportunity for cleanup and unification, but at the
 * time of component creation it did not feel wortwhile investing into this
 * effort. This is tracked in b/360288262.
 */
@customElement('gr-checks-fix-preview')
export class GrChecksFixPreview extends LitElement {
  @query('gr-suggestion-diff-preview')
  suggestionDiffPreview?: GrSuggestionDiffPreview;

  @property({type: Object})
  fixSuggestionInfo?: FixSuggestionInfo;

  @property({type: Number})
  patchSet?: PatchSetNumber;

  @state()
  repo?: RepoName;

  @state()
  changeNum?: NumericChangeId;

  @state()
  latestPatchNum?: PatchSetNumber;

  @state() previewLoaded = false;

  @state()
  applyingFix = false;

  @state() isChangeMerged = false;

  @state() isChangeAbandoned = false;

  @state() loggedIn = false;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getUserModel = resolve(this, userModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChangeModel().changeNum$,
      changeNum => (this.changeNum = changeNum)
    );
    subscribe(
      this,
      () => this.getChangeModel().latestPatchNum$,
      x => (this.latestPatchNum = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().repo$,
      x => (this.repo = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().status$,
      status => (this.isChangeMerged = status === ChangeStatus.MERGED)
    );
    subscribe(
      this,
      () => this.getChangeModel().status$,
      status => (this.isChangeAbandoned = status === ChangeStatus.ABANDONED)
    );
    subscribe(
      this,
      () => this.getUserModel().loggedIn$,
      loggedIn => (this.loggedIn = loggedIn)
    );
  }

  static override get styles() {
    return [
      css`
        :host {
          display: block;
        }
        .header {
          background-color: var(--background-color-primary);
          border: 1px solid var(--border-color);
          border-bottom: none;
          padding: var(--spacing-xs) var(--spacing-xl);
          display: flex;
          align-items: center;
        }
        .header .title {
          flex: 1;
        }
        .loading {
          border: 1px solid var(--border-color);
          padding: var(--spacing-xl);
        }
      `,
    ];
  }

  override render() {
    if (!this.fixSuggestionInfo) return nothing;
    return html`${this.renderHeader()}${this.renderDiff()}`;
  }

  private renderHeader() {
    return html`
      <div class="header">
        <div class="title">
          <span>Attached Fix</span>
        </div>
        <div>
          <gr-button
            class="showFix"
            secondary
            flatten
            .disabled=${!this.previewLoaded}
            @click=${this.showFix}
          >
            Show fix side-by-side
          </gr-button>
          <gr-button
            class="applyFix"
            primary
            flatten
            .loading=${this.applyingFix}
            .disabled=${this.isApplyEditDisabled()}
            @click=${this.applyFix}
            .title=${this.computeApplyFixTooltip()}
          >
            Apply fix
          </gr-button>
        </div>
      </div>
    `;
  }

  private renderDiff() {
    return html`
      <gr-suggestion-diff-preview
        .fixSuggestionInfo=${this.fixSuggestionInfo}
        .patchSet=${this.patchSet}
        .codeText=${'Loading fix preview ...'}
        @preview-loaded=${() => (this.previewLoaded = true)}
      ></gr-suggestion-diff-preview>
    `;
  }

  private showFix() {
    if (!this.patchSet || !this.fixSuggestionInfo) return;
    const eventDetail: OpenFixPreviewEventDetail = {
      patchNum: this.patchSet,
      fixSuggestions: [this.fixSuggestionInfo],
      onCloseFixPreviewCallbacks: [],
    };
    fire(this, 'open-fix-preview', eventDetail);
  }

  /**
   * Applies the fix and then navigates to the EDIT patchset.
   */
  private async applyFix() {
    const changeNum = this.changeNum;
    const basePatchNum = this.patchSet as BasePatchSetNum;
    if (!changeNum || !basePatchNum || !this.fixSuggestionInfo) return;

    this.applyingFix = true;
    try {
      await this.suggestionDiffPreview?.applyFix();
    } finally {
      this.applyingFix = false;
    }
  }

  private isApplyEditDisabled() {
    if (this.patchSet === undefined) return true;
    if (this.isChangeMerged) return true;
    if (this.isChangeAbandoned) return true;
    if (!this.loggedIn) return true;
    return !this.previewLoaded;
  }

  private computeApplyFixTooltip() {
    if (this.patchSet === undefined) return '';
    if (this.isChangeMerged) return 'Change is already merged';
    if (this.isChangeAbandoned) return 'Change is abandoned';
    if (!this.previewLoaded) return 'Fix is still loading ...';
    if (!this.loggedIn) return 'You must be logged in to apply a fix';
    return '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-fix-preview': GrChecksFixPreview;
  }
}
