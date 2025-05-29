/**
 * @license
 * Copyright Copyright 2024 The Chromium Authors
 * SPDX-License-Identifier: BSD-3-Clause
 */

import {assert} from '../../../utils/common-util.js';
import type {GrIconsetElement} from './gr-iconset';

let iconsetMap: IconsetMap | null = null;

export class IconsetMap extends EventTarget {
  private iconsets_: Map<string, GrIconsetElement> = new Map();

  static getInstance() {
    return iconsetMap || (iconsetMap = new IconsetMap());
  }

  static resetInstanceForTesting(instance: IconsetMap) {
    iconsetMap = instance;
  }

  get(id: string): GrIconsetElement | null {
    return this.iconsets_.get(id) || null;
  }

  set(id: string, iconset: GrIconsetElement) {
    assert(
      !this.iconsets_.has(id),
      `Tried to add a second iconset with id '${id}'`
    );
    this.iconsets_.set(id, iconset);
    this.dispatchEvent(new CustomEvent('cr-iconset-added', {detail: id}));
  }
}
