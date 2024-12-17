/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ReactiveController, ReactiveControllerHost} from 'lit';
import {Binding, ShortcutOptions} from '../../utils/dom-util';
import {shortcutsServiceToken} from '../../services/shortcuts/shortcuts-service';
import {Shortcut} from '../../services/shortcuts/shortcuts-config';
import {resolve} from '../../models/dependency';

export {Shortcut};
interface ShortcutListener {
  binding: Binding;
  listener: (e: KeyboardEvent) => void;
  options?: ShortcutOptions;
}

interface AbstractListener {
  shortcut: Shortcut;
  listener: (e: KeyboardEvent) => void;
  options?: ShortcutOptions;
}

type Cleanup = () => void;

export class ShortcutController implements ReactiveController {
  private readonly getShortcutsService;

  private readonly listenersLocal: ShortcutListener[] = [];

  private readonly listenersGlobal: ShortcutListener[] = [];

  private readonly listenersAbstract: AbstractListener[] = [];

  private cleanups: Cleanup[] = [];

  constructor(private readonly host: ReactiveControllerHost & HTMLElement) {
    this.getShortcutsService = resolve(this.host, shortcutsServiceToken);
    host.addController(this);
  }

  // Note that local shortcuts are *not* suppressed when the user has shortcuts
  // disabled or when the event comes from elements like <input>. So this method
  // is intended for shortcuts like ESC and Ctrl-ENTER.
  // Call method in constructor of the component
  addLocal(
    binding: Binding,
    listener: (e: KeyboardEvent) => void,
    options?: ShortcutOptions
  ) {
    this.listenersLocal.push({binding, listener, options});
  }

  // Call method in constructor of the component
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
   *
   * Call method in constructor of the component
   */
  addAbstract(
    shortcut: Shortcut,
    listener: (e: KeyboardEvent) => void,
    options?: ShortcutOptions
  ) {
    this.listenersAbstract.push({shortcut, listener, options});
  }

  hostConnected() {
    const shortcutsService = this.getShortcutsService();
    for (const {binding, listener, options} of this.listenersLocal) {
      const cleanup = shortcutsService.addShortcut(
        this.host,
        binding,
        listener,
        {
          shouldSuppress: options?.shouldSuppress ?? false,
          preventDefault: options?.preventDefault,
        }
      );
      this.cleanups.push(cleanup);
    }
    for (const {shortcut, listener, options} of this.listenersAbstract) {
      const cleanup = shortcutsService.addShortcutListener(
        shortcut,
        listener,
        options
      );
      this.cleanups.push(cleanup);
    }
    for (const {binding, listener} of this.listenersGlobal) {
      const cleanup = shortcutsService.addShortcut(
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
