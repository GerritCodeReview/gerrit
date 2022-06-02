/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {page} from '../../../utils/page-wrapper-utils';
import {GroupName} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, query, property} from 'lit/decorators';
import {BindValueChangeEvent} from '../../../types/events';
import {fireEvent} from '../../../utils/event-util';

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

  private computeGroupUrl(groupId: string) {
    return getBaseUrl() + '/admin/groups/' + encodeURL(groupId, true);
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
        page.show(this.computeGroupUrl(String(group.group_id!)));
      });
    });
  }

  private handleGroupNameBindValueChanged(e: BindValueChangeEvent) {
    this.name = e.detail.value as GroupName;
  }
}
