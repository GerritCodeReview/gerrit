/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {resolve} from '../../../models/dependency';
import {ChangeInfo, TopicName} from '../../../types/common';
import {subscribe} from '../../lit/subscription-controller';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-autocomplete/gr-autocomplete';
import {getAppContext} from '../../../services/app-context';
import {isDefined} from '../../../types/types';
import {unique} from '../../../utils/common-util';
import {AutocompleteSuggestion} from '../../../utils/autocomplete-util';
import {when} from 'lit/directives/when.js';
import {ValueChangedEvent} from '../../../types/events';
import {classMap} from 'lit/directives/class-map.js';
import {spinnerStyles} from '../../../styles/gr-spinner-styles';
import {ProgressStatus} from '../../../constants/constants';
import {allSettled} from '../../../utils/async-util';
import {fireReload} from '../../../utils/event-util';
import {fireAlert} from '../../../utils/event-util';
import {pluralize} from '../../../utils/string-util';
import {Interaction} from '../../../constants/reporting';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import '@material/web/menu/menu';
import {MdMenu} from '@material/web/menu/menu';

@customElement('gr-change-list-topic-flow')
export class GrChangeListTopicFlow extends LitElement {
  @state() private selectedChanges: ChangeInfo[] = [];

  @state() private topicToAdd: TopicName = '' as TopicName;

  @state() private existingTopicSuggestions: TopicName[] = [];

  @state() private loadingText?: string;

  @state() private errorText?: string;

  /** dropdown status is tracked here to lazy-load the inner DOM contents */
  @state() private isDropdownOpen = false;

  @state() private overallProgress: ProgressStatus = ProgressStatus.NOT_STARTED;

  @query('md-menu') private dropdown?: MdMenu;

  private selectedExistingTopics: Set<TopicName> = new Set();

  private getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private restApiService = getAppContext().restApiService;

  private readonly reportingService = getAppContext().reportingService;

  static override get styles() {
    return [
      spinnerStyles,
      css`
        md-menu {
          --md-menu-container-color: var(--dialog-background-color);
          --md-menu-top-space: 0px;
          --md-menu-bottom-space: 0px;
        }
        .dropdown-content {
          box-shadow: var(--elevation-level-2);
          width: 400px;
          border-radius: 4px;
          padding: var(--spacing-xl) var(--spacing-l) var(--spacing-l);
        }
        gr-autocomplete {
          --prominent-border-color: var(--gray-800);
        }
        .footer {
          display: flex;
          justify-content: space-between;
          align-items: baseline;
        }
        .buttons {
          padding-top: var(--spacing-m);
          display: flex;
          justify-content: flex-end;
          gap: var(--spacing-m);
        }
        .chips {
          display: flex;
          flex-wrap: wrap;
          gap: 6px;
        }
        .chip {
          padding: var(--spacing-s) var(--spacing-xl);
          border-radius: 10px;
          width: fit-content;
          cursor: pointer;
          color: var(--primary-text-color);
        }
        .chip:not(.selected) {
          border: var(--spacing-xxs) solid var(--border-color);
          background: none;
        }
        .chip.selected {
          border: 0;
          color: var(--selected-foreground);
          background-color: var(--selected-chip-background);
          margin: var(--spacing-xxs);
        }
        .loadingOrError {
          display: flex;
          align-items: baseline;
          gap: var(--spacing-s);
        }

        /* The basics of .loadingSpin are defined in spinnerStyles. */
        .loadingSpin {
          vertical-align: top;
          position: relative;
          top: 3px;
        }
        .error {
          color: var(--deemphasized-text-color);
        }
        gr-icon {
          color: var(--error-color);
          /* Center with text by aligning it to the top and then pushing it down
             to match the text */
          vertical-align: top;
          position: relative;
          top: 7px;
        }
        .dropdown {
          position: relative;
          display: inline-block;
        }
      `,
    ];
  }

  constructor() {
    super();
    subscribe(
      this,
      () => this.getBulkActionsModel().selectedChanges$,
      selectedChanges => {
        this.selectedChanges = selectedChanges;
      }
    );
  }

