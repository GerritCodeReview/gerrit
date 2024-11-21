/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '../gr-suggestion-diff-preview/gr-suggestion-diff-preview';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, state, query, property} from 'lit/decorators.js';
import {fire} from '../../../utils/event-util';
import {getDocUrl} from '../../../utils/url-util';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {GrSuggestionDiffPreview} from '../gr-suggestion-diff-preview/gr-suggestion-diff-preview';
import {changeModelToken} from '../../../models/change/change-model';
import {Comment, PatchSetNumber} from '../../../types/common';
import {OpenFixPreviewEventDetail} from '../../../types/events';
import {pluginLoaderToken} from '../gr-js-api-interface/gr-plugin-loader';
import {SuggestionsProvider} from '../../../api/suggestions';
import {PROVIDED_FIX_ID} from '../../../utils/comment-util';
import {when} from 'lit/directives/when.js';
import {storageServiceToken} from '../../../services/storage/gr-storage_impl';
import {getAppContext} from '../../../services/app-context';
import {Interaction} from '../../../constants/reporting';
import {ChangeStatus} from '../../../api/rest-api';

export const COLLAPSE_SUGGESTION_STORAGE_KEY = 'collapseSuggestionStorageKey';

/**
 * gr-fix-suggestions is UI for comment.fix_suggestions.
 * gr-fix-suggestions is wrapper for gr-suggestion-diff-preview with buttons
 * to preview and apply fix and for giving a context about suggestion.
 */
@customElement('gr-fix-suggestions')
export class GrFixSuggestions extends LitElement {
  @query('gr-suggestion-diff-preview')
  suggestionDiffPreview?: GrSuggestionDiffPreview;

  @property({type: Object})
  comment?: Comment;

  @state() private docsBaseUrl = '';

  @state() private applyingFix = false;

  @state() latestPatchNum?: PatchSetNumber;

  @state()
  suggestionsProvider?: SuggestionsProvider;

  @state() private isOwner = false;

  /**
   * This is just a reflected property such that css rules can be based on it.
   */
  @property({type: Boolean, reflect: true})
  collapsed = false;

  @state() isChangeMerged = false;

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

  private readonly getStorage = resolve(this, storageServiceToken);

  private readonly reporting = getAppContext().reportingService;

