/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {getAppContext} from '../../../services/app-context';
import {KnownExperimentId} from '../../../services/flags/flags';
import {fire} from '../../../utils/event-util';
import {anyLineTooLong} from '../../../utils/diff-util';
import {DiffLayer, DiffPreferencesInfo, DiffViewMode, RenderPreferences} from '../../../api/diff';
import {when} from 'lit/directives/when.js';
import {GrSyntaxLayerWorker} from '../../../embed/diff/gr-syntax-layer/gr-syntax-layer-worker';
import {resolve} from '../../../models/dependency';
import {highlightServiceToken} from '../../../services/highlight/highlight-service';
import {NumericChangeId, PatchSetNum} from '../../../api/rest-api';
import {changeModelToken} from '../../../models/change/change-model';
import {subscribe} from '../../lit/subscription-controller';
import {FilePreview} from '../../diff/gr-apply-fix-dialog/gr-apply-fix-dialog';
import {userModelToken} from '../../../models/user/user-model';

declare global {
  interface HTMLElementEventMap {
    'open-user-suggest-preview': OpenUserSuggestionPreviewEvent;
  }
}

export type OpenUserSuggestionPreviewEvent =
  CustomEvent<OpenUserSuggestionPreviewEventDetail>;
export interface OpenUserSuggestionPreviewEventDetail {
  code: string;
}

@customElement('gr-user-suggestion-fix')
export class GrUserSuggetionFix extends LitElement {
  @state()
  layers: DiffLayer[] = [];

  @state()
  previewLoaded = false;

  @state()
  changeNum?: NumericChangeId;

  // TODO: get patchNum
  @state()
  patchNum: PatchSetNum = 3 as PatchSetNum;

  @state()
  preview?: FilePreview;

  @state()
  diffPrefs?: DiffPreferencesInfo;

  @state()
  renderPrefs: RenderPreferences = {
    // hide_left_side: true,
    disable_context_control_buttons: true,
    show_file_comment_button: false,
    hide_line_length_indicator: true,
    view_mode: DiffViewMode.UNIFIED,
  };

  private readonly flagsService = getAppContext().flagsService;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly restApiService = getAppContext().restApiService;

  private readonly getUserModel = resolve(this, userModelToken);

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
  }

  static override styles = [
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

  override render() {
    if (!this.previewLoaded) {
      this.fetchFixPreview();
    }
    if (!this.flagsService.isEnabled(KnownExperimentId.SUGGEST_EDIT)) {
      return nothing;
    }
    if (!this.textContent) return nothing;
    const code = this.textContent;
    return html`<div class="header">
        <div class="title">
          <span>Suggested edit</span>
          <a
            href="https://gerrit-review.googlesource.com/Documentation/user-suggest-edits.html"
            target="_blank"
            ><gr-icon icon="help" title="read documentation"></gr-icon
          ></a>
        </div>
        <div class="copyButton">
          <gr-copy-clipboard
            hideInput=""
            text=${code}
            copyTargetName="Suggested edit"
          ></gr-copy-clipboard>
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
        </div>
      </div>
      ${when(
        this.previewLoaded,
        () => this.renderDiff(),
        () => html`<code>${code}</code>`
      )} `;
  }

  handleShowFix() {
    if (!this.textContent) return;
    fire(this, 'open-user-suggest-preview', {code: this.textContent});
  }

  private renderDiff() {
    const diff = this.preview!.preview;
    if (!anyLineTooLong(diff)) {
      this.syntaxLayer.process(diff);
    }
    return html`<gr-diff
      .prefs=${this.overridePartialDiffPrefs()}
      .path=${this.preview!.filepath}
      .diff=${diff}
      .layers=${this.layers}
      .renderPrefs=${this.renderPrefs}
      .viewMode=${DiffViewMode.UNIFIED}
    ></gr-diff>`;
  }

  private async fetchFixPreview() {
    if (!this.changeNum || !this.patchNum) return;
    const res = await this.restApiService.getFixPreview(
      this.changeNum,
      this.patchNum,
      // TODO: get replacements
      // fixSuggestion.replacements
      [
        {
          path: 'polygerrit-ui/app/elements/shared/gr-comment-thread/gr-comment-thread.ts',
          range: {
            start_line: 508,
            start_character: 0,
            end_line: 508,
            end_character: 53,
          },
          replacement:
            '    return html`${publishedComments}${draftComment}${test}`;',
        },
      ]
    );
    if (res) {
      const currentPreviews = Object.keys(res).map(key => {
        return {filepath: key, preview: res[key]};
      });
      if (currentPreviews.length > 0) {
        this.preview = currentPreviews[0];
        this.previewLoaded = true;
      }
    }

    return res;
  }

  private overridePartialDiffPrefs() {
    if (!this.diffPrefs) return undefined;
    // generate a smaller gr-diff than fullscreen for dialog
    return {
      ...this.diffPrefs,
      context: 2,
      line_length: Math.min(this.diffPrefs.line_length, 100),
    };
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-user-suggestion-fix': GrUserSuggetionFix;
  }
}
