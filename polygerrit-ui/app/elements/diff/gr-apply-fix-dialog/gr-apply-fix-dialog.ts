/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/shared-styles';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-icon/gr-icon';
import '../../../embed/diff/gr-diff/gr-diff';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {
  NumericChangeId,
  EDIT,
  FixSuggestionInfo,
  PatchSetNum,
  BasePatchSetNum,
  FilePathToDiffInfoMap,
  PatchSetNumber,
} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {PROVIDED_FIX_ID} from '../../../utils/comment-util';
import {OpenFixPreviewEvent} from '../../../types/events';
import {getAppContext} from '../../../services/app-context';
import {DiffLayer, ParsedChangeInfo} from '../../../types/types';
import {GrButton} from '../../shared/gr-button/gr-button';
import {TokenHighlightLayer} from '../../../embed/diff/gr-diff-builder/token-highlight-layer';
import {css, html, LitElement, nothing} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {subscribe} from '../../lit/subscription-controller';
import {assert} from '../../../utils/common-util';
import {resolve} from '../../../models/dependency';
import {createChangeUrl} from '../../../models/views/change';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {userModelToken} from '../../../models/user/user-model';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {GrSyntaxLayerWorker} from '../../../embed/diff/gr-syntax-layer/gr-syntax-layer-worker';
import {highlightServiceToken} from '../../../services/highlight/highlight-service';
import {anyLineTooLong} from '../../../utils/diff-util';
import {fireReload} from '../../../utils/event-util';
import {when} from 'lit/directives/when.js';
import {Timing} from '../../../constants/reporting';
import {changeModelToken} from '../../../models/change/change-model';
import {getFileExtension} from '../../../utils/file-util';
import {ChangeStatus} from '../../../api/rest-api';

export interface DiffPreview {
  filepath: string;
  preview: DiffInfo;
}

@customElement('gr-apply-fix-dialog')
export class GrApplyFixDialog extends LitElement {
  @query('#applyFixModal')
  applyFixModal?: HTMLDialogElement;

  @query('#applyFixDialog')
  applyFixDialog?: GrDialog;

  @query('#nextFix')
  nextFix?: GrButton;

  @state()
  change?: ParsedChangeInfo;

  @state()
  changeNum?: NumericChangeId;

  @state()
  patchNum?: PatchSetNum;

  @state() latestPatchNum?: PatchSetNumber;

  @state()
  currentFix?: FixSuggestionInfo;

  @state()
  currentPreviews: DiffPreview[] = [];

  @state()
  fixSuggestions?: FixSuggestionInfo[];

  @state()
  isApplyFixLoading = false;

  @state()
  selectedFixIdx = 0;

  @state()
  layers: DiffLayer[] = [];

  @state()
  diffPrefs?: DiffPreferencesInfo;

  @state()
  loading = false;

  @state() isChangeMerged = false;

  @state()
  onCloseFixPreviewCallbacks: ((fixapplied: boolean) => void)[] = [];

  private readonly restApiService = getAppContext().restApiService;

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getNavigation = resolve(this, navigationToken);

  private readonly reporting = getAppContext().reportingService;

