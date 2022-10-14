/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/shared-styles';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-overlay/gr-overlay';
import '../../../embed/diff/gr-diff/gr-diff';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {
  NumericChangeId,
  EDIT,
  FixSuggestionInfo,
  PatchSetNum,
  BasePatchSetNum,
  FilePathToDiffInfoMap,
} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {PROVIDED_FIX_ID} from '../../../utils/comment-util';
import {OpenFixPreviewEvent} from '../../../types/events';
import {getAppContext} from '../../../services/app-context';
import {fireCloseFixPreview} from '../../../utils/event-util';
import {DiffLayer, ParsedChangeInfo} from '../../../types/types';
import {GrButton} from '../../shared/gr-button/gr-button';
import {TokenHighlightLayer} from '../../../embed/diff/gr-diff-builder/token-highlight-layer';
import {css, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {subscribe} from '../../lit/subscription-controller';
import {assert} from '../../../utils/common-util';
import {resolve} from '../../../models/dependency';
import {createChangeUrl} from '../../../models/views/change';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';

interface FilePreview {
  filepath: string;
  preview: DiffInfo;
}

@customElement('gr-apply-fix-dialog')
export class GrApplyFixDialog extends LitElement {
  @query('#applyFixOverlay')
  applyFixOverlay?: GrOverlay;

  @query('#applyFixDialog')
  applyFixDialog?: GrDialog;

  /** The currently observed dialog by `dialogOberserver`. */
  observedDialog?: GrDialog;

  /** The current observer observing the `observedDialog`. */
  dialogObserver?: ResizeObserver;

  @query('#nextFix')
  nextFix?: GrButton;

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: Number})
  changeNum?: NumericChangeId;

  @state()
  patchNum?: PatchSetNum;

  @state()
  currentFix?: FixSuggestionInfo;

  @state()
  currentPreviews: FilePreview[] = [];

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

  private readonly restApiService = getAppContext().restApiService;

  private readonly userModel = getAppContext().userModel;

  private readonly getNavigation = resolve(this, navigationToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.userModel.preferences$,
      preferences => {
        if (!preferences?.disable_token_highlighting) {
          this.layers = [new TokenHighlightLayer(this)];
        }
      }
    );
    subscribe(
      this,
      () => this.userModel.diffPreferences$,
      diffPreferences => {
        if (!diffPreferences) return;
        this.diffPrefs = diffPreferences;
      }
    );
  }

  static override styles = [
    sharedStyles,
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
    `,
  ];

  override render() {
    return html`
      <gr-overlay id="applyFixOverlay" with-backdrop="">
        <gr-dialog
          id="applyFixDialog"
          .confirmLabel=${this.isApplyFixLoading ? 'Saving...' : 'Apply Fix'}
          .confirmTooltip=${this.computeTooltip()}
          ?disabled=${this.computeDisableApplyFixButton()}
          @confirm=${this.handleApplyFix}
          @cancel=${this.onCancel}
        >
          ${this.renderHeader()} ${this.renderMain()} ${this.renderFooter()}
        </gr-dialog>
      </gr-overlay>
    `;
  }

  override updated() {
    this.updateDialogObserver();
  }

  override disconnectedCallback() {
    this.removeDialogObserver();
    super.disconnectedCallback();
  }

  private removeDialogObserver() {
    this.dialogObserver?.disconnect();
    this.dialogObserver = undefined;
    this.observedDialog = undefined;
  }

  private updateDialogObserver() {
    if (
      this.applyFixDialog === this.observedDialog &&
      this.dialogObserver !== undefined
    ) {
      return;
    }

    this.removeDialogObserver();
    if (!this.applyFixDialog) return;

    this.observedDialog = this.applyFixDialog;
    this.dialogObserver = new ResizeObserver(() => {
      this.applyFixOverlay?.refit();
    });
    this.dialogObserver.observe(this.observedDialog);
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
        <div class="diffContainer">
          <gr-diff
            .prefs=${this.overridePartialDiffPrefs()}
            .path=${item.filepath}
            .diff=${item.preview}
            .layers=${this.layers}
          ></gr-diff>
        </div>
      `
    );
    return html`<div slot="main">${items}</div>`;
  }

  private renderFooter() {
    const id = this.selectedFixIdx;
    const fixCount = this.fixSuggestions?.length ?? 0;
    if (fixCount < 2) return;
    return html`
      <div slot="footer" class="fix-picker">
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
      </div>
    `;
  }

  /**
   * Given event with fixSuggestions, fetch diffs associated with first
   * suggested fix and open dialog.
   */
  open(e: OpenFixPreviewEvent) {
    this.patchNum = e.detail.patchNum;
    this.fixSuggestions = e.detail.fixSuggestions;
    assert(this.fixSuggestions.length > 0, 'no fix in the event');
    this.selectedFixIdx = 0;
    const promises = [];
    promises.push(
      this.showSelectedFixSuggestion(this.fixSuggestions[0]),
      this.applyFixOverlay?.open()
    );
  }

  private async showSelectedFixSuggestion(fixSuggestion: FixSuggestionInfo) {
    this.currentFix = fixSuggestion;
    await this.fetchFixPreview(fixSuggestion);
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

    fireCloseFixPreview(this, fixApplied);
    this.applyFixOverlay?.close();
  }

  private computeTooltip() {
    if (!this.change || !this.patchNum) return '';
    const latestPatchNum =
      this.change.revisions[this.change.current_revision]._number;
    return latestPatchNum !== this.patchNum
      ? 'Fix can only be applied to the latest patchset'
      : '';
  }

  private computeDisableApplyFixButton() {
    if (!this.change || !this.patchNum) return true;
    const latestPatchNum =
      this.change.revisions[this.change.current_revision]._number;
    return this.patchNum !== latestPatchNum || this.isApplyFixLoading;
  }

  // visible for testing
  async handleApplyFix(e: Event) {
    if (e) e.stopPropagation();

    const changeNum = this.changeNum;
    const patchNum = this.patchNum;
    const change = this.change;
    if (!changeNum || !patchNum || !change || !this.currentFix) {
      throw new Error('Not all required properties are set.');
    }
    this.isApplyFixLoading = true;
    let res;
    if (this.fixSuggestions?.[0].fix_id === PROVIDED_FIX_ID) {
      res = await this.restApiService.applyFixSuggestion(
        changeNum,
        patchNum,
        this.fixSuggestions[0].replacements
      );
    } else {
      res = await this.restApiService.applyRobotFixSuggestion(
        changeNum,
        patchNum,
        this.currentFix.fix_id
      );
    }
    if (res && res.ok) {
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
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-apply-fix-dialog': GrApplyFixDialog;
  }
}
