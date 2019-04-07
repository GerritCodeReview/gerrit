/**
 * @license
 * Copyright (c) 2015 The Polymer Project Authors. All rights reserved.
 * This code may only be used under the BSD style license found at https://polymer.github.io/LICENSE.txt
 * The complete set of authors may be found at https://polymer.github.io/AUTHORS.txt
 * The complete set of contributors may be found at https://polymer.github.io/CONTRIBUTORS.txt
 * Code distributed by Google as part of the polymer project is also
 * subject to an additional IP rights grant found at https://polymer.github.io/PATENTS.txt
 */
(function() {
  'use strict';

  Polymer({
    is: 'gr-submenu',

    properties: {
      /**
       * Fired when the submenu is opened.
       *
       * @event app-submenu-open
       */

      /**
       * Fired when the submenu is closed.
       *
       * @event app-submenu-close
       */

      /**
       * Set opened to true to show the collapse element and to false to hide it.
       *
       * @attribute opened
       */
      opened: {
        type: Boolean,
        value: false,
        notify: true,
        observer: '_openedChanged'
      },

      /**
       * Set noAutoClose to true to prevent submenu from auto closing.
       *
       * @attribute noAutoClose
       */
      noAutoClose: {
        type: Boolean,
        value: false,
        observer: '_closeOthers'
      },

      /**
       * Set noAutoClose to true to prevent submenu from closing when its deactivated.
       *
       * @attribute noAutoClose
       */
      noAutoCloseOnDeactivate: {
        type: Boolean,
        value: false
      }
    },

    behaviors: [
      Polymer.IronControlState,
      Polymer.IronA11yKeysBehavior
    ],

    listeners: {
      'focus': '_onFocus',
      'iron-select': '_onIronSelect',
      'iron-deselect': '_onIronDeselect'
    },

    keyBindings: {
      'enter:keydown': '_asyncClick',
      'space:keydown': '_spaceKeyDownHandler',
      'space:keyup': '_spaceKeyUpHandler'
    },

    get __parent() {
      return Polymer.dom(this).parentNode;
    },

    get __childNodes() {
      return Polymer.dom(this.__parent).childNodes;
    },

    get __trigger() {
      return Polymer.dom(this.$.trigger).getDistributedNodes()[0];
    },

    get __content() {
      return Polymer.dom(this.$.content).getDistributedNodes()[0];
    },

    attached: function() {
      this.listen(this.__parent, 'iron-activate', '_onParentIronActivate');
    },

    detached: function() {
      this.unlisten(this.__parent, 'iron-activate', '_onParentIronActivate');
    },

    /**
     * Expand the submenu content.
     */
    open: function() {
      if (!this.disabled) {
        this.opened = true;
      }
    },

    /**
     * Collapse the submenu content.
     */
    close: function() {
      this.opened = false;
    },

    /**
     * Toggle the submenu.
     */
    toggle: function() {
      if (this.opened) {
        this.close();
      } else {
        this.open();
      }
    },

    _spaceKeyDownHandler: function(event) {
      var keyboardEvent = event.detail.keyboardEvent;
      var target = Polymer.dom(keyboardEvent).localTarget;

      // Ignore the event if this is coming from a focused light child, since that
      // element will deal with it.
      if (this.isLightDescendant( /** @type {Node} */ (target)))
        return;

      keyboardEvent.preventDefault();
      keyboardEvent.stopImmediatePropagation();
      this._submenuBtnPressed = true;
    },

    _spaceKeyUpHandler: function(event) {
      var keyboardEvent = event.detail.keyboardEvent;
      var target = Polymer.dom(keyboardEvent).localTarget;

      // Ignore the event if this is coming from a focused light child, since that
      // element will deal with it.
      if (this.isLightDescendant( /** @type {Node} */ (target)))
        return;

      if (this._submenuBtnPressed) {
        this._asyncClick();
      }
      this._submenuBtnPressed = false;
    },

    _asyncClick: function() {
      this.async(function() {
        this.$.trigger.click();
      }, 1);
    },

    _closeOthers: function() {
      if (!this.noAutoClose) {
        this.__childNodes.forEach(function(item) {
          if (item !== this && item.nodeName === 'APP-SUBMENU' && item.opened) {
            item.close();
          }
        }.bind(this));
      }
    },

    /**
     * A handler that is called when the trigger is tapped.
     */
    _onTap: function(e) {
      e.stopPropagation();
      if (!this.disabled) {
        this.toggle();
        this._closeOthers();
      }
    },

    /**
     * Toggles the submenu content when the trigger is tapped.
     */
    _openedChanged: function(opened, oldOpened) {
      if (opened) {
        this.fire('app-submenu-open');
      } else if (oldOpened != null) {
        this.fire('app-submenu-close');
      }
    },

    /**
     * A handler that is called when `iron-activate` is fired.
     *
     * @param {CustomEvent} event An `iron-activate` event.
     */
    _onParentIronActivate: function(event) {
      var parent = this.__parent;

      var submenuList = Polymer.dom(event).localTarget;
      var localTarget = Polymer.dom(submenuList).parentNode;

      if (localTarget === parent || localTarget !== this) {
        if (!this.noAutoCloseOnDeactivate && !this.noAutoClose) {
          this.close();
        }
        this.__content.selectIndex(-1);
      }
    },

    /**
     * If the dropdown is open when disabled becomes true, close the
     * dropdown.
     *
     * @param {boolean} disabled True if disabled, otherwise false.
     */
    _disabledChanged: function(disabled) {
      Polymer.IronControlState._disabledChanged.apply(this, arguments);
      if (disabled && this.opened) {
        this.close();
      }
    },

    /**
     * Handler that is called when the menu item is selected
     *
     */
    _onIronSelect: function() {
      if (this.__parent.nodeName === 'APP-MENU') {
        this.__parent.selectIndex(-1);
      }

      if (!this.opened) {
        this.open();
      }

      this.__trigger && this.__trigger.classList.add('iron-selected');
    },

    /**
     * Handler that is called when menu item is deselected
     *
     */
    _onIronDeselect: function() {
      this.__trigger && this.__trigger.classList.remove('iron-selected');
    },

    /**
     * Handler that is called when the menu receives focus.
     *
     * @param {FocusEvent} event A focus event.
     */
    _onFocus: function(event) {
      this.__trigger && this.__trigger.focus();
    }
  });
})();
