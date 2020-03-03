import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      .diffHeader,
      .diffActions {
        padding: var(--spacing-l) var(--spacing-xl);
      }
      .diffHeader,
      .diffActions {
        background-color: var(--dialog-background-color);
      }
      .diffHeader {
        border-bottom: 1px solid var(--border-color);
        font-weight: var(--font-weight-bold);
      }
      .diffActions {
        border-top: 1px solid var(--border-color);
        display: flex;
        justify-content: flex-end;
      }
      .diffPrefsOverlay gr-button {
        margin-left: var(--spacing-l);
      }
      div.edited:after {
        color: var(--deemphasized-text-color);
        content: ' *';
      }
      #diffPreferences {
        display: flex;
        padding: var(--spacing-s) var(--spacing-xl);
      }
    </style>
    <gr-overlay id="diffPrefsOverlay" with-backdrop="">
      <div class\$="diffHeader [[_computeHeaderClass(_diffPrefsChanged)]]">Diff Preferences</div>
      <gr-diff-preferences id="diffPreferences" diff-prefs="{{diffPrefs}}" has-unsaved-changes="{{_diffPrefsChanged}}"></gr-diff-preferences>
      <div class="diffActions">
        <gr-button id="cancelButton" link="" on-click="_handleCancelDiff">
            Cancel
        </gr-button>
        <gr-button id="saveButton" link="" primary="" on-click="_handleSaveDiffPreferences" disabled\$="[[!_diffPrefsChanged]]">
            Save
        </gr-button>
      </div>
    </gr-overlay>
`;
