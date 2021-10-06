/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
 * Enum for all shortcut sections, where that shortcut should be applied to.
 */
import {SPECIAL_SHORTCUT} from './shortcuts-service';

export enum ShortcutSection {
  ACTIONS = 'Actions',
  DIFFS = 'Diffs',
  EVERYWHERE = 'Global Shortcuts',
  FILE_LIST = 'File list',
  NAVIGATION = 'Navigation',
  REPLY_DIALOG = 'Reply dialog',
}

/**
 * Enum for all possible shortcut names.
 */
export enum Shortcut {
  OPEN_SHORTCUT_HELP_DIALOG = 'OPEN_SHORTCUT_HELP_DIALOG',
  GO_TO_USER_DASHBOARD = 'GO_TO_USER_DASHBOARD',
  GO_TO_OPENED_CHANGES = 'GO_TO_OPENED_CHANGES',
  GO_TO_MERGED_CHANGES = 'GO_TO_MERGED_CHANGES',
  GO_TO_ABANDONED_CHANGES = 'GO_TO_ABANDONED_CHANGES',
  GO_TO_WATCHED_CHANGES = 'GO_TO_WATCHED_CHANGES',

  CURSOR_NEXT_CHANGE = 'CURSOR_NEXT_CHANGE',
  CURSOR_PREV_CHANGE = 'CURSOR_PREV_CHANGE',
  OPEN_CHANGE = 'OPEN_CHANGE',
  NEXT_PAGE = 'NEXT_PAGE',
  PREV_PAGE = 'PREV_PAGE',
  TOGGLE_CHANGE_REVIEWED = 'TOGGLE_CHANGE_REVIEWED',
  TOGGLE_CHANGE_STAR = 'TOGGLE_CHANGE_STAR',
  REFRESH_CHANGE_LIST = 'REFRESH_CHANGE_LIST',
  OPEN_SUBMIT_DIALOG = 'OPEN_SUBMIT_DIALOG',
  TOGGLE_ATTENTION_SET = 'TOGGLE_ATTENTION_SET',

  OPEN_REPLY_DIALOG = 'OPEN_REPLY_DIALOG',
  OPEN_DOWNLOAD_DIALOG = 'OPEN_DOWNLOAD_DIALOG',
  EXPAND_ALL_MESSAGES = 'EXPAND_ALL_MESSAGES',
  COLLAPSE_ALL_MESSAGES = 'COLLAPSE_ALL_MESSAGES',
  UP_TO_DASHBOARD = 'UP_TO_DASHBOARD',
  UP_TO_CHANGE = 'UP_TO_CHANGE',
  TOGGLE_DIFF_MODE = 'TOGGLE_DIFF_MODE',
  REFRESH_CHANGE = 'REFRESH_CHANGE',
  EDIT_TOPIC = 'EDIT_TOPIC',
  DIFF_AGAINST_BASE = 'DIFF_AGAINST_BASE',
  DIFF_AGAINST_LATEST = 'DIFF_AGAINST_LATEST',
  DIFF_BASE_AGAINST_LEFT = 'DIFF_BASE_AGAINST_LEFT',
  DIFF_RIGHT_AGAINST_LATEST = 'DIFF_RIGHT_AGAINST_LATEST',
  DIFF_BASE_AGAINST_LATEST = 'DIFF_BASE_AGAINST_LATEST',

  NEXT_LINE = 'NEXT_LINE',
  PREV_LINE = 'PREV_LINE',
  VISIBLE_LINE = 'VISIBLE_LINE',
  NEXT_CHUNK = 'NEXT_CHUNK',
  PREV_CHUNK = 'PREV_CHUNK',
  TOGGLE_ALL_DIFF_CONTEXT = 'TOGGLE_ALL_DIFF_CONTEXT',
  NEXT_COMMENT_THREAD = 'NEXT_COMMENT_THREAD',
  PREV_COMMENT_THREAD = 'PREV_COMMENT_THREAD',
  EXPAND_ALL_COMMENT_THREADS = 'EXPAND_ALL_COMMENT_THREADS',
  COLLAPSE_ALL_COMMENT_THREADS = 'COLLAPSE_ALL_COMMENT_THREADS',
  LEFT_PANE = 'LEFT_PANE',
  RIGHT_PANE = 'RIGHT_PANE',
  TOGGLE_LEFT_PANE = 'TOGGLE_LEFT_PANE',
  NEW_COMMENT = 'NEW_COMMENT',
  SAVE_COMMENT = 'SAVE_COMMENT',
  OPEN_DIFF_PREFS = 'OPEN_DIFF_PREFS',
  TOGGLE_DIFF_REVIEWED = 'TOGGLE_DIFF_REVIEWED',