  private readonly syntaxLayer = new GrSyntaxLayerWorker(
    resolve(this, highlightServiceToken),
    () => getAppContext().reportingService
  );

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().preferences$,
      preferences => {
        const layers: DiffLayer[] = [this.syntaxLayer];
        if (!preferences?.disable_token_highlighting) {
          layers.push(new TokenHighlightLayer(this));
        }
        this.layers = layers;
      }
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
      () => this.getChangeModel().change$,
      change => (this.change = change)
    );
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
      () => this.getChangeModel().status$,
      status => (this.isChangeMerged = status === ChangeStatus.MERGED)
    );
  }

  static override get styles() {
    return [
      sharedStyles,
      modalStyles,
      css`
        .diffContainer {
          padding: var(--spacing-l) 0;
          border-bottom: 1px solid var(--border-color);
        }
        .file-name {
          display: block;
          padding: var(--spacing-s) var(--spacing-l);
          background-color: var(--background-color-secondary);
          border-bottom: 1px solid var(--border-color);
        }
        gr-button {
          margin-left: var(--spacing-m);
        }
        .fix-picker {
          display: flex;
          align-items: center;
          margin-right: var(--spacing-l);
        }
        .info {
          background-color: var(--info-background);
          padding: var(--spacing-l) var(--spacing-xl);
        }
        .info gr-icon {
          color: var(--selected-foreground);
          margin-right: var(--spacing-xl);
        }
      `,
    ];
  }

  override render() {
    return html`
      <dialog id="applyFixModal" tabindex="-1">
        <gr-dialog
          id="applyFixDialog"
          ?loading=${this.loading}
          .loadingLabel=${'Creating preview ...'}
          .confirmLabel=${this.isApplyFixLoading ? 'Saving...' : 'Apply Fix'}
          .confirmTooltip=${this.computeTooltip()}
          ?disabled=${this.computeDisableApplyFixButton()}
          @confirm=${this.handleApplyFix}
          @cancel=${this.onCancel}
        >
          ${this.renderHeader()} ${this.renderMain()} ${this.renderFooter()}
        </gr-dialog>
      </dialog>
    `;
  }

  private renderHeader() {
    return html`
      <div slot="header">${this.currentFix?.description ?? ''}</div>
    `;
  }

  private renderMain() {
    const items = this.currentPreviews.map(
      item => html`
        <div class="file-name">
          <span>${item.filepath}</span>
        </div>
        <div class="diffContainer">${this.renderDiff(item)}</div>
      `
    );
    return html`<div slot="main">${items}</div>`;
  }

  private renderDiff(preview: DiffPreview) {
    const diff = preview.preview;
    if (!anyLineTooLong(diff)) {
      this.syntaxLayer.process(diff);
    }
    return html`<gr-diff
      .prefs=${this.overridePartialDiffPrefs()}
      .path=${preview.filepath}
      .diff=${diff}
      .layers=${this.layers}
    ></gr-diff>`;
  }

  private renderFooter() {
    const fixCount = this.fixSuggestions?.length ?? 0;
    const reasonForDisabledApplyButton = this.computeTooltip();
    const shouldRenderNav = fixCount >= 2;
    const shouldRenderWarning = !!reasonForDisabledApplyButton;

    if (!shouldRenderNav && !shouldRenderWarning) return nothing;

    return html`
      <div slot="footer" class="fix-picker">
        ${when(shouldRenderNav, () =>
          this.renderNavForMultipleSuggestedFixes(fixCount)
        )}
        ${when(shouldRenderWarning, () =>
          this.renderWarning(reasonForDisabledApplyButton)
        )}
      </div>
    `;
  }

  private renderNavForMultipleSuggestedFixes(fixCount: number) {
    const id = this.selectedFixIdx;
    return html`
      <span>Suggested fix ${id + 1} of ${fixCount}</span>
      <gr-button
        id="prevFix"
        @click=${this.onPrevFixClick}
        ?disabled=${id === 0}
      >
        <gr-icon icon="chevron_left"></gr-icon>
      </gr-button>
      <gr-button
        id="nextFix"
        @click=${this.onNextFixClick}
        ?disabled=${id === fixCount - 1}
      >
        <gr-icon icon="chevron_right"></gr-icon>
      </gr-button>
    `;
  }

  private renderWarning(message: string) {
    if (!message) return nothing;
    return html`<span class="info"
      ><gr-icon icon="info"></gr-icon>${message}</span
    >`;
  }

  /**
   * Given event with fixSuggestions, fetch diffs associated with first
   * suggested fix and open dialog.
   */
  open(e: OpenFixPreviewEvent) {
    this.patchNum = e.detail.patchNum;
    this.fixSuggestions = e.detail.fixSuggestions;
    this.onCloseFixPreviewCallbacks = e.detail.onCloseFixPreviewCallbacks;
    assert(this.fixSuggestions.length > 0, 'no fix in the event');
    this.selectedFixIdx = 0;
    this.applyFixModal?.showModal();
    return this.showSelectedFixSuggestion(this.fixSuggestions[0]);
  }

  private async showSelectedFixSuggestion(fixSuggestion: FixSuggestionInfo) {
    this.currentFix = fixSuggestion;
    this.loading = true;
    this.reporting.time(Timing.PREVIEW_FIX_LOAD);
    await this.fetchFixPreview(fixSuggestion);
    this.reporting.timeEnd(Timing.PREVIEW_FIX_LOAD);
    this.loading = false;
  }

  private async fetchFixPreview(fixSuggestion: FixSuggestionInfo) {
    if (!this.changeNum || !this.patchNum) {
      return Promise.reject(
        new Error('Both patchNum and changeNum must be set')
      );
    }
    let res: FilePathToDiffInfoMap | undefined;
    try {
      if (fixSuggestion.fix_id === PROVIDED_FIX_ID) {
        res = await this.restApiService.getFixPreview(
          this.changeNum,
          this.patchNum,
          fixSuggestion.replacements
        );
      } else {
        // TODO(b/227463363) Remove once Robot Comments are deprecated.
        // We don't use this for user suggestions or comments.fix_suggestions.
        res = await this.restApiService.getRobotCommentFixPreview(
          this.changeNum,
          this.patchNum,
          fixSuggestion.fix_id
        );
      }
      if (res) {
        this.currentPreviews = Object.keys(res).map(key => {
          return {filepath: key, preview: res![key]};
        });
      }
    } catch (e) {
      this.close(false);
      throw e;
    }
    return res;
  }

  private overridePartialDiffPrefs() {
    if (!this.diffPrefs) return undefined;
    // generate a smaller gr-diff than fullscreen for dialog
    return {
      ...this.diffPrefs,
      line_length: Math.min(this.diffPrefs.line_length, 100),
    };
  }

  // visible for testing
  onCancel(e: Event) {
    if (e) e.stopPropagation();
    this.close(false);
  }

  // visible for testing
  onPrevFixClick(e: Event) {
    if (e) e.stopPropagation();
    if (this.selectedFixIdx >= 1 && this.fixSuggestions) {
      this.selectedFixIdx -= 1;
      this.showSelectedFixSuggestion(this.fixSuggestions[this.selectedFixIdx]);
    }
  }

  // visible for testing
  onNextFixClick(e: Event) {
    if (e) e.stopPropagation();
    if (
      this.fixSuggestions &&
      this.selectedFixIdx < this.fixSuggestions.length
    ) {
      this.selectedFixIdx += 1;
      this.showSelectedFixSuggestion(this.fixSuggestions[this.selectedFixIdx]);
    }
  }

  private close(fixApplied: boolean) {
    this.currentFix = undefined;
    this.currentPreviews = [];
    this.isApplyFixLoading = false;

    this.onCloseFixPreviewCallbacks.forEach(fn => fn(fixApplied));
    this.applyFixModal?.close();
    if (fixApplied) fireReload(this);
  }

  private computeTooltip() {
    if (!this.change || !this.patchNum) return '';
    if (this.isChangeMerged) return 'Change is already merged';
    if (this.isApplyFixLoading) return 'Fix is still loading ...';
    return '';
  }

  private computeDisableApplyFixButton() {
    if (!this.change || !this.patchNum) return true;
    if (this.isChangeMerged) return true;
    return this.isApplyFixLoading;
  }

  // visible for testing
  async handleApplyFix(e: Event) {
    if (e) e.stopPropagation();

    const {changeNum, patchNum, change} = this;
    if (!changeNum || !patchNum || !change || !this.currentFix) {
      throw new Error('Not all required properties are set.');
    }
    this.isApplyFixLoading = true;
    this.reporting.time(Timing.APPLY_FIX_LOAD);
    let res;
    if (this.fixSuggestions?.[0].fix_id === PROVIDED_FIX_ID) {
      res = await this.restApiService.applyFixSuggestion(
        changeNum,
        patchNum,
        this.fixSuggestions[0].replacements,
        this.latestPatchNum
      );
    } else {
      res = await this.restApiService.applyRobotFixSuggestion(
        changeNum,
        patchNum,
        this.currentFix.fix_id
      );
    }
    if (res?.ok) {
      this.getNavigation().setUrl(
        createChangeUrl({
          change,
          patchNum: EDIT,
          basePatchNum: patchNum as BasePatchSetNum,
        })
      );
      this.close(true);
    }
    this.isApplyFixLoading = false;
    this.reporting.timeEnd(Timing.APPLY_FIX_LOAD, {
      method: 'apply-fix-dialog',
      description: this.fixSuggestions?.[0].description,
      fileExtension: getFileExtension(
        this.fixSuggestions?.[0].replacements?.[0].path ?? ''
      ),
      success: res.ok,
      status: res.status,
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-apply-fix-dialog': GrApplyFixDialog;
  }
}
