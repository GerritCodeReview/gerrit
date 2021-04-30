/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * This plugin will a button to quickly add favorite reviewers to
 * reviewers in reply dialog.
 */

const onToggleButtonClicks = [];
function toggleButtonClicked(expanded) {
  onToggleButtonClicks.forEach(cb => {
    cb(expanded);
  });
}

class ReviewerShortcut extends Polymer.Element {
  static get is() { return 'reviewer-shortcut'; }

  static get properties() {
    return {
      change: Object,
      expanded: {
        type: Boolean,
        value: false,
      },
    };
  }

  static get template() {
    return Polymer.html`
      <button on-click="toggleControlContent">
        [[computeButtonText(expanded)]]
      </button>
    `;
  }

  toggleControlContent() {
    this.expanded = !this.expanded;
    toggleButtonClicked(this.expanded);
  }

  computeButtonText(expanded) {
    return expanded ? 'Collapse' : 'Add favorite reviewers';
  }
}

customElements.define(ReviewerShortcut.is, ReviewerShortcut);

class ReviewerShortcutContent extends Polymer.Element {
  static get is() { return 'reviewer-shortcut-content'; }

  static get properties() {
    return {
      change: Object,
      hidden: {
        type: Boolean,
        value: true,
        reflectToAttribute: true,
      },
    };
  }

  static get template() {
    return Polymer.html`
      <style>
      :host([hidden]) {
        display: none;
      }
      :host {
        display: block;
      }
      </style>
      <ul>
        <li><button on-click="addApple">Apple</button></li>
        <li><button on-click="addBanana">Banana</button></li>
        <li><button on-click="addCherry">Cherry</button></li>
      </ul>
    `;
  }

  connectedCallback() {
    super.connectedCallback();
    onToggleButtonClicks.push(expanded => {
      this.hidden = !expanded;
    });
  }

  addApple() {
    this.dispatchEvent(new CustomEvent('add-reviewer', {detail: {
      reviewer: {
        display_name: 'Apple',
        email: 'apple@gmail.com',
        name: 'Apple',
        _account_id: 0,
      },
    },
    composed: true, bubbles: true}));
  }

  addBanana() {
    this.dispatchEvent(new CustomEvent('add-reviewer', {detail: {
      reviewer: {
        display_name: 'Banana',
        email: 'banana@gmail.com',
        name: 'B',
        _account_id: 1,
      },
    },
    composed: true, bubbles: true}));
  }

  addCherry() {
    this.dispatchEvent(new CustomEvent('add-reviewer', {detail: {
      reviewer: {
        display_name: 'Cherry',
        email: 'cherry@gmail.com',
        name: 'C',
        _account_id: 2,
      },
    },
    composed: true, bubbles: true}));
  }
}

customElements.define(ReviewerShortcutContent.is, ReviewerShortcutContent);

Gerrit.install(plugin => {
  plugin.registerCustomComponent(
      'reply-reviewers', ReviewerShortcut.is, {slot: 'right'});
  plugin.registerCustomComponent(
      'reply-reviewers', ReviewerShortcutContent.is, {slot: 'below'});
});