  NEXT_FILE = 'NEXT_FILE',
  PREV_FILE = 'PREV_FILE',
  NEXT_FILE_WITH_COMMENTS = 'NEXT_FILE_WITH_COMMENTS',
  PREV_FILE_WITH_COMMENTS = 'PREV_FILE_WITH_COMMENTS',
  NEXT_UNREVIEWED_FILE = 'NEXT_UNREVIEWED_FILE',
  CURSOR_NEXT_FILE = 'CURSOR_NEXT_FILE',
  CURSOR_PREV_FILE = 'CURSOR_PREV_FILE',
  OPEN_FILE = 'OPEN_FILE',
  TOGGLE_FILE_REVIEWED = 'TOGGLE_FILE_REVIEWED',
  TOGGLE_ALL_INLINE_DIFFS = 'TOGGLE_ALL_INLINE_DIFFS',
  TOGGLE_INLINE_DIFF = 'TOGGLE_INLINE_DIFF',
  TOGGLE_HIDE_ALL_COMMENT_THREADS = 'TOGGLE_HIDE_ALL_COMMENT_THREADS',
  OPEN_FILE_LIST = 'OPEN_FILE_LIST',

  OPEN_FIRST_FILE = 'OPEN_FIRST_FILE',
  OPEN_LAST_FILE = 'OPEN_LAST_FILE',

  SEARCH = 'SEARCH',
  SEND_REPLY = 'SEND_REPLY',
  EMOJI_DROPDOWN = 'EMOJI_DROPDOWN',
  TOGGLE_BLAME = 'TOGGLE_BLAME',
}

export interface ShortcutHelpItem {
  shortcut: Shortcut;
  text: string;
  bindings: string[];
}

export const config = new Map<ShortcutSection, ShortcutHelpItem[]>();

function describe(
  shortcut: Shortcut,
  section: ShortcutSection,
  text: string,
  binding: string,
  ...moreBindings: string[]
) {
  if (!config.has(section)) {
    config.set(section, []);
  }
  const shortcuts = config.get(section);
  if (shortcuts) {
    shortcuts.push({shortcut, text, bindings: [binding, ...moreBindings]});
  }
}

describe(Shortcut.SEARCH, ShortcutSection.EVERYWHERE, 'Search', '/');
describe(
  Shortcut.OPEN_SHORTCUT_HELP_DIALOG,
  ShortcutSection.EVERYWHERE,
  'Show this dialog',
  '?'
);
describe(
  Shortcut.GO_TO_USER_DASHBOARD,
  ShortcutSection.EVERYWHERE,
  'Go to User Dashboard',
  SPECIAL_SHORTCUT.GO_KEY,
  'i'
);
describe(
  Shortcut.GO_TO_OPENED_CHANGES,
  ShortcutSection.EVERYWHERE,
  'Go to Opened Changes',
  SPECIAL_SHORTCUT.GO_KEY,
  'o'
);
describe(
  Shortcut.GO_TO_MERGED_CHANGES,
  ShortcutSection.EVERYWHERE,
  'Go to Merged Changes',
  SPECIAL_SHORTCUT.GO_KEY,
  'm'
);
describe(
  Shortcut.GO_TO_ABANDONED_CHANGES,
  ShortcutSection.EVERYWHERE,
  'Go to Abandoned Changes',
  SPECIAL_SHORTCUT.GO_KEY,
  'a'
);
describe(
  Shortcut.GO_TO_WATCHED_CHANGES,
  ShortcutSection.EVERYWHERE,
  'Go to Watched Changes',
  SPECIAL_SHORTCUT.GO_KEY,
  'w'
);

