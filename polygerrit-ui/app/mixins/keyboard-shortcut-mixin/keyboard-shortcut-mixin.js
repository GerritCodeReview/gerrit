/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
/*

How to Add a Keyboard Shortcut
==============================

A keyboard shortcut is composed of the following parts:

  1. A semantic identifier (e.g. OPEN_CHANGE, NEXT_PAGE)
  2. Documentation for the keyboard shortcut help dialog
  3. A binding between key combos and the semantic identifier
  4. A binding between the semantic identifier and a listener

Parts (1) and (2) for all shortcuts are defined in this file. The semantic
identifier is declared in the Shortcut enum near the head of this script:

  const Shortcut = {
    // ...
    TOGGLE_LEFT_PANE: 'TOGGLE_LEFT_PANE',
    // ...
  };

Immediately following the Shortcut enum definition, there is a _describe
function defined which is then invoked many times to populate the help dialog.
Add a new invocation here to document the shortcut:

  _describe(Shortcut.TOGGLE_LEFT_PANE, ShortcutSection.DIFFS,
      'Hide/show left diff');

When an attached view binds one or more key combos to this shortcut, the help
dialog will display this text in the given section (in this case, "Diffs"). See
the ShortcutSection enum immediately below for the list of supported sections.

Part (3), the actual key bindings, are declared by gr-app. In the future, this
system may be expanded to allow key binding customizations by plugins or user
preferences. Key bindings are defined in the following forms:

  // Ordinary shortcut with a single binding.
  this.bindShortcut(
      Shortcut.TOGGLE_LEFT_PANE, 'shift+a');

  // Ordinary shortcut with multiple bindings.
  this.bindShortcut(
      Shortcut.CURSOR_NEXT_FILE, 'j', 'down');

  // A "go-key" keyboard shortcut, which is combined with a previously and
  // continuously pressed "go" key (the go-key is hard-coded as 'g').
  this.bindShortcut(
      Shortcut.GO_TO_OPENED_CHANGES, SPECIAL_SHORTCUT.GO_KEY, 'o');

  // A "doc-only" keyboard shortcut. This declares the key-binding for help
  // dialog purposes, but doesn't actually implement the binding. It is up
  // to some element to implement this binding using iron-a11y-keys-behavior's
  // keyBindings property.
  this.bindShortcut(
      Shortcut.EXPAND_ALL_COMMENT_THREADS, SPECIAL_SHORTCUT.DOC_ONLY, 'e');

Part (4), the listener definitions, are declared by the view or element that
implements the shortcut behavior. This is done by implementing a method named
keyboardShortcuts() in an element that mixes in this behavior, returning an
object that maps semantic identifiers (as property names) to listener method
names, like this:

  keyboardShortcuts() {
    return {
      [Shortcut.TOGGLE_LEFT_PANE]: '_handleToggleLeftPane',
    };
  },

You can implement key bindings in an element that is hosted by a view IF that
element is always attached exactly once under that view (e.g. the search bar in
gr-app). When that is not the case, you will have to define a doc-only binding
in gr-app, declare the shortcut in the view that hosts the element, and use
iron-a11y-keys-behavior's keyBindings attribute to implement the binding in the
element. An example of this is in comment threads. A diff view supports actions
on comment threads, but there may be zero or many comment threads attached at
any given point. So the shortcut is declared as doc-only by the diff view and
by gr-app, and actually implemented by gr-comment-thread.

NOTE: doc-only shortcuts will not be customizable in the same way that other
shortcuts are.
*/

import {IronA11yKeysBehavior} from '@polymer/iron-a11y-keys-behavior/iron-a11y-keys-behavior.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {dedupingMixin} from '@polymer/polymer/lib/utils/mixin.js';

export const SPECIAL_SHORTCUT = {
  DOC_ONLY: 'DOC_ONLY',
  GO_KEY: 'GO_KEY',
  V_KEY: 'V_KEY',
};

// The maximum age of a keydown event to be used in a jump navigation. This
// is only for cases when the keyup event is lost.
const GO_KEY_TIMEOUT_MS = 1000;

