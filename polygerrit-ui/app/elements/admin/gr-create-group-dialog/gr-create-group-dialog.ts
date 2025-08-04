/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import {GroupName} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, query} from 'lit/decorators.js';
import {fire} from '../../../utils/event-util';
import {createGroupUrl} from '../../../models/views/group';
import {resolve} from '../../../models/dependency';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';

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
      materialStyles,
      grFormStyles,
      sharedStyles,
      css`
        :host {
          display: inline-block;
        }
        input {
          width: 20em;
        }
        md-outlined-text-field {
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
            <md-outlined-text-field
              class="showBlueFocusBorder"
              .value=${this.name ?? ''}
              @input=${(e: InputEvent) => {
                const target = e.target as HTMLInputElement;
                this.name = target.value as GroupName;
              }}
            >
            </md-outlined-text-field>
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
}
