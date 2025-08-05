/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../shared/gr-icon/gr-icon';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {RunResult} from '../../models/checks/checks-model';
import {
  computeIsExpandable,
  createFixAction,
  createPleaseFixComment,
  iconFor,
} from '../../models/checks/checks-util';
import {modifierPressed} from '../../utils/dom-util';
import './gr-checks-results';
import './gr-hovercard-run';
import {fontStyles} from '../../styles/gr-font-styles';
import {Action, Category} from '../../api/checks';
import {assertIsDefined} from '../../utils/common-util';
import {resolve} from '../../models/dependency';
import {commentsModelToken} from '../../models/comments/comments-model';
import {subscribe} from '../lit/subscription-controller';
import {changeModelToken} from '../../models/change/change-model';
import {getAppContext} from '../../services/app-context';
import {Interaction} from '../../constants/reporting';
import {KnownExperimentId} from '../../services/flags/flags';
import {
  ReportSource,
  suggestionsServiceToken,
} from '../../services/suggestions/suggestions-service';
import {
  FixSuggestionInfo,
  PatchSetNumber,
  RevisionPatchSetNum,
} from '../../api/rest-api';
import {when} from 'lit/directives/when.js';
import {fireAlert} from '../../utils/event-util';

@customElement('gr-diff-check-result')
export class GrDiffCheckResult extends LitElement {
  @property({attribute: false})
  result?: RunResult;

  /**
   * This is required by <gr-diff> as an identifier for this component. It will
   * be set to the internalResultId of the check result.
   */
  @property({type: String})
  rootId?: string;

  @state()
  isExpanded = false;

  @state()
  isExpandable = false;

  @state()
  isOwner = false;

  @state()
  suggestionLoading = false;

  @state()
  suggestion?: FixSuggestionInfo;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly reporting = getAppContext().reportingService;

  private readonly getSuggestionsService = resolve(
    this,
    suggestionsServiceToken
  );

  private readonly flagsService = getAppContext().flagsService;