const V_KEY_TIMEOUT_MS = 1000;

export const ShortcutSection = {
  ACTIONS: 'Actions',
  DIFFS: 'Diffs',
  EVERYWHERE: 'Everywhere',
  FILE_LIST: 'File list',
  NAVIGATION: 'Navigation',
  REPLY_DIALOG: 'Reply dialog',
};

export const Shortcut = {
  OPEN_SHORTCUT_HELP_DIALOG: 'OPEN_SHORTCUT_HELP_DIALOG',
  GO_TO_USER_DASHBOARD: 'GO_TO_USER_DASHBOARD',
  GO_TO_OPENED_CHANGES: 'GO_TO_OPENED_CHANGES',
  GO_TO_MERGED_CHANGES: 'GO_TO_MERGED_CHANGES',
  GO_TO_ABANDONED_CHANGES: 'GO_TO_ABANDONED_CHANGES',
  GO_TO_WATCHED_CHANGES: 'GO_TO_WATCHED_CHANGES',

  CURSOR_NEXT_CHANGE: 'CURSOR_NEXT_CHANGE',
  CURSOR_PREV_CHANGE: 'CURSOR_PREV_CHANGE',
  OPEN_CHANGE: 'OPEN_CHANGE',
  NEXT_PAGE: 'NEXT_PAGE',
  PREV_PAGE: 'PREV_PAGE',
  TOGGLE_CHANGE_REVIEWED: 'TOGGLE_CHANGE_REVIEWED',
  TOGGLE_CHANGE_STAR: 'TOGGLE_CHANGE_STAR',
  REFRESH_CHANGE_LIST: 'REFRESH_CHANGE_LIST',

  OPEN_REPLY_DIALOG: 'OPEN_REPLY_DIALOG',
  OPEN_DOWNLOAD_DIALOG: 'OPEN_DOWNLOAD_DIALOG',
  EXPAND_ALL_MESSAGES: 'EXPAND_ALL_MESSAGES',
  COLLAPSE_ALL_MESSAGES: 'COLLAPSE_ALL_MESSAGES',
  UP_TO_DASHBOARD: 'UP_TO_DASHBOARD',
  UP_TO_CHANGE: 'UP_TO_CHANGE',
  TOGGLE_DIFF_MODE: 'TOGGLE_DIFF_MODE',
  REFRESH_CHANGE: 'REFRESH_CHANGE',
  EDIT_TOPIC: 'EDIT_TOPIC',
  DIFF_AGAINST_BASE: 'DIFF_AGAINST_BASE',
  DIFF_AGAINST_LATEST: 'DIFF_AGAINST_LATEST',
  DIFF_BASE_AGAINST_LEFT: 'DIFF_BASE_AGAINST_LEFT',
  DIFF_RIGHT_AGAINST_LATEST: 'DIFF_RIGHT_AGAINST_LATEST',
  DIFF_BASE_AGAINST_LATEST: 'DIFF_BASE_AGAINST_LATEST',

  NEXT_LINE: 'NEXT_LINE',
  PREV_LINE: 'PREV_LINE',
  VISIBLE_LINE: 'VISIBLE_LINE',
  NEXT_CHUNK: 'NEXT_CHUNK',
  PREV_CHUNK: 'PREV_CHUNK',
  EXPAND_ALL_DIFF_CONTEXT: 'EXPAND_ALL_DIFF_CONTEXT',
  NEXT_COMMENT_THREAD: 'NEXT_COMMENT_THREAD',
  PREV_COMMENT_THREAD: 'PREV_COMMENT_THREAD',
  EXPAND_ALL_COMMENT_THREADS: 'EXPAND_ALL_COMMENT_THREADS',
  COLLAPSE_ALL_COMMENT_THREADS: 'COLLAPSE_ALL_COMMENT_THREADS',
  LEFT_PANE: 'LEFT_PANE',
  RIGHT_PANE: 'RIGHT_PANE',
  TOGGLE_LEFT_PANE: 'TOGGLE_LEFT_PANE',
  NEW_COMMENT: 'NEW_COMMENT',
  SAVE_COMMENT: 'SAVE_COMMENT',
  OPEN_DIFF_PREFS: 'OPEN_DIFF_PREFS',
  TOGGLE_DIFF_REVIEWED: 'TOGGLE_DIFF_REVIEWED',

  NEXT_FILE: 'NEXT_FILE',
  PREV_FILE: 'PREV_FILE',
  NEXT_FILE_WITH_COMMENTS: 'NEXT_FILE_WITH_COMMENTS',
  PREV_FILE_WITH_COMMENTS: 'PREV_FILE_WITH_COMMENTS',
  NEXT_UNREVIEWED_FILE: 'NEXT_UNREVIEWED_FILE',
  CURSOR_NEXT_FILE: 'CURSOR_NEXT_FILE',
  CURSOR_PREV_FILE: 'CURSOR_PREV_FILE',
  OPEN_FILE: 'OPEN_FILE',
  TOGGLE_FILE_REVIEWED: 'TOGGLE_FILE_REVIEWED',
  TOGGLE_ALL_INLINE_DIFFS: 'TOGGLE_ALL_INLINE_DIFFS',
  TOGGLE_INLINE_DIFF: 'TOGGLE_INLINE_DIFF',
  TOGGLE_HIDE_ALL_COMMENT_THREADS: 'TOGGLE_HIDE_ALL_COMMENT_THREADS',

  OPEN_FIRST_FILE: 'OPEN_FIRST_FILE',
  OPEN_LAST_FILE: 'OPEN_LAST_FILE',

  SEARCH: 'SEARCH',
  SEND_REPLY: 'SEND_REPLY',
  EMOJI_DROPDOWN: 'EMOJI_DROPDOWN',
  TOGGLE_BLAME: 'TOGGLE_BLAME',
};