describe(
  Shortcut.CURSOR_NEXT_CHANGE,
  ShortcutSection.ACTIONS,
  'Select next change',
  'j'
);
describe(
  Shortcut.CURSOR_PREV_CHANGE,
  ShortcutSection.ACTIONS,
  'Select previous change',
  'k'
);
describe(
  Shortcut.OPEN_CHANGE,
  ShortcutSection.ACTIONS,
  'Show selected change',
  'o'
);
describe(
  Shortcut.NEXT_PAGE,
  ShortcutSection.ACTIONS,
  'Go to next page',
  'n',
  ']'
);
describe(
  Shortcut.PREV_PAGE,
  ShortcutSection.ACTIONS,
  'Go to previous page',
  'p',
  '['
);
describe(
  Shortcut.OPEN_REPLY_DIALOG,
  ShortcutSection.ACTIONS,
  'Open reply dialog to publish comments and add reviewers',
  'a:keyup'
);
describe(
  Shortcut.OPEN_DOWNLOAD_DIALOG,
  ShortcutSection.ACTIONS,
  'Open download overlay',
  'd:keyup'
);
describe(
  Shortcut.EXPAND_ALL_MESSAGES,
  ShortcutSection.ACTIONS,
  'Expand all messages',
  'x'
);
describe(
  Shortcut.COLLAPSE_ALL_MESSAGES,
  ShortcutSection.ACTIONS,
  'Collapse all messages',
  'z'
);
describe(
  Shortcut.REFRESH_CHANGE,
  ShortcutSection.ACTIONS,
  'Reload the change at the latest patch',
  'shift+r:keyup'
);
describe(
  Shortcut.TOGGLE_CHANGE_REVIEWED,
  ShortcutSection.ACTIONS,
  'Mark/unmark change as reviewed',
  'r:keyup'
);
describe(
  Shortcut.TOGGLE_FILE_REVIEWED,
  ShortcutSection.ACTIONS,
  'Toggle review flag on selected file',
  'r:keyup'
);
describe(
  Shortcut.REFRESH_CHANGE_LIST,
  ShortcutSection.ACTIONS,
  'Refresh list of changes',
  'shift+r:keyup'
);
describe(
  Shortcut.TOGGLE_CHANGE_STAR,
  ShortcutSection.ACTIONS,
  'Star/unstar change',
  's:keydown'
);
describe(
  Shortcut.OPEN_SUBMIT_DIALOG,
  ShortcutSection.ACTIONS,
  'Open submit dialog',
  'shift+s'
);
describe(
  Shortcut.TOGGLE_ATTENTION_SET,
  ShortcutSection.ACTIONS,
  'Toggle attention set status',
  'shift+t'
);
describe(
  Shortcut.EDIT_TOPIC,
  ShortcutSection.ACTIONS,
  'Add a change topic',
  't'
);
describe(
  Shortcut.DIFF_AGAINST_BASE,
  ShortcutSection.DIFFS,
  'Diff against base',
  SPECIAL_SHORTCUT.V_KEY,
  'down',
  's'
);
describe(
  Shortcut.DIFF_AGAINST_LATEST,
  ShortcutSection.DIFFS,
  'Diff against latest patchset',
  SPECIAL_SHORTCUT.V_KEY,
  'up',
  'w'
);
describe(
  Shortcut.DIFF_BASE_AGAINST_LEFT,
  ShortcutSection.DIFFS,
  'Diff base against left',
  SPECIAL_SHORTCUT.V_KEY,
  'left',
  'a'
);
describe(
  Shortcut.DIFF_RIGHT_AGAINST_LATEST,
  ShortcutSection.DIFFS,
  'Diff right against latest',
  SPECIAL_SHORTCUT.V_KEY,
  'right',
  'd'
);
describe(
  Shortcut.DIFF_BASE_AGAINST_LATEST,
  ShortcutSection.DIFFS,
  'Diff base against latest',
  SPECIAL_SHORTCUT.V_KEY,
  'b'
);

