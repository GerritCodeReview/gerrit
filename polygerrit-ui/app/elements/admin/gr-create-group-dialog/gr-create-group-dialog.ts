/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import {page} from '../../../utils/page-wrapper-utils';
import {GroupId, GroupName} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, query, property} from 'lit/decorators.js';
import {BindValueChangeEvent} from '../../../types/events';
import {fireEvent} from '../../../utils/event-util';
import {createGroupUrl} from '../../../models/views/group';
import {resolve} from '../../../models/dependency';
import {routerToken} from '../../core/gr-router/gr-router';

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-group-dialog': GrCreateGroupDialog;
  }
}

@customElement('gr-create-group-dialog')
export class GrCreateGroupDialog extends LitElement {
  @query('input') private input!: HTMLInputElement;

  @property({type: String})
  name: GroupName | '' = '';

  private readonly restApiService = getAppContext().restApiService;

  readonly getRouter = resolve(this, routerToken);

  static override get styles() {
    return [
      formStyles,
      sharedStyles,
      css`
        :host {
          display: inline-block;
        }
        input {
          width: 20em;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div class="gr-form-styles">
        <div id="form">
          <section>
            <span class="title">Group name</span>
            <iron-input
              .bindValue=${this.name}
              @bind-value-changed=${this.handleGroupNameBindValueChanged}
            >
              <input />
            </iron-input>
          </section>
        </div>
      </div>
    `;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('name')) {
      this.updateGroupName();
    }
  }

  private updateGroupName() {
    fireEvent(this, 'has-new-group-name');
  }

  override focus() {
    this.input.focus();
  }

  handleCreateGroup() {
    const name = this.name as GroupName;
    return this.restApiService.createGroup({name}).then(groupRegistered => {
      if (groupRegistered.status !== 201) return;
      return this.restApiService.getGroupConfig(name).then(group => {
        if (!group) return;
        const groupId = String(group.group_id!) as GroupId;
        // TODO: Use navigation service instead of `page.show()` directly.
        this.getRouter().page.show(createGroupUrl({groupId}));
      });
    });
  }

  private handleGroupNameBindValueChanged(e: BindValueChangeEvent) {
    this.name = e.detail.value as GroupName;
  }
}