const _help = new Map();

function _describe(shortcut, section, text) {
  if (!_help.has(section)) {
    _help.set(section, []);
  }
  _help.get(section).push({shortcut, text});
}

_describe(Shortcut.SEARCH, ShortcutSection.EVERYWHERE, 'Search');
_describe(Shortcut.OPEN_SHORTCUT_HELP_DIALOG, ShortcutSection.EVERYWHERE,
    'Show this dialog');
_describe(Shortcut.GO_TO_USER_DASHBOARD, ShortcutSection.EVERYWHERE,
    'Go to User Dashboard');
_describe(Shortcut.GO_TO_OPENED_CHANGES, ShortcutSection.EVERYWHERE,
    'Go to Opened Changes');
_describe(Shortcut.GO_TO_MERGED_CHANGES, ShortcutSection.EVERYWHERE,
    'Go to Merged Changes');
_describe(Shortcut.GO_TO_ABANDONED_CHANGES, ShortcutSection.EVERYWHERE,
    'Go to Abandoned Changes');
_describe(Shortcut.GO_TO_WATCHED_CHANGES, ShortcutSection.EVERYWHERE,
    'Go to Watched Changes');

_describe(Shortcut.CURSOR_NEXT_CHANGE, ShortcutSection.ACTIONS,
    'Select next change');
_describe(Shortcut.CURSOR_PREV_CHANGE, ShortcutSection.ACTIONS,
    'Select previous change');
_describe(Shortcut.OPEN_CHANGE, ShortcutSection.ACTIONS,
    'Show selected change');
_describe(Shortcut.NEXT_PAGE, ShortcutSection.ACTIONS, 'Go to next page');
_describe(Shortcut.PREV_PAGE, ShortcutSection.ACTIONS, 'Go to previous page');
_describe(Shortcut.OPEN_REPLY_DIALOG, ShortcutSection.ACTIONS,
    'Open reply dialog to publish comments and add reviewers');
_describe(Shortcut.OPEN_DOWNLOAD_DIALOG, ShortcutSection.ACTIONS,
    'Open download overlay');
_describe(Shortcut.EXPAND_ALL_MESSAGES, ShortcutSection.ACTIONS,
    'Expand all messages');
_describe(Shortcut.COLLAPSE_ALL_MESSAGES, ShortcutSection.ACTIONS,
    'Collapse all messages');