  override render() {
    const isFlowDisabled = this.selectedChanges.length === 0;
    return html`<div class="dropdown">
      <gr-button
        id="start-flow"
        flatten
        down-arrow
        @click=${this.toggleDropdown}
        ?disabled=${isFlowDisabled}
        >Topic</gr-button
      >
      <md-menu
        anchor="start-flow"
        tabindex="-1"
        .quick=${true}
        @opening=${() => (this.isDropdownOpen = true)}
        @closing=${() => (this.isDropdownOpen = false)}
      >
        ${when(
          this.isDropdownOpen,
          () => html`
            <div class="dropdown-content">
              ${when(
                this.selectedChanges.some(change => change.topic),
                () => this.renderExistingTopicsMode(),
                () => this.renderNoExistingTopicsMode()
              )}
            </div>
          `
        )}
      </md-menu>
    </div> `;
  }

  private disableApplyToAllButton() {
    if (this.selectedExistingTopics.size !== 1) return true;
    // Ensure there is one selected change that does not have this topic
    // already
    return !this.selectedChanges
      .map(change => change.topic)
      .filter(unique)
      .some(topic => !topic || !this.selectedExistingTopics.has(topic));
  }

  private renderExistingTopicsMode() {
    const topics = this.selectedChanges
      .map(change => change.topic)
      .filter(isDefined)
      .filter(unique);
    const removeDisabled =
      this.selectedExistingTopics.size === 0 ||
      this.overallProgress === ProgressStatus.RUNNING;
    return html`
      <div class="chips">
        ${topics.map(name => this.renderExistingTopicChip(name))}
      </div>
      <div class="footer">
        <div class="loadingOrError" role="progressbar">
          ${this.renderLoadingOrError()}
        </div>
        <div class="buttons">
          ${when(
            this.overallProgress !== ProgressStatus.FAILED,
            () => html` <gr-button
                id="apply-to-all-button"
                flatten
                ?disabled=${this.disableApplyToAllButton()}
                @click=${this.applyTopicToAll}
                >Apply${this.selectedChanges.length > 1
                  ? ' to all'
                  : nothing}</gr-button
              >
              <gr-button
                id="remove-topics-button"
                flatten
                ?disabled=${removeDisabled}
                @click=${this.removeTopics}
                >Remove</gr-button
              >`,
            () =>
              html`
                <gr-button
                  id="cancel-button"
                  flatten
                  @click=${this.closeDropdown}
                  >Cancel</gr-button
                >
              `
          )}
        </div>
      </div>
    `;
  }

  private renderExistingTopicChip(name: TopicName) {
    const chipClasses = {
      chip: true,
      selected: this.selectedExistingTopics.has(name),
    };
    return html`
      <button
        role="listbox"
        aria-label=${`${name as string} selection`}
        class=${classMap(chipClasses)}
        @click=${() => this.toggleExistingTopicSelected(name)}
      >
        ${name}
      </button>
    `;
  }

  private renderLoadingOrError() {
    switch (this.overallProgress) {
      case ProgressStatus.RUNNING:
        return html`
          <span class="loadingSpin"></span>
          <span class="loadingText">${this.loadingText}</span>
        `;
      case ProgressStatus.FAILED:
        return html`
          <gr-icon icon="error" filled></gr-icon>
          <div class="error">${this.errorText}</div>
        `;
      default:
        return nothing;
    }
  }

  private renderNoExistingTopicsMode() {
    const isApplyTopicDisabled =
      this.topicToAdd === '' || this.overallProgress === ProgressStatus.RUNNING;
    return html`
      <!--
        The .query function needs to be bound to this because lit's autobind
        seems to work only for @event handlers.
        'this.getTopicSuggestions.bind(this)' gets in trouble with our linter
        even though the bind is necessary here, so an anonymous function is used
        instead.
      -->
      <gr-autocomplete
        .text=${this.topicToAdd}
        .query=${(query: string) => this.getTopicSuggestions(query)}
        show-blue-focus-border
        placeholder="Type topic name to create or filter topics"
        @text-changed=${(e: ValueChangedEvent<TopicName>) =>
          (this.topicToAdd = e.detail.value)}
      ></gr-autocomplete>
      <div class="footer">
        <div class="loadingOrError" role="progressbar">
          ${this.renderLoadingOrError()}
        </div>
        <div class="buttons">
          ${when(
            this.overallProgress !== ProgressStatus.FAILED,
            () => html`
              <gr-button
                id="set-topic-button"
                flatten
                @click=${() => this.setTopic('Setting topic...')}
                .disabled=${isApplyTopicDisabled}
                >Set Topic</gr-button
              >
            `,
            () => html`
              <gr-button id="cancel-button" flatten @click=${this.closeDropdown}
                >Cancel</gr-button
              >
            `
          )}
        </div>
      </div>
    `;
  }

