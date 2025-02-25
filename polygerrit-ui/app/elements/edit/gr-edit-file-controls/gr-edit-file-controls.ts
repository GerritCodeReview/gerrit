/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-dropdown/gr-dropdown';
import {GrEditConstants} from '../gr-edit-constants';
import {sharedStyles} from '../../../styles/shared-styles';
import {FileActionTapEvent} from '../../../types/events';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {fire} from '../../../utils/event-util';
import {DropdownLink} from '../../../types/common';
import {SpecialFilePath} from '../../../constants/constants';

interface EditAction {
  label: string;
  id: string;
}

@customElement('gr-edit-file-controls')
export class GrEditFileControls extends LitElement {
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
          --gr-dropdown-item-background-color: transparent;
          --gr-dropdown-item-border: none;
        }
        #actions {
          margin-right: var(--spacing-l);
        }
      `,
    ];
  }

  override render() {
    const fileActions = this._computeFileActions(this._allFileActions);
    return html` <gr-dropdown
      id="actions"
      .items=${fileActions}
      down-arrow=""
      vertical-offset="20"
      @tap-item=${this._handleActionTap}
      link=""
      >Actions</gr-dropdown
    >`;
  }

  _handleActionTap(e: CustomEvent<DropdownLink>) {
    e.preventDefault();
    e.stopPropagation();
    const actionId = e.detail.id;
    if (!actionId) return;
    if (!this.filePath) return;
    this._dispatchFileAction(actionId, this.filePath);
  }

  _dispatchFileAction(action: string, path: string) {
    fire(this, 'file-action-tap', {action, path});
  }

  _computeFileActions(actions: EditAction[]): DropdownLink[] {
    // TODO(kaspern): conditionally disable some actions based on file status.
    return actions
      .filter(
        action =>
          this.filePath !== SpecialFilePath.COMMIT_MESSAGE ||
          action.label === GrEditConstants.Actions.OPEN.label
      )
      .map(action => {
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
  interface HTMLElementEventMap {
    'file-action-tap': FileActionTapEvent;
  }
}
