/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../embed/diff/gr-diff/gr-diff';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {getAppContext} from '../../../services/app-context';
import {Comment, EDIT, BasePatchSetNum, RepoName} from '../../../types/common';
import {anyLineTooLong} from '../../../utils/diff-util';
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
import {NumericChangeId} from '../../../api/rest-api';
import {changeModelToken} from '../../../models/change/change-model';
import {subscribe} from '../../lit/subscription-controller';
import {FilePreview} from '../../diff/gr-apply-fix-dialog/gr-apply-fix-dialog';
import {userModelToken} from '../../../models/user/user-model';
import {createUserFixSuggestion} from '../../../utils/comment-util';
import {commentModelToken} from '../gr-comment-model/gr-comment-model';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {fire} from '../../../utils/event-util';
import {Interaction, Timing} from '../../../constants/reporting';
import {createChangeUrl} from '../../../models/views/change';

declare global {
  interface HTMLElementEventMap {
    'add-generated-suggestion': AddGeneratedSuggestionEvent;
  }
}

export type AddGeneratedSuggestionEvent =
  CustomEvent<OpenUserSuggestionPreviewEventDetail>;
export interface OpenUserSuggestionPreviewEventDetail {
  code: string;
}

@customElement('gr-suggestion-diff-preview')
export class GrSuggestionDiffPreview extends LitElement {
  @property({type: String})
  suggestion?: string;

  @property({type: Boolean})
  showAddSuggestionButton = false;

  @property({type: String})
  uuid?: string;

  @state()
  comment?: Comment;

  @state()
  commentedText?: string;

  @state()
  layers: DiffLayer[] = [];

  @state()
  previewLoadedFor?: string;

  @state() repo?: RepoName;

  @state()
  changeNum?: NumericChangeId;

  @state()
  preview?: FilePreview;

  @state()
  diffPrefs?: DiffPreferencesInfo;

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

  private readonly getCommentModel = resolve(this, commentModelToken);

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
      () => this.getUserModel().diffPreferences$,
      diffPreferences => {
        if (!diffPreferences) return;
        this.diffPrefs = diffPreferences;
        this.syntaxLayer.setEnabled(!!this.diffPrefs.syntax_highlighting);
      }
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
    subscribe(
      this,
      () => this.getChangeModel().repo$,
      x => (this.repo = x)
    );
  }

  static override get styles() {
    return [
      css`
        .buttons {
          text-align: right;
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
    if (changed.has('commentedText') || changed.has('comment')) {
      if (this.previewLoadedFor !== this.suggestion) {
        this.fetchFixPreview();
      }
    }
  }

  override render() {
    if (!this.suggestion) return nothing;
    const code = this.suggestion;
    return html`
      ${when(
        this.previewLoadedFor,
        () => this.renderDiff(),
        () => html`<code>${code}</code>`
      )}
      ${when(
        this.showAddSuggestionButton,
        () =>
          html`<div class="buttons">
            <gr-button
              link
              class="action add-suggestion"
              @click=${this.handleAddGeneratedSuggestion}
            >
              Add suggestion to comment
            </gr-button>
          </div>`
      )}
    `;
  }

  private renderDiff() {
    if (!this.preview) return;
    const diff = this.preview.preview;
    if (!anyLineTooLong(diff)) {
      this.syntaxLayer.process(diff);
    }
    return html`<gr-diff
      .prefs=${this.overridePartialDiffPrefs()}
      .path=${this.preview.filepath}
      .diff=${diff}
      .layers=${this.layers}
      .renderPrefs=${this.renderPrefs}
      .viewMode=${DiffViewMode.UNIFIED}
    ></gr-diff>`;
  }

  private async fetchFixPreview() {
    if (
      !this.changeNum ||
      !this.comment?.patch_set ||
      !this.suggestion ||
      !this.commentedText
    )
      return;
    const fixSuggestions = createUserFixSuggestion(
      this.comment,
      this.commentedText,
      this.suggestion
    );
    this.reporting.time(Timing.PREVIEW_FIX_LOAD);
    const res = await this.restApiService.getFixPreview(
      this.changeNum,
      this.comment?.patch_set,
      fixSuggestions[0].replacements
    );
    if (!res) return;
    const currentPreviews = Object.keys(res).map(key => {
      return {filepath: key, preview: res[key]};
    });
    this.reporting.timeEnd(Timing.PREVIEW_FIX_LOAD, {
      uuid: this.uuid,
    });
    if (currentPreviews.length > 0) {
      this.preview = currentPreviews[0];
      this.previewLoadedFor = this.suggestion;
    }

    return res;
  }

  // Used in gr-user-suggestion-fix
  public async applyFix() {
    if (
      !this.changeNum ||
      !this.comment?.patch_set ||
      !this.suggestion ||
      !this.commentedText
    )
      return;
    const changeNum = this.changeNum;
    const basePatchNum = this.comment?.patch_set as BasePatchSetNum;
    const fixSuggestions = createUserFixSuggestion(
      this.comment,
      this.commentedText,
      this.suggestion
    );
    this.reporting.time(Timing.APPLY_FIX_LOAD);
    const res = await this.restApiService.applyFixSuggestion(
      this.changeNum,
      this.comment?.patch_set,
      fixSuggestions[0].replacements
    );
    this.reporting.timeEnd(Timing.APPLY_FIX_LOAD);
    if (res?.ok) {
      this.getNavigation().setUrl(
        createChangeUrl({
          changeNum,
          repo: this.repo!,
          patchNum: EDIT,
          basePatchNum,
        })
      );
      fire(this, 'apply-user-suggestion', {});
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

  handleAddGeneratedSuggestion() {
    if (!this.suggestion) return;
    this.reporting.reportInteraction(Interaction.GENERATE_SUGGESTION_ADDED, {
      uuid: this.uuid,
    });
    fire(this, 'add-generated-suggestion', {code: this.suggestion});
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-suggestion-diff-preview': GrSuggestionDiffPreview;
  }
}
