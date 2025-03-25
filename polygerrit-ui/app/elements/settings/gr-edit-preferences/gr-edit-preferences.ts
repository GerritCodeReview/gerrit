/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../../shared/gr-button/gr-button';
import {EditPreferencesInfo} from '../../../types/common';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {convertToString} from '../../../utils/string-util';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {userModelToken} from '../../../models/user/user-model';
import {fire} from '../../../utils/event-util';
import {ValueChangedEvent} from '../../../types/events';

@customElement('gr-edit-preferences')
export class GrEditPreferences extends LitElement {
  @query('#editTabWidth') private editTabWidth?: HTMLInputElement;

  @query('#editColumns') private editColumns?: HTMLInputElement;

  @query('#editIndentUnit') private editIndentUnit?: HTMLInputElement;

  @query('#editSyntaxHighlighting')
  private editSyntaxHighlighting?: HTMLInputElement;

  @query('#showAutoCloseBrackets')
  private showAutoCloseBrackets?: HTMLInputElement;

  @query('#showIndentWithTabs') private showIndentWithTabs?: HTMLInputElement;

  @query('#showMatchBrackets') private showMatchBrackets?: HTMLInputElement;

  @query('#editShowLineWrapping')
  private editShowLineWrapping?: HTMLInputElement;

  @query('#editShowTabs') private editShowTabs?: HTMLInputElement;

  @query('#editShowTrailingWhitespaceInput')
  private editShowTrailingWhitespaceInput?: HTMLInputElement;

  @state() editPrefs?: EditPreferencesInfo;

  @state() private originalEditPrefs?: EditPreferencesInfo;

