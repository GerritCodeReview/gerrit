/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement, query, state} from 'lit/decorators';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {resolve} from '../../../models/dependency';
import {ChangeInfo} from '../../../types/common';
import {subscribe} from '../../lit/subscription-controller';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '@polymer/iron-dropdown/iron-dropdown';
import {IronDropdownElement} from '@polymer/iron-dropdown/iron-dropdown';
import {getAppContext} from '../../../services/app-context';
import {notUndefined} from '../../../types/types';
import {unique} from '../../../utils/common-util';
import {AutocompleteSuggestion} from '../../shared/gr-autocomplete/gr-autocomplete';

@customElement('gr-change-list-topic-flow')
export class GrChangeListTopicFlow extends LitElement {
  @state() private selectedChanges: ChangeInfo[] = [];

  @state() private topicName = '';

  @state() private existingTopics: string[] = [];

  @state() private isDropdownOpen = false;

  @query('iron-dropdown') private dropdown!: IronDropdownElement;

  private getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private restApiService = getAppContext().restApiService;

  static override get styles() {
    return css`
      iron-dropdown {
        box-shadow: var(--elevation-level-2);
        width: 400px;
        background-color: var(--dialog-background-color);
      }
      [slot='dropdown-content'] {
        padding: var(--spacing-xl) var(--spacing-l) var(--spacing-l);
      }
      gr-autocomplete {
        --border-color: var(--gray-800);
      }
      .buttons {
        padding-top: var(--spacing-m);
        display: flex;
        justify-content: flex-end;
        gap: var(--spacing-m);
      }
    `;
  }

  override connectedCallback(): void {
    super.connectedCallback();
    subscribe(
      this,
      this.getBulkActionsModel().selectedChanges$,
      selectedChanges => {
        this.selectedChanges = selectedChanges;
      }
    );
  }

  override render() {
    return html`
      <gr-button id="start-flow" flatten @click=${() => this.openDropdown()}
        >Topic</gr-button
      >
      <iron-dropdown
        .horizontalAlign=${'auto'}
        .verticalAlign=${'auto'}
        .verticalOffset=${24 /* roughly line height in pixels */}
        @opened-changed=${(e: CustomEvent) =>
          (this.isDropdownOpen = e.detail.value)}
      >
        ${this.isDropdownOpen
          ? html`
              <div slot="dropdown-content">
                <gr-autocomplete
                  .text=${this.topicName}
                  .query=${this.getTopicSuggestions.bind(this)}
                  placeholder="Type topic name to create or filter topics"
                  @text-changed=${(e: CustomEvent) =>
                    (this.topicName = e.detail.value)}
                ></gr-autocomplete>
                <div class="buttons">
                  <gr-button
                    id="create-new-topic-button"
                    flatten
                    @click=${() => this.applyTopic()}
                    .disabled=${this.isCreateNewTopicDisabled()}
                    >Create new topic</gr-button
                  >
                  <gr-button
                    id="apply-topic-button"
                    flatten
                    @click=${() => this.applyTopic()}
                    .disabled=${this.isApplyDisabled()}
                    >Apply</gr-button
                  >
                </div>
              </div>
            `
          : nothing}
      </iron-dropdown>
    `;
  }

  private openDropdown() {
    this.topicName = '';
    this.isDropdownOpen = true;
    this.dropdown.open();
  }

  private async getTopicSuggestions(
    query: string
  ): Promise<AutocompleteSuggestion[]> {
    const suggestions = await this.restApiService.getChangesWithSimilarTopic(
      query
    );
    this.existingTopics = (suggestions ?? [])
      .map(change => change.topic)
      .filter(notUndefined)
      .filter(unique);
    return this.existingTopics.map(topic => {
      return {name: topic, value: topic};
    });
  }

  private async applyTopic() {
    const promises = this.selectedChanges.map(change =>
      this.restApiService.setChangeTopic(change._number, this.topicName)
    );
    const results = await Promise.allSettled(promises);
    // TODO: show error when some are rejected
    if (results.every(result => result.status === 'fulfilled')) {
      this.dropdown.close();
      this.isDropdownOpen = false;
    }
  }

  private isCreateNewTopicDisabled() {
    return (
      this.topicName === '' || this.existingTopics.includes(this.topicName)
    );
  }

  private isApplyDisabled() {
    return (
      this.topicName !== '' && !this.existingTopics.includes(this.topicName)
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-topic-flow': GrChangeListTopicFlow;
  }
}
