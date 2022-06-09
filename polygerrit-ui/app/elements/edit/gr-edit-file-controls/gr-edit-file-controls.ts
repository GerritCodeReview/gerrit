/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-dropdown/gr-dropdown';
import {GrEditConstants} from '../gr-edit-constants';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';

interface EditAction {
  label: string;
  id: string;
}

@customElement('gr-edit-file-controls')
export class GrEditFileControls extends LitElement {
  /**
   * Fired when an action in the overflow menu is tapped.
   *
   * @event file-action-tap
   */

  @property({type: String})
  filePath?: string;

  @property({type: Array})
  _allFileActions = Object.values(GrEditConstants.Actions);

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          align-items: center;
          display: flex;
          justify-content: flex-end;
        }
        gr-dropdown {
          --gr-dropdown-item-color: var(--link-color);
          --gr-button-padding: var(--spacing-xs) var(--spacing-s);
        }
        #actions {
          margin-right: var(--spacing-l);
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
        /* prettier formatter removes semi-colons after css mixins. */
        /* prettier-ignore */
        gr-dropdown {
          --gr-dropdown-item: {
            background-color: transparent;
            border: none;
            text-transform: uppercase;
          };
        }
      </style>
    `;
    const fileActions = this._computeFileActions(this._allFileActions);
    return html`${customStyle}
      <gr-dropdown
        id="actions"
        .items=${fileActions}
        down-arrow=""
        vertical-offset="20"
        @tap-item=${this._handleActionTap}
        link=""
        >Actions</gr-dropdown
      >`;
  }

  _handleActionTap(e: CustomEvent) {
    e.preventDefault();
    e.stopPropagation();
    this._dispatchFileAction(e.detail.id, this.filePath);
  }

  _dispatchFileAction(action: EditAction, path?: string) {
    this.dispatchEvent(
      new CustomEvent('file-action-tap', {
        detail: {action, path},
        bubbles: true,
        composed: true,
      })
    );
  }

  _computeFileActions(actions: EditAction[]) {
    // TODO(kaspern): conditionally disable some actions based on file status.
    return actions.map(action => {
      return {
        name: action.label,
        id: action.id,
      };
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-edit-file-controls': GrEditFileControls;
  }
}