_describe(Shortcut.REFRESH_CHANGE, ShortcutSection.ACTIONS,
    'Reload the change at the latest patch');
_describe(Shortcut.TOGGLE_CHANGE_REVIEWED, ShortcutSection.ACTIONS,
    'Mark/unmark change as reviewed');
_describe(Shortcut.TOGGLE_FILE_REVIEWED, ShortcutSection.ACTIONS,
    'Toggle review flag on selected file');
_describe(Shortcut.REFRESH_CHANGE_LIST, ShortcutSection.ACTIONS,
    'Refresh list of changes');
_describe(Shortcut.TOGGLE_CHANGE_STAR, ShortcutSection.ACTIONS,
    'Star/unstar change');
_describe(Shortcut.EDIT_TOPIC, ShortcutSection.ACTIONS,
    'Add a change topic');
_describe(Shortcut.DIFF_AGAINST_BASE, ShortcutSection.ACTIONS,
    'Diff against base');
_describe(Shortcut.DIFF_AGAINST_LATEST, ShortcutSection.ACTIONS,
    'Diff against latest patchset');
_describe(Shortcut.DIFF_BASE_AGAINST_LEFT, ShortcutSection.ACTIONS,
    'Diff base against left');
_describe(Shortcut.DIFF_RIGHT_AGAINST_LATEST, ShortcutSection.ACTIONS,
    'Diff right against latest');
_describe(Shortcut.DIFF_BASE_AGAINST_LATEST, ShortcutSection.ACTIONS,
    'Diff base against latest');

_describe(Shortcut.NEXT_LINE, ShortcutSection.DIFFS, 'Go to next line');
_describe(Shortcut.PREV_LINE, ShortcutSection.DIFFS, 'Go to previous line');
_describe(Shortcut.DIFF_AGAINST_BASE, ShortcutSection.DIFFS,
    'Diff against base');
_describe(Shortcut.DIFF_AGAINST_LATEST, ShortcutSection.DIFFS,
    'Diff against latest patchset');
_describe(Shortcut.DIFF_BASE_AGAINST_LEFT, ShortcutSection.DIFFS,
    'Diff base against left');
_describe(Shortcut.DIFF_RIGHT_AGAINST_LATEST, ShortcutSection.DIFFS,
    'Diff right against latest');
_describe(Shortcut.DIFF_BASE_AGAINST_LATEST, ShortcutSection.DIFFS,
    'Diff base against latest');
_describe(Shortcut.VISIBLE_LINE, ShortcutSection.DIFFS,
    'Move cursor to currently visible code');
_describe(Shortcut.NEXT_CHUNK, ShortcutSection.DIFFS,
    'Go to next diff chunk');
_describe(Shortcut.PREV_CHUNK, ShortcutSection.DIFFS,
    'Go to previous diff chunk');
_describe(Shortcut.EXPAND_ALL_DIFF_CONTEXT, ShortcutSection.DIFFS,
    'Expand all diff context');
_describe(Shortcut.NEXT_COMMENT_THREAD, ShortcutSection.DIFFS,
    'Go to next comment thread');
_describe(Shortcut.PREV_COMMENT_THREAD, ShortcutSection.DIFFS,
    'Go to previous comment thread');
_describe(Shortcut.EXPAND_ALL_COMMENT_THREADS, ShortcutSection.DIFFS,
    'Expand all comment threads');
_describe(Shortcut.COLLAPSE_ALL_COMMENT_THREADS, ShortcutSection.DIFFS,
    'Collapse all comment threads');
_describe(Shortcut.TOGGLE_HIDE_ALL_COMMENT_THREADS, ShortcutSection.DIFFS,
    'Hide/Display all comment threads');
_describe(Shortcut.LEFT_PANE, ShortcutSection.DIFFS, 'Select left pane');
_describe(Shortcut.RIGHT_PANE, ShortcutSection.DIFFS, 'Select right pane');
_describe(Shortcut.TOGGLE_LEFT_PANE, ShortcutSection.DIFFS,
    'Hide/show left diff');
