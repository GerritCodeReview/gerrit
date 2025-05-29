/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../embed/diff/gr-diff/gr-diff';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {getAppContext} from '../../../services/app-context';
import {
  BasePatchSetNum,
  EDIT,
  PatchSetNumber,
  RepoName,
} from '../../../types/common';
import {
  DiffLayer,
  DiffPreferencesInfo,
  DiffViewMode,
  RenderPreferences,
} from '../../../api/diff';
import {when} from 'lit/directives/when.js';
import {GrSyntaxLayerWorker} from '../../../embed/diff/gr-syntax-layer/gr-syntax-layer-worker';
import {resolve} from '../../../models/dependency';
import {highlightServiceToken} from '../../../services/highlight/highlight-service';
import {FixSuggestionInfo, NumericChangeId} from '../../../api/rest-api';
import {changeModelToken} from '../../../models/change/change-model';
import {subscribe} from '../../lit/subscription-controller';
import {DiffPreview} from '../../diff/gr-apply-fix-dialog/gr-apply-fix-dialog';
import {userModelToken} from '../../../models/user/user-model';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {fire, fireError} from '../../../utils/event-util';
import {Timing} from '../../../constants/reporting';
import {createChangeUrl} from '../../../models/views/change';
import {getFileExtension} from '../../../utils/file-util';
import {throwingErrorCallback} from '../gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {ReportSource} from '../../../services/suggestions/suggestions-service';

export interface PreviewLoadedDetail {
  previewLoadedFor?: FixSuggestionInfo;
}
/**
 * Diff preview for
 * 1. code block suggestion vs commented Text
 * or 2. fixSuggestionInfo that are attached to a comment.
 *
 * It shouldn't be created with both 1. and 2. but if it is
 * it shows just for 1. (code block suggestion)
 */
@customElement('gr-suggestion-diff-preview')
export class GrSuggestionDiffPreview extends LitElement {
  // Optional. Used as backup when preview is not loaded.
  @property({type: String})
  codeText?: string;

  // Required.
  @property({type: Object})
  fixSuggestionInfo?: FixSuggestionInfo;

  // Used to determine if the preview has been loaded
  // this is identical to previewLoadedFor !== undefined and can be removed
  @property({type: Boolean, attribute: 'previewed', reflect: true})
  previewed = false;

  // Optional. Used in logging.
  @property({type: String})
  uuid?: string;

  @property({type: Number})
  patchSet?: BasePatchSetNum;

  // Optional. Used in logging.
  @property({type: String})
  commentId?: string;

  @state()
  layers: DiffLayer[] = [];

  /**
   * The fix suggestion info that the preview is loaded for.
   *
   * This is used to determine if the preview has been loaded for the same
   * fix suggestion info currently in gr-comment.
   */
  @state()
  public previewLoadedFor?: FixSuggestionInfo;

  @state() repo?: RepoName;

  @state() hasEdit = false;

  @state()
  changeNum?: NumericChangeId;

  @state()
  preview?: DiffPreview;

  @state()
  diffPrefs?: DiffPreferencesInfo;

  @state() latestPatchNum?: PatchSetNumber;

  @state()
  renderPrefs: RenderPreferences = {
    disable_context_control_buttons: true,
    show_file_comment_button: false,
    hide_line_length_indicator: true,
  };

  private readonly reporting = getAppContext().reportingService;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly restApiService = getAppContext().restApiService;

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
      () => this.getChangeModel().revisions$,
      revisions =>
        (this.hasEdit = Object.values(revisions).some(
          info => info._number === EDIT
        ))
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
        .buttons {
          text-align: right;
        }
        .diff-container {
          border: 1px solid var(--border-color);
          border-top: none;
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
    if (
      changed.has('fixSuggestionInfo') ||
      changed.has('changeNum') ||
      changed.has('patchSet')
    ) {
      this.fetchFixPreview();
    }
  }

  override render() {
    if (!this.fixSuggestionInfo) return nothing;
    return html`
      ${when(
        this.previewLoadedFor,
        () => this.renderDiff(),
        () => html`<code>${this.codeText}</code>`
      )}
    `;
  }

  private renderDiff() {
    if (!this.preview) return;
    const diff = this.preview.preview;
    this.syntaxLayer.process(diff);
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

    this.reporting.time(Timing.PREVIEW_FIX_LOAD);
    const res = await this.restApiService.getFixPreview(
      this.changeNum,
      this.patchSet,
      this.fixSuggestionInfo.replacements
    );
    if (!res) return;
    const currentPreviews = Object.keys(res).map(key => {
      return {filepath: key, preview: res[key]};
    });
    this.reporting.timeEnd(Timing.PREVIEW_FIX_LOAD, {
      uuid: this.uuid,
      commentId: this.commentId ?? '',
    });
    if (currentPreviews.length > 0) {
      this.preview = currentPreviews[0];
      this.previewLoadedFor = this.fixSuggestionInfo;
      this.previewed = true;

      fire(this, 'preview-loaded', {
        previewLoadedFor: this.fixSuggestionInfo,
      });
    }

    return res;
  }
  /**
   * Applies a fix (codeblock in comment message) previewed in
   * `suggestion-diff-preview`, navigating to the new change URL with the EDIT
   * patchset.
   *
   * Similar code flow is in gr-apply-fix-dialog.handleApplyFix
   * Used in gr-user-suggestion-fix
   */

  public async applyFix() {
    const changeNum = this.changeNum;
    const basePatchNum = this.patchSet;
    const fixSuggestion = this.fixSuggestionInfo;
    if (!changeNum || !basePatchNum || !fixSuggestion) return;

    this.reporting.time(Timing.APPLY_FIX_LOAD);
    let res: Response | undefined = undefined;
    let errorText = '';
    let status = '';
    try {
      res = await this.restApiService.applyFixSuggestion(
        changeNum,
        basePatchNum,
        fixSuggestion.replacements,
        this.latestPatchNum,
        throwingErrorCallback
      );
    } catch (error) {
      if (error instanceof Error) {
        errorText = error.message;
        status = errorText.match(/\b\d{3}\b/)?.[0] || '';
      }
      fireError(this, `Applying Fix failed.\n${errorText}`);
    } finally {
      this.reporting.timeEnd(Timing.APPLY_FIX_LOAD, {
        method: '1-click',
        description: fixSuggestion.description,
        fileExtension: getFileExtension(
          fixSuggestion?.replacements?.[0].path ?? ''
        ),
        commentId: this.commentId ?? '',
        success: res?.ok ?? false,
        status: res?.status ?? status,
        errorText,
      });
    }
    if (res?.ok) {
      this.getNavigation().setUrl(
        createChangeUrl({
          changeNum,
          repo: this.repo!,
          patchNum: EDIT,
          basePatchNum,
          forceReload: !this.hasEdit,
        })
      );
      fire(this, 'reload-diff', {path: fixSuggestion.replacements[0].path});
      fire(this, 'apply-user-suggestion', {
        fixSuggestion: fixSuggestion.description.includes(
          ReportSource.GET_AI_FIX_FOR_COMMENT
        )
          ? fixSuggestion
          : undefined,
      });
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
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-suggestion-diff-preview': GrSuggestionDiffPreview;
  }
  interface HTMLElementEventMap {
    'preview-loaded': CustomEvent<PreviewLoadedDetail>;
  }
}
