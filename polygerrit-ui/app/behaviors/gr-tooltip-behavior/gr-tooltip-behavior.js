// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function(window) {
  'use strict';

  var BOTTOM_OFFSET = 7.2; // Height of the arrow in tooltip.

  /** @polymerBehavior Gerrit.TooltipBehavior */
  var TooltipBehavior = {

    properties: {
      hasTooltip: Boolean,

      _tooltip: Element,
      _titleText: String,
    },

    attached: function() {
      if (!this.hasTooltip) { return; }

      this.addEventListener('mouseover', this._handleShowTooltip.bind(this));
      this.addEventListener('mouseout', this._handleHideTooltip.bind(this));
      this.addEventListener('focusin', this._handleShowTooltip.bind(this));
      this.addEventListener('focusout', this._handleHideTooltip.bind(this));
      this.listen(window, 'scroll', '_handleWindowScroll');
    },

    detached: function() {
      this.unlisten(window, 'scroll', '_handleWindowScroll');
    },

    _handleShowTooltip: function(e) {
      if (!this.hasAttribute('title') ||
          this.getAttribute('title') === '' ||
          this._tooltip) {
        return;
      }

      // Store the title attribute text then set it to an empty string to
      // prevent it from showing natively.
      this._titleText = this.getAttribute('title');
      this.setAttribute('title', '');

      var tooltip = document.createElement('gr-tooltip');
      tooltip.text = this._titleText;

      // Set visibility to hidden before appending to the DOM so that
      // calculations can be made based on the element’s size.
      tooltip.style.visibility = 'hidden';
      Polymer.dom(document.body).appendChild(tooltip);
      this._positionTooltip(tooltip);
      tooltip.style.visibility = null;

      this._tooltip = tooltip;
    },

    _handleHideTooltip: function(e) {
      if (!this.hasAttribute('title') ||
          this._titleText == null ||
          this === document.activeElement) { return; }

      this.setAttribute('title', this._titleText);
      if (this._tooltip && this._tooltip.parentNode) {
        this._tooltip.parentNode.removeChild(this._tooltip);
      }
      this._tooltip = null;
    },

    _handleWindowScroll: function(e) {
      if (!this._tooltip) { return; }

      this._positionTooltip(this._tooltip);
    },

    _positionTooltip: function(tooltip) {
      var rect = this.getBoundingClientRect();
      var boxRect = tooltip.getBoundingClientRect();
      var parentRect = tooltip.parentElement.getBoundingClientRect();
      var top = rect.top - parentRect.top - boxRect.height - BOTTOM_OFFSET;
      var left =
          rect.left - parentRect.left + (rect.width - boxRect.width) / 2;
      if (left < 0) {
        tooltip.updateStyles({
          '--gr-tooltip-arrow-center-offset': left + 'px',
        });
      }
      tooltip.style.left = Math.max(0, left) + 'px';
      tooltip.style.top = Math.max(0, top) + 'px';
    },
  };

  window.Gerrit = window.Gerrit || {};
  window.Gerrit.TooltipBehavior = TooltipBehavior;
})(window);
