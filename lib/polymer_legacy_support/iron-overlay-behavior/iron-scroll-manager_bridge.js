/**
@license
Copyright (c) 2018 The Polymer Project Authors. All rights reserved.
This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE.txt
The complete set of authors may be found at http://polymer.github.io/AUTHORS.txt
The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS.txt
Code distributed by Google as part of the polymer project is also
subject to an additional IP rights grant found at http://polymer.github.io/PATENTS.txt
*/

/**
 * @fileoverview This file is a backwards-compatibility shim. Before Polymer
 *     converted to ES Modules, it wrote its API out onto the global Polymer
 *     object. The *_bridge.js files (like this one) maintain compatibility
 *     with that API.
 */

import '../polymer/lib/utils/boot_bridge.js';
import {elementIsScrollLocked, pushScrollLock, removeScrollLock, _lockingElements, _lockedElementCache, _unlockedElementCache, _hasCachedLockedElement, _hasCachedUnlockedElement, _composedTreeContains, _scrollInteractionHandler, _boundScrollHandler, _lockScrollInteractions, _unlockScrollInteractions, _shouldPreventScrolling, _getScrollableNodes, _getScrollingNode, _getScrollInfo} from '@polymer/iron-overlay-behavior/iron-scroll-manager.js';

Polymer.IronScrollManager = {};

/** @const */
Polymer.IronScrollManager.elementIsScrollLocked = elementIsScrollLocked;

/** @const */
Polymer.IronScrollManager.pushScrollLock = pushScrollLock;

/** @const */
Polymer.IronScrollManager.removeScrollLock = removeScrollLock;

/** @const */
Polymer.IronScrollManager._lockingElements = _lockingElements;

/** @const */
Polymer.IronScrollManager._lockedElementCache = _lockedElementCache;

/** @const */
Polymer.IronScrollManager._unlockedElementCache = _unlockedElementCache;

/** @const */
Polymer.IronScrollManager._hasCachedLockedElement = _hasCachedLockedElement;

/** @const */
Polymer.IronScrollManager._hasCachedUnlockedElement = _hasCachedUnlockedElement;

/** @const */
Polymer.IronScrollManager._composedTreeContains = _composedTreeContains;

/** @const */
Polymer.IronScrollManager._scrollInteractionHandler = _scrollInteractionHandler;

/** @const */
Polymer.IronScrollManager._boundScrollHandler = _boundScrollHandler;

/** @const */
Polymer.IronScrollManager._lockScrollInteractions = _lockScrollInteractions;

/** @const */
Polymer.IronScrollManager._unlockScrollInteractions = _unlockScrollInteractions;

/** @const */
Polymer.IronScrollManager._shouldPreventScrolling = _shouldPreventScrolling;

/** @const */
Polymer.IronScrollManager._getScrollableNodes = _getScrollableNodes;

/** @const */
Polymer.IronScrollManager._getScrollingNode = _getScrollingNode;

/** @const */
Polymer.IronScrollManager._getScrollInfo = _getScrollInfo;

goog.declareModuleId('HtmlImportsNamespace.IronOverlayBehavior.IronScrollManager');

