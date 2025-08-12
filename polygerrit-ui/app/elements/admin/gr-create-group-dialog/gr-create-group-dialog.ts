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
import {customElement, property, query, state} from 'lit/decorators.js';
import {fire} from '../../../utils/event-util';
import {createGroupUrl} from '../../../models/views/group';
import {resolve} from '../../../models/dependency';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import '@material/web/textfield/outlined-text-field';
import {MdOutlinedTextField} from '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';
import '@material/web/checkbox/checkbox';

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
  @query('md-outlined-text-field') private input!: MdOutlinedTextField;

  @property({type: String})
  name: GroupName | '' = '';

  @state()
  visibleToAll = false;

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
        div.title-flex,
        div.value-flex {
          display: flex;
          flex-direction: column;
          justify-content: center;
        }
        div.gr-form-styles section {
          margin: var(--spacing-m) 0;
        }
        div.gr-form-styles span.title {
          width: 13em;
        }
        md-outlined-text-field {
          width: 20em;
        }
        /* These colours come from paper-checkbox */
        md-checkbox {
          --md-sys-color-primary: var(--checkbox-primary);
          --md-sys-color-on-primary: var(--checkbox-on-primary);
          --md-sys-color-on-surface: var(--checkbox-on-surface);
          --md-sys-color-on-surface-variant: var(--checkbox-on-surface-variant);
          --md-checkbox-container-shape: 0px;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div class="gr-form-styles">
        <div id="form">
          <section>
            <div class="title-flex">
              <span class="title">Group name</span>
            </div>
            <div class="value-flex">
              <span class="value">
                <md-outlined-text-field
                  class="showBlueFocusBorder"
                  .value=${this.name ?? ''}
                  @input=${(e: InputEvent) => {
                    const target = e.target as MdOutlinedTextField;
                    this.name = target.value as GroupName;
                  }}
                >
                </md-outlined-text-field>
              </span>
            </div>
          </section>
          <section>
            <div class="title-flex">
              <span class="title">
                Make group visible to all registered users
              </span>
            </div>
            <div class="value-flex">
              <span class="value">
                <md-checkbox
                  ?checked=${this.visibleToAll}
                  @change=${() => {
                    this.visibleToAll = !this.visibleToAll;
                  }}
                ></md-checkbox>
              </span>
            </div>
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
    const visible_to_all = this.visibleToAll;
    return this.restApiService
      .createGroup({name, visible_to_all})
      .then(groupRegistered => {
        if (groupRegistered.status !== 201) return;
        return this.restApiService.getGroupConfig(name).then(group => {
          if (!group) return;
          this.getNavigation().setUrl(createGroupUrl({groupId: group.id}));
        });
      });
  }
}
