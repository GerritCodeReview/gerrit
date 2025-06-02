/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-account-label/gr-account-label';
import '../gr-button/gr-button';
import '../gr-icon/gr-icon';
import {
  AccountInfo,
  ApprovalInfo,
  ChangeInfo,
  LabelInfo,
} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {ClassInfo, classMap} from 'lit/directives/class-map.js';
import {getLabelStatus, hasVoted, LabelStatus} from '../../../utils/label-util';
import {fire} from '../../../utils/event-util';
import {RemoveAccountEvent} from '../../../types/events';

@customElement('gr-account-chip')
export class GrAccountChip extends LitElement {
  /**
   * Fired to indicate a key was pressed while this chip was focused.
   *
   * @event account-chip-keydown
   */

  /**
   * Fired to indicate this chip should be removed, i.e. when the x button is
   * clicked or when the remove function is called.
   *
   * @event remove
   */

  @property({type: Object})
  account?: AccountInfo;

  /**
   * Optional ChangeInfo object, typically comes from the change page or
   * from a row in a list of search results. This is needed for some change
   * related features like adding the user as a reviewer.
   */
  @property({type: Object})
  change?: ChangeInfo;

  /**
   * Should this user be considered to be in the attention set, regardless
   * of the current state of the change object?
   */
  @property({type: Boolean})
  forceAttention = false;

  @property({type: Boolean, reflect: true})
  disabled = false;

  @property({type: Boolean, reflect: true})
  removable = false;

  /**
   * Should attention set related features be shown in the component? Note
   * that the information whether the user is in the attention set or not is
   * part of the ChangeInfo object in the change property.
   */
  @property({type: Boolean})
  highlightAttention = false;

  @property({type: Boolean, reflect: true})
  showAvatar?: boolean;

  @property({type: Object})
  vote?: ApprovalInfo;

  @property({type: Object})
  label?: LabelInfo;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      css`
        :host {
          display: block;
          overflow: hidden;
        }
        .container {
          align-items: center;
          background-color: var(--background-color-primary);
          /** round */
          border-radius: var(--account-chip-border-radius, 20px);
          border: 1px solid var(--border-color);
          display: inline-flex;
          padding: 0 1px;
          /* Any outermost circular icon would fit neatly in the border-radius
             and won't need padding, but the exact outermost elements will
             depend on account state and the context gr-account-chip is used.
             So, these values are passed down to gr-account-label and any
             outermost elements will use the value and then override it. */
          --account-label-padding-left: 6px;
          --account-label-padding-right: 6px;
          --account-label-circle-padding-left: 0;
          --account-label-circle-padding-right: 0;
        }
        :host:focus {
          border-color: transparent;
          box-shadow: none;
          outline: none;
        }
        :host:focus .container,
        :host:focus gr-button {
          background: #ccc;
        }
        :host([disabled]) {
          opacity: 0.6;
          pointer-events: none;
        }
        gr-icon {
          font-size: 1.2rem;
        }
        gr-icon[icon='close'] {
          margin-top: var(--spacing-xxs);
        }
        .container gr-account-label::part(gr-account-label-text) {
          color: var(--deemphasized-text-color);
        }
        .container.disliked {
          border: 1px solid var(--vote-outline-disliked);
        }
        .container.recommended {
          border: 1px solid var(--vote-outline-recommended);
        }
        .container.disliked,
        .container.recommended {
          --account-label-padding-right: var(--spacing-xs);
          --account-label-circle-padding-right: var(--spacing-xs);
        }
        .container.closeShown {
          --account-label-padding-right: 3px;
          --account-label-circle-padding-right: 3px;
        }
        gr-button.remove::part(md-text-button),
        gr-button.remove:hover::part(md-text-button),
        gr-button.remove:focus::part(md-text-button) {
          border-top-width: 0;
          border-right-width: 0;
          border-bottom-width: 0;
          border-left-width: 0;
          color: var(--deemphasized-text-color);
          font-weight: var(--font-weight-normal);
          height: 0.6em;
          line-height: 10px;
          /* This cancels most of the --account-label-padding-horizontal. */
          margin-left: -4px;
          padding: 0 2px 0 1px;
          text-decoration: none;
        }
      `,
    ];
  }

  override render() {
    return html`<div
      class=${classMap({
        ...this.computeVoteClasses(),
        container: true,
        closeShown: this.removable,
      })}
    >
      <div>
        <gr-account-label
          .account=${this.account}
          .change=${this.change}
          ?forceAttention=${this.forceAttention}
          ?highlightAttention=${this.highlightAttention}
          clickable
        >
        </gr-account-label>
      </div>
      <slot name="vote-chip"></slot>
      <gr-button
        id="remove"
        link=""
        ?hidden=${!this.removable}
        aria-label="Remove"
        class="remove"
        @click=${this.handleRemoveTap}
      >
        <gr-icon icon="close"></gr-icon>
      </gr-button>
    </div>`;
  }

  constructor() {
    super();
    this.getHasAvatars().then(hasAvatars => {
      this.showAvatar = hasAvatars;
    });
  }

  private handleRemoveTap(e: MouseEvent) {
    e.preventDefault();
    if (!this.account) return;
    fire(this, 'remove-account', {account: this.account});
  }

  private getHasAvatars() {
    return this.restApiService
      .getConfig()
      .then(cfg =>
        Promise.resolve(!!(cfg && cfg.plugin && cfg.plugin.has_avatars))
      );
  }

  private computeVoteClasses(): ClassInfo {
    if (!this.label || !this.account || !hasVoted(this.label, this.account)) {
      return {};
    }
    const status = getLabelStatus(this.label, this.vote?.value);
    if ([LabelStatus.APPROVED, LabelStatus.RECOMMENDED].includes(status)) {
      return {recommended: true};
    } else if ([LabelStatus.REJECTED, LabelStatus.DISLIKED].includes(status)) {
      return {disliked: true};
    } else {
      return {};
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-account-chip': GrAccountChip;
  }
  interface HTMLElementEventMap {
    'remove-account': RemoveAccountEvent;
  }
}