  readonly getUserModel = resolve(this, userModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().editPreferences$,
      editPreferences => {
        this.originalEditPrefs = editPreferences;
        this.editPrefs = {...editPreferences};
      }
    );
  }

  static override get styles() {
    return [sharedStyles, grFormStyles];
  }

  override render() {
    return html`
      <div id="editPreferences" class="gr-form-styles">
        <section>
          <label for="editTabWidth" class="title">Tab width</label>
          <span class="value">
            <iron-input
              .allowedPattern=${'[0-9]'}
              .bindValue=${convertToString(this.editPrefs?.tab_size)}
              @change=${this.handleEditTabWidthChanged}
            >
              <input id="editTabWidth" type="number" />
            </iron-input>
          </span>
        </section>
        <section>
          <label for="editColumns" class="title">Columns</label>
          <span class="value">
            <iron-input
              .allowedPattern=${'[0-9]'}
              .bindValue=${convertToString(this.editPrefs?.line_length)}
              @change=${this.handleEditLineLengthChanged}
            >
              <input id="editColumns" type="number" />
            </iron-input>
          </span>
        </section>
        <section>
          <label for="editIndentUnit" class="title">Indent unit</label>
          <span class="value">
            <iron-input
              .allowedPattern=${'[0-9]'}
              .bindValue=${convertToString(this.editPrefs?.indent_unit)}
              @change=${this.handleEditIndentUnitChanged}
            >
              <input id="editIndentUnit" type="number" />
            </iron-input>
          </span>
        </section>
        <section>
          <label for="editSyntaxHighlighting" class="title"
            >Syntax highlighting</label
          >
          <span class="value">
            <input
              id="editSyntaxHighlighting"
              type="checkbox"
              ?checked=${this.editPrefs?.syntax_highlighting}
              @change=${this.handleEditSyntaxHighlightingChanged}
            />
          </span>
        </section>
        <section>
          <label for="editShowTabs" class="title">Show tabs</label>
          <span class="value">
            <input
              id="editShowTabs"
              type="checkbox"
              ?checked=${this.editPrefs?.show_tabs}
              @change=${this.handleEditShowTabsChanged}
            />
          </span>
        </section>
        <section>
          <label for="showTrailingWhitespaceInput" class="title"
            >Show trailing whitespace</label
          >
          <span class="value">
            <input
              id="editShowTrailingWhitespaceInput"
              type="checkbox"
              ?checked=${this.editPrefs?.show_whitespace_errors}
              @change=${this.handleEditShowTrailingWhitespaceTap}
            />
          </span>
        </section>
        <section>
          <label for="showMatchBrackets" class="title">Match brackets</label>
          <span class="value">
            <input
              id="showMatchBrackets"
              type="checkbox"
              ?checked=${this.editPrefs?.match_brackets}
              @change=${this.handleMatchBracketsChanged}
            />
          </span>
        </section>
        <section>
          <label for="editShowLineWrapping" class="title">Line wrapping</label>
          <span class="value">
            <input
              id="editShowLineWrapping"
              type="checkbox"
              ?checked=${this.editPrefs?.line_wrapping}
              @change=${this.handleEditLineWrappingChanged}
            />
          </span>
        </section>
        <section>
          <label for="showIndentWithTabs" class="title">Indent with tabs</label>
          <span class="value">
            <input
              id="showIndentWithTabs"
              type="checkbox"
              ?checked=${this.editPrefs?.indent_with_tabs}
              @change=${this.handleIndentWithTabsChanged}
            />
          </span>
        </section>
        <section>
          <label for="showAutoCloseBrackets" class="title"
            >Auto close brackets</label
          >
          <span class="value">
            <input
              id="showAutoCloseBrackets"
              type="checkbox"
              ?checked=${this.editPrefs?.auto_close_brackets}
              @change=${this.handleAutoCloseBracketsChanged}
            />
          </span>
        </section>
      </div>
    `;
  }

  private readonly handleEditTabWidthChanged = () => {
    this.editPrefs!.tab_size = Number(this.editTabWidth!.value);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleEditLineLengthChanged = () => {
    this.editPrefs!.line_length = Number(this.editColumns!.value);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleEditIndentUnitChanged = () => {
    this.editPrefs!.indent_unit = Number(this.editIndentUnit!.value);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleEditSyntaxHighlightingChanged = () => {
    this.editPrefs!.syntax_highlighting = this.editSyntaxHighlighting!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  // private but used in test
  readonly handleEditShowTabsChanged = () => {
    this.editPrefs!.show_tabs = this.editShowTabs!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleEditShowTrailingWhitespaceTap = () => {
    this.editPrefs!.show_whitespace_errors =
      this.editShowTrailingWhitespaceInput!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleMatchBracketsChanged = () => {
    this.editPrefs!.match_brackets = this.showMatchBrackets!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleEditLineWrappingChanged = () => {
    this.editPrefs!.line_wrapping = this.editShowLineWrapping!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleIndentWithTabsChanged = () => {
    this.editPrefs!.indent_with_tabs = this.showIndentWithTabs!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleAutoCloseBracketsChanged = () => {
    this.editPrefs!.auto_close_brackets = this.showAutoCloseBrackets!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  // private but used in test
  hasUnsavedChanges() {
    // We have to wrap boolean values in Boolean() to ensure undefined values
    // use false rather than undefined.
    return (
      this.originalEditPrefs?.tab_size !== this.editPrefs?.tab_size ||
      this.originalEditPrefs?.line_length !== this.editPrefs?.line_length ||
      this.originalEditPrefs?.indent_unit !== this.editPrefs?.indent_unit ||
      Boolean(this.originalEditPrefs?.syntax_highlighting) !==
        Boolean(this.editPrefs?.syntax_highlighting) ||
      Boolean(this.originalEditPrefs?.show_tabs) !==
        Boolean(this.editPrefs?.show_tabs) ||
      Boolean(this.originalEditPrefs?.show_whitespace_errors) !==
        Boolean(this.editPrefs?.show_whitespace_errors) ||
      Boolean(this.originalEditPrefs?.match_brackets) !==
        Boolean(this.editPrefs?.match_brackets) ||
      Boolean(this.originalEditPrefs?.line_wrapping) !==
        Boolean(this.editPrefs?.line_wrapping) ||
      Boolean(this.originalEditPrefs?.indent_with_tabs) !==
        Boolean(this.editPrefs?.indent_with_tabs) ||
      Boolean(this.originalEditPrefs?.auto_close_brackets) !==
        Boolean(this.editPrefs?.auto_close_brackets)
    );
  }

  async save() {
    if (!this.editPrefs) return;
    await this.getUserModel().updateEditPreference(this.editPrefs);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  }
}

declare global {
  interface HTMLElementEventMap {
    'has-unsaved-changes-changed': ValueChangedEvent<boolean>;
  }
  interface HTMLElementTagNameMap {
    'gr-edit-preferences': GrEditPreferences;
  }
}
