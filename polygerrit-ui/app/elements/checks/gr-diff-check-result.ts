/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/paper-tooltip/paper-tooltip';
import '../shared/gr-icon/gr-icon';
import {LitElement, css, html, PropertyValues, nothing} from 'lit';
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
import {Action} from '../../api/checks';
import {assertIsDefined} from '../../utils/common-util';
import {resolve} from '../../models/dependency';
import {commentsModelToken} from '../../models/comments/comments-model';
import {subscribe} from '../lit/subscription-controller';
import {changeModelToken} from '../../models/change/change-model';

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

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  static override get styles() {
    return [
      fontStyles,
      css`
        .container {
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
          font-weight: var(--font-weight-bold);
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
  }

  override render() {
    if (!this.result) return;
    const cat = this.result.category.toLowerCase();
    const icon = iconFor(this.result.category);
    return html`
      <div class="${cat} container font-normal">
        <div class="header" @click=${this.toggleExpandedClick}>
          <div class="icon">
            <gr-icon icon=${icon.name} ?filled=${icon.filled}></gr-icon>
          </div>
          <div class="name">
            <gr-hovercard-run
              .run=${this.result}
              .worstCategory=${this.result.worstCategory}
            ></gr-hovercard-run>
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
    `;
  }

  private renderActions() {
    if (!this.isExpanded) return nothing;
    return html`<div class="actions">
      ${this.renderPleaseFixButton()}${this.renderShowFixButton()}
    </div>`;
  }

  private renderPleaseFixButton() {
    if (this.isOwner) return nothing;
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
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-check-result': GrDiffCheckResult;
  }
}
