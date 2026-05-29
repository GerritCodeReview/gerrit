/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

export interface Updatable {
  requestUpdate(): void;
}

export class PeriodicUpdateManager<T extends Updatable = Updatable> {
  readonly components = new Set<T>();

  refreshTimer: ReturnType<typeof setInterval> | null = null;

  constructor(private readonly refreshIntervalMs: number) {}

  register(component: T) {
    this.components.add(component);
    if (this.refreshTimer === null) {
      this.refreshTimer = setInterval(() => {
        for (const c of this.components) {
          c.requestUpdate();
        }
      }, this.refreshIntervalMs);
    }
  }

  unregister(component: T) {
    this.components.delete(component);
    if (this.components.size === 0 && this.refreshTimer !== null) {
      clearInterval(this.refreshTimer);
      this.refreshTimer = null;
    }
  }

  // visible for testing
  _testOnly_getRefreshTimer() {
    return this.refreshTimer;
  }
}
