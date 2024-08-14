/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../embed/diff/gr-diff/gr-diff';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {getAppContext} from '../../services/app-context';
import {EDIT, BasePatchSetNum, RepoName} from '../../types/common';
import {anyLineTooLong} from '../../utils/diff-util';
import {
  DiffLayer,
  DiffPreferencesInfo,
  DiffViewMode,
  RenderPreferences,
} from '../../api/diff';
import {GrSyntaxLayerWorker} from '../../embed/diff/gr-syntax-layer/gr-syntax-layer-worker';
import {resolve} from '../../models/dependency';
import {highlightServiceToken} from '../../services/highlight/highlight-service';
import {
  FixSuggestionInfo,
  NumericChangeId,
  PatchSetNumber,
} from '../../api/rest-api';
import {changeModelToken} from '../../models/change/change-model';
import {subscribe} from '../lit/subscription-controller';
import {FilePreview} from '../diff/gr-apply-fix-dialog/gr-apply-fix-dialog';
import {userModelToken} from '../../models/user/user-model';
import {navigationToken} from '../core/gr-navigation/gr-navigation';
import {fire} from '../../utils/event-util';
import {createChangeUrl} from '../../models/views/change';
import {OpenFixPreviewEventDetail} from '../../types/events';
import {rectifyFix} from '../../models/checks/checks-util';

@customElement('gr-checks-fix-preview')
export class GrChecksFixPreview extends LitElement {
  @property({type: Object})
  fixSuggestionInfo?: FixSuggestionInfo;

  @property({type: Number})
  patchSet?: PatchSetNumber;

  @state()
  layers: DiffLayer[] = [];

  @state()
  repo?: RepoName;

  @state()
  changeNum?: NumericChangeId;

  @state()
  latestPatchNum?: PatchSetNumber;

  @state()
  preview?: FilePreview;

  @state()
  applyingFix = false;

  @state()
  diffPrefs?: DiffPreferencesInfo;

  @state()
  renderPrefs: RenderPreferences = {
    disable_context_control_buttons: true,
    show_file_comment_button: false,
    hide_line_length_indicator: true,
  };

  private readonly restApiService = getAppContext().restApiService;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getNavigation = resolve(this, navigationToken);

  private readonly syntaxLayer = new GrSyntaxLayerWorker(
    resolve(this, highlightServiceToken),
    () => getAppContext().reportingService
  );

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
      () => this.getUserModel().diffPreferences$,
      diffPreferences => {
        if (!diffPreferences) return;
        this.diffPrefs = diffPreferences;
        this.syntaxLayer.setEnabled(!!this.diffPrefs.syntax_highlighting);
      }
    );
    subscribe(
      this,
      () => this.getChangeModel().repo$,
      x => (this.repo = x)
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
        .buttons {
          text-align: right;
        }
        .diff-container {
          border: 1px solid var(--border-color);
          border-top: none;
          border-bottom: none;
        }
        .loading {
          border: 1px solid var(--border-color);
          padding: var(--spacing-xl);
        }
        code {
          max-width: var(--gr-formatted-text-prose-max-width, none);
          background-color: var(--background-color-secondary);
          border: 1px solid var(--border-color);
          border-top: 0;
          display: block;
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-code);
          line-height: var(--line-height-mono);
          margin-bottom: var(--spacing-m);
          padding: var(--spacing-xxs) var(--spacing-s);
          overflow-x: auto;
          /* Pre will preserve whitespace and line breaks but not wrap */
          white-space: pre;
          border-bottom-left-radius: var(--border-radius);
          border-bottom-right-radius: var(--border-radius);
        }
      `,
    ];
  }

  override updated(changed: PropertyValues) {
    if (changed.has('fixSuggestionInfo')) {
      this.fetchFixPreview();
    }
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
            secondary
            flatten
            .disabled=${!this.preview}
            class="action show-fix"
            @click=${this.showFix}
          >
            Show fix side-by-side
          </gr-button>
          <gr-button
            primary
            flatten
            .loading=${this.applyingFix}
            .disabled=${this.isApplyEditDisabled()}
            class="action show-fix"
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
    if (!this.preview) {
      return html`<div class="loading">Loading fix preview ...</div>`;
    }
    const diff = this.preview.preview;
    if (!anyLineTooLong(diff)) {
      this.syntaxLayer.process(diff);
    }
    return html`<div class="diff-container">
      <gr-diff
        .prefs=${this.overridePartialDiffPrefs()}
        .path=${this.preview.filepath}
        .diff=${diff}
        .layers=${this.layers}
        .renderPrefs=${this.renderPrefs}
        .viewMode=${DiffViewMode.UNIFIED}
      ></gr-diff>
    </div>`;
  }

  private async fetchFixPreview() {
    if (!this.changeNum || !this.patchSet || !this.fixSuggestionInfo) return;

    const res = await this.restApiService.getFixPreview(
      this.changeNum,
      this.patchSet,
      this.fixSuggestionInfo.replacements
    );

    if (!res) return;
    const currentPreviews = Object.keys(res).map(key => {
      return {filepath: key, preview: res[key]};
    });
    if (currentPreviews.length > 0) {
      this.preview = currentPreviews[0];
    }

    return res;
  }

  private showFix() {
    if (!this.patchSet || !this.fixSuggestionInfo) return;
    const fix = rectifyFix(this.fixSuggestionInfo, 'checker');
    if (!fix) return;
    const eventDetail: OpenFixPreviewEventDetail = {
      patchNum: this.patchSet,
      fixSuggestions: [fix],
      onCloseFixPreviewCallbacks: [],
    };
    fire(this, 'open-fix-preview', eventDetail);
  }

  private async applyFix() {
    const changeNum = this.changeNum;
    const basePatchNum = this.patchSet as BasePatchSetNum;
    if (!changeNum || !basePatchNum || !this.fixSuggestionInfo) return;

    this.applyingFix = true;
    const res = await this.restApiService.applyFixSuggestion(
      changeNum,
      basePatchNum,
      this.fixSuggestionInfo.replacements
    );
    this.applyingFix = false;
    if (res?.ok) {
      const url = createChangeUrl({
        changeNum,
        repo: this.repo!,
        patchNum: EDIT,
        basePatchNum,
        forceReload: true,
      });
      this.getNavigation().setUrl(url);
    }
  }

  private overridePartialDiffPrefs() {
    if (!this.diffPrefs) return undefined;
    return {
      ...this.diffPrefs,
      context: 0,
      line_length: Math.min(this.diffPrefs.line_length, 100),
      line_wrapping: true,
    };
  }

  private isApplyEditDisabled() {
    if (!this.preview || this.patchSet === undefined) return true;
    return this.patchSet !== this.latestPatchNum;
  }

  private computeApplyFixTooltip() {
    if (this.patchSet === undefined) return '';
    if (!this.preview) return 'Fix is still loading ...';
    return this.patchSet !== this.latestPatchNum
      ? 'You cannot apply this fix because it is from a previous patchset'
      : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-fix-preview': GrChecksFixPreview;
  }
}