describe(
  Shortcut.NEXT_LINE,
  ShortcutSection.DIFFS,
  'Go to next line',
  'j',
  'down'
);
describe(
  Shortcut.PREV_LINE,
  ShortcutSection.DIFFS,
  'Go to previous line',
  'k',
  'up'
);
describe(
  Shortcut.VISIBLE_LINE,
  ShortcutSection.DIFFS,
  'Move cursor to currently visible code',
  '.'
);
describe(
  Shortcut.NEXT_CHUNK,
  ShortcutSection.DIFFS,
  'Go to next diff chunk',
  'n'
);
describe(
  Shortcut.PREV_CHUNK,
  ShortcutSection.DIFFS,
  'Go to previous diff chunk',
  'p'
);
describe(
  Shortcut.TOGGLE_ALL_DIFF_CONTEXT,
  ShortcutSection.DIFFS,
  'Toggle all diff context',
  'shift+x'
);
describe(
  Shortcut.NEXT_COMMENT_THREAD,
  ShortcutSection.DIFFS,
  'Go to next comment thread',
  'shift+n'
);
describe(
  Shortcut.PREV_COMMENT_THREAD,
  ShortcutSection.DIFFS,
  'Go to previous comment thread',
  'shift+p'
);
describe(
  Shortcut.EXPAND_ALL_COMMENT_THREADS,
  ShortcutSection.DIFFS,
  'Expand all comment threads',
  SPECIAL_SHORTCUT.DOC_ONLY,
  'e'
);
describe(
  Shortcut.COLLAPSE_ALL_COMMENT_THREADS,
  ShortcutSection.DIFFS,
  'Collapse all comment threads',
  SPECIAL_SHORTCUT.DOC_ONLY,
  'shift+e'
);
describe(
  Shortcut.TOGGLE_HIDE_ALL_COMMENT_THREADS,
  ShortcutSection.DIFFS,
  'Hide/Display all comment threads',
  'h'
);
describe(
  Shortcut.LEFT_PANE,
  ShortcutSection.DIFFS,
  'Select left pane',
  'shift+left'
);
describe(
  Shortcut.RIGHT_PANE,
  ShortcutSection.DIFFS,
  'Select right pane',
  'shift+right'
);
describe(
  Shortcut.TOGGLE_LEFT_PANE,
  ShortcutSection.DIFFS,
  'Hide/show left diff',
  'shift+a'
);
describe(Shortcut.NEW_COMMENT, ShortcutSection.DIFFS, 'Draft new comment', 'c');
describe(
  Shortcut.SAVE_COMMENT,
  ShortcutSection.DIFFS,
  'Save comment',
  'ctrl+enter',
  'meta+enter',
  'ctrl+s',
  'meta+s'
);
describe(
  Shortcut.OPEN_DIFF_PREFS,
  ShortcutSection.DIFFS,
  'Show diff preferences',
  ','
);
describe(
  Shortcut.TOGGLE_DIFF_REVIEWED,
  ShortcutSection.DIFFS,
  'Mark/unmark file as reviewed',
  'r:keyup'
);
describe(
  Shortcut.TOGGLE_DIFF_MODE,
  ShortcutSection.DIFFS,
  'Toggle unified/side-by-side diff',
  'm:keyup'
);
describe(
  Shortcut.NEXT_UNREVIEWED_FILE,
  ShortcutSection.DIFFS,
  'Mark file as reviewed and go to next unreviewed file',
  'shift+m'
);
describe(
  Shortcut.TOGGLE_BLAME,
  ShortcutSection.DIFFS,
  'Toggle blame',
  'b:keyup'
);
describe(Shortcut.OPEN_FILE_LIST, ShortcutSection.DIFFS, 'Open file list', 'f');
describe(
  Shortcut.NEXT_FILE,
  ShortcutSection.NAVIGATION,
  'Go to next file',
  ']'
);
describe(
  Shortcut.PREV_FILE,
  ShortcutSection.NAVIGATION,
  'Go to previous file',
  '['
);
describe(
  Shortcut.NEXT_FILE_WITH_COMMENTS,
  ShortcutSection.NAVIGATION,
  'Go to next file that has comments',
  'shift+j'
);
describe(
  Shortcut.PREV_FILE_WITH_COMMENTS,
  ShortcutSection.NAVIGATION,
  'Go to previous file that has comments',
  'shift+k'
);
describe(
  Shortcut.OPEN_FIRST_FILE,
  ShortcutSection.NAVIGATION,
  'Go to first file',
  ']'
);
describe(
  Shortcut.OPEN_LAST_FILE,
  ShortcutSection.NAVIGATION,
  'Go to last file',
  '['
);
describe(
  Shortcut.UP_TO_DASHBOARD,
  ShortcutSection.NAVIGATION,
  'Up to dashboard',
  'u'
);
describe(
  Shortcut.UP_TO_CHANGE,
  ShortcutSection.NAVIGATION,
  'Up to change',
  'u'
);

describe(
  Shortcut.CURSOR_NEXT_FILE,
  ShortcutSection.FILE_LIST,
  'Select next file',
  'j',
  'down'
);
describe(
  Shortcut.CURSOR_PREV_FILE,
  ShortcutSection.FILE_LIST,
  'Select previous file',
  'k',
  'up'
);
describe(
  Shortcut.OPEN_FILE,
  ShortcutSection.FILE_LIST,
  'Go to selected file',
  'o',
  'enter'
);
describe(
  Shortcut.TOGGLE_ALL_INLINE_DIFFS,
  ShortcutSection.FILE_LIST,
  'Show/hide all inline diffs',
  'shift+i'
);
describe(
  Shortcut.TOGGLE_INLINE_DIFF,
  ShortcutSection.FILE_LIST,
  'Show/hide selected inline diff',
  'i'
);

describe(
  Shortcut.SEND_REPLY,
  ShortcutSection.REPLY_DIALOG,
  'Send reply',
  SPECIAL_SHORTCUT.DOC_ONLY,
  'ctrl+enter',
  'meta+enter'
);
describe(
  Shortcut.EMOJI_DROPDOWN,
  ShortcutSection.REPLY_DIALOG,
  'Emoji dropdown',
  SPECIAL_SHORTCUT.DOC_ONLY,
  ':'
);