  private toggleDropdown() {
    this.isDropdownOpen ? this.closeDropdown() : this.openDropdown();
  }

  private reset() {
    this.topicToAdd = '' as TopicName;
    this.selectedExistingTopics = new Set();
    this.overallProgress = ProgressStatus.NOT_STARTED;
    this.errorText = undefined;
  }

  private closeDropdown() {
    this.isDropdownOpen = false;
    this.dropdown?.close();
  }

  private openDropdown() {
    this.reset();
    this.isDropdownOpen = true;
    this.dropdown?.show();
  }

  private async getTopicSuggestions(
    query: string
  ): Promise<AutocompleteSuggestion[]> {
    const suggestions = await this.restApiService.getChangesWithSimilarTopic(
      query,
      throwingErrorCallback
    );
    this.existingTopicSuggestions = (suggestions ?? [])
      .map(change => change.topic)
      .filter(isDefined)
      .filter(unique);
    return this.existingTopicSuggestions.map(topic => {
      return {name: topic, value: topic};
    });
  }

  private removeTopics() {
    this.reportingService.reportInteraction(Interaction.BULK_ACTION, {
      type: 'removing-topic',
      selectedChangeCount: this.selectedChanges.length,
    });
    this.loadingText = `Removing topic${
      this.selectedExistingTopics.size > 1 ? 's' : ''
    }...`;
    this.trackPromises(
      this.selectedChanges
        .filter(
          change =>
            change.topic && this.selectedExistingTopics.has(change.topic)
        )
        .map(
          change =>
            // With throwing callback guaranteed to be non-null.
            this.restApiService.removeChangeTopic(
              change._number,
              throwingErrorCallback
            ) as Promise<string>
        ),
      `${this.selectedChanges[0].topic} removed from changes`,
      'Failed to remove topic'
    );
  }

  private applyTopicToAll() {
    this.reportingService.reportInteraction(Interaction.BULK_ACTION, {
      type: 'apply-topic-to-all',
      selectedChangeCount: this.selectedChanges.length,
    });
    this.loadingText = 'Applying to all';
    const topic = Array.from(this.selectedExistingTopics.values())[0];
    this.trackPromises(
      this.selectedChanges.map(
        change =>
          // With throwing callback guaranteed to be non-null.
          this.restApiService.setChangeTopic(
            change._number,
            topic,
            throwingErrorCallback
          ) as Promise<string>
      ),
      `${topic} applied to all changes`,
      'Failed to apply topic'
    );
  }

  private setTopic(loadingText: string) {
    this.reportingService.reportInteraction(Interaction.BULK_ACTION, {
      type: 'add-topic',
      selectedChangeCount: this.selectedChanges.length,
    });
    const alert = `${pluralize(
      this.selectedChanges.length,
      'Change'
    )} added to ${this.topicToAdd}`;
    this.loadingText = loadingText;
    this.trackPromises(
      this.selectedChanges.map(
        change =>
          // With throwing callback guaranteed to be non-null.
          this.restApiService.setChangeTopic(
            change._number,
            this.topicToAdd,
            throwingErrorCallback
          ) as Promise<string>
      ),
      alert,
      'Failed to set topic'
    );
  }

  private async trackPromises(
    promises: Promise<string>[],
    alert: string,
    errorMessage: string
  ) {
    this.overallProgress = ProgressStatus.RUNNING;
    const results = await allSettled(promises);
    if (results.every(result => result.status === 'fulfilled')) {
      this.overallProgress = ProgressStatus.SUCCESSFUL;
      this.closeDropdown();
      if (alert) {
        fireAlert(this, alert);
      }
      fireReload(this);
    } else {
      this.overallProgress = ProgressStatus.FAILED;
      this.errorText = errorMessage;
    }
  }

  private toggleExistingTopicSelected(name: TopicName) {
    if (this.selectedExistingTopics.has(name)) {
      this.selectedExistingTopics.delete(name);
    } else {
      this.selectedExistingTopics.add(name);
    }
    this.requestUpdate();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-topic-flow': GrChangeListTopicFlow;
  }
}
