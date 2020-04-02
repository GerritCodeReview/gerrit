class TestFixtureElement extends HTMLElement {
  constructor() {
    super();
    this._fixtureTemplates = null;
    this._elementsFixtured = false;
  }

  get elementsFixtured () {
    return this._elementsFixtured;
  }

  get fixtureTemplates () {
    if (!this._fixtureTemplates) {
      this._fixtureTemplates = this.querySelectorAll('template');
    }

    return this._fixtureTemplates;
  }

  create(model) {
    var generatedDoms = [];

    this.restore();

    this.removeElements(this.fixtureTemplates);

    this.forElements(this.fixtureTemplates, function (fixtureTemplate) {
      generatedDoms.push(
          this.createFrom(fixtureTemplate, model)
      );
    }, this);

    this.forcePolyfillAttachedStateSynchrony();

    if (generatedDoms.length < 2) {
      return generatedDoms[0];
    }

    return generatedDoms;
  }

  createFrom(fixtureTemplate, model) {
    var fixturedFragment;
    var fixturedElements;
    var fixturedElement;

    if (!(fixtureTemplate &&
        fixtureTemplate.tagName === 'TEMPLATE')) {
      return;
    }

    try {
      fixturedFragment = this.stamp(fixtureTemplate, model);
    } catch (error) {
      console.error('Error stamping', fixtureTemplate, error);
      throw error;
    }

    fixturedElements = this.collectElementChildren(fixturedFragment);

    this.appendChild(fixturedFragment);
    this._elementsFixtured = true;

    if (fixturedElements.length < 2) {
      return fixturedElements[0];
    }

    return fixturedElements;
  }

  restore() {
    if (!this._elementsFixtured) {
      return;
    }

    this.removeElements(this.children);

    this.forElements(this.fixtureTemplates, function (fixtureTemplate) {
      this.appendChild(fixtureTemplate);
    }, this);

    this.generatedDomStack = [];

    this._elementsFixtured = false;

  }

  collectElementChildren(parent) {
    // Note: Safari 7.1 does not support `firstElementChild` or
    // `nextElementSibling`, so we do things the old-fashioned way:
    var elements = [];
    var child = parent.firstChild;

    while (child) {
      if (child.nodeType === Node.ELEMENT_NODE) {
        elements.push(child);
      }

      child = child.nextSibling;
    }

    return elements;
  }

  removeElements(elements) {
    elements.forEach(element => {
      this.removeChild(element);
    });
  }

  stamp(fixtureTemplate, model) {
    var stamped;
    // Check if we are dealing with a "stampable" `<template>`. This is a
    // vaguely defined special case of a `<template>` that is a custom
    // element with a public `stamp` method that implements some manner of
    // data binding.
    if (fixtureTemplate.stamp) {
      stamped = fixtureTemplate.stamp(model);
      // We leak Polymer specifics a little; if there is an element `root`, we
      // want that to be returned.
      stamped = stamped.root || stamped;

      // Otherwise, we fall back to standard HTML templates, which do not have
      // any sort of binding support.
    } else {
      if (model) {
        console.warn(this, 'was given a model to stamp, but the template is not of a bindable type');
      }

      stamped = document.importNode(fixtureTemplate.content, true);

      // Immediately upgrade the subtree if we are dealing with async
      // Web Components polyfill.
      // https://github.com/Polymer/polymer/blob/0.8-preview/src/features/mini/template.html#L52
      if (window.CustomElements && CustomElements.upgradeSubtree) {
        CustomElements.upgradeSubtree(stamped);
      }
    }

    return stamped;
  }
}

customElements.define('test-fixture', TestFixtureElement);