_describe(Shortcut.NEW_COMMENT, ShortcutSection.DIFFS, 'Draft new comment');
_describe(Shortcut.SAVE_COMMENT, ShortcutSection.DIFFS, 'Save comment');
_describe(Shortcut.OPEN_DIFF_PREFS, ShortcutSection.DIFFS,
    'Show diff preferences');
_describe(Shortcut.TOGGLE_DIFF_REVIEWED, ShortcutSection.DIFFS,
    'Mark/unmark file as reviewed');
_describe(Shortcut.TOGGLE_DIFF_MODE, ShortcutSection.DIFFS,
    'Toggle unified/side-by-side diff');
_describe(Shortcut.NEXT_UNREVIEWED_FILE, ShortcutSection.DIFFS,
    'Mark file as reviewed and go to next unreviewed file');
_describe(Shortcut.TOGGLE_BLAME, ShortcutSection.DIFFS, 'Toggle blame');

_describe(Shortcut.NEXT_FILE, ShortcutSection.NAVIGATION, 'Go to next file');
_describe(Shortcut.PREV_FILE, ShortcutSection.NAVIGATION,
    'Go to previous file');
_describe(Shortcut.NEXT_FILE_WITH_COMMENTS, ShortcutSection.NAVIGATION,
    'Go to next file that has comments');
_describe(Shortcut.PREV_FILE_WITH_COMMENTS, ShortcutSection.NAVIGATION,
    'Go to previous file that has comments');
_describe(Shortcut.OPEN_FIRST_FILE, ShortcutSection.NAVIGATION,
    'Go to first file');
_describe(Shortcut.OPEN_LAST_FILE, ShortcutSection.NAVIGATION,
    'Go to last file');
_describe(Shortcut.UP_TO_DASHBOARD, ShortcutSection.NAVIGATION,
    'Up to dashboard');
_describe(Shortcut.UP_TO_CHANGE, ShortcutSection.NAVIGATION, 'Up to change');

_describe(Shortcut.CURSOR_NEXT_FILE, ShortcutSection.FILE_LIST,
    'Select next file');
_describe(Shortcut.CURSOR_PREV_FILE, ShortcutSection.FILE_LIST,
    'Select previous file');
_describe(Shortcut.OPEN_FILE, ShortcutSection.FILE_LIST,
    'Go to selected file');
_describe(Shortcut.TOGGLE_ALL_INLINE_DIFFS, ShortcutSection.FILE_LIST,
    'Show/hide all inline diffs');
_describe(Shortcut.TOGGLE_HIDE_ALL_COMMENT_THREADS, ShortcutSection.FILE_LIST,
    'Hide/Display all comment threads');
_describe(Shortcut.TOGGLE_INLINE_DIFF, ShortcutSection.FILE_LIST,
    'Show/hide selected inline diff');

_describe(Shortcut.SEND_REPLY, ShortcutSection.REPLY_DIALOG, 'Send reply');
_describe(Shortcut.EMOJI_DROPDOWN, ShortcutSection.REPLY_DIALOG,
    'Emoji dropdown');

// Must be declared outside behavior implementation to be accessed inside
// behavior functions.

/** @return {!(Event|PolymerDomApi|PolymerEventApi)} */
const getKeyboardEvent = function(e) {
  e = dom(e.detail ? e.detail.keyboardEvent : e);
  // When e is a keyboardEvent, e.event is not null.
  if (e.event) { e = e.event; }
  return e;
};

export class ShortcutManager {
  constructor() {
    this.activeHosts = new Map();
    this.bindings = new Map();
    this.listeners = new Set();
  }

  bindShortcut(shortcut, ...bindings) {
    this.bindings.set(shortcut, bindings);
  }

  getBindingsForShortcut(shortcut) {
    return this.bindings.get(shortcut);
  }

  attachHost(host) {
    if (!host.keyboardShortcuts) { return; }
    const shortcuts = host.keyboardShortcuts();
    this.activeHosts.set(host, new Map(Object.entries(shortcuts)));
    this.notifyListeners();
    return shortcuts;
  }

