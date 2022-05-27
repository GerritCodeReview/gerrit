/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ReactiveController, ReactiveControllerHost} from 'lit';
import {Binding} from '../../utils/dom-util';
import {ShortcutsService} from '../../services/shortcuts/shortcuts-service';
import {getAppContext} from '../../services/app-context';
import {Shortcut} from '../../services/shortcuts/shortcuts-config';

interface ShortcutListener {
  binding: Binding;
  listener: (e: KeyboardEvent) => void;
}

interface AbstractListener {
  shortcut: Shortcut;
  listener: (e: KeyboardEvent) => void;
}

type Cleanup = () => void;

export class ShortcutController implements ReactiveController {
  private readonly service: ShortcutsService = getAppContext().shortcutsService;

  private readonly listenersLocal: ShortcutListener[] = [];

  private readonly listenersGlobal: ShortcutListener[] = [];

  private readonly listenersAbstract: AbstractListener[] = [];

  private cleanups: Cleanup[] = [];

  constructor(private readonly host: ReactiveControllerHost & HTMLElement) {
    host.addController(this);
  }

  // Note that local shortcuts are *not* suppressed when the user has shortcuts
  // disabled or when the event comes from elements like <input>. So this method
  // is intended for shortcuts like ESC and Ctrl-ENTER.
  // If you need suppressed local shortcuts, then just add an options parameter.
  addLocal(binding: Binding, listener: (e: KeyboardEvent) => void) {
    this.listenersLocal.push({binding, listener});
  }

  addGlobal(binding: Binding, listener: (e: KeyboardEvent) => void) {
    this.listenersGlobal.push({binding, listener});
  }

  /**
   * `Shortcut` is more abstract than a concrete `Binding`. A `Shortcut` has a
   * description text and (several) bindings configured in the file
   * `shortcuts-config.ts`.
   *
   * Use this method when you are migrating from Polymer to Lit. Call it for
   * each entry of keyboardShortcuts().
   */
  addAbstract(shortcut: Shortcut, listener: (e: KeyboardEvent) => void) {
    this.listenersAbstract.push({shortcut, listener});
  }

  hostConnected() {
    for (const {binding, listener} of this.listenersLocal) {
      const cleanup = this.service.addShortcut(this.host, binding, listener, {
        shouldSuppress: false,
      });
      this.cleanups.push(cleanup);
    }
    for (const {shortcut, listener} of this.listenersAbstract) {
      const cleanup = this.service.addShortcutListener(shortcut, listener);
      this.cleanups.push(cleanup);
    }
    for (const {binding, listener} of this.listenersGlobal) {
      const cleanup = this.service.addShortcut(
        document.body,
        binding,
        listener
      );
      this.cleanups.push(cleanup);
    }
  }

  hostDisconnected() {
    for (const cleanup of this.cleanups) {
      cleanup();
    }
    this.cleanups = [];
  }
}