  static override get styles() {
    return [
      fontStyles,
      css`
        .container {
          /* Allows hiding the check results along with the comments
             when the user presses the keyboard shortcut 'h'. */
          display: var(--gr-check-code-pointers-display, block);
          font-family: var(--font-family);
          margin: 0 var(--spacing-s) var(--spacing-s);
          background-color: var(--unresolved-comment-background-color);
          box-shadow: var(--elevation-level-2);
          border-radius: var(--border-radius);
          padding: var(--spacing-xs) var(--spacing-m);
          border: 1px solid #888;
        }
        .container.info {
          border-color: var(--info-foreground);
          background-color: var(--info-background);
        }
        .container.info gr-icon {
          color: var(--info-foreground);
        }
        .container.warning {
          border-color: var(--warning-foreground);
          background-color: var(--warning-background);
        }
        .container.warning gr-icon {
          color: var(--warning-foreground);
        }
        .container.error {
          border-color: var(--error-foreground);
          background-color: var(--error-background);
        }
        .container.error gr-icon {
          color: var(--error-foreground);
        }
        .header {
          display: flex;
          white-space: nowrap;
          cursor: pointer;
        }
        .icon {
          margin-right: var(--spacing-s);
        }
        .name {
          margin-right: var(--spacing-m);
        }
        .summary {
          font-weight: var(--font-weight-medium);
          flex-shrink: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          margin-right: var(--spacing-s);
        }
        .message {
          flex-grow: 1;
          /* Looks a bit unexpected, but the idea is that .message shrinks
             first, and only when that has shrunken to 0, then .summary should
             also start shrinking (substantially). */
          flex-shrink: 1000000;
          overflow: hidden;
          text-overflow: ellipsis;
          color: var(--deemphasized-text-color);
        }
        gr-result-expanded {
          display: block;
          margin-top: var(--spacing-m);
        }
        gr-icon {
          font-size: var(--line-height-normal);
        }
        .icon gr-icon {
          font-size: calc(var(--line-height-normal) - 4px);
          position: relative;
          top: 2px;
        }
        div.actions {
          display: flex;
          justify-content: flex-end;
        }
      `,
    ];
  }

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChangeModel().isOwner$,
      x => (this.isOwner = x)
    );
    subscribe(
      this,
      () => this.getSuggestionsService().suggestionsServiceUpdated$,
      updated => {
        if (updated) {
          this.requestUpdate();
        }
      }
    );
  }

  protected override firstUpdated(_changedProperties: PropertyValues): void {
    // This component is only used in gr-diff-host, so we can assume that the
    // result is always rendered in the diff.
    this.reporting.reportInteraction(Interaction.CHECKS_RESULT_DIFF_RENDERED, {
      checkName: this.result?.checkName,
    });
  }

  override render() {
    if (!this.result) return;
    const cat = this.result.category.toLowerCase();
    const icon = iconFor(this.result.category);
    return html`
      <div class="${cat} container font-normal">
        <div class="header" @click=${this.toggleExpandedClick}>
          <div class="icon">
            <gr-icon icon=${icon.name} ?filled=${!!icon.filled}></gr-icon>
          </div>
          <div class="name">
            <gr-hovercard-run .run=${this.result}></gr-hovercard-run>
            <div
              class="name"
              role="button"
              tabindex="0"
              @keydown=${this.toggleExpandedPress}
            >
              ${this.result.checkName}
            </div>
          </div>
          <!-- The &nbsp; is for being able to shrink a tiny amount without
                the text itself getting shrunk with an ellipsis. -->
          <div class="summary">${this.result.summary}&nbsp;</div>
          <div class="message">
            ${this.isExpanded ? nothing : this.result.message}
          </div>
          ${this.renderToggle()}
        </div>
        <div class="details">
          ${this.renderExpanded()}${this.renderActions()}
        </div>
      </div>
    `;
  }

  private renderToggle() {
    if (!this.isExpandable) return nothing;
    return html`
      <div
        class="show-hide"
        role="switch"
        tabindex="0"
        aria-checked=${this.isExpanded ? 'true' : 'false'}
        aria-label=${this.isExpanded
          ? 'Collapse result row'
          : 'Expand result row'}
        @keydown=${this.toggleExpandedPress}
      >
        <gr-icon
          icon=${this.isExpanded ? 'expand_less' : 'expand_more'}
        ></gr-icon>
      </div>
    `;
  }

  private renderExpanded() {
    if (!this.isExpanded) return nothing;
    return html`
      <gr-result-expanded
        hidecodepointers
        .result=${this.result}
      ></gr-result-expanded>
      ${this.renderSuggestionPreview()}
    `;
  }

  private renderSuggestionPreview() {
    if (!this.suggestion) return nothing;
    return html`<gr-checks-fix-preview
      .fixSuggestionInfos=${[this.suggestion]}
      .patchSet=${this.result?.patchset as PatchSetNumber | undefined}
    ></gr-checks-fix-preview>`;
  }

  private renderActions() {
    return html`<div class="actions">
      ${this.renderAIFixButton()}
      ${this.renderShowFixButton()}${this.renderPleaseFixButton()}
    </div>`;
  }

  private renderAIFixButton() {
    if (!this.shouldShowAIFixButton()) return nothing;
    return html`<gr-button
      id="aiFixBtn"
      link
      class="action ai-fix"
      ?disabled=${this.suggestionLoading}
      @click=${this.handleAIFix}
      >Get AI Fix
      ${when(
        this.suggestionLoading,
        () => html`<span class="loadingSpin"></span>`
      )}</gr-button
    >`;
  }

  private shouldShowAIFixButton() {
    if (!this.flagsService.isEnabled(KnownExperimentId.GET_AI_FIX)) {
      return false;
    }
    if (
      !this.getSuggestionsService()?.isGeneratedSuggestedFixEnabled(
        this.result?.codePointers?.[0].path
      )
    ) {
      return false;
    }
    if (this.result?.fixes?.length) {
      return false;
    }
    if (
      this.result?.category === Category.SUCCESS ||
      this.result?.category === Category.INFO
    ) {
      return false;
    }
    // disable on codepointer with range pointing to 0 line
    if (
      this.result?.codePointers?.[0]?.range.start_line === 0 ||
      this.result?.codePointers?.[0]?.range.end_line === 0
    ) {
      return false;
    }
    return this.isOwner;
  }

  private renderPleaseFixButton() {
    const action: Action = {
      name: 'Please Fix',
      callback: () => {
        assertIsDefined(this.result, 'result');
        this.getCommentsModel().saveDraft(createPleaseFixComment(this.result));
        return undefined;
      },
    };
    return html`
      <gr-checks-action
        id="please-fix"
        context="diff-fix"
        .action=${action}
      ></gr-checks-action>
    `;
  }

  private renderShowFixButton() {
    if (this.isExpanded) return nothing;
    const action = createFixAction(this, this.result);
    if (!action) return nothing;
    return html`
      <gr-checks-action
        id="show-fix"
        context="diff-fix"
        .action=${action}
      ></gr-checks-action>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('result')) {
      this.isExpandable = computeIsExpandable(this.result);
    }
  }

  private toggleExpandedClick(e: MouseEvent) {
    if (!this.isExpandable) return;
    e.preventDefault();
    e.stopPropagation();
    this.toggleExpanded();
  }

  private toggleExpandedPress(e: KeyboardEvent) {
    if (!this.isExpandable) return;
    if (modifierPressed(e)) return;
    // Only react to `return` and `space`.
    if (e.key !== 'Enter' && e.key !== ' ') return;
    e.preventDefault();
    e.stopPropagation();
    this.toggleExpanded();
  }

  private toggleExpanded() {
    if (!this.isExpandable) return;
    this.isExpanded = !this.isExpanded;
  }

  private async handleAIFix(): Promise<void> {
    const codePointer = this.result?.codePointers?.[0];
    if (!this.result || !this.result.message || !codePointer || !this.isOwner)
      return;

    this.suggestionLoading = true;
    let suggestion: FixSuggestionInfo | undefined;
    try {
      suggestion = await this.getSuggestionsService().generateSuggestedFix({
        prompt: this.result.message,
        patchsetNumber: this.result.patchset as RevisionPatchSetNum,
        filePath: codePointer.path,
        range: codePointer.range,
        reportSource: ReportSource.GET_AI_FIX_FOR_CHECK,
      });
    } finally {
      this.suggestionLoading = false;
    }
    if (!suggestion) {
      fireAlert(this, 'No suitable AI fix could be found');
      return;
    }
    suggestion.description =
      ReportSource.GET_AI_FIX_FOR_CHECK + ' ' + suggestion.description;
    this.suggestion = suggestion;
    this.isExpanded = true;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-check-result': GrDiffCheckResult;
  }
}