  detachHost(host) {
    if (this.activeHosts.delete(host)) {
      this.notifyListeners();
      return true;
    }
    return false;
  }

  addListener(listener) {
    this.listeners.add(listener);
    listener(this.directoryView());
  }

  removeListener(listener) {
    return this.listeners.delete(listener);
  }

  getDescription(section, shortcutName) {
    const binding =
        _help.get(section).find(binding => binding.shortcut == shortcutName);
    return binding ? binding.text : '';
  }

  getShortcut(shortcutName) {
    const binding = this.bindings.get(shortcutName);
    return binding ? this.describeBinding(binding) : '';
  }

  activeShortcutsBySection() {
    const activeShortcuts = new Set();
    this.activeHosts.forEach(shortcuts => {
      shortcuts.forEach((_, shortcut) => activeShortcuts.add(shortcut));
    });

    const activeShortcutsBySection = new Map();
    _help.forEach((shortcutList, section) => {
      shortcutList.forEach(shortcutHelp => {
        if (activeShortcuts.has(shortcutHelp.shortcut)) {
          if (!activeShortcutsBySection.has(section)) {
            activeShortcutsBySection.set(section, []);
          }
          activeShortcutsBySection.get(section).push(shortcutHelp);
        }
      });
    });
    return activeShortcutsBySection;
  }

  directoryView() {
    const view = new Map();
    this.activeShortcutsBySection().forEach((shortcutHelps, section) => {
      const sectionView = [];
      shortcutHelps.forEach(shortcutHelp => {
        const bindingDesc = this.describeBindings(shortcutHelp.shortcut);
        if (!bindingDesc) { return; }
        this.distributeBindingDesc(bindingDesc).forEach(bindingDesc => {
          sectionView.push({
            binding: bindingDesc,
            text: shortcutHelp.text,
          });
        });
      });
      view.set(section, sectionView);
    });
    return view;
  }

  distributeBindingDesc(bindingDesc) {
    if (bindingDesc.length === 1 ||
        this.comboSetDisplayWidth(bindingDesc) < 21) {
      return [bindingDesc];
    }
    // Find the largest prefix of bindings that is under the
    // size threshold.
    const head = [bindingDesc[0]];
    for (let i = 1; i < bindingDesc.length; i++) {
      head.push(bindingDesc[i]);
      if (this.comboSetDisplayWidth(head) >= 21) {
        head.pop();
        return [head].concat(
            this.distributeBindingDesc(bindingDesc.slice(i)));
      }
    }
  }

  comboSetDisplayWidth(bindingDesc) {
    const bindingSizer = binding => binding.reduce(
        (acc, key) => acc + key.length, 0);
    // Width is the sum of strings + (n-1) * 2 to account for the word
    // "or" joining them.
    return bindingDesc.reduce(
        (acc, binding) => acc + bindingSizer(binding), 0) +
        2 * (bindingDesc.length - 1);
  }

  describeBindings(shortcut) {
    const bindings = this.bindings.get(shortcut);
    if (!bindings) { return null; }
    if (bindings[0] === SPECIAL_SHORTCUT.GO_KEY) {
      return bindings.slice(1).map(
          binding => this._describeKey(binding)
      )
          .map(binding => ['g'].concat(binding));
    }
    if (bindings[0] === SPECIAL_SHORTCUT.V_KEY) {
      return bindings.slice(1).map(
          binding => this._describeKey(binding)
      )
          .map(binding => ['v'].concat(binding));
    }
    return bindings
        .filter(binding => binding !== SPECIAL_SHORTCUT.DOC_ONLY)
        .map(binding => this.describeBinding(binding));
  }

  _describeKey(key) {
    switch (key) {
      case 'shift':
        return 'Shift';
      case 'meta':
        return 'Meta';
      case 'ctrl':
        return 'Ctrl';
      case 'enter':
        return 'Enter';
      case 'up':
        return '\u2191'; // ↑
      case 'down':
        return '\u2193'; // ↓
      case 'left':
        return '\u2190'; // ←
      case 'right':
        return '\u2192'; // →
      default:
        return key;
    }
  }

