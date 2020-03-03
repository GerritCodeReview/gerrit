import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-form-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <div id="editPreferences" class="gr-form-styles">
      <section>
        <span class="title">Tab width</span>
        <span class="value">
          <iron-input type="number" prevent-invalid-input="" allowed-pattern="[0-9]" bind-value="{{editPrefs.tab_size}}" on-keypress="_handleEditPrefsChanged" on-change="_handleEditPrefsChanged">
            <input is="iron-input" type="number" prevent-invalid-input="" allowed-pattern="[0-9]" bind-value="{{editPrefs.tab_size}}" on-keypress="_handleEditPrefsChanged" on-change="_handleEditPrefsChanged">
          </iron-input>
        </span>
      </section>
      <section>
        <span class="title">Columns</span>
        <span class="value">
          <iron-input type="number" prevent-invalid-input="" allowed-pattern="[0-9]" bind-value="{{editPrefs.line_length}}" on-keypress="_handleEditPrefsChanged" on-change="_handleEditPrefsChanged">
            <input is="iron-input" type="number" prevent-invalid-input="" allowed-pattern="[0-9]" bind-value="{{editPrefs.line_length}}" on-keypress="_handleEditPrefsChanged" on-change="_handleEditPrefsChanged">
          </iron-input>
        </span>
      </section>
      <section>
        <span class="title">Indent unit</span>
        <span class="value">
          <iron-input type="number" prevent-invalid-input="" allowed-pattern="[0-9]" bind-value="{{editPrefs.indent_unit}}" on-keypress="_handleEditPrefsChanged" on-change="_handleEditPrefsChanged">
            <input is="iron-input" type="number" prevent-invalid-input="" allowed-pattern="[0-9]" bind-value="{{editPrefs.indent_unit}}" on-keypress="_handleEditPrefsChanged" on-change="_handleEditPrefsChanged">
          </iron-input>
        </span>
      </section>
      <section>
        <span class="title">Syntax highlighting</span>
        <span class="value">
          <input id="editSyntaxHighlighting" type="checkbox" checked\$="[[editPrefs.syntax_highlighting]]" on-change="_handleEditSyntaxHighlightingChanged">
        </span>
      </section>
      <section>
        <span class="title">Show tabs</span>
        <span class="value">
          <input id="editShowTabs" type="checkbox" checked\$="[[editPrefs.show_tabs]]" on-change="_handleEditShowTabsChanged">
        </span>
      </section>
      <section>
        <span class="title">Match brackets</span>
        <span class="value">
          <input id="showMatchBrackets" type="checkbox" checked\$="[[editPrefs.match_brackets]]" on-change="_handleMatchBracketsChanged">
        </span>
      </section>
      <section>
        <span class="title">Line wrapping</span>
        <span class="value">
          <input id="editShowLineWrapping" type="checkbox" checked\$="[[editPrefs.line_wrapping]]" on-change="_handleEditLineWrappingChanged">
        </span>
      </section>
      <section>
        <span class="title">Indent with tabs</span>
        <span class="value">
          <input id="showIndentWithTabs" type="checkbox" checked\$="[[editPrefs.indent_with_tabs]]" on-change="_handleIndentWithTabsChanged">
        </span>
      </section>
      <section>
        <span class="title">Auto close brackets</span>
        <span class="value">
          <input id="showAutoCloseBrackets" type="checkbox" checked\$="[[editPrefs.auto_close_brackets]]" on-change="_handleAutoCloseBracketsChanged">
        </span>
      </section>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
