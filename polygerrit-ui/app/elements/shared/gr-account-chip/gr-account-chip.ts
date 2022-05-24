/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import '../gr-account-label/gr-account-label';
import '../gr-button/gr-button';
import '../gr-icons/gr-icons';
import {
  AccountInfo,
  ApprovalInfo,
  ChangeInfo,
  LabelInfo,
} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {ClassInfo, classMap} from 'lit/directives/class-map';
import {getLabelStatus, hasVoted, LabelStatus} from '../../../utils/label-util';

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

  @property({type: String})
  voteableText?: string;

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
        iron-icon {
          height: 1.2rem;
          width: 1.2rem;
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
      `,
    ];
  }

  override render() {
    // To pass CSS mixins for @apply to Polymer components, they need to appear
    // in <style> inside the template.
    /* eslint-disable lit/prefer-static-styles */
    const customStyle = html`
      <style>
        gr-button.remove::part(paper-button),
        gr-button.remove:hover::part(paper-button),
        gr-button.remove:focus::part(paper-button) {
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
      </style>
    `;
    return html`${customStyle}
      <div
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
            .voteableText=${this.voteableText}
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
          <iron-icon icon="gr-icons:close"></iron-icon>
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
    this.dispatchEvent(
      new CustomEvent('remove', {
        detail: {account: this.account},
        composed: true,
        bubbles: true,
      })
    );
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
}
