/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import {GroupName} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, query} from 'lit/decorators.js';
import {BindValueChangeEvent} from '../../../types/events';
import {fire} from '../../../utils/event-util';
import {createGroupUrl} from '../../../models/views/group';
import {resolve} from '../../../models/dependency';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-group-dialog': GrCreateGroupDialog;
  }
  interface HTMLElementEventMap {
    'has-new-group-name': CustomEvent<{}>;
  }
}

@customElement('gr-create-group-dialog')
export class GrCreateGroupDialog extends LitElement {
  @query('input') private input!: HTMLInputElement;

  @property({type: String})
  name: GroupName | '' = '';

  private readonly restApiService = getAppContext().restApiService;

  private readonly getNavigation = resolve(this, navigationToken);

  static override get styles() {
    return [
      grFormStyles,
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
    fire(this, 'has-new-group-name', {});
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
        this.getNavigation().setUrl(createGroupUrl({groupId: group.id}));
      });
    });
  }

  private handleGroupNameBindValueChanged(e: BindValueChangeEvent) {
    this.name = e.detail.value as GroupName;
  }
}