  @state() private previewLoaded = false;

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
      () => this.getChangeModel().isOwner$,
      x => (this.isOwner = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().status$,
      status => (this.isChangeMerged = status === ChangeStatus.MERGED)
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    this.getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        const suggestionsPlugins =
          this.getPluginLoader().pluginsModel.getState().suggestionsPlugins;
        // We currently support results from only 1 provider.
        this.suggestionsProvider = suggestionsPlugins?.[0]?.provider;
      });

    if (this.comment?.id) {
      const generateSuggestionStoredContent =
        this.getStorage().getEditableContentItem(
          COLLAPSE_SUGGESTION_STORAGE_KEY + this.comment.id
        );
      if (generateSuggestionStoredContent?.message === 'true') {
        this.collapsed = true;
      }
    }
  }

  static override get styles() {
    return [
      css`
        :host {
          display: block;
        }
        :host([collapsed]) gr-suggestion-diff-preview {
          display: none;
        }
        .show-hide {
          margin-left: var(--spacing-s);
        }
        /* just for a11y */
        input.show-hide {
          display: none;
        }
        label.show-hide {
          cursor: pointer;
          display: block;
        }
        label.show-hide gr-icon {
          vertical-align: top;
        }
        .header {
          background-color: var(--background-color-primary);
          border: 1px solid var(--border-color);
          padding: var(--spacing-xs) var(--spacing-xl);
          display: flex;
          justify-content: space-between;
          align-items: center;
          border-top-left-radius: var(--border-radius);
          border-top-right-radius: var(--border-radius);
        }
        .header .title {
          flex: 1;
        }
        .headerMiddle {
          display: flex;
          align-items: center;
        }
        .copyButton {
          margin-right: var(--spacing-l);
        }
      `,
    ];
  }

  override render() {
    if (!this.comment?.fix_suggestions) return;
    const fix_suggestions = this.comment.fix_suggestions;
    return html`<div class="header">
        <div class="title">
          <span
            >${this.suggestionsProvider?.getFixSuggestionTitle?.(
              fix_suggestions
            ) || 'Suggested edit'}</span
          >
          <a
            href=${this.suggestionsProvider?.getDocumentationLink?.(
              fix_suggestions
            ) || getDocUrl(this.docsBaseUrl, 'user-suggest-edits.html')}
            target="_blank"
            rel="noopener noreferrer"
            ><gr-endpoint-decorator name="fix-suggestion-title-help">
              <gr-endpoint-param
                name="suggestion"
                .value=${fix_suggestions}
              ></gr-endpoint-param
              ><gr-icon
                icon="help"
                title="read documentation"
              ></gr-icon></gr-endpoint-decorator
          ></a>
        </div>
        <div class="headerMiddle">
          <gr-button
            secondary
            flatten
            class="action show-fix"
            @click=${this.handleShowFix}
          >
            Show edit
          </gr-button>
          ${when(
            this.isOwner && !this.collapsed,
            () =>
              html`<gr-button
                secondary
                flatten
                .loading=${this.applyingFix}
                .disabled=${this.isApplyEditDisabled()}
                class="action show-fix"
                @click=${this.handleApplyFix}
                .title=${this.computeApplyEditTooltip()}
              >
                Apply edit
              </gr-button>`
          )}
          ${this.renderToggle()}
        </div>
      </div>
      <gr-suggestion-diff-preview
        .fixSuggestionInfo=${this.comment?.fix_suggestions?.[0]}
        .patchSet=${this.comment?.patch_set}
        .commentId=${this.comment?.id}
        @preview-loaded=${() => (this.previewLoaded = true)}
      ></gr-suggestion-diff-preview>`;
  }

  private renderToggle() {
    const icon = this.collapsed ? 'expand_more' : 'expand_less';
    const ariaLabel = this.collapsed ? 'Expand' : 'Collapse';
    return html`
      <div class="show-hide" tabindex="0">
        <label class="show-hide" aria-label=${ariaLabel}>
          <input
            type="checkbox"
            class="show-hide"
            .checked=${this.collapsed}
            @change=${() => {
              this.collapsed = !this.collapsed;
              if (this.collapsed) {
                this.reporting.reportInteraction(
                  Interaction.GENERATE_SUGGESTION_COLLAPSED
                );
              } else {
                this.reporting.reportInteraction(
                  Interaction.GENERATE_SUGGESTION_EXPANDED
                );
              }
              if (this.comment?.id) {
                this.getStorage().setEditableContentItem(
                  COLLAPSE_SUGGESTION_STORAGE_KEY + this.comment.id,
                  this.collapsed.toString()
                );
              }
            }}
          />
          <gr-icon icon=${icon} id="icon"></gr-icon>
        </label>
      </div>
    `;
  }

  handleShowFix() {
    if (!this.comment?.fix_suggestions || !this.comment?.patch_set) return;
    const eventDetail: OpenFixPreviewEventDetail = {
      fixSuggestions: this.comment.fix_suggestions.map(s => {
        return {
          ...s,
          fix_id: PROVIDED_FIX_ID,
          description:
            this.suggestionsProvider?.getFixSuggestionTitle?.(
              this.comment?.fix_suggestions
            ) || 'Suggested edit',
        };
      }),
      patchNum: this.comment.patch_set,
      onCloseFixPreviewCallbacks: [
        fixApplied => {
          if (fixApplied) fire(this, 'apply-user-suggestion', {});
        },
      ],
    };
    fire(this, 'open-fix-preview', eventDetail);
  }

  async handleApplyFix() {
    if (!this.comment?.fix_suggestions) return;
    this.applyingFix = true;
    try {
      await this.suggestionDiffPreview?.applyFix();
    } finally {
      this.applyingFix = false;
    }
  }

  private isApplyEditDisabled() {
    if (this.comment?.patch_set === undefined) return true;
    if (this.isChangeMerged) return true;
    return !this.previewLoaded;
  }

  private computeApplyEditTooltip() {
    if (this.comment?.patch_set === undefined) return '';
    if (this.isChangeMerged) return 'Change is already merged';
    if (!this.previewLoaded) return 'Fix is still loading ...';
    return '';
  }

  override updated(changedProperties: PropertyValues) {
    super.updated(changedProperties);
    if (changedProperties.has('comment') && this.comment?.fix_suggestions) {
      this.previewLoaded = false;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-fix-suggestions': GrFixSuggestions;
  }
}
