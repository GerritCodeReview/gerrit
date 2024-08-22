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
import {Timing} from '../../constants/reporting';
import {
  DiffInfo,
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
import {DiffPreview} from '../diff/gr-apply-fix-dialog/gr-apply-fix-dialog';
import {userModelToken} from '../../models/user/user-model';
import {navigationToken} from '../core/gr-navigation/gr-navigation';
import {fire} from '../../utils/event-util';
import {createChangeUrl} from '../../models/views/change';
import {OpenFixPreviewEventDetail} from '../../types/events';

/**
 * This component renders a <gr-diff> and an "apply fix" button and can be used
 * when showing check results that have a fix for an easy preview and a quick
 * apply-fix experience.
 *
 * There is a certain overlap with similar components for comment fixes:
 * GrSuggestionDiffPreview also renders a <gr-diff> and fetches a diff preview,
 * it relies on a `comment` (and the comment model) to be available. It supports
 * both a `string` fix and `FixSuggestionInfo`. It also differs in logging and
 * event handling. And it misses the header component that we need for the
 * buttons.
 *
 * There is also `GrUserSuggestionsFix` which wraps `GrSuggestionDiffPreview`
 * and has the header that we also need. But it is very targeted to be used for
 * user suggestions and inside comments.
 *
 * So there is certainly an opportunity for cleanup and unification, but at the
 * time of component creation it did not feel wortwhile investing into this
 * effort. This is tracked in b/360288262.
 */
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
  diff?: DiffPreview;

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

  private readonly reporting = getAppContext().reportingService;

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
        .diff-container {
          border: 1px solid var(--border-color);
          border-top: none;
          border-bottom: none;
        }
        .loading {
          border: 1px solid var(--border-color);
          padding: var(--spacing-xl);
        }
      `,
    ];
  }

  override willUpdate(changed: PropertyValues) {
    if (changed.has('fixSuggestionInfo')) {
      this.fetchDiffPreview().then(diff => (this.diff = diff));
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
            class="showFix"
            secondary
            flatten
            .disabled=${!this.diff}
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
    if (!this.diff) {
      return html`<div class="loading">Loading fix preview ...</div>`;
    }
    const diff = this.diff.preview;
    if (!anyLineTooLong(diff)) {
      this.syntaxLayer.process(diff);
    }
    return html`
      <div class="diff-container">
        <gr-diff
          .prefs=${this.getDiffPrefs()}
          .path=${this.diff.filepath}
          .diff=${diff}
          .layers=${this.layers}
          .renderPrefs=${this.renderPrefs}
          .viewMode=${DiffViewMode.UNIFIED}
        ></gr-diff>
      </div>
    `;
  }

  /**
   * Calls the REST API to convert the fix into a DiffInfo.
   */
  private async fetchDiffPreview(): Promise<DiffPreview | undefined> {
    if (!this.changeNum || !this.patchSet || !this.fixSuggestionInfo) return;
    const pathsToDiffs: {[path: string]: DiffInfo} | undefined =
      await this.restApiService.getFixPreview(
        this.changeNum,
        this.patchSet,
        this.fixSuggestionInfo.replacements
      );

    if (!pathsToDiffs) return;
    const diffs = Object.keys(pathsToDiffs).map(filepath => {
      const diff = pathsToDiffs[filepath];
      return {filepath, preview: diff};
    });
    // Showing diff for one file only.
    return diffs?.[0];
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
    this.reporting.time(Timing.APPLY_FIX_LOAD);
    const res = await this.restApiService.applyFixSuggestion(
      changeNum,
      basePatchNum,
      this.fixSuggestionInfo.replacements
    );
    this.applyingFix = false;
    this.reporting.timeEnd(Timing.APPLY_FIX_LOAD, {
      method: '1-click',
      description: this.fixSuggestionInfo.description,
    });
    if (res?.ok) this.navigateToEditPatchset();
  }

  private navigateToEditPatchset() {
    const changeNum = this.changeNum;
    const repo = this.repo;
    const basePatchNum = this.patchSet;
    if (!changeNum || !repo || !basePatchNum) return;

    const url = createChangeUrl({
      changeNum,
      repo,
      patchNum: EDIT,
      basePatchNum,
      // We have to force reload, because the EDIT patchset is otherwise not yet known.
      forceReload: true,
    });
    this.getNavigation().setUrl(url);
  }

  /**
   * We have to override some diff prefs of the user, because for example in the context of showing
   * an inline diff for fixes we do not want to show context lines around the changes lines of code
   * as we would normally do for a diff.
   */
  private getDiffPrefs() {
    if (!this.diffPrefs) return undefined;
    return {
      ...this.diffPrefs,
      context: 0,
      line_length: Math.min(this.diffPrefs.line_length, 100),
      line_wrapping: true,
    };
  }

  private isApplyEditDisabled() {
    if (!this.diff || this.patchSet === undefined) return true;
    return this.patchSet !== this.latestPatchNum;
  }

  private computeApplyFixTooltip() {
    if (this.patchSet === undefined) return '';
    if (!this.diff) return 'Fix is still loading ...';
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
