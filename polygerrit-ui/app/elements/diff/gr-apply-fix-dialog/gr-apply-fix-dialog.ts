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
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  NumericChangeId,
  EDIT,
  FixSuggestionInfo,
  PatchSetNum,
  RobotId,
  BasePatchSetNum,
  FilePathToDiffInfoMap,
} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {
  createUserFixSuggestion,
  getContentInCommentRange,
  getUserSuggestion,
  hasUserSuggestion,
  isRobot,
  USER_SUGGEST_EDIT_FIX_ID,
} from '../../../utils/comment-util';
import {OpenFixPreviewEvent} from '../../../types/events';
import {getAppContext} from '../../../services/app-context';
import {fireCloseFixPreview, fireEvent} from '../../../utils/event-util';
import {DiffLayer, ParsedChangeInfo} from '../../../types/types';
import {GrButton} from '../../shared/gr-button/gr-button';
import {TokenHighlightLayer} from '../../../embed/diff/gr-diff-builder/token-highlight-layer';
import {css, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {subscribe} from '../../lit/subscription-controller';
import {isBase64FileContent} from '../../../api/rest-api';
import {assertIsDefined} from '../../../utils/common-util';

interface FilePreview {
  filepath: string;
  preview: DiffInfo;
}

@customElement('gr-apply-fix-dialog')
export class GrApplyFixDialog extends LitElement {
  @query('#applyFixOverlay')
  applyFixOverlay?: GrOverlay;

  @query('#nextFix')
  nextFix?: GrButton;

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: Number})
  changeNum?: NumericChangeId;

  @state()
  patchNum?: PatchSetNum;

  @state()
  robotId?: RobotId;

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
    this.addEventListener('diff-context-expanded', () => {
      if (this.applyFixOverlay) fireEvent(this.applyFixOverlay, 'iron-resize');
    });
  }

  static override styles = [
    sharedStyles,
    css`
      gr-diff {
        --content-width: 90vw;
      }
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

  private renderHeader() {
    return html`
      <div slot="header">
        ${this.robotId ?? ''} - ${this.currentFix?.description ?? ''}
      </div>
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
   * Given robot comment CustomEvent object, fetch diffs associated
   * with first robot comment suggested fix and open dialog.
   *
   * @param e to be passed from gr-comment with robot comment detail.
   * @return Promise that resolves either when all
   * preview diffs are fetched or no fix suggestions in custom event detail.
   */
  async open(e: OpenFixPreviewEvent) {
    const detail = e.detail;
    const comment = detail.comment;
    if (comment && hasUserSuggestion(comment)) {
      const replacement = getUserSuggestion(comment);
      if (!replacement) throw new Error('User suggestion is malformatted');
      // TODO(milutin): This should belongs into service/model.
      const file = await this.restApiService.getFileContent(
        this.changeNum!,
        comment.path!,
        comment.patch_set!
      );
      assertIsDefined(file, 'file');
      if (!isBase64FileContent(file))
        throw new Error('Cannot retrieve file content');
      assertIsDefined(file.content, 'content');
      const line = getContentInCommentRange(file.content, comment);
      this.fixSuggestions = createUserFixSuggestion(comment, line, replacement);
    } else {
      if (!detail.patchNum || !comment || !isRobot(comment)) {
        return Promise.resolve();
      }
      this.fixSuggestions = comment.fix_suggestions;
      this.robotId = comment.robot_id;
    }
    this.patchNum = detail.patchNum;
    if (!this.fixSuggestions || !this.fixSuggestions.length) {
      return Promise.resolve();
    }
    this.selectedFixIdx = 0;
    const promises = [];
    promises.push(
      this.showSelectedFixSuggestion(this.fixSuggestions[0]),
      this.applyFixOverlay?.open()
    );
    return Promise.all(promises).then(() => {
      if (this.applyFixOverlay) fireEvent(this.applyFixOverlay, 'iron-resize');
    });
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
    if (fixSuggestion.fix_id === USER_SUGGEST_EDIT_FIX_ID) {
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
    } else {
      this.close(false);
    }
    return;
  }

  private overridePartialDiffPrefs() {
    if (!this.diffPrefs) return undefined;
    // generate a smaller gr-diff than fullscreen for dialog
    return {...this.diffPrefs, line_length: 50};
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
    if (this.fixSuggestions![0].fix_id === 'user_suggestion') {
      res = await this.restApiService.applyFixSuggestion(
        changeNum,
        patchNum,
        this.fixSuggestions![0].replacements
      );
    } else {
      res = await this.restApiService.applyRobotFixSuggestion(
        changeNum,
        patchNum,
        this.currentFix.fix_id
      );
    }
    if (res && res.ok) {
      GerritNav.navigateToChange(change, {
        patchNum: EDIT,
        basePatchNum: patchNum as BasePatchSetNum,
      });
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