  describeBinding(binding) {
    if (binding.length === 1) {
      return [binding];
    }
    return binding.split(':')[0].split('+').map(part =>
      this._describeKey(part)
    );
  }

  notifyListeners() {
    const view = this.directoryView();
    this.listeners.forEach(listener => listener(view));
  }
}

const shortcutManager = new ShortcutManager();

/**
 * @polymer
 * @mixinFunction
 */
const InternalKeyboardShortcutMixin = dedupingMixin(superClass => {
  /**
   * @polymer
   * @mixinClass
   */
  class Mixin extends superClass {
    static get properties() {
      return {
        _shortcut_go_key_last_pressed: {
          type: Number,
          value: null,
        },
        _shortcut_v_key_last_pressed: {
          type: Number,
          value: null,
        },
        _shortcut_go_table: {
          type: Array,
          value() {
            return new Map();
          },
        },
        _shortcut_v_table: {
          type: Array,
          value() {
            return new Map();
          },
        },
      };
    }

    modifierPressed(e) {
      /* We are checking for g/v as modifiers pressed. There are cases such as
       * pressing v and then /, where we want the handler for / to be triggered.
       * TODO(dhruvsri): find a way to support that keyboard combination
       */
      e = getKeyboardEvent(e);
      return e.altKey || e.ctrlKey || e.metaKey || e.shiftKey ||
        !!this._inGoKeyMode() || !!this._inVKeyMode();
    }

    isModifierPressed(e, modifier) {
      return getKeyboardEvent(e)[modifier];
    }

    shouldSuppressKeyboardShortcut(e) {
      e = getKeyboardEvent(e);
      const tagName = dom(e).rootTarget.tagName;
      if (tagName === 'INPUT' || tagName === 'TEXTAREA' ||
          (e.keyCode === 13 && tagName === 'A')) {
        // Suppress shortcuts if the key is 'enter' and target is an anchor.
        return true;
      }
      for (let i = 0; e.path && i < e.path.length; i++) {
        if (e.path[i].tagName === 'GR-OVERLAY') { return true; }
      }

      this.dispatchEvent(new CustomEvent('shortcut-triggered', {
        detail: {
          event: e,
          goKey: this._inGoKeyMode(),
          vKey: this._inVKeyMode(),
        },
        composed: true, bubbles: true,
      }));
      return false;
    }

    // Alias for getKeyboardEvent.
    /** @return {!Event} */
    getKeyboardEvent(e) {
      return getKeyboardEvent(e);
    }

    getRootTarget(e) {
      return dom(getKeyboardEvent(e)).rootTarget;
    }

    bindShortcut(shortcut, ...bindings) {
      shortcutManager.bindShortcut(shortcut, ...bindings);
    }

    createTitle(shortcutName, section) {
      const desc = shortcutManager.getDescription(section, shortcutName);
      const shortcut = shortcutManager.getShortcut(shortcutName);
      return (desc && shortcut) ? `${desc} (shortcut: ${shortcut})` : '';
    }

    _addOwnKeyBindings(shortcut, handler) {
      const bindings = shortcutManager.getBindingsForShortcut(shortcut);
      if (!bindings) {
        return;
      }
      if (bindings[0] === SPECIAL_SHORTCUT.DOC_ONLY) {
        return;
      }
      if (bindings[0] === SPECIAL_SHORTCUT.GO_KEY) {
        bindings.slice(1).forEach(binding =>
          this._shortcut_go_table.set(binding, handler));
      } else if (bindings[0] === SPECIAL_SHORTCUT.V_KEY) {
        // for each binding added with the go/v key, we set the handler to be
        // handleVKeyAction. handleVKeyAction then looks up in th
        // shortcut_table to see what the relevant handler should be
        bindings.slice(1).forEach(binding =>
          this._shortcut_v_table.set(binding, handler));
      } else {
        this.addOwnKeyBinding(bindings.join(' '), handler);
      }
    }

    ready() {
      super.ready();
    }

    /** @override */
    connectedCallback() {
      super.connectedCallback();
      const shortcuts = shortcutManager.attachHost(this);
      if (!shortcuts) { return; }

      for (const key of Object.keys(shortcuts)) {
        this._addOwnKeyBindings(key, shortcuts[key]);
      }

      // each component that uses this behaviour must be aware if go key is
      // pressed or not, since it needs to check it as a modifier
      this.addOwnKeyBinding('g:keydown', '_handleGoKeyDown');
      this.addOwnKeyBinding('g:keyup', '_handleGoKeyUp');

      // If any of the shortcuts utilized GO_KEY, then they are handled
      // directly by this behavior.
      if (this._shortcut_go_table.size > 0) {
        this._shortcut_go_table.forEach((handler, key) => {
          this.addOwnKeyBinding(key, '_handleGoAction');
        });
      }

      this.addOwnKeyBinding('v:keydown', '_handleVKeyDown');
      this.addOwnKeyBinding('v:keyup', '_handleVKeyUp');
      if (this._shortcut_v_table.size > 0) {
        this._shortcut_v_table.forEach((handler, key) => {
          this.addOwnKeyBinding(key, '_handleVAction');
        });
      }
    }

    /** @override */
    disconnectedCallback() {
      super.disconnectedCallback();
      if (shortcutManager.detachHost(this)) {
        this.removeOwnKeyBindings();
      }
    }

    keyboardShortcuts() {
      return {};
    }

    addKeyboardShortcutDirectoryListener(listener) {
      shortcutManager.addListener(listener);
    }

    removeKeyboardShortcutDirectoryListener(listener) {
      shortcutManager.removeListener(listener);
    }

    _handleVKeyDown(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) return;
      this._shortcut_v_key_last_pressed = Date.now();
    }

    _handleVKeyUp(e) {
      setTimeout(() => {
        this._shortcut_v_key_last_pressed = null;
      }, V_KEY_TIMEOUT_MS);
    }

    _inVKeyMode() {
      return this._shortcut_v_key_last_pressed &&
          (Date.now() - this._shortcut_v_key_last_pressed <=
              V_KEY_TIMEOUT_MS);
    }

    _handleVAction(e) {
      if (!this._inVKeyMode() ||
          !this._shortcut_v_table.has(e.detail.key) ||
          this.shouldSuppressKeyboardShortcut(e)) {
        return;
      }
      e.preventDefault();
      const handler = this._shortcut_v_table.get(e.detail.key);
      this[handler](e);
    }

    _handleGoKeyDown(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) return;
      this._shortcut_go_key_last_pressed = Date.now();
    }

    _handleGoKeyUp(e) {
      // Set go_key_last_pressed to null `GO_KEY_TIMEOUT_MS` after keyup event
      // so that users can trigger `g + i` by pressing g and i quickly.
      setTimeout(() => {
        this._shortcut_go_key_last_pressed = null;
      }, GO_KEY_TIMEOUT_MS);
    }

    _inGoKeyMode() {
      return this._shortcut_go_key_last_pressed &&
          (Date.now() - this._shortcut_go_key_last_pressed <=
              GO_KEY_TIMEOUT_MS);
    }

    _handleGoAction(e) {
      if (!this._inGoKeyMode() ||
          !this._shortcut_go_table.has(e.detail.key) ||
          this.shouldSuppressKeyboardShortcut(e)) {
        return;
      }
      e.preventDefault();
      const handler = this._shortcut_go_table.get(e.detail.key);
      this[handler](e);
    }
  }

  return Mixin;
});

// The following doesn't work (IronA11yKeysBehavior crashes):
// const KeyboardShortcutMixin = dedupingMixin(superClass => {
//    class Mixin extends mixinBehaviors([IronA11yKeysBehavior], superClass) {
//    ...
//    }
//    return Mixin;
// }
// This is a workaround
export const KeyboardShortcutMixin = superClass =>
  InternalKeyboardShortcutMixin(
      mixinBehaviors([IronA11yKeysBehavior], superClass));

export function _testOnly_getShortcutManagerInstance() {
  return shortcutManager;
}
