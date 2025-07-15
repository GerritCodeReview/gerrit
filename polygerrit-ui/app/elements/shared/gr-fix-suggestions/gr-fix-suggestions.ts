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
import {customElement, property, query, state} from 'lit/decorators.js';
import {fire} from '../../../utils/event-util';
import {getDocUrl} from '../../../utils/url-util';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {GrSuggestionDiffPreview} from '../gr-suggestion-diff-preview/gr-suggestion-diff-preview';
import {changeModelToken} from '../../../models/change/change-model';
import {Comment, isDraft, PatchSetNumber} from '../../../types/common';
import {OpenFixPreviewEventDetail} from '../../../types/events';
import {pluginLoaderToken} from '../gr-js-api-interface/gr-plugin-loader';
import {SuggestionsProvider} from '../../../api/suggestions';
import {PROVIDED_FIX_ID} from '../../../utils/comment-util';
import {KnownExperimentId} from '../../../services/flags/flags';
import {when} from 'lit/directives/when.js';
import {storageServiceToken} from '../../../services/storage/gr-storage_impl';
import {getAppContext} from '../../../services/app-context';
import {Interaction} from '../../../constants/reporting';
import {ChangeStatus, FixSuggestionInfo} from '../../../api/rest-api';
import {stringToReplacements} from '../../../utils/comment-util';
import {ReportSource} from '../../../services/suggestions/suggestions-service';

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

  @property({type: Object})
  generated_fix_suggestions?: FixSuggestionInfo[];

  @state() private docsBaseUrl = '';

  @state() private applyingFix = false;

  @state() private isEditingSuggestion = false;

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

  @state() isChangeAbandoned = false;

  @state() private thumbUpSelected = false;

  @state() private thumbDownSelected = false;

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

  private readonly getStorage = resolve(this, storageServiceToken);

  private readonly reporting = getAppContext().reportingService;

  private readonly flagsService = getAppContext().flagsService;

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
    subscribe(
      this,
      () => this.getChangeModel().status$,
      status => (this.isChangeAbandoned = status === ChangeStatus.ABANDONED)
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
          display: flex;
          align-items: center;
        }
        .headerMiddle {
          display: flex;
          align-items: center;
        }
        .copyButton {
          margin-right: var(--spacing-l);
        }
        .feedback-button[aria-label='Thumb up'] {
          margin-left: var(--spacing-l);
          margin-right: 0;
        }
        .feedback-button[aria-label='Thumb down'] {
          margin-left: 0;
        }
        .selected {
          color: var(--selected-foreground);
          background-color: var(--selected-background);
        }
      `,
    ];
  }

  override render() {
    const fix_suggestions = this.getFixSuggestions();
    if (!fix_suggestions) return;
    const editableSuggestionEnabled = this.flagsService.isEnabled(
      KnownExperimentId.ML_SUGGESTED_EDIT_EDITABLE_SUGGESTION
    );
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
              ><gr-icon icon="help" title="read documentation"></gr-icon
            ></gr-endpoint-decorator>
          </a>
          ${when(
            this.flagsService.isEnabled(
              KnownExperimentId.ML_SUGGESTED_EDIT_FEEDBACK
            ),
            () => html`
              <gr-button
                secondary
                flatten
                class="action feedback-button ${this.thumbUpSelected
                  ? 'selected'
                  : ''}"
                aria-label="Thumb up"
                @click=${this.handleThumbUpClick}
              >
                <gr-icon
                  icon="thumb_up"
                  ?filled=${this.thumbUpSelected}
                ></gr-icon>
              </gr-button>
              <gr-button
                secondary
                flatten
                class="action feedback-button ${this.thumbDownSelected
                  ? 'selected'
                  : ''}"
                aria-label="Thumb down"
                @click=${this.handleThumbDownClick}
              >
                <gr-icon
                  icon="thumb_down"
                  ?filled=${this.thumbDownSelected}
                ></gr-icon>
              </gr-button>
            `
          )}
        </div>
        <div class="headerMiddle">
          <gr-button
            secondary
            flatten
            class="action show-fix"
            @click=${this.handleShowFix}
          >
            Show Edit
          </gr-button>
          ${when(
            !this.collapsed &&
              editableSuggestionEnabled &&
              this.isEditingSuggestion,
            () => this.renderEditingButtons()
          )}
          ${when(
            !this.collapsed &&
              editableSuggestionEnabled &&
              !this.isEditingSuggestion &&
              isDraft(this.comment),
            () => this.renderChangeEditButton()
          )}
          ${when(
            this.isOwner &&
              !this.collapsed &&
              !(editableSuggestionEnabled && this.isEditingSuggestion),
            () => this.renderApplyEditButton()
          )}
          ${this.renderToggle()}
        </div>
      </div>
      <gr-suggestion-diff-preview
        .fixSuggestionInfo=${this.getFixSuggestions()?.[0]}
        .patchSet=${this.comment?.patch_set}
        .commentId=${this.comment?.id}
        .editable=${editableSuggestionEnabled && this.isEditingSuggestion}
        @preview-loaded=${() => (this.previewLoaded = true)}
      ></gr-suggestion-diff-preview>`;
  }

  private renderChangeEditButton() {
    return html`<gr-button
      secondary
      flatten
      class="action edit-fix"
      @click=${this.handleEditFix}
    >
      Change Edit
    </gr-button>`;
  }

  private renderEditingButtons() {
    return html`<gr-button
        secondary
        flatten
        class="action cancel-fix"
        @click=${this.handleCancelFix}
      >
        Cancel </gr-button
      ><gr-button
        secondary
        flatten
        class="action reset-fix"
        @click=${this.handleResetFix}
      >
        Reset </gr-button
      ><gr-button
        secondary
        flatten
        class="action save-fix"
        @click=${this.handleSaveFix}
      >
        Save
      </gr-button>`;
  }

  private renderApplyEditButton() {
    return html`<gr-button
      secondary
      flatten
      .loading=${this.applyingFix}
      .disabled=${this.isApplyEditDisabled()}
      class="action apply-fix"
      @click=${this.handleApplyFix}
      .title=${this.computeApplyEditTooltip()}
    >
      Apply Edit
    </gr-button>`;
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

  getFixSuggestions() {
    return this.comment?.fix_suggestions || this.generated_fix_suggestions;
  }

  handleShowFix() {
    const fixSuggestions = this.getFixSuggestions();
    if (!fixSuggestions || !this.comment?.patch_set) return;
    const eventDetail: OpenFixPreviewEventDetail = {
      fixSuggestions: fixSuggestions.map(s => {
        return {
          ...s,
          fix_id: PROVIDED_FIX_ID,
          description:
            this.suggestionsProvider?.getFixSuggestionTitle?.(fixSuggestions) ||
            'Suggested edit',
        };
      }),
      patchNum: this.comment.patch_set,
      onCloseFixPreviewCallbacks: [
        fixApplied => {
          if (fixApplied)
            fire(this, 'apply-user-suggestion', {
              fixSuggestion: fixSuggestions?.[0]?.description?.includes(
                ReportSource.GET_AI_FIX_FOR_COMMENT
              )
                ? fixSuggestions?.[0]
                : undefined,
            });
        },
      ],
    };
    fire(this, 'open-fix-preview', eventDetail);
  }

  handleCancelFix() {
    this.isEditingSuggestion = false;
    this.suggestionDiffPreview?.reset();
  }

  handleEditFix() {
    this.isEditingSuggestion = true;
  }

  handleResetFix() {
    this.suggestionDiffPreview?.reset();
  }

  handleSaveFix() {
    this.isEditingSuggestion = false;

    const newContent = this.suggestionDiffPreview?.getEditedContent();
    if (newContent === undefined) return;

    const suggestions = this.getFixSuggestions();
    if (!suggestions || !suggestions.length) return;

    const newReplacements = stringToReplacements(newContent);
    const newSuggestions = [
      {
        ...suggestions[0],
        replacements: newReplacements,
      },
      ...suggestions.slice(1),
    ];
    if (this.comment) {
      this.comment.fix_suggestions = newSuggestions;
    }
    fire(this, 'generated-suggestion-changed', {suggestions: newSuggestions});
  }

  async handleApplyFix() {
    if (!this.getFixSuggestions()) return;
    this.applyingFix = true;
    try {
      await this.suggestionDiffPreview?.applyFix();
    } finally {
      this.applyingFix = false;
    }
  }

  private handleThumbUpClick() {
    this.thumbUpSelected = !this.thumbUpSelected;
    if (this.thumbUpSelected) {
      this.thumbDownSelected = false;
    }
    this.reporting.reportInteraction(Interaction.GENERATE_SUGGESTION_THUMB_UP);
  }

  private handleThumbDownClick() {
    this.thumbDownSelected = !this.thumbDownSelected;
    if (this.thumbDownSelected) {
      this.thumbUpSelected = false;
    }
    this.reporting.reportInteraction(
      Interaction.GENERATE_SUGGESTION_THUMB_DOWN
    );
  }

  private isApplyEditDisabled() {
    if (this.comment?.patch_set === undefined) return true;
    if (this.isChangeMerged) return true;
    if (this.isChangeAbandoned) return true;
    return !this.previewLoaded;
  }

  private computeApplyEditTooltip() {
    if (this.comment?.patch_set === undefined) return '';
    if (this.isChangeMerged) return 'Change is already merged';
    if (this.isChangeAbandoned) return 'Change is abandoned';
    if (!this.previewLoaded) return 'Fix is still loading ...';
    return '';
  }

  override updated(changedProperties: PropertyValues) {
    super.updated(changedProperties);
    if (changedProperties.has('comment') && this.getFixSuggestions()) {
      this.previewLoaded = false;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-fix-suggestions': GrFixSuggestions;
  }
  interface HTMLElementEventMap {
    'generated-suggestion-changed': CustomEvent<{
      suggestions: FixSuggestionInfo[];
    }>;
  }
}
