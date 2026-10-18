/**
 * @license
 * Copyright 2026 Monty Taylor
 * SPDX-License-Identifier: Apache-2.0
 */
import {Model} from '../base/model';
import {define} from '../dependency';
import {select} from '../../utils/observable-util';
import {getBaseUrl} from '../../utils/url-util';

interface BrowserPreferencesState {
  threadedChanges: boolean;
}

export const browserPreferencesModelToken = define<BrowserPreferencesModel>(
  'browser-preferences-model'
);

/** Browser-local settings, deliberately separate from account PreferencesInfo. */
export class BrowserPreferencesModel extends Model<BrowserPreferencesState> {
  readonly threadedChanges$ = select(
    this.state$,
    state => state.threadedChanges
  );

  private readonly key = `gerrit:${getBaseUrl()}:threaded-changes`;

  constructor() {
    super({threadedChanges: true});
    this.read();
    window.addEventListener('storage', this.onStorage);
  }

  private read() {
    try {
      this.updateState({
        threadedChanges: window.localStorage.getItem(this.key) !== 'false',
      });
    } catch {
      // Storage may be disabled. The current in-memory preference still works.
    }
  }

  private readonly onStorage = (event: StorageEvent) => {
    if (event.key === null || event.key === this.key) this.read();
  };

  setThreadedChanges(threadedChanges: boolean) {
    this.updateState({threadedChanges});
    try {
      window.localStorage.setItem(this.key, String(threadedChanges));
    } catch {
      // Quota and security errors must not prevent changing the current view.
    }
  }

  override finalize() {
    window.removeEventListener('storage', this.onStorage);
    super.finalize();
  }
}
