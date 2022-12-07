(function(){//[javascript/closure/base.js]
/*

 Copyright The Closure Library Authors.
 SPDX-License-Identifier: Apache-2.0
*/
'use strict';
function $jscomp$arrayIteratorImpl(array) {
  var index = 0;
  return function() {
    return index < array.length ? {done:!1, value:array[index++],} : {done:!0};
  };
}
var $jscomp$defineProperty = "function" == typeof Object.defineProperties ? Object.defineProperty : function(target, property, descriptor) {
  if (target == Array.prototype || target == Object.prototype) {
    return target;
  }
  target[property] = descriptor.value;
  return target;
};
function $jscomp$getGlobal(passedInThis_possibleGlobals) {
  passedInThis_possibleGlobals = ["object" == typeof globalThis && globalThis, passedInThis_possibleGlobals, "object" == typeof window && window, "object" == typeof self && self, "object" == typeof global && global,];
  for (var i = 0; i < passedInThis_possibleGlobals.length; ++i) {
    var maybeGlobal = passedInThis_possibleGlobals[i];
    if (maybeGlobal && maybeGlobal.Math == Math) {
      return maybeGlobal;
    }
  }
  throw Error("Cannot find global object");
}
var $jscomp$global = $jscomp$getGlobal(this);
function $jscomp$polyfill(property$jscomp$inline_91_split$jscomp$inline_88_target, impl$jscomp$inline_93_polyfill) {
  if (impl$jscomp$inline_93_polyfill) {
    a: {
      var obj = $jscomp$global;
      property$jscomp$inline_91_split$jscomp$inline_88_target = property$jscomp$inline_91_split$jscomp$inline_88_target.split(".");
      for (var i$jscomp$inline_89_orig = 0; i$jscomp$inline_89_orig < property$jscomp$inline_91_split$jscomp$inline_88_target.length - 1; i$jscomp$inline_89_orig++) {
        var key = property$jscomp$inline_91_split$jscomp$inline_88_target[i$jscomp$inline_89_orig];
        if (!(key in obj)) {
          break a;
        }
        obj = obj[key];
      }
      property$jscomp$inline_91_split$jscomp$inline_88_target = property$jscomp$inline_91_split$jscomp$inline_88_target[property$jscomp$inline_91_split$jscomp$inline_88_target.length - 1];
      i$jscomp$inline_89_orig = obj[property$jscomp$inline_91_split$jscomp$inline_88_target];
      impl$jscomp$inline_93_polyfill = impl$jscomp$inline_93_polyfill(i$jscomp$inline_89_orig);
      impl$jscomp$inline_93_polyfill != i$jscomp$inline_89_orig && null != impl$jscomp$inline_93_polyfill && $jscomp$defineProperty(obj, property$jscomp$inline_91_split$jscomp$inline_88_target, {configurable:!0, writable:!0, value:impl$jscomp$inline_93_polyfill});
    }
  }
}
$jscomp$polyfill("Symbol", function(orig) {
  function symbolPolyfill(opt_description) {
    if (this instanceof symbolPolyfill) {
      throw new TypeError("Symbol is not a constructor");
    }
    return new SymbolClass(SYMBOL_PREFIX + (opt_description || "") + "_" + counter++, opt_description);
  }
  function SymbolClass(id, opt_description) {
    this.$jscomp$symbol$id_ = id;
    $jscomp$defineProperty(this, "description", {configurable:!0, writable:!0, value:opt_description});
  }
  if (orig) {
    return orig;
  }
  SymbolClass.prototype.toString = function() {
    return this.$jscomp$symbol$id_;
  };
  var SYMBOL_PREFIX = "jscomp_symbol_" + (1E9 * Math.random() >>> 0) + "_", counter = 0;
  return symbolPolyfill;
});
$jscomp$polyfill("Symbol.iterator", function(orig) {
  if (orig) {
    return orig;
  }
  orig = Symbol("Symbol.iterator");
  for (var arrayLikes = "Array Int8Array Uint8Array Uint8ClampedArray Int16Array Uint16Array Int32Array Uint32Array Float32Array Float64Array".split(" "), i = 0; i < arrayLikes.length; i++) {
    var ArrayLikeCtor = $jscomp$global[arrayLikes[i]];
    "function" === typeof ArrayLikeCtor && "function" != typeof ArrayLikeCtor.prototype[orig] && $jscomp$defineProperty(ArrayLikeCtor.prototype, orig, {configurable:!0, writable:!0, value:function() {
      return $jscomp$iteratorPrototype($jscomp$arrayIteratorImpl(this));
    }});
  }
  return orig;
});
function $jscomp$iteratorPrototype(iterator) {
  iterator = {next:iterator};
  iterator[Symbol.iterator] = function() {
    return this;
  };
  return iterator;
}
var goog$global = this || self;
function goog$identity_(s) {
  return s;
}
;
//[third_party/javascript/tslib/tslib_closure.js]
/*

 Copyright 2022 Google LLC
 SPDX-License-Identifier: Apache-2.0
*/
function module$exports$google3$third_party$javascript$tslib$tslib$__decorate(decorators, target, key, desc) {
  var c = arguments.length, r = 3 > c ? target : null === desc ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
  if ("object" === typeof Reflect && Reflect && "function" === typeof Reflect.decorate) {
    r = Reflect.decorate(decorators, target, key, desc);
  } else {
    for (var i = decorators.length - 1; 0 <= i; i--) {
      if (d = decorators[i]) {
        r = (3 > c ? d(r) : 3 < c ? d(target, key, r) : d(target, key)) || r;
      }
    }
  }
  return 3 < c && r && Object.defineProperty(target, key, r), r;
}
function module$exports$google3$third_party$javascript$tslib$tslib$__metadata(metadataValue) {
  if ("object" === typeof Reflect && Reflect && "function" === typeof Reflect.metadata) {
    return Reflect.metadata("design:type", metadataValue);
  }
}
;
//[blaze-out/k8-fastbuild/bin/javascript/lit/testing/shadow_piercer.closure.js]
//[blaze-out/k8-fastbuild/bin/javascript/polymer/detect_transpilation/detect_transpilation.closure.js]
var module$exports$google3$javascript$polymer$detect_transpilation$detect_transpilation$wasTranspiledToEs5 = !/^\s*class\s*\{\s*\}\s*$/.test(class {
}.toString());
//[blaze-out/k8-fastbuild/bin/javascript/polymer/testing/shadow_piercer.closure.js]
//[blaze-out/k8-fastbuild/bin/javascript/typescript/contrib/async.closure.js]
//[blaze-out/k8-fastbuild/bin/javascript/typescript/contrib/check.closure.js]
function module$contents$google3$javascript$typescript$contrib$check_checkExhaustiveAllowing(value, msg = `unexpected value ${value}!`) {
  throw Error(msg);
}
;
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/dev-mode.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/lit-html/src/polyfill-support.closure.js]
/*

 Copyright 2017 Google LLC
 SPDX-License-Identifier: BSD-3-Clause
*/
const module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_styledScopes = new Set(), module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_scopeCssStore = new Map(), module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_polyfillSupport = (Template, ChildPart_childPartProto) => {
  if (void 0 !== window.ShadyCSS && (!window.ShadyCSS.nativeShadow || window.ShadyCSS.ApplyShim)) {
    var wrap = window.ShadyDOM?.inUse && !0 === window.ShadyDOM?.noPatch ? window.ShadyDOM.wrap : node => node, needsPrepareStyles = name => void 0 !== name && !module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_styledScopes.has(name), cssForScope = name => {
      let scopeCss = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_scopeCssStore.get(name);
      void 0 === scopeCss && module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_scopeCssStore.set(name, scopeCss = []);
      return scopeCss;
    }, prepareStyles = (name$jscomp$95_style, template) => {
      const scopeCss = cssForScope(name$jscomp$95_style), hasScopeCss = 0 !== scopeCss.length;
      if (hasScopeCss) {
        const style = document.createElement("style");
        style.textContent = scopeCss.join("\n");
        template.content.appendChild(style);
      }
      module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_styledScopes.add(name$jscomp$95_style);
      module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_scopeCssStore.delete(name$jscomp$95_style);
      window.ShadyCSS.prepareTemplateStyles(template, name$jscomp$95_style);
      hasScopeCss && window.ShadyCSS.nativeShadow && (name$jscomp$95_style = template.content.querySelector("style"), null !== name$jscomp$95_style && template.content.appendChild(name$jscomp$95_style));
    }, scopedTemplateCache = new Map(), originalCreateElement = Template.createElement;
    Template.createElement = function(element$jscomp$14_html, options) {
      element$jscomp$14_html = originalCreateElement.call(Template, element$jscomp$14_html, options);
      options = options?.scope;
      void 0 !== options && (window.ShadyCSS.nativeShadow || window.ShadyCSS.prepareTemplateDom(element$jscomp$14_html, options), needsPrepareStyles(options) && cssForScope(options).push(...Array.from(element$jscomp$14_html.content.querySelectorAll("style")).map(style => {
        style.parentNode?.removeChild(style);
        return style.textContent;
      })));
      return element$jscomp$14_html;
    };
    var renderContainer = document.createDocumentFragment(), renderContainerMarker = document.createComment("");
    ChildPart_childPartProto = ChildPart_childPartProto.prototype;
    var setValue = ChildPart_childPartProto._$setValue;
    ChildPart_childPartProto._$setValue = function(template$jscomp$5_value, directiveParent = this) {
      const container = wrap(this._$startNode).parentNode;
      var scope$jscomp$1_style = this.options?.scope;
      if ((container instanceof ShadowRoot || container === this.options?.renderContainer) && needsPrepareStyles(scope$jscomp$1_style)) {
        const startNode = this._$startNode, endNode = this._$endNode;
        renderContainer.appendChild(renderContainerMarker);
        this._$startNode = renderContainerMarker;
        this._$endNode = null;
        setValue.call(this, template$jscomp$5_value, directiveParent);
        template$jscomp$5_value = template$jscomp$5_value?._$litType$ ? this._$committedValue._$template.el : document.createElement("template");
        prepareStyles(scope$jscomp$1_style, template$jscomp$5_value);
        renderContainer.removeChild(renderContainerMarker);
        window.ShadyCSS?.nativeShadow && (scope$jscomp$1_style = template$jscomp$5_value.content.querySelector("style"), null !== scope$jscomp$1_style && renderContainer.appendChild(scope$jscomp$1_style.cloneNode(!0)));
        container.insertBefore(renderContainer, endNode);
        this._$startNode = startNode;
        this._$endNode = endNode;
      } else {
        setValue.call(this, template$jscomp$5_value, directiveParent);
      }
    };
    ChildPart_childPartProto._$getTemplate = function(result) {
      var scope$jscomp$2_template = this.options?.scope;
      let templateCache = scopedTemplateCache.get(scope$jscomp$2_template);
      void 0 === templateCache && scopedTemplateCache.set(scope$jscomp$2_template, templateCache = new Map());
      scope$jscomp$2_template = templateCache.get(result.strings);
      void 0 === scope$jscomp$2_template && templateCache.set(result.strings, scope$jscomp$2_template = new Template(result, this.options));
      return scope$jscomp$2_template;
    };
  }
};
let $jscomp$logical$assign$tmp696711778$1;
($jscomp$logical$assign$tmp696711778$1 = window).litHtmlPolyfillSupport ?? ($jscomp$logical$assign$tmp696711778$1.litHtmlPolyfillSupport = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_polyfillSupport);
let module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_symbolKey = "";
if (window.Symbol) {
  const s = Symbol();
  "symbol" !== typeof s && (module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_symbolKey = Object.keys(s)[0]);
}
const module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_needsSymbolSupport = "" !== module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_symbolKey;
var module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport$isPolyfilledSymbol = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_needsSymbolSupport ? value => null != value && void 0 !== value[module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_symbolKey] : () => !1;
if (module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport_needsSymbolSupport && !window.Symbol.for) {
  const map = new Map();
  window.Symbol.for = key => {
    map.has(key) || map.set(key, Symbol(key));
    return map.get(key);
  };
}
;
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/polyfill-support.closure.js]
const module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$polyfill$2dsupport_polyfillSupport = ({ReactiveElement:ReactiveElement_elementProto}) => {
  if (void 0 !== window.ShadyCSS && (!window.ShadyCSS.nativeShadow || window.ShadyCSS.ApplyShim)) {
    ReactiveElement_elementProto = ReactiveElement_elementProto.prototype;
    window.ShadyDOM && window.ShadyDOM.inUse && !0 === window.ShadyDOM.noPatch && window.ShadyDOM.patchElementProto(ReactiveElement_elementProto);
    var createRenderRoot = ReactiveElement_elementProto.createRenderRoot;
    ReactiveElement_elementProto.createRenderRoot = function() {
      const name = this.localName;
      if (window.ShadyCSS.nativeShadow) {
        return createRenderRoot.call(this);
      }
      if (!this.constructor.hasOwnProperty("__scoped")) {
        this.constructor.__scoped = !0;
        const css = this.constructor.elementStyles.map(v => v instanceof CSSStyleSheet ? Array.from(v.cssRules).reduce((a, r) => a + r.cssText, "") : v.cssText);
        window.ShadyCSS?.ScopingShim?.prepareAdoptedCssText(css, name);
        void 0 === this.constructor._$handlesPrepareStyles && window.ShadyCSS.prepareTemplateStyles(document.createElement("template"), name);
      }
      return this.shadowRoot ?? this.attachShadow(this.constructor.shadowRootOptions);
    };
    var connectedCallback = ReactiveElement_elementProto.connectedCallback;
    ReactiveElement_elementProto.connectedCallback = function() {
      connectedCallback.call(this);
      this.hasUpdated && window.ShadyCSS.styleElement(this);
    };
    var didUpdate = ReactiveElement_elementProto._$didUpdate;
    ReactiveElement_elementProto._$didUpdate = function(changedProperties) {
      this.hasUpdated || window.ShadyCSS.styleElement(this);
      didUpdate.call(this, changedProperties);
    };
  }
};
let $jscomp$logical$assign$tmp920511337$1;
($jscomp$logical$assign$tmp920511337$1 = window).reactiveElementPolyfillSupport ?? ($jscomp$logical$assign$tmp920511337$1.reactiveElementPolyfillSupport = module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$polyfill$2dsupport_polyfillSupport);
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/lit-element/src/polyfill-support.closure.js]
const module$contents$google3$third_party$javascript$lit$packages$lit$2delement$src$polyfill$2dsupport_polyfillSupport = ({LitElement:LitElement_litElementProto}) => {
  if (void 0 !== window.ShadyCSS && (!window.ShadyCSS.nativeShadow || window.ShadyCSS.ApplyShim)) {
    LitElement_litElementProto._$handlesPrepareStyles = !0;
    LitElement_litElementProto = LitElement_litElementProto.prototype;
    var createRenderRoot = LitElement_litElementProto.createRenderRoot;
    LitElement_litElementProto.createRenderRoot = function() {
      this.renderOptions.scope = this.localName;
      return createRenderRoot.call(this);
    };
  }
};
let $jscomp$logical$assign$tmp1742265091$1;
($jscomp$logical$assign$tmp1742265091$1 = window).litElementPolyfillSupport ?? ($jscomp$logical$assign$tmp1742265091$1.litElementPolyfillSupport = module$contents$google3$third_party$javascript$lit$packages$lit$2delement$src$polyfill$2dsupport_polyfillSupport);
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/lit-html/src/disable_sanitization_capability.closure.js]
//[third_party/javascript/closure/debug/error.js]
//[third_party/javascript/closure/dom/nodetype.js]
//[third_party/javascript/closure/asserts/asserts.js]
//[third_party/javascript/closure/array/array.js]
//[third_party/javascript/closure/debug/errorcontext.js]
//[third_party/javascript/closure/debug/debug.js]
//[third_party/javascript/closure/log/log.js]
//[third_party/javascript/closure/string/typedstring.js]
//[third_party/javascript/closure/string/const.js]
function goog$string$Const(opt_token, opt_content) {
  this.stringConstValueWithSecurityContract__googStringSecurityPrivate_ = opt_token === goog$string$Const$GOOG_STRING_CONSTRUCTOR_TOKEN_PRIVATE_ && opt_content || "";
  this.STRING_CONST_TYPE_MARKER__GOOG_STRING_SECURITY_PRIVATE_ = goog$string$Const$TYPE_MARKER_;
}
goog$string$Const.prototype.implementsGoogStringTypedString = !0;
goog$string$Const.prototype.getTypedStringValue = function() {
  return this.stringConstValueWithSecurityContract__googStringSecurityPrivate_;
};
var goog$string$Const$TYPE_MARKER_ = {}, goog$string$Const$GOOG_STRING_CONSTRUCTOR_TOKEN_PRIVATE_ = {};
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/builders/sensitive_attributes.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/environment/dev.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/internals/secrets.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/internals/attribute_impl.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/internals/string_literal.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/builders/attribute_builders.closure.js]
//[third_party/javascript/closure/dom/htmlelement.js]
//[third_party/javascript/closure/dom/tagname.js]
//[third_party/javascript/closure/object/object.js]
//[third_party/javascript/closure/dom/tags.js]
//[third_party/javascript/closure/html/trustedtypes.js]
var goog$html$trustedtypes$cachedPolicy_;
//[third_party/javascript/closure/html/safescript.js]
const module$contents$goog$html$SafeScript_CONSTRUCTOR_TOKEN_PRIVATE = {};
class module$contents$goog$html$SafeScript_SafeScript {
  constructor(value, token) {
    this.privateDoNotAccessOrElseSafeScriptWrappedValue_ = token === module$contents$goog$html$SafeScript_CONSTRUCTOR_TOKEN_PRIVATE ? value : "";
    this.implementsGoogStringTypedString = !0;
  }
  toString() {
    return this.privateDoNotAccessOrElseSafeScriptWrappedValue_.toString();
  }
  getTypedStringValue() {
    return this.privateDoNotAccessOrElseSafeScriptWrappedValue_.toString();
  }
}
;
//[third_party/javascript/closure/fs/url.js]
//[third_party/javascript/closure/fs/blob.js]
//[third_party/javascript/closure/html/trustedresourceurl.js]
var goog$html$TrustedResourceUrl = class {
  constructor(value, token) {
    this.privateDoNotAccessOrElseTrustedResourceUrlWrappedValue_ = token === goog$html$TrustedResourceUrl$CONSTRUCTOR_TOKEN_PRIVATE_ ? value : "";
  }
  toString() {
    return this.privateDoNotAccessOrElseTrustedResourceUrlWrappedValue_ + "";
  }
};
goog$html$TrustedResourceUrl.prototype.implementsGoogStringTypedString = !0;
goog$html$TrustedResourceUrl.prototype.getTypedStringValue = function() {
  return this.privateDoNotAccessOrElseTrustedResourceUrlWrappedValue_.toString();
};
var goog$html$TrustedResourceUrl$CONSTRUCTOR_TOKEN_PRIVATE_ = {};
//[third_party/javascript/closure/string/internal.js]
//[third_party/javascript/closure/html/safeurl.js]
var goog$html$SafeUrl = class {
  constructor(value, token) {
    this.privateDoNotAccessOrElseSafeUrlWrappedValue_ = token === goog$html$SafeUrl$CONSTRUCTOR_TOKEN_PRIVATE_ ? value : "";
  }
  toString() {
    return this.privateDoNotAccessOrElseSafeUrlWrappedValue_.toString();
  }
};
goog$html$SafeUrl.prototype.implementsGoogStringTypedString = !0;
goog$html$SafeUrl.prototype.getTypedStringValue = function() {
  return this.privateDoNotAccessOrElseSafeUrlWrappedValue_.toString();
};
var goog$html$SafeUrl$CONSTRUCTOR_TOKEN_PRIVATE_ = {};
//[third_party/javascript/closure/html/safestyle.js]
const module$contents$goog$html$SafeStyle_CONSTRUCTOR_TOKEN_PRIVATE = {};
class module$contents$goog$html$SafeStyle_SafeStyle {
  constructor(value, token) {
    this.privateDoNotAccessOrElseSafeStyleWrappedValue_ = token === module$contents$goog$html$SafeStyle_CONSTRUCTOR_TOKEN_PRIVATE ? value : "";
    this.implementsGoogStringTypedString = !0;
  }
  getTypedStringValue() {
    return this.privateDoNotAccessOrElseSafeStyleWrappedValue_;
  }
  toString() {
    return this.privateDoNotAccessOrElseSafeStyleWrappedValue_.toString();
  }
}
;
//[third_party/javascript/closure/html/safestylesheet.js]
//[third_party/javascript/closure/labs/useragent/useragent.js]
//[third_party/javascript/closure/labs/useragent/util.js]
//[third_party/javascript/closure/labs/useragent/highentropy/highentropyvalue.js]
//[third_party/javascript/closure/labs/useragent/highentropy/highentropydata.js]
//[third_party/javascript/closure/labs/useragent/browser.js]
//[third_party/javascript/closure/html/safehtml.js]
const module$contents$goog$html$SafeHtml_CONSTRUCTOR_TOKEN_PRIVATE = {};
function module$contents$goog$html$SafeHtml_SafeHtml$unwrapTrustedHTML(safeHtml) {
  return safeHtml instanceof module$contents$goog$html$SafeHtml_SafeHtml && safeHtml.constructor === module$contents$goog$html$SafeHtml_SafeHtml ? safeHtml.privateDoNotAccessOrElseSafeHtmlWrappedValue_ : "type_error:SafeHtml";
}
class module$contents$goog$html$SafeHtml_SafeHtml {
  constructor(value, token) {
    this.privateDoNotAccessOrElseSafeHtmlWrappedValue_ = token === module$contents$goog$html$SafeHtml_CONSTRUCTOR_TOKEN_PRIVATE ? value : "";
    this.implementsGoogStringTypedString = !0;
  }
  getTypedStringValue() {
    return this.privateDoNotAccessOrElseSafeHtmlWrappedValue_.toString();
  }
  toString() {
    return this.privateDoNotAccessOrElseSafeHtmlWrappedValue_.toString();
  }
}
;
//[third_party/javascript/closure/html/internals.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/internals/html_impl.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/internals/resource_url_impl.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/internals/script_impl.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/builders/html_builders.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/internals/style_impl.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/dom/elements/element.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/dom/globals/dom_parser.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/builders/html_sanitizer/inert_fragment.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/builders/html_sanitizer/no_clobber.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/builders/html_sanitizer/sanitizer_table/sanitizer_table.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/builders/html_sanitizer/sanitizer_table/default_sanitizer_table.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/internals/url_impl.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/builders/url_sanitizer.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/internals/pure.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/builders/html_sanitizer/html_sanitizer.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/builders/html_sanitizer/html_sanitizer_builder.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/builders/resource_url_builders.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/builders/script_builders.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/builders/style_builders.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/internals/style_sheet_impl.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/builders/style_sheet_builders.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/builders/url_builders.closure.js]
/*

 SPDX-License-Identifier: Apache-2.0
*/
class module$contents$google3$third_party$javascript$safevalues$builders$url_builders_SchemeImpl {
  constructor(isValid) {
    this.isValid = isValid;
  }
}
function module$contents$google3$third_party$javascript$safevalues$builders$url_builders_simpleScheme(scheme) {
  return new module$contents$google3$third_party$javascript$safevalues$builders$url_builders_SchemeImpl(url => url.substr(0, scheme.length + 1).toLowerCase() === scheme + ":");
}
const module$contents$google3$third_party$javascript$safevalues$builders$url_builders_DEFAULT_SCHEMES = [module$contents$google3$third_party$javascript$safevalues$builders$url_builders_simpleScheme("data"), module$contents$google3$third_party$javascript$safevalues$builders$url_builders_simpleScheme("http"), module$contents$google3$third_party$javascript$safevalues$builders$url_builders_simpleScheme("https"), module$contents$google3$third_party$javascript$safevalues$builders$url_builders_simpleScheme("mailto"), 
module$contents$google3$third_party$javascript$safevalues$builders$url_builders_simpleScheme("ftp"), new module$contents$google3$third_party$javascript$safevalues$builders$url_builders_SchemeImpl(url => /^[^:]*([/?#]|$)/.test(url)),];
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/legacy/index.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/safevalues/index.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/security/polymer_resin/closure-bridge.closure.js]
/*

 Copyright 2018 Google LLC
 SPDX-License-Identifier: BSD-3-Clause
*/
function module$contents$google3$third_party$javascript$security$polymer_resin$closure$2dbridge_unwrapString(value) {
  return value && value.implementsGoogStringTypedString ? value.getTypedStringValue() : value;
}
const module$contents$google3$third_party$javascript$security$polymer_resin$closure$2dbridge_UNWRAPPERS = {CONSTANT:{isUnwrappable(value) {
  return value instanceof goog$string$Const;
}, unwrap:function(stringConst) {
  return stringConst instanceof goog$string$Const && stringConst.constructor === goog$string$Const && stringConst.STRING_CONST_TYPE_MARKER__GOOG_STRING_SECURITY_PRIVATE_ === goog$string$Const$TYPE_MARKER_ ? stringConst.stringConstValueWithSecurityContract__googStringSecurityPrivate_ : "type_error:Const";
}}, JAVASCRIPT:{isUnwrappable(value) {
  return value instanceof module$contents$goog$html$SafeScript_SafeScript;
}, unwrap:function(value) {
  return value instanceof module$contents$goog$html$SafeScript_SafeScript && value.constructor === module$contents$goog$html$SafeScript_SafeScript ? value.privateDoNotAccessOrElseSafeScriptWrappedValue_ : "type_error:SafeScript";
}}, HTML:{isUnwrappable(value) {
  return value instanceof module$contents$goog$html$SafeHtml_SafeHtml;
}, unwrap:value => module$contents$goog$html$SafeHtml_SafeHtml$unwrapTrustedHTML(value)}, RESOURCE_URL:{isUnwrappable(value) {
  return value instanceof goog$html$TrustedResourceUrl;
}, unwrap:function(value) {
  return value instanceof goog$html$TrustedResourceUrl && value.constructor === goog$html$TrustedResourceUrl ? value.privateDoNotAccessOrElseTrustedResourceUrlWrappedValue_ : "type_error:TrustedResourceUrl";
}}, STRING:{isUnwrappable(value) {
  return value instanceof Object;
}, unwrap:module$contents$google3$third_party$javascript$security$polymer_resin$closure$2dbridge_unwrapString}, STYLE:{isUnwrappable(value) {
  return value instanceof module$contents$goog$html$SafeStyle_SafeStyle;
}, unwrap:function(value) {
  return value instanceof module$contents$goog$html$SafeStyle_SafeStyle && value.constructor === module$contents$goog$html$SafeStyle_SafeStyle ? value.privateDoNotAccessOrElseSafeStyleWrappedValue_ : "type_error:SafeStyle";
}}, URL:{isUnwrappable(value) {
  return value instanceof goog$html$SafeUrl;
}, unwrap:function(value) {
  return value instanceof goog$html$SafeUrl && value.constructor === goog$html$SafeUrl ? value.privateDoNotAccessOrElseSafeUrlWrappedValue_ : "type_error:SafeUrl";
}}};
function module$contents$google3$third_party$javascript$security$polymer_resin$closure$2dbridge_disallow(value, fallback) {
  return fallback;
}
const module$contents$google3$third_party$javascript$security$polymer_resin$closure$2dbridge_FILTERS = {CONSTANT:module$contents$google3$third_party$javascript$security$polymer_resin$closure$2dbridge_disallow, JAVASCRIPT:module$contents$google3$third_party$javascript$security$polymer_resin$closure$2dbridge_disallow, HTML:JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value => {
  var noinlineHtml$jscomp$inline_733_options$jscomp$inline_96_trustedHtml = {};
  JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value = JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&apos;");
  noinlineHtml$jscomp$inline_733_options$jscomp$inline_96_trustedHtml.preserveSpaces && (JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value = JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value.replace(/(^|[\r\n\t ]) /g, "$1&#160;"));
  noinlineHtml$jscomp$inline_733_options$jscomp$inline_96_trustedHtml.preserveNewlines && (JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value = JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value.replace(/(\r\n|\n|\r)/g, "<br>"));
  noinlineHtml$jscomp$inline_733_options$jscomp$inline_96_trustedHtml.preserveTabs && (JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value = JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value.replace(/(\t+)/g, '<span style="white-space:pre">$1</span>'));
  noinlineHtml$jscomp$inline_733_options$jscomp$inline_96_trustedHtml = JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value;
  if (void 0 === goog$html$trustedtypes$cachedPolicy_) {
    JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value = null;
    var policyFactory = goog$global.trustedTypes;
    if (policyFactory && policyFactory.createPolicy) {
      try {
        JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value = policyFactory.createPolicy("goog#html", {createHTML:goog$identity_, createScript:goog$identity_, createScriptURL:goog$identity_});
      } catch (e) {
        goog$global.console && goog$global.console.error(e.message);
      }
      goog$html$trustedtypes$cachedPolicy_ = JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value;
    } else {
      goog$html$trustedtypes$cachedPolicy_ = JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value;
    }
  }
  noinlineHtml$jscomp$inline_733_options$jscomp$inline_96_trustedHtml = (JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value = goog$html$trustedtypes$cachedPolicy_) ? JSCompiler_inline_result$jscomp$737_htmlEscapedString$jscomp$inline_97_policy$jscomp$inline_740_value.createHTML(noinlineHtml$jscomp$inline_733_options$jscomp$inline_96_trustedHtml) : noinlineHtml$jscomp$inline_733_options$jscomp$inline_96_trustedHtml;
  return module$contents$goog$html$SafeHtml_SafeHtml$unwrapTrustedHTML(new module$contents$goog$html$SafeHtml_SafeHtml(noinlineHtml$jscomp$inline_733_options$jscomp$inline_96_trustedHtml, module$contents$goog$html$SafeHtml_CONSTRUCTOR_TOKEN_PRIVATE));
}, RESOURCE_URL:module$contents$google3$third_party$javascript$security$polymer_resin$closure$2dbridge_disallow, STRING:String, STYLE:module$contents$google3$third_party$javascript$security$polymer_resin$closure$2dbridge_disallow, URL:(JSCompiler_inline_result$jscomp$10_safeValue_value, fallback) => {
  a: {
    for (let i = 0; i < module$contents$google3$third_party$javascript$safevalues$builders$url_builders_DEFAULT_SCHEMES.length; ++i) {
      const scheme = module$contents$google3$third_party$javascript$safevalues$builders$url_builders_DEFAULT_SCHEMES[i];
      if (scheme instanceof module$contents$google3$third_party$javascript$safevalues$builders$url_builders_SchemeImpl && scheme.isValid(JSCompiler_inline_result$jscomp$10_safeValue_value)) {
        JSCompiler_inline_result$jscomp$10_safeValue_value = new goog$html$SafeUrl(JSCompiler_inline_result$jscomp$10_safeValue_value, goog$html$SafeUrl$CONSTRUCTOR_TOKEN_PRIVATE_);
        break a;
      }
    }
    JSCompiler_inline_result$jscomp$10_safeValue_value = void 0;
  }
  return void 0 === JSCompiler_inline_result$jscomp$10_safeValue_value ? fallback : JSCompiler_inline_result$jscomp$10_safeValue_value.toString();
}};
//[blaze-out/k8-fastbuild/bin/third_party/javascript/security/polymer_resin/configs/goog_log_config.closure.js]
//[third_party/javascript/closure/dom/element.js]
//[third_party/javascript/closure/asserts/dom.js]
//[third_party/javascript/closure/dom/asserts.js]
//[third_party/javascript/closure/functions/functions.js]
//[third_party/javascript/closure/html/uncheckedconversions.js]
//[third_party/javascript/closure/dom/safe.js]
//[third_party/javascript/closure/string/string.js]
function goog$string$toCamelCase(str) {
  return String(str).replace(/\-([a-z])/g, function(all, match) {
    return match.toUpperCase();
  });
}
;
//[blaze-out/k8-fastbuild/bin/third_party/javascript/security/html/contracts/contracts.closure.js]
function module$contents$google3$third_party$javascript$security$html$contracts$contracts_typeOfAttribute(attrInfoArray_elName_elementInfo, attrName, getValue) {
  if (Object.hasOwnProperty.call(module$contents$google3$third_party$javascript$security$html$contracts$contracts_ELEMENT_CONTRACTS, attrInfoArray_elName_elementInfo) && (attrInfoArray_elName_elementInfo = module$contents$google3$third_party$javascript$security$html$contracts$contracts_ELEMENT_CONTRACTS[attrInfoArray_elName_elementInfo], Object.hasOwnProperty.call(attrInfoArray_elName_elementInfo, attrName) && (attrInfoArray_elName_elementInfo = attrInfoArray_elName_elementInfo[attrName], attrInfoArray_elName_elementInfo instanceof 
  Array))) {
    let valueCache = null, requiredValueNotFound = !1;
    for (let i = 0, n = attrInfoArray_elName_elementInfo.length; i < n; ++i) {
      const attrInfo = attrInfoArray_elName_elementInfo[i];
      var actualValue_contingentAttr = attrInfo.contingentAttribute;
      if (!actualValue_contingentAttr) {
        return attrInfo.contract;
      }
      null === valueCache && (valueCache = {});
      actualValue_contingentAttr = Object.hasOwnProperty.call(valueCache, actualValue_contingentAttr) ? valueCache[actualValue_contingentAttr] : valueCache[actualValue_contingentAttr] = getValue(actualValue_contingentAttr);
      if (actualValue_contingentAttr === attrInfo.requiredValue) {
        return attrInfo.contract;
      }
      null == actualValue_contingentAttr && (requiredValueNotFound = !0);
    }
    if (requiredValueNotFound) {
      return null;
    }
  }
  attrName = module$contents$google3$third_party$javascript$security$html$contracts$contracts_GLOBAL_ATTRS[attrName];
  return "number" === typeof attrName ? attrName : null;
}
const module$contents$google3$third_party$javascript$security$html$contracts$contracts_GLOBAL_ATTRS = {align:1, alt:1, "aria-activedescendant":10, "aria-atomic":1, "aria-autocomplete":1, "aria-busy":1, "aria-checked":1, "aria-controls":10, "aria-current":1, "aria-disabled":1, "aria-dropeffect":1, "aria-expanded":1, "aria-haspopup":1, "aria-hidden":1, "aria-invalid":1, "aria-label":1, "aria-labelledby":10, "aria-level":1, "aria-live":1, "aria-multiline":1, "aria-multiselectable":1, "aria-orientation":1, 
"aria-owns":10, "aria-posinset":1, "aria-pressed":1, "aria-readonly":1, "aria-relevant":1, "aria-required":1, "aria-selected":1, "aria-setsize":1, "aria-sort":1, "aria-valuemax":1, "aria-valuemin":1, "aria-valuenow":1, "aria-valuetext":1, async:8, autocapitalize:1, autocomplete:1, autocorrect:1, autofocus:1, autoplay:1, bgcolor:1, border:1, cellpadding:1, cellspacing:1, checked:1, cite:3, "class":1, color:1, cols:1, colspan:1, contenteditable:1, controls:1, datetime:1, dir:8, disabled:1, download:1, 
draggable:1, enctype:1, face:1, "for":10, formenctype:1, frameborder:1, height:1, hidden:1, href:4, hreflang:1, id:10, ismap:1, itemid:1, itemprop:1, itemref:1, itemscope:1, itemtype:1, label:1, lang:1, list:10, loading:8, loop:1, max:1, maxlength:1, media:1, min:1, minlength:1, multiple:1, muted:1, name:10, nonce:1, open:1, placeholder:1, poster:3, preload:1, rel:1, required:1, reversed:1, role:1, rows:1, rowspan:1, selected:1, shape:1, size:1, sizes:1, slot:1, span:1, spellcheck:1, src:4, srcset:11, 
start:1, step:1, style:5, summary:1, tabindex:1, target:8, title:1, translate:1, type:1, valign:1, value:1, width:1, wrap:1,}, module$contents$google3$third_party$javascript$security$html$contracts$contracts_ELEMENT_CONTRACTS = {a:{href:[{contract:3,},],}, area:{href:[{contract:3,},],}, audio:{src:[{contract:3,},],}, button:{formaction:[{contract:3,},], formmethod:[{contract:1,},],}, form:{action:[{contract:3,},], method:[{contract:1,},],}, iframe:{srcdoc:[{contract:2,},],}, img:{src:[{contract:3,
},],}, input:{accept:[{contract:1,},], formaction:[{contract:3,},], formmethod:[{contract:1,},], pattern:[{contract:1,},], readonly:[{contract:1,},], src:[{contract:3,},],}, link:{href:[{contract:3, contingentAttribute:"rel", requiredValue:"alternate"}, {contract:3, contingentAttribute:"rel", requiredValue:"author"}, {contract:3, contingentAttribute:"rel", requiredValue:"bookmark"}, {contract:3, contingentAttribute:"rel", requiredValue:"canonical"}, {contract:3, contingentAttribute:"rel", requiredValue:"cite"}, 
{contract:3, contingentAttribute:"rel", requiredValue:"help"}, {contract:3, contingentAttribute:"rel", requiredValue:"icon"}, {contract:3, contingentAttribute:"rel", requiredValue:"license"}, {contract:3, contingentAttribute:"rel", requiredValue:"next"}, {contract:3, contingentAttribute:"rel", requiredValue:"prefetch"}, {contract:3, contingentAttribute:"rel", requiredValue:"dns-prefetch"}, {contract:3, contingentAttribute:"rel", requiredValue:"prerender"}, {contract:3, contingentAttribute:"rel", 
requiredValue:"preconnect"}, {contract:3, contingentAttribute:"rel", requiredValue:"preload"}, {contract:3, contingentAttribute:"rel", requiredValue:"prev"}, {contract:3, contingentAttribute:"rel", requiredValue:"search"}, {contract:3, contingentAttribute:"rel", requiredValue:"subresource"},],}, script:{defer:[{contract:1,},],}, source:{src:[{contract:3,},],}, textarea:{readonly:[{contract:1,},],}, video:{src:[{contract:3,},],},}, module$contents$google3$third_party$javascript$security$html$contracts$contracts_ELEMENT_CONTENT_TYPES = 
{a:1, abbr:1, address:1, applet:4, area:5, article:1, aside:1, audio:1, b:1, base:4, bdi:1, bdo:1, blockquote:1, body:1, br:5, button:1, canvas:1, caption:1, center:1, cite:1, code:1, col:5, colgroup:1, command:1, data:1, datalist:1, dd:1, del:1, details:1, dfn:1, dialog:1, div:1, dl:1, dt:1, em:1, embed:4, fieldset:1, figcaption:1, figure:1, font:1, footer:1, form:1, frame:1, frameset:1, h1:1, h2:1, h3:1, h4:1, h5:1, h6:1, head:1, header:1, hr:5, html:1, i:1, iframe:1, img:5, input:5, ins:1, kbd:1, 
label:1, legend:1, lh:1, li:1, link:5, main:1, map:1, mark:1, math:4, menu:1, meta:4, meter:1, nav:1, noscript:1, object:4, ol:1, optgroup:1, option:1, output:1, p:1, param:5, picture:1, pre:1, progress:1, q:1, rb:1, rp:1, rt:1, rtc:1, ruby:1, s:1, samp:1, script:3, section:1, select:1, slot:1, small:1, source:5, span:1, strong:1, style:2, sub:1, summary:1, sup:1, svg:4, table:1, tbody:1, td:1, template:4, textarea:6, tfoot:1, th:1, thead:1, time:1, title:6, tr:1, track:5, u:1, ul:1, "var":1, video:1, 
wbr:5,}, module$contents$google3$third_party$javascript$security$html$contracts$contracts_ENUM_VALUE_SETS = [{auto:!0, ltr:!0, rtl:!0,}, {async:!0,}, {eager:!0, lazy:!0,}, {_self:!0, _blank:!0,},], module$contents$google3$third_party$javascript$security$html$contracts$contracts_ENUM_VALUE_SET_BY_ATTR = {"*":{async:1, dir:0, loading:2, target:3,},};
//[blaze-out/k8-fastbuild/bin/third_party/javascript/security/html/namealiases/namealiases.closure.js]
function module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_propertyToAttr(propName) {
  var obj = module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_propToAttrInternal;
  if (!obj) {
    obj = module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_getAttrToProp();
    const transposed = {};
    for (attr$jscomp$3_key in obj) {
      transposed[obj[attr$jscomp$3_key]] = attr$jscomp$3_key;
    }
    obj = module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_propToAttrInternal = transposed;
  }
  var attr$jscomp$3_key = obj[propName];
  return "string" === typeof attr$jscomp$3_key ? attr$jscomp$3_key : String(propName).replace(/([A-Z])/g, "-$1").toLowerCase();
}
function module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_attrToProperty(attrName) {
  attrName = String(attrName).toLowerCase();
  const prop = module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_getAttrToProp()[attrName];
  return "string" === typeof prop ? prop : goog$string$toCamelCase(attrName);
}
function module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_specialPropertyNameWorstCase(lcname_name$jscomp$112_prop) {
  lcname_name$jscomp$112_prop = lcname_name$jscomp$112_prop.toLowerCase();
  lcname_name$jscomp$112_prop = module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_getAttrToProp()[lcname_name$jscomp$112_prop];
  return "string" === typeof lcname_name$jscomp$112_prop ? lcname_name$jscomp$112_prop : null;
}
function module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_getAttrToProp() {
  if (!module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_attrToPropInternal) {
    const buildingAttrToPropInternal = {...module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_ODD_ATTR_TO_PROP};
    for (const name of module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_NONCANON_PROPS) {
      buildingAttrToPropInternal[name.toLowerCase()] = name;
    }
    module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_attrToPropInternal = buildingAttrToPropInternal;
  }
  return module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_attrToPropInternal;
}
const module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_NONCANON_PROPS = "aLink accessKey allowFullscreen bgColor cellPadding cellSpacing codeBase codeType contentEditable crossOrigin dateTime dirName formAction formEnctype formMethod formNoValidate formTarget frameBorder innerHTML innerText inputMode isMap longDesc marginHeight marginWidth maxLength mediaGroup minLength noHref noResize noShade noValidate noWrap nodeValue outerHTML outerText readOnly tabIndex textContent trueSpeed useMap vAlign vLink valueAsDate valueAsNumber valueType".split(" "), 
module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_ODD_ATTR_TO_PROP = {accept_charset:"acceptCharset", "char":"ch", charoff:"chOff", checked:"defaultChecked", "class":"className", "for":"htmlFor", http_equiv:"httpEquiv", muted:"defaultMuted", selected:"defaultSelected", value:"defaultValue"};
let module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_attrToPropInternal = null, module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_propToAttrInternal = null;
//[blaze-out/k8-fastbuild/bin/third_party/javascript/security/polymer_resin/classifier.closure.js]
/*

 Copyright 2017 Google LLC
 SPDX-License-Identifier: BSD-3-Clause

*/
const module$contents$google3$third_party$javascript$security$polymer_resin$classifier_docRegisteredElements = {}, module$contents$google3$third_party$javascript$security$polymer_resin$classifier_VALID_CUSTOM_ELEMENT_NAME_REGEX = RegExp("^(?!(?:annotation-xml|color-profile|font-face|font-face(?:-(?:src|uri|format|name))?|missing-glyph)$)[a-z][a-z.0-9_\u00b7\u00c0-\u00d6\u00d8-\u00f6\u00f8-\u037d\u200c\u200d\u203f-\u2040\u2070-\u218f\u2c00-\u2fef\u3001-\udfff\uf900-\ufdcf\ufdf0-\ufffd]*-[\\-a-z.0-9_\u00b7\u00c0-\u00d6\u00d8-\u00f6\u00f8-\u037d\u200c\u200d\u203f-\u2040\u2070-\u218f\u2c00-\u2fef\u3001-\udfff\uf900-\ufdcf\ufdf0-\ufffd]*$");
function module$contents$google3$third_party$javascript$security$polymer_resin$classifier_classifyElement(name, ctor) {
  const customElementsRegistry = window.customElements;
  return customElementsRegistry && customElementsRegistry.get(name) || !0 === module$contents$google3$third_party$javascript$security$polymer_resin$classifier_docRegisteredElements[name] ? 2 : "HTMLUnknownElement" === ctor.name ? 1 : "HTMLElement" === ctor.name && module$contents$google3$third_party$javascript$security$polymer_resin$classifier_VALID_CUSTOM_ELEMENT_NAME_REGEX.test(name) ? 3 : 0;
}
;
//[blaze-out/k8-fastbuild/bin/third_party/javascript/security/polymer_resin/allowed-dom-properties.closure.js]
/*

 Copyright 2020 Google LLC
 SPDX-License-Identifier: BSD-3-Clause
*/
function module$contents$google3$third_party$javascript$security$polymer_resin$allowed$2ddom$2dproperties_getDomPropertyContract(contentType$jscomp$6_element, propertyName) {
  switch(propertyName) {
    case "innerHTML":
      return 1 === module$contents$google3$third_party$javascript$security$polymer_resin$allowed$2ddom$2dproperties_getContentType(contentType$jscomp$6_element) ? 2 : null;
    case "textContent":
      return contentType$jscomp$6_element = module$contents$google3$third_party$javascript$security$polymer_resin$allowed$2ddom$2dproperties_getContentType(contentType$jscomp$6_element), 1 === contentType$jscomp$6_element || 6 === contentType$jscomp$6_element ? 1 : null;
    default:
      return module$contents$google3$third_party$javascript$security$polymer_resin$allowed$2ddom$2dproperties_propertyAllowlist[contentType$jscomp$6_element.localName]?.[propertyName] ?? null;
  }
}
function module$contents$google3$third_party$javascript$security$polymer_resin$allowed$2ddom$2dproperties_getContentType(element) {
  const elementName = element.localName, classification = module$contents$google3$third_party$javascript$security$polymer_resin$classifier_classifyElement(elementName, element.constructor);
  switch(classification) {
    case 0:
    case 1:
      return module$contents$google3$third_party$javascript$security$polymer_resin$allowed$2ddom$2dproperties_contentTypeForElement(elementName, element);
    case 3:
    case 2:
      return 1;
    default:
      module$contents$google3$javascript$typescript$contrib$check_checkExhaustiveAllowing(classification, "got an unknown element classification");
  }
}
function module$contents$google3$third_party$javascript$security$polymer_resin$allowed$2ddom$2dproperties_contentTypeForElement(elementName, element) {
  var JSCompiler_inline_result = Object.hasOwnProperty.call(module$contents$google3$third_party$javascript$security$html$contracts$contracts_ELEMENT_CONTENT_TYPES, elementName) ? module$contents$google3$third_party$javascript$security$html$contracts$contracts_ELEMENT_CONTENT_TYPES[elementName] : null;
  return null !== JSCompiler_inline_result ? JSCompiler_inline_result : Object.hasOwnProperty.call(module$contents$google3$third_party$javascript$security$polymer_resin$allowed$2ddom$2dproperties_SVG_CONTENT_TYPES, elementName) && element instanceof SVGElement ? module$contents$google3$third_party$javascript$security$polymer_resin$allowed$2ddom$2dproperties_SVG_CONTENT_TYPES[elementName] : null;
}
const module$contents$google3$third_party$javascript$security$polymer_resin$allowed$2ddom$2dproperties_SVG_CONTENT_TYPES = {text:1,}, module$contents$google3$third_party$javascript$security$polymer_resin$allowed$2ddom$2dproperties_propertyAllowlist = {audio:{currentTime:1, srcObject:1,}, video:{currentTime:1, srcObject:1,},};
//[blaze-out/k8-fastbuild/bin/third_party/javascript/security/polymer_resin/sanitize.closure.js]
const module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_SRCSET_IMG_CANDIDATE_RE = /(?!,)([^\t\n\f\r ]+)(?:[\t\n\f\r ]+([.0-9+\-]+[a-z]?))?/gi, module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_ASCII_SPACES_RE = /[\t\n\f\r ]+/, module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_SRCSET_METACHARS_RE = /[\t\n\f\r ,]+/g;
function module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_parseImageCandidate(match$jscomp$12_str) {
  return (match$jscomp$12_str = match$jscomp$12_str.split(module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_ASCII_SPACES_RE, 2)) ? {url:match$jscomp$12_str[0], metadata:match$jscomp$12_str[1]} : null;
}
function module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_unparseImageCandidate(imageCandidate_metadata) {
  let imageCandidateString = String(imageCandidate_metadata.url).replace(module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_SRCSET_METACHARS_RE, encodeURIComponent);
  if (imageCandidate_metadata = imageCandidate_metadata.metadata) {
    module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_SRCSET_METACHARS_RE.lastIndex = 0;
    if (module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_SRCSET_METACHARS_RE.test(imageCandidate_metadata)) {
      return null;
    }
    imageCandidateString += " " + imageCandidate_metadata;
  }
  return imageCandidateString;
}
const module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_DEFAULT_SAFE_TYPES_BRIDGE = (value, type, fallback) => fallback, module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_DID_NOT_UNWRAP = {};
function module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_getValueHandlers(allowedIdentifierPattern, safeTypesBridge, reportHandler) {
  const valueHandlers = [, {filterRaw(elementName, attributeName, initialValue) {
    return initialValue;
  }, filterString:void 0, safeReplacement:void 0, safeType:void 0}, {filterRaw:void 0, filterString:void 0, safeReplacement:void 0, safeType:"HTML"}];
  valueHandlers[3] = {filterRaw:void 0, filterString:void 0, safeReplacement:module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_URL, safeType:"URL"};
  valueHandlers[4] = {filterRaw:void 0, filterString:void 0, safeReplacement:module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_URL, safeType:"RESOURCE_URL"};
  valueHandlers[5] = {filterRaw:void 0, filterString:void 0, safeReplacement:module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_STRING, safeType:"STYLE"};
  valueHandlers[7] = {filterRaw:void 0, filterString:void 0, safeReplacement:module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_SCRIPT, safeType:"JAVASCRIPT"};
  valueHandlers[8] = {filterRaw:void 0, filterString(attrToValueSetIndex$jscomp$inline_116_elementName, JSCompiler_inline_result$jscomp$12_attributeName, lowerValue_stringValue) {
    lowerValue_stringValue = String(lowerValue_stringValue).toLowerCase();
    a: {
      let valueSetIndex = null;
      (attrToValueSetIndex$jscomp$inline_116_elementName = module$contents$google3$third_party$javascript$security$html$contracts$contracts_ENUM_VALUE_SET_BY_ATTR[attrToValueSetIndex$jscomp$inline_116_elementName]) && (valueSetIndex = attrToValueSetIndex$jscomp$inline_116_elementName[JSCompiler_inline_result$jscomp$12_attributeName]);
      if ("number" !== typeof valueSetIndex && ((attrToValueSetIndex$jscomp$inline_116_elementName = module$contents$google3$third_party$javascript$security$html$contracts$contracts_ENUM_VALUE_SET_BY_ATTR["*"]) && (valueSetIndex = attrToValueSetIndex$jscomp$inline_116_elementName[JSCompiler_inline_result$jscomp$12_attributeName]), "number" !== typeof valueSetIndex)) {
        JSCompiler_inline_result$jscomp$12_attributeName = !1;
        break a;
      }
      JSCompiler_inline_result$jscomp$12_attributeName = !0 === module$contents$google3$third_party$javascript$security$html$contracts$contracts_ENUM_VALUE_SETS[valueSetIndex][String(lowerValue_stringValue).toLowerCase()];
    }
    return JSCompiler_inline_result$jscomp$12_attributeName ? lowerValue_stringValue : module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_STRING;
  }, safeReplacement:module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_STRING, safeType:void 0};
  valueHandlers[9] = {filterRaw:void 0, filterString:void 0, safeReplacement:module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_STRING, safeType:"CONSTANT"};
  valueHandlers[10] = {filterRaw:void 0, filterString(elementName, attributeName, stringValue) {
    return allowedIdentifierPattern.test(stringValue) ? stringValue : module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_STRING;
  }, safeReplacement:module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_STRING, safeType:"CONSTANT"};
  valueHandlers[11] = {filterRaw(elementName, attributeName, initialValue, element) {
    if ("string" === typeof initialValue) {
      var imageCandidateStrings$jscomp$inline_119_safe$jscomp$inline_124_value = (imageCandidateStrings$jscomp$inline_119_safe$jscomp$inline_124_value = initialValue.match(module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_SRCSET_IMG_CANDIDATE_RE)) ? imageCandidateStrings$jscomp$inline_119_safe$jscomp$inline_124_value.map(module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_parseImageCandidate).filter(Boolean) : [];
    } else if (Array.isArray(initialValue)) {
      imageCandidateStrings$jscomp$inline_119_safe$jscomp$inline_124_value = initialValue;
    } else {
      return module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_URL;
    }
    var JSCompiler_object_inline_problems_630_x = imageCandidateStrings$jscomp$inline_119_safe$jscomp$inline_124_value;
    imageCandidateStrings$jscomp$inline_119_safe$jscomp$inline_124_value = [];
    var problems$jscomp$inline_125_safeValue = [];
    const sentinel = {};
    if (Array.isArray(JSCompiler_object_inline_problems_630_x)) {
      for (let i = 0, n = JSCompiler_object_inline_problems_630_x.length; i < n; ++i) {
        const imageCandidate = JSCompiler_object_inline_problems_630_x[i], url = imageCandidate && imageCandidate.url;
        if (url) {
          const safeUrl = safeTypesBridge(url, "URL", sentinel);
          if (safeUrl) {
            const foundSafeValue = safeUrl !== sentinel;
            (foundSafeValue ? imageCandidateStrings$jscomp$inline_119_safe$jscomp$inline_124_value : problems$jscomp$inline_125_safeValue).push({url:foundSafeValue ? safeUrl : url, metadata:imageCandidate.metadata,});
          }
        }
      }
    } else {
      problems$jscomp$inline_125_safeValue.push(JSCompiler_object_inline_problems_630_x);
    }
    JSCompiler_object_inline_problems_630_x = problems$jscomp$inline_125_safeValue.length ? JSON.stringify(problems$jscomp$inline_125_safeValue) : null;
    problems$jscomp$inline_125_safeValue = module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_DID_NOT_UNWRAP;
    if (imageCandidateStrings$jscomp$inline_119_safe$jscomp$inline_124_value.length) {
      if (!Array.isArray(imageCandidateStrings$jscomp$inline_119_safe$jscomp$inline_124_value)) {
        throw Error();
      }
      problems$jscomp$inline_125_safeValue = imageCandidateStrings$jscomp$inline_119_safe$jscomp$inline_124_value.map(module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_unparseImageCandidate).filter(Boolean).join(" , ") || module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_DID_NOT_UNWRAP;
    }
    JSCompiler_object_inline_problems_630_x && reportHandler && reportHandler(!0, `Failed to sanitize attribute value of <${elementName}>: <${elementName} ${attributeName}="${initialValue}">: ${JSCompiler_object_inline_problems_630_x}`, element);
    return problems$jscomp$inline_125_safeValue === module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_DID_NOT_UNWRAP ? module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_URL : problems$jscomp$inline_125_safeValue;
  }, filterString:void 0, safeReplacement:void 0, safeType:void 0};
  return valueHandlers;
}
const {INNOCUOUS_STRING:module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_STRING, INNOCUOUS_SCRIPT:module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_SCRIPT, INNOCUOUS_URL:module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_URL} = (() => {
  var INNOCUOUS_URL_innocuousConstantsPolicy = (() => {
    const policyFn = {createHTML:() => "zClosurez", createScript:() => " /*zClosurez*/ ", createScriptURL:() => "about:invalid#zClosurez",};
    return "undefined" !== typeof trustedTypes ? trustedTypes.createPolicy("polymer_resin", policyFn) : policyFn;
  })();
  const INNOCUOUS_STRING = INNOCUOUS_URL_innocuousConstantsPolicy.createHTML(""), INNOCUOUS_SCRIPT = INNOCUOUS_URL_innocuousConstantsPolicy.createScript("");
  INNOCUOUS_URL_innocuousConstantsPolicy = INNOCUOUS_URL_innocuousConstantsPolicy.createScriptURL("");
  return {INNOCUOUS_STRING, INNOCUOUS_SCRIPT, INNOCUOUS_URL:INNOCUOUS_URL_innocuousConstantsPolicy};
})();
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/lit-html/src/internal-security.closure.js]
/*

 Copyright 2019 Google LLC
 SPDX-License-Identifier: BSD-3-Clause
*/
var module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$internal$2dsecurity$sanitizerFactory = function(config) {
  function getUncustomizedProxy(element) {
    const elementName = element.localName;
    if (!element.getAttribute("is") && 2 === module$contents$google3$third_party$javascript$security$polymer_resin$classifier_classifyElement(elementName, element.constructor)) {
      return VANILLA_HTML_ELEMENT;
    }
    (element = uncustomizedProxies[elementName]) || (element = uncustomizedProxies[elementName] = document.createElement(elementName));
    return element;
  }
  let reportHandler = config.reportHandler || void 0;
  const safeTypesBridge = config.safeTypesBridge || module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_DEFAULT_SAFE_TYPES_BRIDGE;
  let allowedIdentifierPattern = /^$/;
  if (config = config.allowedIdentifierPrefixes) {
    for (const allowedPrefix of config) {
      allowedIdentifierPattern = new RegExp(allowedIdentifierPattern.source + "|^" + String(allowedPrefix).replace(/([-()\[\]{}+?*.$\^|,:#<!\\])/g, "\\$1").replace(/\x08/g, "\\x08"));
    }
  }
  reportHandler && reportHandler(!1, "initResin", null);
  const valueHandlers = module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_getValueHandlers(allowedIdentifierPattern, safeTypesBridge, reportHandler), uncustomizedProxies = {}, VANILLA_HTML_ELEMENT = document.createElement("polyresinuncustomized");
  return function(node, contentType$jscomp$8_name, allowText_type) {
    var elementProxy_nodeType = node.nodeType;
    if (elementProxy_nodeType !== Node.ELEMENT_NODE) {
      if (elementProxy_nodeType === Node.TEXT_NODE) {
        contentType$jscomp$8_name = node.parentElement;
        allowText_type = !contentType$jscomp$8_name;
        if (contentType$jscomp$8_name && contentType$jscomp$8_name.nodeType === Node.ELEMENT_NODE) {
          elementProxy_nodeType = contentType$jscomp$8_name.localName;
          var parentClassification_worstCase = module$contents$google3$third_party$javascript$security$polymer_resin$classifier_classifyElement(elementProxy_nodeType, contentType$jscomp$8_name.constructor);
          switch(parentClassification_worstCase) {
            case 0:
            case 1:
              contentType$jscomp$8_name = module$contents$google3$third_party$javascript$security$polymer_resin$allowed$2ddom$2dproperties_contentTypeForElement(elementProxy_nodeType, contentType$jscomp$8_name);
              allowText_type = 1 === contentType$jscomp$8_name || 6 === contentType$jscomp$8_name;
              break;
            case 3:
            case 2:
              allowText_type = !0;
              break;
            default:
              module$contents$google3$javascript$typescript$contrib$check_checkExhaustiveAllowing(parentClassification_worstCase, "got an unknown element classification");
          }
        }
        if (allowText_type) {
          return value => "" + safeTypesBridge(value, "STRING", value);
        }
      }
      return value => {
        if (!value && value !== document.all) {
          return value;
        }
        reportHandler && reportHandler(!0, `Failed to sanitize ${node.parentElement && node.parentElement.nodeName} #text node to value ${value}`, node.parentElement);
        return module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_STRING;
      };
    }
    const elementName = node.localName;
    elementProxy_nodeType = getUncustomizedProxy(node);
    let enforcedType = null;
    switch(allowText_type) {
      case "attribute":
        if (module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_attrToProperty(contentType$jscomp$8_name) in elementProxy_nodeType) {
          break;
        }
        return value => value;
      case "property":
        if (contentType$jscomp$8_name in elementProxy_nodeType) {
          enforcedType = module$contents$google3$third_party$javascript$security$polymer_resin$allowed$2ddom$2dproperties_getDomPropertyContract(elementProxy_nodeType, contentType$jscomp$8_name);
          break;
        }
        if ((parentClassification_worstCase = module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_specialPropertyNameWorstCase(contentType$jscomp$8_name)) && parentClassification_worstCase in elementProxy_nodeType) {
          break;
        }
        return value => value;
      default:
        module$contents$google3$javascript$typescript$contrib$check_checkExhaustiveAllowing(allowText_type, "got an unknown resin type, expected either 'property' or 'attribute'");
    }
    const attrName = "attribute" === allowText_type ? contentType$jscomp$8_name.toLowerCase() : module$contents$google3$third_party$javascript$security$html$namealiases$namealiases_propertyToAttr(contentType$jscomp$8_name);
    enforcedType || (enforcedType = module$contents$google3$third_party$javascript$security$html$contracts$contracts_typeOfAttribute(elementName, attrName, name => {
      const value = node.getAttribute(name);
      return !value || /[\[\{]/.test(name) ? null : value;
    }));
    return value => {
      var safeValue$jscomp$1_stringValue = module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_DID_NOT_UNWRAP;
      let safeReplacement = null;
      if (!value && value !== document.all) {
        return value;
      }
      if (null != enforcedType) {
        const valueHandler = valueHandlers[enforcedType], safeType = valueHandler.safeType;
        safeReplacement = valueHandler.safeReplacement;
        safeType && (safeValue$jscomp$1_stringValue = safeTypesBridge(value, safeType, module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_DID_NOT_UNWRAP));
        safeValue$jscomp$1_stringValue === module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_DID_NOT_UNWRAP && (valueHandler.filterString ? (safeValue$jscomp$1_stringValue = String(safeTypesBridge(value, "STRING", value)), safeValue$jscomp$1_stringValue = valueHandler.filterString(elementName, attrName, safeValue$jscomp$1_stringValue)) : valueHandler.filterRaw && (safeValue$jscomp$1_stringValue = valueHandler.filterRaw(elementName, attrName, value, node)), safeValue$jscomp$1_stringValue === 
        safeReplacement && (safeValue$jscomp$1_stringValue = module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_DID_NOT_UNWRAP));
      }
      safeValue$jscomp$1_stringValue === module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_DID_NOT_UNWRAP && (safeValue$jscomp$1_stringValue = safeReplacement || module$contents$google3$third_party$javascript$security$polymer_resin$sanitize_INNOCUOUS_STRING, reportHandler && reportHandler(!0, `Failed to sanitize attribute of <${elementName}>: <${elementName} ${attrName}="${value}">`, node));
      return safeValue$jscomp$1_stringValue;
    };
  };
}({allowedIdentifierPrefixes:[""], reportHandler:function() {
}, safeTypesBridge:(value, expectedType, fallback) => {
  var unwrapper_uw = module$contents$google3$third_party$javascript$security$polymer_resin$closure$2dbridge_UNWRAPPERS[expectedType];
  return unwrapper_uw.isUnwrappable(value) && (unwrapper_uw = unwrapper_uw.unwrap(value, fallback), unwrapper_uw !== fallback) ? unwrapper_uw : (0,module$contents$google3$third_party$javascript$security$polymer_resin$closure$2dbridge_FILTERS[expectedType])(String(module$contents$google3$third_party$javascript$security$polymer_resin$closure$2dbridge_unwrapString(value)), fallback);
},});
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/lit-html/src/lit-html.closure.js]
const module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_global = window, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_wrap = !module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_global.ShadyDOM?.inUse || !0 !== module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_global.ShadyDOM?.noPatch && "on-demand" !== module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_global.ShadyDOM?.noPatch ? 
node => node : module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_global.ShadyDOM.wrap, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_trustedTypes = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_global.trustedTypes, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_policy = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_trustedTypes ? 
module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_trustedTypes.createPolicy("lit-html", {createHTML:s => s,}) : void 0, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_identityFunction = value => value, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_noopSanitizer = () => module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_identityFunction, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_templateStringContentsToTemplates = 
new Map(), module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_marker = `lit$${String(Math.random()).slice(9)}$`, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_markerMatch = "?" + module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_marker, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_nodeMarker = `<${module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_markerMatch}>`, 
module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_d = document, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_isPrimitive = value => null === value || "object" != typeof value && "function" != typeof value || module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$polyfill$2dsupport$isPolyfilledSymbol(value), module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_isArray = 
Array.isArray, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_textEndRegex = /<(?:(!--|\/[^a-zA-Z])|(\/?[a-zA-Z][^>\s]*)|(\/?$))/g, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_commentEndRegex = /--\x3e/g, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_comment2EndRegex = />/g, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_tagEndRegex = RegExp(">|[ \t\n\f\r](?:([^\\s\"'>=/]+)([ \t\n\f\r]*=[ \t\n\f\r]*(?:[^ \t\n\f\r\"'`<>=]|(\"|')|))|$)", 
"g"), module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_singleQuoteAttrEndRegex = /'/g, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_doubleQuoteAttrEndRegex = /"/g, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_rawTextElement = /^(?:script|style|textarea|title)$/i;
var module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$html = (strings, ...values) => ({_$litType$:1, strings, values,}), module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$noChange = Symbol.for("lit-noChange"), module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing = Symbol.for("lit-nothing");
const module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_templateCache = new WeakMap(), module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_walker = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_d.createTreeWalker(module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_d, 129, null, !1);
var module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$Template = class {
  constructor({strings:JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings, _$litType$:content$jscomp$12_node$jscomp$21_type}, attrsToRemove_i$jscomp$117_options$jscomp$75_strings) {
    this.parts = [];
    let nodeIndex = 0, attrNameIndex = 0;
    const partCount = JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings.length - 1, parts = this.parts;
    var htmlResult$jscomp$inline_143_l = JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings.length - 1, attrNames$jscomp$inline_139_i$jscomp$116_m = [];
    let html = 2 === content$jscomp$12_node$jscomp$21_type ? "<svg>" : "", rawTextEndRegex, regex = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_textEndRegex;
    for (let i = 0; i < htmlResult$jscomp$inline_143_l; i++) {
      const s = JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings[i];
      let attrNameEndIndex = -1, attrName;
      var end$jscomp$inline_150_lastIndex = 0;
      let match;
      for (; end$jscomp$inline_150_lastIndex < s.length;) {
        regex.lastIndex = end$jscomp$inline_150_lastIndex;
        match = regex.exec(s);
        if (null === match) {
          break;
        }
        end$jscomp$inline_150_lastIndex = regex.lastIndex;
        regex === module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_textEndRegex ? "!--" === match[1] ? regex = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_commentEndRegex : void 0 !== match[1] ? regex = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_comment2EndRegex : void 0 !== match[2] ? (module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_rawTextElement.test(match[2]) && 
        (rawTextEndRegex = new RegExp(`</${match[2]}`, "g")), regex = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_tagEndRegex) : void 0 !== match[3] && (regex = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_tagEndRegex) : regex === module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_tagEndRegex ? ">" === match[0] ? (regex = rawTextEndRegex ?? module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_textEndRegex, 
        attrNameEndIndex = -1) : void 0 === match[1] ? attrNameEndIndex = -2 : (attrNameEndIndex = regex.lastIndex - match[2].length, attrName = match[1], regex = void 0 === match[3] ? module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_tagEndRegex : '"' === match[3] ? module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_doubleQuoteAttrEndRegex : module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_singleQuoteAttrEndRegex) : 
        regex === module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_doubleQuoteAttrEndRegex || regex === module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_singleQuoteAttrEndRegex ? regex = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_tagEndRegex : regex === module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_commentEndRegex || regex === module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_comment2EndRegex ? 
        regex = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_textEndRegex : (regex = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_tagEndRegex, rawTextEndRegex = void 0);
      }
      end$jscomp$inline_150_lastIndex = regex === module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_tagEndRegex && JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings[i + 1].startsWith("/>") ? " " : "";
      html += regex === module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_textEndRegex ? s + module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_nodeMarker : 0 <= attrNameEndIndex ? (attrNames$jscomp$inline_139_i$jscomp$116_m.push(attrName), s.slice(0, attrNameEndIndex) + "$lit$" + s.slice(attrNameEndIndex)) + module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_marker + end$jscomp$inline_150_lastIndex : 
      s + module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_marker + (-2 === attrNameEndIndex ? (attrNames$jscomp$inline_139_i$jscomp$116_m.push(void 0), i) : end$jscomp$inline_150_lastIndex);
    }
    htmlResult$jscomp$inline_143_l = html + (JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings[htmlResult$jscomp$inline_143_l] || "<?>") + (2 === content$jscomp$12_node$jscomp$21_type ? "</svg>" : "");
    if (!Array.isArray(JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings) || !JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings.hasOwnProperty("raw")) {
      throw Error("invalid template strings array");
    }
    JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings = [void 0 !== module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_policy ? module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_policy.createHTML(htmlResult$jscomp$inline_143_l) : htmlResult$jscomp$inline_143_l, attrNames$jscomp$inline_139_i$jscomp$116_m,];
    const [html__tsickle_destructured_1, attrNames__tsickle_destructured_2] = JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings;
    this.el = module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$Template.createElement(html__tsickle_destructured_1, attrsToRemove_i$jscomp$117_options$jscomp$75_strings);
    module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_walker.currentNode = this.el.content;
    2 === content$jscomp$12_node$jscomp$21_type && (content$jscomp$12_node$jscomp$21_type = this.el.content, attrsToRemove_i$jscomp$117_options$jscomp$75_strings = content$jscomp$12_node$jscomp$21_type.firstChild, attrsToRemove_i$jscomp$117_options$jscomp$75_strings.remove(), content$jscomp$12_node$jscomp$21_type.append(...attrsToRemove_i$jscomp$117_options$jscomp$75_strings.childNodes));
    for (; null !== (content$jscomp$12_node$jscomp$21_type = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_walker.nextNode()) && parts.length < partCount;) {
      if (1 === content$jscomp$12_node$jscomp$21_type.nodeType) {
        if (content$jscomp$12_node$jscomp$21_type.hasAttributes()) {
          attrsToRemove_i$jscomp$117_options$jscomp$75_strings = [];
          for (const name of content$jscomp$12_node$jscomp$21_type.getAttributeNames()) {
            if (name.endsWith("$lit$") || name.startsWith(module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_marker)) {
              attrNames$jscomp$inline_139_i$jscomp$116_m = attrNames__tsickle_destructured_2[attrNameIndex++], attrsToRemove_i$jscomp$117_options$jscomp$75_strings.push(name), void 0 !== attrNames$jscomp$inline_139_i$jscomp$116_m ? (JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings = content$jscomp$12_node$jscomp$21_type.getAttribute(attrNames$jscomp$inline_139_i$jscomp$116_m.toLowerCase() + "$lit$").split(module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_marker), 
              attrNames$jscomp$inline_139_i$jscomp$116_m = /([.?@])?(.*)/.exec(attrNames$jscomp$inline_139_i$jscomp$116_m), parts.push({type:1, index:nodeIndex, name:attrNames$jscomp$inline_139_i$jscomp$116_m[2], strings:JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings, ctor:"." === attrNames$jscomp$inline_139_i$jscomp$116_m[1] ? module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$PropertyPart : "?" === attrNames$jscomp$inline_139_i$jscomp$116_m[1] ? 
              module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$BooleanAttributePart : "@" === attrNames$jscomp$inline_139_i$jscomp$116_m[1] ? module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$EventPart : module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$AttributePart,})) : parts.push({type:6, index:nodeIndex,});
            }
          }
          for (const name of attrsToRemove_i$jscomp$117_options$jscomp$75_strings) {
            content$jscomp$12_node$jscomp$21_type.removeAttribute(name);
          }
        }
        if (module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_rawTextElement.test(content$jscomp$12_node$jscomp$21_type.tagName) && (attrsToRemove_i$jscomp$117_options$jscomp$75_strings = content$jscomp$12_node$jscomp$21_type.textContent.split(module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_marker), JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings = attrsToRemove_i$jscomp$117_options$jscomp$75_strings.length - 
        1, 0 < JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings)) {
          content$jscomp$12_node$jscomp$21_type.textContent = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_trustedTypes ? module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_trustedTypes.emptyScript : "";
          for (attrNames$jscomp$inline_139_i$jscomp$116_m = 0; attrNames$jscomp$inline_139_i$jscomp$116_m < JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings; attrNames$jscomp$inline_139_i$jscomp$116_m++) {
            content$jscomp$12_node$jscomp$21_type.append(attrsToRemove_i$jscomp$117_options$jscomp$75_strings[attrNames$jscomp$inline_139_i$jscomp$116_m], module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_d.createComment("")), module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_walker.nextNode(), parts.push({type:2, index:++nodeIndex});
          }
          content$jscomp$12_node$jscomp$21_type.append(attrsToRemove_i$jscomp$117_options$jscomp$75_strings[JSCompiler_inline_result$jscomp$17_lastIndex$jscomp$1_statics$jscomp$1_strings], module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_d.createComment(""));
        }
      } else if (8 === content$jscomp$12_node$jscomp$21_type.nodeType) {
        if (content$jscomp$12_node$jscomp$21_type.data === module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_markerMatch) {
          parts.push({type:2, index:nodeIndex});
        } else {
          for (attrsToRemove_i$jscomp$117_options$jscomp$75_strings = -1; -1 !== (attrsToRemove_i$jscomp$117_options$jscomp$75_strings = content$jscomp$12_node$jscomp$21_type.data.indexOf(module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_marker, attrsToRemove_i$jscomp$117_options$jscomp$75_strings + 1));) {
            parts.push({type:7, index:nodeIndex}), attrsToRemove_i$jscomp$117_options$jscomp$75_strings += module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_marker.length - 1;
          }
        }
      }
      nodeIndex++;
    }
  }
  static createElement(html) {
    const el = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_d.createElement("template");
    el.innerHTML = html;
    return el;
  }
};
function module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_resolveDirective(part, value, parent = part, attributeIndex) {
  if (value === module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$noChange) {
    return value;
  }
  let currentDirective = void 0 !== attributeIndex ? parent.__directives?.[attributeIndex] : parent.__directive;
  const nextDirectiveConstructor = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_isPrimitive(value) ? void 0 : value._$litDirective$;
  currentDirective?.constructor !== nextDirectiveConstructor && (currentDirective?._$notifyDirectiveConnectionChanged?.(!1), void 0 === nextDirectiveConstructor ? currentDirective = void 0 : (currentDirective = new nextDirectiveConstructor(part), currentDirective._$initialize(part, parent, attributeIndex)), void 0 !== attributeIndex ? (parent.__directives ?? (parent.__directives = []))[attributeIndex] = currentDirective : parent.__directive = currentDirective);
  void 0 !== currentDirective && (value = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_resolveDirective(part, currentDirective._$resolve(part, value.values), currentDirective, attributeIndex));
  return value;
}
class module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_TemplateInstance {
  constructor(template, parent) {
    this._parts = [];
    this._$disconnectableChildren = void 0;
    this._$template = template;
    this._$parent = parent;
  }
  get parentNode() {
    return this._$parent.parentNode;
  }
  get _$isConnected() {
    return this._$parent._$isConnected;
  }
  _clone(options) {
    const {el:{content}, parts} = this._$template, fragment = (options?.creationScope ?? module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_d).importNode(content, !0);
    module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_walker.currentNode = fragment;
    let node = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_walker.nextNode(), nodeIndex = 0, partIndex = 0, templatePart = parts[0];
    for (; void 0 !== templatePart;) {
      if (nodeIndex === templatePart.index) {
        let part;
        2 === templatePart.type ? part = new module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$ChildPart(node, node.nextSibling, this, options) : 1 === templatePart.type ? part = new templatePart.ctor(node, templatePart.name, templatePart.strings, this, options) : 6 === templatePart.type && (part = new module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$ElementPart(node, this, options));
        this._parts.push(part);
        templatePart = parts[++partIndex];
      }
      nodeIndex !== templatePart?.index && (node = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_walker.nextNode(), nodeIndex++);
    }
    return fragment;
  }
  _update(values) {
    let i = 0;
    for (const part of this._parts) {
      void 0 !== part && (void 0 !== part.strings ? (part._$setValue(values, part, i), i += part.strings.length - 2) : part._$setValue(values[i])), i++;
    }
  }
}
var module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$ChildPart = class {
  constructor(startNode, endNode, parent, options) {
    this.type = 2;
    this._$committedValue = module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing;
    this._$disconnectableChildren = void 0;
    this._$startNode = startNode;
    this._$endNode = endNode;
    this._$parent = parent;
    this.options = options;
    this.__isConnected = options?.isConnected ?? !0;
    this._textSanitizer = void 0;
  }
  get _$isConnected() {
    return this._$parent?._$isConnected ?? this.__isConnected;
  }
  get parentNode() {
    let parentNode = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_wrap(this._$startNode).parentNode;
    const parent = this._$parent;
    void 0 !== parent && 11 === parentNode.nodeType && (parentNode = parent.parentNode);
    return parentNode;
  }
  _$setValue(value, directiveParent = this) {
    value = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_resolveDirective(this, value, directiveParent);
    module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_isPrimitive(value) ? value === module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing || null == value || "" === value ? (this._$committedValue !== module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing && this._$clear(), this._$committedValue = module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing) : 
    value !== this._$committedValue && value !== module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$noChange && this._commitText(value) : void 0 !== value._$litType$ ? this._commitTemplateResult(value) : void 0 !== value.nodeType ? this._commitNode(value) : module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_isArray(value) || "function" === typeof value?.[Symbol.iterator] ? this._commitIterable(value) : this._commitText(value);
  }
  _insert(node, ref = this._$endNode) {
    return module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_wrap(module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_wrap(this._$startNode).parentNode).insertBefore(node, ref);
  }
  _commitNode(value) {
    if (this._$committedValue !== value) {
      this._$clear();
      if (module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$internal$2dsecurity$sanitizerFactory !== module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_noopSanitizer) {
        const parentNodeName = this._$startNode.parentNode?.nodeName;
        if ("STYLE" === parentNodeName || "SCRIPT" === parentNodeName) {
          throw Error("Forbidden");
        }
      }
      this._$committedValue = this._insert(value);
    }
  }
  _commitText(value) {
    if (this._$committedValue !== module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing && module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_isPrimitive(this._$committedValue)) {
      var node$jscomp$24_textNode = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_wrap(this._$startNode).nextSibling;
      void 0 === this._textSanitizer && (this._textSanitizer = module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$internal$2dsecurity$sanitizerFactory(node$jscomp$24_textNode, "data", "property"));
      value = this._textSanitizer(value);
      node$jscomp$24_textNode.data = value;
    } else {
      node$jscomp$24_textNode = document.createTextNode(""), this._commitNode(node$jscomp$24_textNode), void 0 === this._textSanitizer && (this._textSanitizer = module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$internal$2dsecurity$sanitizerFactory(node$jscomp$24_textNode, "data", "property")), value = this._textSanitizer(value), node$jscomp$24_textNode.data = value;
    }
    this._$committedValue = value;
  }
  _commitTemplateResult(instance$jscomp$1_result$jscomp$19_template) {
    const {values, _$litType$:type} = instance$jscomp$1_result$jscomp$19_template;
    instance$jscomp$1_result$jscomp$19_template = "number" === typeof type ? this._$getTemplate(instance$jscomp$1_result$jscomp$19_template) : (void 0 === type.el && (type.el = module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$Template.createElement(type.h, this.options)), type);
    if (this._$committedValue?._$template === instance$jscomp$1_result$jscomp$19_template) {
      this._$committedValue._update(values);
    } else {
      instance$jscomp$1_result$jscomp$19_template = new module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_TemplateInstance(instance$jscomp$1_result$jscomp$19_template, this);
      const fragment = instance$jscomp$1_result$jscomp$19_template._clone(this.options);
      instance$jscomp$1_result$jscomp$19_template._update(values);
      this._commitNode(fragment);
      this._$committedValue = instance$jscomp$1_result$jscomp$19_template;
    }
  }
  _$getTemplate(result) {
    const strings = result.strings;
    let template = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_templateCache.get(strings);
    if (void 0 === template) {
      const key = strings.join("\x00");
      template = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_templateStringContentsToTemplates.get(key);
      void 0 === template && (template = new module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$Template(result), module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_templateStringContentsToTemplates.set(key, template));
      module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_templateCache.set(strings, template);
    }
    return template;
  }
  _commitIterable(value) {
    module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_isArray(this._$committedValue) || (this._$committedValue = [], this._$clear());
    const itemParts = this._$committedValue;
    let partIndex = 0, itemPart;
    for (const item of value) {
      partIndex === itemParts.length ? itemParts.push(itemPart = new module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$ChildPart(this._insert(module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_d.createComment("")), this._insert(module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_d.createComment("")), this, this.options)) : itemPart = itemParts[partIndex], itemPart._$setValue(item), partIndex++;
    }
    partIndex < itemParts.length && (this._$clear(itemPart && module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_wrap(itemPart._$endNode).nextSibling, partIndex), itemParts.length = partIndex);
  }
  _$clear(start = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_wrap(this._$startNode).nextSibling, from$jscomp$7_n) {
    for (this._$notifyConnectionChanged?.(!1, !0, from$jscomp$7_n); start && start !== this._$endNode;) {
      from$jscomp$7_n = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_wrap(start).nextSibling, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_wrap(start).remove(), start = from$jscomp$7_n;
    }
  }
  setConnected(isConnected) {
    void 0 === this._$parent && (this.__isConnected = isConnected, this._$notifyConnectionChanged?.(isConnected));
  }
}, module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$AttributePart = class {
  constructor(element, name, strings, parent, options) {
    this.type = 1;
    this._$committedValue = module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing;
    this._$disconnectableChildren = void 0;
    this.element = element;
    this.name = name;
    this._$parent = parent;
    this.options = options;
    2 < strings.length || "" !== strings[0] || "" !== strings[1] ? (this._$committedValue = Array(strings.length - 1).fill(new String()), this.strings = strings) : this._$committedValue = module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing;
    this._sanitizer = void 0;
  }
  get tagName() {
    return this.element.tagName;
  }
  get _$isConnected() {
    return this._$parent._$isConnected;
  }
  _$setValue(value, directiveParent = this, valueIndex, noCommit) {
    const strings = this.strings;
    let change = !1;
    if (void 0 === strings) {
      if (value = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_resolveDirective(this, value, directiveParent, 0), change = !module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_isPrimitive(value) || value !== this._$committedValue && value !== module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$noChange) {
        this._$committedValue = value;
      }
    } else {
      const values = value;
      value = strings[0];
      let i, v;
      for (i = 0; i < strings.length - 1; i++) {
        v = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_resolveDirective(this, values[valueIndex + i], directiveParent, i), v === module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$noChange && (v = this._$committedValue[i]), change || (change = !module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_isPrimitive(v) || v !== this._$committedValue[i]), v === module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing ? 
        value = module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing : value !== module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing && (value += (v ?? "") + strings[i + 1]), this._$committedValue[i] = v;
      }
    }
    change && !noCommit && this._commitValue(value);
  }
  _commitValue(value) {
    value === module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing ? module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_wrap(this.element).removeAttribute(this.name) : (void 0 === this._sanitizer && (this._sanitizer = module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$internal$2dsecurity$sanitizerFactory(this.element, this.name, "attribute")), value = this._sanitizer(value ?? ""), module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_wrap(this.element).setAttribute(this.name, 
    value ?? ""));
  }
}, module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$PropertyPart = class extends module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$AttributePart {
  constructor() {
    super(...arguments);
    this.type = 3;
  }
  _commitValue(value) {
    void 0 === this._sanitizer && (this._sanitizer = module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$internal$2dsecurity$sanitizerFactory(this.element, this.name, "property"));
    value = this._sanitizer(value);
    this.element[this.name] = value === module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing ? void 0 : value;
  }
};
const module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_emptyStringForBooleanAttribute = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_trustedTypes ? module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_trustedTypes.emptyScript : "";
var module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$BooleanAttributePart = class extends module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$AttributePart {
  constructor() {
    super(...arguments);
    this.type = 4;
  }
  _commitValue(value) {
    value && value !== module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing ? module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_wrap(this.element).setAttribute(this.name, module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_emptyStringForBooleanAttribute) : module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_wrap(this.element).removeAttribute(this.name);
  }
}, module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$EventPart = class extends module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$AttributePart {
  constructor(element, name, strings, parent, options) {
    super(element, name, strings, parent, options);
    this.type = 5;
  }
  _$setValue(newListener, directiveParent = this) {
    newListener = module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_resolveDirective(this, newListener, directiveParent, 0) ?? module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing;
    if (newListener !== module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$noChange) {
      directiveParent = this._$committedValue;
      var shouldRemoveListener = newListener === module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing && directiveParent !== module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing || newListener.capture !== directiveParent.capture || newListener.once !== directiveParent.once || newListener.passive !== directiveParent.passive, shouldAddListener = newListener !== module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing && 
      (directiveParent === module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$nothing || shouldRemoveListener);
      shouldRemoveListener && this.element.removeEventListener(this.name, this, directiveParent);
      shouldAddListener && this.element.addEventListener(this.name, this, newListener);
      this._$committedValue = newListener;
    }
  }
  handleEvent(event) {
    "function" === typeof this._$committedValue ? this._$committedValue.call(this.options?.host ?? this.element, event) : this._$committedValue.handleEvent(event);
  }
}, module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$ElementPart = class {
  constructor(element, parent, options) {
    this.element = element;
    this.type = 6;
    this._$disconnectableChildren = void 0;
    this._$parent = parent;
    this.options = options;
  }
  get _$isConnected() {
    return this._$parent._$isConnected;
  }
  _$setValue(value) {
    module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_resolveDirective(this, value);
  }
};
(0,window.litHtmlPolyfillSupport)?.(module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$Template, module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$ChildPart);
(module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_global.litHtmlVersions ?? (module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_global.litHtmlVersions = [])).push("2.4.0");
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/lit-html/src/index.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/css-tag.closure.js]
const module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag_global = window;
var module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag$supportsAdoptingStyleSheets = module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag_global.ShadowRoot && (void 0 === module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag_global.ShadyCSS || module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag_global.ShadyCSS.nativeShadow) && "adoptedStyleSheets" in 
Document.prototype && "replace" in CSSStyleSheet.prototype;
const module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag_constructionToken = Symbol(), module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag_cssTagCache = new WeakMap();
var module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag$CSSResult = class {
  constructor(cssText, strings) {
    this._$cssResult$ = !0;
    if (module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag_constructionToken !== module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag_constructionToken) {
      throw Error("CSSResult is not constructable. Use `unsafeCSS` or `css` instead.");
    }
    this.cssText = cssText;
    this._strings = strings;
  }
  get JSC$2349_styleSheet() {
    let styleSheet = this._styleSheet;
    const strings = this._strings;
    if (module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag$supportsAdoptingStyleSheets && void 0 === styleSheet) {
      const cacheable = void 0 !== strings && 1 === strings.length;
      cacheable && (styleSheet = module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag_cssTagCache.get(strings));
      void 0 === styleSheet && ((this._styleSheet = styleSheet = new CSSStyleSheet()).replaceSync(this.cssText), cacheable && module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag_cssTagCache.set(strings, styleSheet));
    }
    return styleSheet;
  }
  toString() {
    return this.cssText;
  }
}, module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag$css = (strings, ...cssText$jscomp$2_values) => {
  cssText$jscomp$2_values = 1 === strings.length ? strings[0] : cssText$jscomp$2_values.reduce((acc, JSCompiler_inline_result$jscomp$19_v, idx) => {
    if (!0 === JSCompiler_inline_result$jscomp$19_v._$cssResult$) {
      JSCompiler_inline_result$jscomp$19_v = JSCompiler_inline_result$jscomp$19_v.cssText;
    } else {
      if ("number" !== typeof JSCompiler_inline_result$jscomp$19_v) {
        throw Error("Value passed to 'css' function must be a 'css' function result: " + `${JSCompiler_inline_result$jscomp$19_v}. Use 'unsafeCSS' to pass non-literal values, but take care ` + "to ensure page security.");
      }
    }
    return acc + JSCompiler_inline_result$jscomp$19_v + strings[idx + 1];
  }, strings[0]);
  return new module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag$CSSResult(cssText$jscomp$2_values, strings);
}, module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag$adoptStyles = (renderRoot, styles) => {
  module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag$supportsAdoptingStyleSheets ? renderRoot.adoptedStyleSheets = styles.map(s => s instanceof CSSStyleSheet ? s : s.JSC$2349_styleSheet) : styles.forEach(s => {
    const style = document.createElement("style"), nonce = module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag_global.litNonce;
    void 0 !== nonce && style.setAttribute("nonce", nonce);
    style.textContent = s.cssText;
    renderRoot.appendChild(style);
  });
}, module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag$getCompatibleStyle = module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag$supportsAdoptingStyleSheets ? s => s : JSCompiler_temp$jscomp$20_s => {
  if (JSCompiler_temp$jscomp$20_s instanceof CSSStyleSheet) {
    let cssText = "";
    for (const rule of JSCompiler_temp$jscomp$20_s.cssRules) {
      cssText += rule.cssText;
    }
    JSCompiler_temp$jscomp$20_s = new module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag$CSSResult("string" === typeof cssText ? cssText : String(cssText));
  }
  return JSCompiler_temp$jscomp$20_s;
};
//[third_party/javascript/closure/reflect/reflect.js]
//[third_party/javascript/custom_elements/src/auto-native-shim.js]
/*

 Copyright 2016 Google LLC
 SPDX-License-Identifier: BSD-3-Clause
*/
(function() {
  if (module$exports$google3$javascript$polymer$detect_transpilation$detect_transpilation$wasTranspiledToEs5 && !HTMLElement.es5Shimmed && void 0 !== goog$global.Reflect && void 0 !== goog$global.customElements && !goog$global.customElements.polyfillWrapFlushCallback) {
    var BuiltInHTMLElement = HTMLElement;
    goog$global.HTMLElement = function() {
      return Reflect.construct(BuiltInHTMLElement, [], this.constructor);
    };
    HTMLElement.prototype = BuiltInHTMLElement.prototype;
    HTMLElement.prototype.constructor = HTMLElement;
    HTMLElement.es5Shimmed = !0;
    Object.setPrototypeOf(HTMLElement, BuiltInHTMLElement);
  }
})();
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/reactive-element.closure.js]
const module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_global = window, module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_trustedTypes = module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_global.trustedTypes, module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_emptyStringForBooleanAttribute = module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_trustedTypes ? 
module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_trustedTypes.emptyScript : "", module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_polyfillSupport = module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_global.reactiveElementPolyfillSupport;
var module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement$defaultConverter = {toAttribute(value, type) {
  switch(type) {
    case Boolean:
      value = value ? module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_emptyStringForBooleanAttribute : null;
      break;
    case Object:
    case Array:
      value = null == value ? value : JSON.stringify(value);
  }
  return value;
}, fromAttribute(value, type) {
  let fromValue = value;
  switch(type) {
    case Boolean:
      fromValue = null !== value;
      break;
    case Number:
      fromValue = null === value ? null : Number(value);
      break;
    case Object:
    case Array:
      try {
        fromValue = JSON.parse(value);
      } catch (e) {
        fromValue = null;
      }
  }
  return fromValue;
},}, module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement$notEqual = (value, old) => old !== value && (old === old || value === value);
const module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_defaultPropertyDeclaration = {attribute:!0, type:String, converter:module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement$defaultConverter, reflect:!1, hasChanged:module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement$notEqual,};
function JSCompiler_StaticMethods_finalize(JSCompiler_StaticMethods_finalize$self) {
  if (!JSCompiler_StaticMethods_finalize$self.hasOwnProperty("finalized")) {
    JSCompiler_StaticMethods_finalize$self.finalized = !0;
    var props$jscomp$2_set$jscomp$inline_161_styles = Object.getPrototypeOf(JSCompiler_StaticMethods_finalize$self);
    JSCompiler_StaticMethods_finalize(props$jscomp$2_set$jscomp$inline_161_styles);
    void 0 !== props$jscomp$2_set$jscomp$inline_161_styles._initializers && (JSCompiler_StaticMethods_finalize$self._initializers = [...props$jscomp$2_set$jscomp$inline_161_styles._initializers]);
    JSCompiler_StaticMethods_finalize$self.elementProperties = new Map(props$jscomp$2_set$jscomp$inline_161_styles.elementProperties);
    JSCompiler_StaticMethods_finalize$self.__attributeToPropertyMap = new Map();
    if (JSCompiler_StaticMethods_finalize$self.hasOwnProperty("properties")) {
      props$jscomp$2_set$jscomp$inline_161_styles = JSCompiler_StaticMethods_finalize$self.properties;
      const propKeys = [...Object.getOwnPropertyNames(props$jscomp$2_set$jscomp$inline_161_styles), ...Object.getOwnPropertySymbols(props$jscomp$2_set$jscomp$inline_161_styles),];
      for (elementStyles$jscomp$inline_160_p of propKeys) {
        JSCompiler_StaticMethods_createProperty(JSCompiler_StaticMethods_finalize$self, elementStyles$jscomp$inline_160_p, props$jscomp$2_set$jscomp$inline_161_styles[elementStyles$jscomp$inline_160_p]);
      }
    }
    props$jscomp$2_set$jscomp$inline_161_styles = JSCompiler_StaticMethods_finalize$self.styles;
    var elementStyles$jscomp$inline_160_p = [];
    if (Array.isArray(props$jscomp$2_set$jscomp$inline_161_styles)) {
      props$jscomp$2_set$jscomp$inline_161_styles = new Set(props$jscomp$2_set$jscomp$inline_161_styles.flat(Infinity).reverse());
      for (const s of props$jscomp$2_set$jscomp$inline_161_styles) {
        elementStyles$jscomp$inline_160_p.unshift(module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag$getCompatibleStyle(s));
      }
    } else {
      void 0 !== props$jscomp$2_set$jscomp$inline_161_styles && elementStyles$jscomp$inline_160_p.push(module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag$getCompatibleStyle(props$jscomp$2_set$jscomp$inline_161_styles));
    }
    JSCompiler_StaticMethods_finalize$self.elementStyles = elementStyles$jscomp$inline_160_p;
  }
}
function JSCompiler_StaticMethods_getPropertyDescriptor(name, key, options) {
  return {get() {
    return this[key];
  }, set(value) {
    const oldValue = this[name];
    this[key] = value;
    JSCompiler_StaticMethods_requestUpdate(this, name, oldValue, options);
  }, configurable:!0, enumerable:!0,};
}
function JSCompiler_StaticMethods_createProperty(JSCompiler_StaticMethods_createProperty$self, name, descriptor$jscomp$2_options = module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_defaultPropertyDeclaration) {
  descriptor$jscomp$2_options.state && (descriptor$jscomp$2_options.attribute = !1);
  JSCompiler_StaticMethods_finalize(JSCompiler_StaticMethods_createProperty$self);
  JSCompiler_StaticMethods_createProperty$self.elementProperties.set(name, descriptor$jscomp$2_options);
  descriptor$jscomp$2_options.noAccessor || JSCompiler_StaticMethods_createProperty$self.prototype.hasOwnProperty(name) || (descriptor$jscomp$2_options = JSCompiler_StaticMethods_getPropertyDescriptor(name, "symbol" === typeof name ? Symbol() : `__${name}`, descriptor$jscomp$2_options), void 0 !== descriptor$jscomp$2_options && Object.defineProperty(JSCompiler_StaticMethods_createProperty$self.prototype, name, descriptor$jscomp$2_options));
}
function JSCompiler_StaticMethods_requestUpdate(JSCompiler_StaticMethods_requestUpdate$self, name, oldValue, options) {
  let shouldRequestUpdate = !0;
  void 0 !== name && (options = options || JSCompiler_StaticMethods_requestUpdate$self.constructor.elementProperties.get(name) || module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_defaultPropertyDeclaration, (options.hasChanged || module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement$notEqual)(JSCompiler_StaticMethods_requestUpdate$self[name], oldValue) ? (JSCompiler_StaticMethods_requestUpdate$self._$changedProperties.has(name) || 
  JSCompiler_StaticMethods_requestUpdate$self._$changedProperties.set(name, oldValue), !0 === options.reflect && JSCompiler_StaticMethods_requestUpdate$self.__reflectingProperty !== name && (void 0 === JSCompiler_StaticMethods_requestUpdate$self.__reflectingProperties && (JSCompiler_StaticMethods_requestUpdate$self.__reflectingProperties = new Map()), JSCompiler_StaticMethods_requestUpdate$self.__reflectingProperties.set(name, options))) : shouldRequestUpdate = !1);
  !JSCompiler_StaticMethods_requestUpdate$self.isUpdatePending && shouldRequestUpdate && (JSCompiler_StaticMethods_requestUpdate$self.__updatePromise = JSCompiler_StaticMethods_requestUpdate$self.__enqueueUpdate());
}
function JSCompiler_StaticMethods_performUpdate(JSCompiler_StaticMethods_performUpdate$self) {
  if (JSCompiler_StaticMethods_performUpdate$self.isUpdatePending) {
    JSCompiler_StaticMethods_performUpdate$self.__instanceProperties && (JSCompiler_StaticMethods_performUpdate$self.__instanceProperties.forEach((v, p) => JSCompiler_StaticMethods_performUpdate$self[p] = v), JSCompiler_StaticMethods_performUpdate$self.__instanceProperties = void 0);
    var shouldUpdate = !1, changedProperties = JSCompiler_StaticMethods_performUpdate$self._$changedProperties;
    try {
      shouldUpdate = !0, JSCompiler_StaticMethods_performUpdate$self.__controllers?.forEach(c => c.hostUpdate?.()), JSCompiler_StaticMethods_performUpdate$self.update(changedProperties);
    } catch (e) {
      throw shouldUpdate = !1, JSCompiler_StaticMethods_performUpdate$self.__markUpdated(), e;
    }
    shouldUpdate && JSCompiler_StaticMethods_performUpdate$self._$didUpdate(changedProperties);
  }
}
var module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement$ReactiveElement = class extends HTMLElement {
  constructor() {
    super();
    this.__instanceProperties = new Map();
    this.hasUpdated = this.isUpdatePending = !1;
    this.__reflectingProperty = null;
    this._initialize();
  }
  static get observedAttributes() {
    JSCompiler_StaticMethods_finalize(this);
    const attributes = [];
    this.elementProperties.forEach((attr$jscomp$4_v, p) => {
      attr$jscomp$4_v = this.__attributeNameForProperty(p, attr$jscomp$4_v);
      void 0 !== attr$jscomp$4_v && (this.__attributeToPropertyMap.set(attr$jscomp$4_v, p), attributes.push(attr$jscomp$4_v));
    });
    return attributes;
  }
  static __attributeNameForProperty(name, attribute$jscomp$4_options) {
    attribute$jscomp$4_options = attribute$jscomp$4_options.attribute;
    return !1 === attribute$jscomp$4_options ? void 0 : "string" === typeof attribute$jscomp$4_options ? attribute$jscomp$4_options : "string" === typeof name ? name.toLowerCase() : void 0;
  }
  _initialize() {
    this.__updatePromise = new Promise(res => this.enableUpdating = res);
    this._$changedProperties = new Map();
    this.__saveInstanceProperties();
    JSCompiler_StaticMethods_requestUpdate(this);
    this.constructor._initializers?.forEach(i => i(this));
  }
  __saveInstanceProperties() {
    this.constructor.elementProperties.forEach((_v, p) => {
      this.hasOwnProperty(p) && (this.__instanceProperties.set(p, this[p]), delete this[p]);
    });
  }
  createRenderRoot() {
    const renderRoot = this.shadowRoot ?? this.attachShadow(this.constructor.shadowRootOptions);
    module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag$adoptStyles(renderRoot, this.constructor.elementStyles);
    return renderRoot;
  }
  connectedCallback() {
    void 0 === this.renderRoot && (this.renderRoot = this.createRenderRoot());
    this.enableUpdating(!0);
    this.__controllers?.forEach(c => c.hostConnected?.());
  }
  enableUpdating() {
  }
  disconnectedCallback() {
    this.__controllers?.forEach(c => c.hostDisconnected?.());
  }
  attributeChangedCallback(name, _old, value) {
    this._$attributeToProperty(name, value);
  }
  __propertyToAttribute(name, attrValue_value, options = module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_defaultPropertyDeclaration) {
    const attr = this.constructor.__attributeNameForProperty(name, options);
    void 0 !== attr && !0 === options.reflect && (attrValue_value = (void 0 !== options.converter?.toAttribute ? options.converter : module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement$defaultConverter).toAttribute(attrValue_value, options.type), this.__reflectingProperty = name, null == attrValue_value ? this.removeAttribute(attr) : this.setAttribute(attr, attrValue_value), this.__reflectingProperty = null);
  }
  _$attributeToProperty(name$jscomp$133_propName, value) {
    var ctor$jscomp$2_options = this.constructor;
    name$jscomp$133_propName = ctor$jscomp$2_options.__attributeToPropertyMap.get(name$jscomp$133_propName);
    if (void 0 !== name$jscomp$133_propName && this.__reflectingProperty !== name$jscomp$133_propName) {
      ctor$jscomp$2_options = ctor$jscomp$2_options.elementProperties.get(name$jscomp$133_propName) || module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_defaultPropertyDeclaration;
      const converter = "function" === typeof ctor$jscomp$2_options.converter ? {fromAttribute:ctor$jscomp$2_options.converter} : void 0 !== ctor$jscomp$2_options.converter?.fromAttribute ? ctor$jscomp$2_options.converter : module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement$defaultConverter;
      this.__reflectingProperty = name$jscomp$133_propName;
      this[name$jscomp$133_propName] = converter.fromAttribute(value, ctor$jscomp$2_options.type);
      this.__reflectingProperty = null;
    }
  }
  async __enqueueUpdate() {
    this.isUpdatePending = !0;
    try {
      await this.__updatePromise;
    } catch (e) {
      this.squelchUpdateErrorsDuringLit2Migration || Promise.reject(e);
    }
    const result = JSCompiler_StaticMethods_performUpdate(this);
    null != result && await result;
    return !this.isUpdatePending;
  }
  _$didUpdate() {
    this.__controllers?.forEach(c => c.hostUpdated?.());
    this.hasUpdated || (this.hasUpdated = !0);
    this.updated();
  }
  __markUpdated() {
    this._$changedProperties = new Map();
    this.isUpdatePending = !1;
  }
  update() {
    void 0 !== this.__reflectingProperties && (this.__reflectingProperties.forEach((v, k) => this.__propertyToAttribute(k, this[k], v)), this.__reflectingProperties = void 0);
    this.__markUpdated();
  }
  updated() {
  }
};
module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement$ReactiveElement.finalized = !0;
module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement$ReactiveElement.elementProperties = new Map();
module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement$ReactiveElement.elementStyles = [];
module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement$ReactiveElement.shadowRootOptions = {mode:"open"};
module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_polyfillSupport?.({ReactiveElement:module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement$ReactiveElement});
(module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_global.reactiveElementVersions ?? (module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement_global.reactiveElementVersions = [])).push("1.4.2");
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/index.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/lit-element/src/lit-element.closure.js]
var module$exports$google3$third_party$javascript$lit$packages$lit$2delement$src$lit$2delement$LitElement = class extends module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$reactive$2delement$ReactiveElement {
  constructor() {
    super(...arguments);
    this.renderOptions = {host:this};
    this.__childPart = void 0;
  }
  createRenderRoot() {
    const renderRoot = super.createRenderRoot();
    let $jscomp$logical$assign$tmpm1798816166$1;
    ($jscomp$logical$assign$tmpm1798816166$1 = this.renderOptions).renderBefore ?? ($jscomp$logical$assign$tmpm1798816166$1.renderBefore = renderRoot.firstChild);
    return renderRoot;
  }
  update(changedProperties$jscomp$4_container) {
    const value = this.render();
    this.hasUpdated || (this.renderOptions.isConnected = this.isConnected);
    super.update(changedProperties$jscomp$4_container);
    changedProperties$jscomp$4_container = this.renderRoot;
    var options = this.renderOptions;
    const partOwnerNode = options?.renderBefore ?? changedProperties$jscomp$4_container;
    var endNode$jscomp$inline_169_part = partOwnerNode._$litPart$;
    void 0 === endNode$jscomp$inline_169_part && (endNode$jscomp$inline_169_part = options?.renderBefore ?? null, partOwnerNode._$litPart$ = endNode$jscomp$inline_169_part = new module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$ChildPart(changedProperties$jscomp$4_container.insertBefore(module$contents$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml_d.createComment(""), endNode$jscomp$inline_169_part), endNode$jscomp$inline_169_part, void 0, 
    options ?? {}));
    endNode$jscomp$inline_169_part._$setValue(value);
    this.__childPart = endNode$jscomp$inline_169_part;
  }
  connectedCallback() {
    super.connectedCallback();
    this.__childPart?.setConnected(!0);
  }
  disconnectedCallback() {
    super.disconnectedCallback();
    this.__childPart?.setConnected(!1);
  }
  render() {
    return module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$noChange;
  }
};
module$exports$google3$third_party$javascript$lit$packages$lit$2delement$src$lit$2delement$LitElement.finalized = !0;
module$exports$google3$third_party$javascript$lit$packages$lit$2delement$src$lit$2delement$LitElement._$litElement$ = !0;
(0,window.litElementPolyfillSupport)?.({LitElement:module$exports$google3$third_party$javascript$lit$packages$lit$2delement$src$lit$2delement$LitElement});
let $jscomp$logical$assign$tmpm1798816166$2;
(($jscomp$logical$assign$tmpm1798816166$2 = window).litElementVersions ?? ($jscomp$logical$assign$tmpm1798816166$2.litElementVersions = [])).push("3.2.2");
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/lit-html/src/is-server.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/lit/src/index.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/java_src/gerritcodereview/plugins/codemirror_editor/web/element/codemirror-css.closure.js]
var module$exports$google3$third_party$java_src$gerritcodereview$plugins$codemirror_editor$web$element$codemirror$2dcss$codemirrorStyles = module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag$css`
/* BASICS */

.CodeMirror {
  /* Set height, width, borders, and global font properties here */
  font-family: monospace;
  height: 300px;
  color: black;
  direction: ltr;
}

/* PADDING */

.CodeMirror-lines {
  padding: 4px 0; /* Vertical padding around content */
}
/* @noflip */
.CodeMirror pre.CodeMirror-line,
.CodeMirror pre.CodeMirror-line-like {
  padding: 0 4px; /* Horizontal padding of content */
}

.CodeMirror-scrollbar-filler, .CodeMirror-gutter-filler {
  background-color: white; /* The little square between H and V scrollbars */
}

/* GUTTER */

/* @noflip */
.CodeMirror-gutters {
  border-right: 1px solid #ddd;
  background-color: #f7f7f7;
  white-space: nowrap;
}
.CodeMirror-linenumbers {}
/* @noflip */
.CodeMirror-linenumber {
  padding: 0 3px 0 5px;
  min-width: 20px;
  text-align: right;
  color: #999;
  white-space: nowrap;
}

.CodeMirror-guttermarker { color: black; }
.CodeMirror-guttermarker-subtle { color: #999; }

/* CURSOR */

/* @noflip */
.CodeMirror-cursor {
  border-left: 1px solid black;
  border-right: none;
  width: 0;
}
/* Shown when moving in bi-directional text */
/* @noflip */
.CodeMirror div.CodeMirror-secondarycursor {
  border-left: 1px solid silver;
}
.cm-fat-cursor .CodeMirror-cursor {
  width: auto;
  border: 0 !important;
  background: #7e7;
}
.cm-fat-cursor div.CodeMirror-cursors {
  z-index: 1;
}
.cm-fat-cursor .CodeMirror-line::selection,
.cm-fat-cursor .CodeMirror-line > span::selection, 
.cm-fat-cursor .CodeMirror-line > span > span::selection { background: transparent; }
.cm-fat-cursor .CodeMirror-line::-moz-selection,
.cm-fat-cursor .CodeMirror-line > span::-moz-selection,
.cm-fat-cursor .CodeMirror-line > span > span::-moz-selection { background: transparent; }
.cm-fat-cursor { caret-color: transparent; }
@-moz-keyframes blink {
  0% {}
  50% { background-color: transparent; }
  100% {}
}
@-webkit-keyframes blink {
  0% {}
  50% { background-color: transparent; }
  100% {}
}
@keyframes blink {
  0% {}
  50% { background-color: transparent; }
  100% {}
}

/* Can style cursor different in overwrite (non-insert) mode */
.CodeMirror-overwrite .CodeMirror-cursor {}

.cm-tab { display: inline-block; text-decoration: inherit; }

.CodeMirror-rulers {
  position: absolute;
  left: 0; right: 0; top: -50px; bottom: 0;
  overflow: hidden;
}
/* @noflip */
.CodeMirror-ruler {
  border-left: 1px solid #ccc;
  top: 0; bottom: 0;
  position: absolute;
}

/* DEFAULT THEME */

.cm-s-default .cm-header {color: blue;}
.cm-s-default .cm-quote {color: #090;}
.cm-negative {color: #d44;}
.cm-positive {color: #292;}
.cm-header, .cm-strong {font-weight: bold;}
.cm-em {font-style: italic;}
.cm-link {text-decoration: underline;}
.cm-strikethrough {text-decoration: line-through;}

.cm-s-default .cm-keyword {color: #708;}
.cm-s-default .cm-atom {color: #219;}
.cm-s-default .cm-number {color: #164;}
.cm-s-default .cm-def {color: #00f;}
.cm-s-default .cm-variable,
.cm-s-default .cm-punctuation,
.cm-s-default .cm-property,
.cm-s-default .cm-operator {}
.cm-s-default .cm-variable-2 {color: #05a;}
.cm-s-default .cm-variable-3, .cm-s-default .cm-type {color: #085;}
.cm-s-default .cm-comment {color: #a50;}
.cm-s-default .cm-string {color: #a11;}
.cm-s-default .cm-string-2 {color: #f50;}
.cm-s-default .cm-meta {color: #555;}
.cm-s-default .cm-qualifier {color: #555;}
.cm-s-default .cm-builtin {color: #30a;}
.cm-s-default .cm-bracket {color: #997;}
.cm-s-default .cm-tag {color: #170;}
.cm-s-default .cm-attribute {color: #00c;}
.cm-s-default .cm-hr {color: #999;}
.cm-s-default .cm-link {color: #00c;}

.cm-s-default .cm-error {color: #f00;}
.cm-invalidchar {color: #f00;}

.CodeMirror-composing { border-bottom: 2px solid; }

/* Default styles for common addons */

div.CodeMirror span.CodeMirror-matchingbracket {color: #0b0;}
div.CodeMirror span.CodeMirror-nonmatchingbracket {color: #a22;}
.CodeMirror-matchingtag { background: rgba(255, 150, 0, .3); }
.CodeMirror-activeline-background {background: #e8f2ff;}

/* STOP */

/* The rest of this file contains styles related to the mechanics of
   the editor. You probably shouldn't touch them. */

.CodeMirror {
  position: relative;
  overflow: hidden;
  background: white;
}

/* @noflip */
.CodeMirror-scroll {
  overflow: scroll !important; /* Things will break if this is overridden */
  /* 50px is the magic margin used to hide the element's real scrollbars */
  /* See overflow: hidden in .CodeMirror */
  margin-bottom: -50px; margin-right: -50px;
  padding-bottom: 50px;
  height: 100%;
  outline: none; /* Prevent dragging from highlighting the element */
  position: relative;
  z-index: 0;
}
/* @noflip */
.CodeMirror-sizer {
  position: relative;
  border-right: 50px solid transparent;
}

/* The fake, visible scrollbars. Used to force redraw during scrolling
   before actual scrolling happens, thus preventing shaking and
   flickering artifacts. */
.CodeMirror-vscrollbar, .CodeMirror-hscrollbar, .CodeMirror-scrollbar-filler, .CodeMirror-gutter-filler {
  position: absolute;
  z-index: 6;
  display: none;
  outline: none;
}
/* @noflip */
.CodeMirror-vscrollbar {
  right: 0; top: 0;
  overflow-x: hidden;
  overflow-y: scroll;
}
/* @noflip */
.CodeMirror-hscrollbar {
  bottom: 0; left: 0;
  overflow-y: hidden;
  overflow-x: scroll;
}
/* @noflip */
.CodeMirror-scrollbar-filler {
  right: 0; bottom: 0;
}
/* @noflip */
.CodeMirror-gutter-filler {
  left: 0; bottom: 0;
}

/* @noflip */
.CodeMirror-gutters {
  position: absolute; left: 0; top: 0;
  min-height: 100%;
  z-index: 3;
}
.CodeMirror-gutter {
  white-space: normal;
  height: 100%;
  display: inline-block;
  vertical-align: top;
  margin-bottom: -50px;
}
.CodeMirror-gutter-wrapper {
  position: absolute;
  z-index: 4;
  background: none !important;
  border: none !important;
}
.CodeMirror-gutter-background {
  position: absolute;
  top: 0; bottom: 0;
  z-index: 4;
}
.CodeMirror-gutter-elt {
  position: absolute;
  cursor: default;
  z-index: 4;
}
.CodeMirror-gutter-wrapper ::selection { background-color: transparent }
.CodeMirror-gutter-wrapper ::-moz-selection { background-color: transparent }

.CodeMirror-lines {
  cursor: text;
  min-height: 1px; /* prevents collapsing before first draw */
}
.CodeMirror pre.CodeMirror-line,
.CodeMirror pre.CodeMirror-line-like {
  /* Reset some styles that the rest of the page might have set */
  -moz-border-radius: 0; -webkit-border-radius: 0; border-radius: 0;
  border-width: 0;
  background: transparent;
  font-family: inherit;
  font-size: inherit;
  margin: 0;
  white-space: pre;
  word-wrap: normal;
  line-height: inherit;
  color: inherit;
  z-index: 2;
  position: relative;
  overflow: visible;
  -webkit-tap-highlight-color: transparent;
  -webkit-font-variant-ligatures: contextual;
  font-variant-ligatures: contextual;
}
.CodeMirror-wrap pre.CodeMirror-line,
.CodeMirror-wrap pre.CodeMirror-line-like {
  word-wrap: break-word;
  white-space: pre-wrap;
  word-break: normal;
}

/* @noflip */
.CodeMirror-linebackground {
  position: absolute;
  left: 0; right: 0; top: 0; bottom: 0;
  z-index: 0;
}

.CodeMirror-linewidget {
  position: relative;
  z-index: 2;
  padding: 0.1px; /* Force widget margins to stay inside of the container */
}

.CodeMirror-widget {}

.CodeMirror-rtl pre { direction: rtl; }

.CodeMirror-code {
  outline: none;
}

/* Force content-box sizing for the elements where we expect it */
.CodeMirror-scroll,
.CodeMirror-sizer,
.CodeMirror-gutter,
.CodeMirror-gutters,
.CodeMirror-linenumber {
  -moz-box-sizing: content-box;
  box-sizing: content-box;
}

.CodeMirror-measure {
  position: absolute;
  width: 100%;
  height: 0;
  overflow: hidden;
  visibility: hidden;
}

.CodeMirror-cursor {
  position: absolute;
  pointer-events: none;
}
.CodeMirror-measure pre { position: static; }

div.CodeMirror-cursors {
  visibility: hidden;
  position: relative;
  z-index: 3;
}
div.CodeMirror-dragcursors {
  visibility: visible;
}

.CodeMirror-focused div.CodeMirror-cursors {
  visibility: visible;
}

.CodeMirror-selected { background: #d9d9d9; }
.CodeMirror-focused .CodeMirror-selected { background: #d7d4f0; }
.CodeMirror-crosshair { cursor: crosshair; }
.CodeMirror-line::selection, .CodeMirror-line > span::selection, .CodeMirror-line > span > span::selection { background: #d7d4f0; }
.CodeMirror-line::-moz-selection, .CodeMirror-line > span::-moz-selection, .CodeMirror-line > span > span::-moz-selection { background: #d7d4f0; }

.cm-searching {
  background-color: #ffa;
  background-color: rgba(255, 255, 0, .4);
}

/* Used to force a border model for a node */
/* @noflip */
.cm-force-border { padding-right: .1px; }

@media print {
  /* Hide the cursor when printing */
  .CodeMirror div.CodeMirror-cursors {
    visibility: hidden;
  }
}

/* See issue #2901 */
.cm-tab-wrap-hack:after { content: ''; }

/* Help users use markselection to safely style text background */
span.CodeMirror-selectedtext { background: none; }
`;
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/decorators/custom-element.closure.js]
const module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$decorators$custom$2delement_standardCustomElement = (tagName, descriptor) => ({kind:descriptor.kind, elements:descriptor.elements, finisher(clazz) {
  customElements.define(tagName, clazz);
},});
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/decorators/base.closure.js]
var module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$decorators$base$decorateProperty = ({finisher, descriptor}) => (info_protoOrDescriptor, name) => {
  if (void 0 !== name) {
    const ctor = info_protoOrDescriptor.constructor;
    void 0 !== descriptor && Object.defineProperty(info_protoOrDescriptor, name, descriptor(name));
    finisher?.(ctor, name);
  } else {
    const key = info_protoOrDescriptor.originalKey ?? info_protoOrDescriptor.key;
    info_protoOrDescriptor = void 0 != descriptor ? {kind:"method", placement:"prototype", key, descriptor:descriptor(info_protoOrDescriptor.key),} : {...info_protoOrDescriptor, key};
    void 0 != finisher && (info_protoOrDescriptor.finisher = function(ctor) {
      finisher(ctor, key);
    });
    return info_protoOrDescriptor;
  }
};
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/decorators/event-options.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/decorators/property.closure.js]
const module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$decorators$property_standardProperty = (options, element) => "method" !== element.kind || !element.descriptor || "value" in element.descriptor ? {kind:"field", key:Symbol(), placement:"own", descriptor:{}, originalKey:element.key, initializer() {
  "function" === typeof element.initializer && (this[element.key] = element.initializer.call(this));
}, finisher(clazz) {
  JSCompiler_StaticMethods_createProperty(clazz, element.key, options);
},} : {...element, finisher(clazz) {
  JSCompiler_StaticMethods_createProperty(clazz, element.key, options);
},};
function module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$decorators$property_property(options) {
  return (JSCompiler_temp$jscomp$24_protoOrDescriptor, name) => {
    void 0 !== name ? (JSCompiler_StaticMethods_createProperty(JSCompiler_temp$jscomp$24_protoOrDescriptor.constructor, name, options), JSCompiler_temp$jscomp$24_protoOrDescriptor = void 0) : JSCompiler_temp$jscomp$24_protoOrDescriptor = module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$decorators$property_standardProperty(options, JSCompiler_temp$jscomp$24_protoOrDescriptor);
    return JSCompiler_temp$jscomp$24_protoOrDescriptor;
  };
}
;
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/decorators/query-all.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/decorators/query-assigned-elements.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/decorators/query-assigned-nodes.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/decorators/query-async.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/decorators/query.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/decorators/state.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/lit/src/decorators.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/java_src/gerritcodereview/plugins/codemirror_editor/web/element/codemirror-element.closure.js]
function JSCompiler_StaticMethods_JSC$2381_initialize(JSCompiler_StaticMethods_JSC$2381_initialize$self) {
  if (JSCompiler_StaticMethods_JSC$2381_initialize$self.params && JSCompiler_StaticMethods_JSC$2381_initialize$self.isConnected && JSCompiler_StaticMethods_JSC$2381_initialize$self.wrapper && !JSCompiler_StaticMethods_JSC$2381_initialize$self.initialized) {
    JSCompiler_StaticMethods_JSC$2381_initialize$self.initialized = !0;
    var cm = CodeMirror(JSCompiler_StaticMethods_JSC$2381_initialize$self.wrapper, JSCompiler_StaticMethods_JSC$2381_initialize$self.params);
    setTimeout(() => {
      cm.refresh();
      cm.focus();
      JSCompiler_StaticMethods_JSC$2381_initialize$self.lineNum && cm.setCursor(JSCompiler_StaticMethods_JSC$2381_initialize$self.lineNum - 1);
    }, 1);
    cm.on("change", e => {
      JSCompiler_StaticMethods_JSC$2381_initialize$self.dispatchEvent(new CustomEvent("content-change", {detail:{value:e.getValue()}, bubbles:!0, composed:!0,}));
    });
  }
}
let module$contents$google3$third_party$java_src$gerritcodereview$plugins$codemirror_editor$web$element$codemirror$2delement_CodeMirrorElement = class extends module$exports$google3$third_party$javascript$lit$packages$lit$2delement$src$lit$2delement$LitElement {
  constructor() {
    super(...arguments);
    this.initialized = !1;
  }
  static get styles() {
    return [module$exports$google3$third_party$java_src$gerritcodereview$plugins$codemirror_editor$web$element$codemirror$2dcss$codemirrorStyles, module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$css$2dtag$css`
        .CodeMirror {
          font-family: var(--monospace-font-family);
          height: auto;
        }
        .CodeMirror-linenumbers {
          background-color: var(--background-color-tertiary);
        }
        .CodeMirror-linenumber {
          color: var(--deemphasized-text-color);
        }
        .CodeMirror-ruler {
          border-left: 1px solid var(--border-color);
        }
        .cm-trailingspace {
          background-color: var(--error-background);
        }
      `,];
  }
  render() {
    return module$exports$google3$third_party$javascript$lit$packages$lit$2dhtml$src$lit$2dhtml$html`<div id="wrapper"></div>`;
  }
  updated() {
    JSCompiler_StaticMethods_JSC$2381_initialize(this);
  }
};
module$exports$google3$third_party$javascript$tslib$tslib$__decorate([module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$decorators$property_property({type:Number}), module$exports$google3$third_party$javascript$tslib$tslib$__metadata(Number)], module$contents$google3$third_party$java_src$gerritcodereview$plugins$codemirror_editor$web$element$codemirror$2delement_CodeMirrorElement.prototype, "lineNum", void 0);
module$exports$google3$third_party$javascript$tslib$tslib$__decorate([module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$decorators$property_property({type:Object}), module$exports$google3$third_party$javascript$tslib$tslib$__metadata(Object)], module$contents$google3$third_party$java_src$gerritcodereview$plugins$codemirror_editor$web$element$codemirror$2delement_CodeMirrorElement.prototype, "params", void 0);
module$exports$google3$third_party$javascript$tslib$tslib$__decorate([function(selector, cache) {
  return module$exports$google3$third_party$javascript$lit$packages$reactive$2delement$src$decorators$base$decorateProperty({descriptor:name => {
    const descriptor = {get() {
      return this.renderRoot?.querySelector(selector) ?? null;
    }, enumerable:!0, configurable:!0,};
    if (cache) {
      const key = "symbol" === typeof name ? Symbol() : `__${name}`;
      descriptor.get = function() {
        void 0 === this[key] && (this[key] = this.renderRoot?.querySelector(selector) ?? null);
        return this[key];
      };
    }
    return descriptor;
  },});
}("#wrapper"), module$exports$google3$third_party$javascript$tslib$tslib$__metadata(HTMLElement)], module$contents$google3$third_party$java_src$gerritcodereview$plugins$codemirror_editor$web$element$codemirror$2delement_CodeMirrorElement.prototype, "wrapper", void 0);
module$contents$google3$third_party$java_src$gerritcodereview$plugins$codemirror_editor$web$element$codemirror$2delement_CodeMirrorElement = module$exports$google3$third_party$javascript$tslib$tslib$__decorate([(tagName => JSCompiler_temp => {
  "function" === typeof JSCompiler_temp ? customElements.define(tagName, JSCompiler_temp) : JSCompiler_temp = module$contents$google3$third_party$javascript$lit$packages$reactive$2delement$src$decorators$custom$2delement_standardCustomElement(tagName, JSCompiler_temp);
  return JSCompiler_temp;
})("codemirror-element")], module$contents$google3$third_party$java_src$gerritcodereview$plugins$codemirror_editor$web$element$codemirror$2delement_CodeMirrorElement);
//[third_party/javascript/codemirror4/lib/codemirror.js]
/*

 Copyright (C) 2017 by Marijn Haverbeke <marijnh@gmail.com> and others

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
*/
var global$jscomp$inline_518 = this;
function factory$jscomp$inline_519() {
  function TextareaInput(cm) {
    this.cm = cm;
    this.prevInput = "";
    this.pollingFast = !1;
    this.polling = new Delayed();
    this.hasSelection = !1;
    this.composing = null;
  }
  function ContentEditableInput(cm) {
    this.cm = cm;
    this.lastAnchorNode = this.lastAnchorOffset = this.lastFocusNode = this.lastFocusOffset = null;
    this.polling = new Delayed();
    this.composing = null;
    this.gracePeriod = !1;
    this.readDOMTimeout = null;
  }
  function PastClick(time, pos, button) {
    this.time = time;
    this.pos = pos;
    this.button = button;
  }
  function Doc(text, mode, firstLine$jscomp$1_start, lineSep, direction) {
    if (!(this instanceof Doc)) {
      return new Doc(text, mode, firstLine$jscomp$1_start, lineSep, direction);
    }
    null == firstLine$jscomp$1_start && (firstLine$jscomp$1_start = 0);
    BranchChunk.call(this, [new LeafChunk([new Line("", null)])]);
    this.first = firstLine$jscomp$1_start;
    this.scrollTop = this.scrollLeft = 0;
    this.cantEdit = !1;
    this.cleanGeneration = 1;
    this.modeFrontier = this.highlightFrontier = firstLine$jscomp$1_start;
    firstLine$jscomp$1_start = Pos(firstLine$jscomp$1_start, 0);
    this.sel = simpleSelection(firstLine$jscomp$1_start);
    this.history = new History(null);
    this.id = ++nextDocId;
    this.modeOption = mode;
    this.lineSep = lineSep;
    this.direction = "rtl" == direction ? "rtl" : "ltr";
    this.extend = !1;
    "string" == typeof text && (text = this.splitLines(text));
    updateDoc(this, {from:firstLine$jscomp$1_start, to:firstLine$jscomp$1_start, text});
    setSelection(this, simpleSelection(firstLine$jscomp$1_start), sel_dontScroll);
  }
  function SharedTextMarker(markers, i$jscomp$232_primary) {
    this.markers = markers;
    this.primary = i$jscomp$232_primary;
    for (i$jscomp$232_primary = 0; i$jscomp$232_primary < markers.length; ++i$jscomp$232_primary) {
      markers[i$jscomp$232_primary].parent = this;
    }
  }
  function TextMarker(doc, type) {
    this.lines = [];
    this.type = type;
    this.doc = doc;
    this.id = ++nextMarkerId;
  }
  function LineWidget(doc, node, options) {
    if (options) {
      for (var opt in options) {
        options.hasOwnProperty(opt) && (this[opt] = options[opt]);
      }
    }
    this.doc = doc;
    this.node = node;
  }
  function Range(anchor, head) {
    this.anchor = anchor;
    this.head = head;
  }
  function Selection(ranges, primIndex) {
    this.ranges = ranges;
    this.primIndex = primIndex;
  }
  function DisplayUpdate(cm, viewport, force) {
    var display = cm.display;
    this.viewport = viewport;
    this.visible = visibleLines(display, cm.doc, viewport);
    this.editorIsHidden = !display.wrapper.offsetWidth;
    this.wrapperHeight = display.wrapper.clientHeight;
    this.wrapperWidth = display.wrapper.clientWidth;
    this.oldDisplayWidth = displayWidth(cm);
    this.force = force;
    this.dims = getDimensions(cm);
    this.events = [];
  }
  function NullScrollbars() {
  }
  function NativeScrollbars(place, scroll, cm) {
    this.cm = cm;
    var vert = this.vert = elt$jscomp$0("div", [elt$jscomp$0("div", null, null, "min-width: 1px")], "CodeMirror-vscrollbar"), horiz = this.horiz = elt$jscomp$0("div", [elt$jscomp$0("div", null, null, "height: 100%; min-height: 1px")], "CodeMirror-hscrollbar");
    vert.tabIndex = horiz.tabIndex = -1;
    place(vert);
    place(horiz);
    on(vert, "scroll", function() {
      vert.clientHeight && scroll(vert.scrollTop, "vertical");
    });
    on(horiz, "scroll", function() {
      horiz.clientWidth && scroll(horiz.scrollLeft, "horizontal");
    });
    this.checkedZeroWidth = !1;
    ie && 8 > ie_version && (this.horiz.style.minHeight = this.vert.style.minWidth = "18px");
  }
  function Line(text, markedSpans, estimateHeight) {
    this.text = text;
    attachMarkedSpans(this, markedSpans);
    this.height = estimateHeight ? estimateHeight(this) : 1;
  }
  function Token(stream, type, state) {
    this.start = stream.start;
    this.end = stream.pos;
    this.string = stream.current();
    this.type = type || null;
    this.state = state;
  }
  function Context(doc, state, line, lookAhead) {
    this.state = state;
    this.doc = doc;
    this.line = line;
    this.maxLookAhead = lookAhead || 0;
    this.baseTokens = null;
    this.baseTokenPos = 1;
  }
  function SavedContext(state, lookAhead) {
    this.state = state;
    this.lookAhead = lookAhead;
  }
  function StringStream(string, tabSize, lineOracle) {
    this.pos = this.start = 0;
    this.string = string;
    this.tabSize = tabSize || 8;
    this.lineStart = this.lastColumnPos = this.lastColumnValue = 0;
    this.lineOracle = lineOracle;
  }
  function on(emitter_map, type, f) {
    emitter_map.addEventListener ? emitter_map.addEventListener(type, f, !1) : emitter_map.attachEvent ? emitter_map.attachEvent("on" + type, f) : (emitter_map = emitter_map._handlers || (emitter_map._handlers = {}), emitter_map[type] = (emitter_map[type] || noHandlers).concat(f));
  }
  function Delayed() {
    this.f = this.id = null;
    this.time = 0;
    this.handler = bind(this.onTimeout, this);
  }
  function selectInput(node) {
    node.select();
  }
  function rmClass(node, cls$jscomp$2_match) {
    var current = node.className;
    if (cls$jscomp$2_match = classTest(cls$jscomp$2_match).exec(current)) {
      var after = current.slice(cls$jscomp$2_match.index + cls$jscomp$2_match[0].length);
      node.className = current.slice(0, cls$jscomp$2_match.index) + (after ? cls$jscomp$2_match[1] + after : "");
    }
  }
  function classTest(cls) {
    return new RegExp("(^|\\s)" + cls + "(?:$|\\s)\\s*");
  }
  function removeChildren(e) {
    for (var count = e.childNodes.length; 0 < count; --count) {
      e.removeChild(e.firstChild);
    }
    return e;
  }
  function removeChildrenAndAdd(parent, e) {
    return removeChildren(parent).appendChild(e);
  }
  function elt$jscomp$0(e$jscomp$44_tag, content, className$jscomp$2_i, style) {
    e$jscomp$44_tag = document.createElement(e$jscomp$44_tag);
    className$jscomp$2_i && (e$jscomp$44_tag.className = className$jscomp$2_i);
    style && (e$jscomp$44_tag.style.cssText = style);
    if ("string" == typeof content) {
      e$jscomp$44_tag.appendChild(document.createTextNode(content));
    } else if (content) {
      for (className$jscomp$2_i = 0; className$jscomp$2_i < content.length; ++className$jscomp$2_i) {
        e$jscomp$44_tag.appendChild(content[className$jscomp$2_i]);
      }
    }
    return e$jscomp$44_tag;
  }
  function eltP(e$jscomp$45_tag, content, className, style) {
    e$jscomp$45_tag = elt$jscomp$0(e$jscomp$45_tag, content, className, style);
    e$jscomp$45_tag.setAttribute("role", "presentation");
    return e$jscomp$45_tag;
  }
  function contains(parent, child) {
    3 == child.nodeType && (child = child.parentNode);
    if (parent.contains) {
      return parent.contains(child);
    }
    do {
      if (11 == child.nodeType && (child = child.host), child == parent) {
        return !0;
      }
    } while (child = child.parentNode);
  }
  function activeElt() {
    try {
      var activeElement = document.activeElement;
    } catch (e) {
      activeElement = document.body || null;
    }
    for (; activeElement && activeElement.shadowRoot && activeElement.shadowRoot.activeElement;) {
      activeElement = activeElement.shadowRoot.activeElement;
    }
    return activeElement;
  }
  function addClass(node, cls) {
    var current = node.className;
    classTest(cls).test(current) || (node.className += (current ? " " : "") + cls);
  }
  function joinClasses(a, b) {
    a = a.split(" ");
    for (var i = 0; i < a.length; i++) {
      a[i] && !classTest(a[i]).test(b) && (b += " " + a[i]);
    }
    return b;
  }
  function bind(f) {
    var args = Array.prototype.slice.call(arguments, 1);
    return function() {
      return f.apply(null, args);
    };
  }
  function copyObj(obj, target, overwrite) {
    target || (target = {});
    for (var prop in obj) {
      !obj.hasOwnProperty(prop) || !1 === overwrite && target.hasOwnProperty(prop) || (target[prop] = obj[prop]);
    }
    return target;
  }
  function countColumn(string, end, tabSize, i, n) {
    null == end && (end = string.search(/[^\s\u00a0]/), -1 == end && (end = string.length));
    i = i || 0;
    for (n = n || 0;;) {
      var nextTab = string.indexOf("\t", i);
      if (0 > nextTab || nextTab >= end) {
        return n + (end - i);
      }
      n += nextTab - i;
      n += tabSize - n % tabSize;
      i = nextTab + 1;
    }
  }
  function indexOf(array, elt) {
    for (var i = 0; i < array.length; ++i) {
      if (array[i] == elt) {
        return i;
      }
    }
    return -1;
  }
  function findColumn(string, goal, tabSize) {
    for (var pos = 0, col = 0;;) {
      var nextTab = string.indexOf("\t", pos);
      -1 == nextTab && (nextTab = string.length);
      var skipped = nextTab - pos;
      if (nextTab == string.length || col + skipped >= goal) {
        return pos + Math.min(skipped, goal - col);
      }
      col += nextTab - pos;
      col += tabSize - col % tabSize;
      pos = nextTab + 1;
      if (col >= goal) {
        return pos;
      }
    }
  }
  function spaceStr(n) {
    for (; spaceStrs.length <= n;) {
      spaceStrs.push(lst(spaceStrs) + " ");
    }
    return spaceStrs[n];
  }
  function lst(arr) {
    return arr[arr.length - 1];
  }
  function map$jscomp$0(array, f) {
    for (var out = [], i = 0; i < array.length; i++) {
      out[i] = f(array[i], i);
    }
    return out;
  }
  function insertSorted(array, value, score) {
    for (var pos = 0, priority = score(value); pos < array.length && score(array[pos]) <= priority;) {
      pos++;
    }
    array.splice(pos, 0, value);
  }
  function nothing() {
  }
  function createObj(base, props) {
    Object.create ? base = Object.create(base) : (nothing.prototype = base, base = new nothing());
    props && copyObj(props, base);
    return base;
  }
  function isWordCharBasic(ch) {
    return /\w/.test(ch) || "\u0080" < ch && (ch.toUpperCase() != ch.toLowerCase() || nonASCIISingleCaseWordChar.test(ch));
  }
  function isWordChar(ch, helper) {
    return helper ? -1 < helper.source.indexOf("\\w") && isWordCharBasic(ch) ? !0 : helper.test(ch) : isWordCharBasic(ch);
  }
  function isEmpty(obj) {
    for (var n in obj) {
      if (obj.hasOwnProperty(n) && obj[n]) {
        return !1;
      }
    }
    return !0;
  }
  function isExtendingChar(ch) {
    return 768 <= ch.charCodeAt(0) && extendingChars.test(ch);
  }
  function skipExtendingChars(str, pos, dir) {
    for (; (0 > dir ? 0 < pos : pos < str.length) && isExtendingChar(str.charAt(pos));) {
      pos += dir;
    }
    return pos;
  }
  function findFirst(pred, from, to) {
    for (var dir = from > to ? -1 : 1;;) {
      if (from == to) {
        return from;
      }
      var mid_midF = (from + to) / 2;
      mid_midF = 0 > dir ? Math.ceil(mid_midF) : Math.floor(mid_midF);
      if (mid_midF == from) {
        return pred(mid_midF) ? from : to;
      }
      pred(mid_midF) ? to = mid_midF : from = mid_midF + dir;
    }
  }
  function iterateBidiSections(order, from, to, f) {
    if (!order) {
      return f(from, to, "ltr", 0);
    }
    for (var found = !1, i = 0; i < order.length; ++i) {
      var part = order[i];
      if (part.from < to && part.to > from || from == to && part.to == from) {
        f(Math.max(part.from, from), Math.min(part.to, to), 1 == part.level ? "rtl" : "ltr", i), found = !0;
      }
    }
    found || f(from, to, "ltr");
  }
  function getBidiPartAt(order, ch, sticky) {
    var found;
    bidiOther = null;
    for (var i = 0; i < order.length; ++i) {
      var cur = order[i];
      if (cur.from < ch && cur.to > ch) {
        return i;
      }
      cur.to == ch && (cur.from != cur.to && "before" == sticky ? found = i : bidiOther = i);
      cur.from == ch && (cur.from != cur.to && "before" != sticky ? found = i : bidiOther = i);
    }
    return null != found ? found : bidiOther;
  }
  function getOrder(line, direction) {
    var order = line.order;
    null == order && (order = line.order = bidiOrdering(line.text, direction));
    return order;
  }
  function off(emitter$jscomp$2_map, type, f$jscomp$50_index) {
    if (emitter$jscomp$2_map.removeEventListener) {
      emitter$jscomp$2_map.removeEventListener(type, f$jscomp$50_index, !1);
    } else if (emitter$jscomp$2_map.detachEvent) {
      emitter$jscomp$2_map.detachEvent("on" + type, f$jscomp$50_index);
    } else {
      var arr = (emitter$jscomp$2_map = emitter$jscomp$2_map._handlers) && emitter$jscomp$2_map[type];
      arr && (f$jscomp$50_index = indexOf(arr, f$jscomp$50_index), -1 < f$jscomp$50_index && (emitter$jscomp$2_map[type] = arr.slice(0, f$jscomp$50_index).concat(arr.slice(f$jscomp$50_index + 1))));
    }
  }
  function signal(emitter, type) {
    var handlers = emitter._handlers && emitter._handlers[type] || noHandlers;
    if (handlers.length) {
      for (var args = Array.prototype.slice.call(arguments, 2), i = 0; i < handlers.length; ++i) {
        handlers[i].apply(null, args);
      }
    }
  }
  function signalDOMEvent(cm, e, override) {
    "string" == typeof e && (e = {type:e, preventDefault:function() {
      this.defaultPrevented = !0;
    }});
    signal(cm, override || e.type, cm, e);
    return e_defaultPrevented(e) || e.codemirrorIgnore;
  }
  function signalCursorActivity(cm$jscomp$3_set) {
    var arr = cm$jscomp$3_set._handlers && cm$jscomp$3_set._handlers.cursorActivity;
    if (arr) {
      cm$jscomp$3_set = cm$jscomp$3_set.curOp.cursorActivityHandlers || (cm$jscomp$3_set.curOp.cursorActivityHandlers = []);
      for (var i = 0; i < arr.length; ++i) {
        -1 == indexOf(cm$jscomp$3_set, arr[i]) && cm$jscomp$3_set.push(arr[i]);
      }
    }
  }
  function hasHandler(emitter, type) {
    return 0 < (emitter._handlers && emitter._handlers[type] || noHandlers).length;
  }
  function eventMixin(ctor) {
    ctor.prototype.on = function(type, f) {
      on(this, type, f);
    };
    ctor.prototype.off = function(type, f) {
      off(this, type, f);
    };
  }
  function e_preventDefault(e) {
    e.preventDefault ? e.preventDefault() : e.returnValue = !1;
  }
  function e_stopPropagation(e) {
    e.stopPropagation ? e.stopPropagation() : e.cancelBubble = !0;
  }
  function e_defaultPrevented(e) {
    return null != e.defaultPrevented ? e.defaultPrevented : 0 == e.returnValue;
  }
  function e_stop(e) {
    e_preventDefault(e);
    e_stopPropagation(e);
  }
  function e_button(e) {
    var b = e.which;
    null == b && (e.button & 1 ? b = 1 : e.button & 2 ? b = 3 : e.button & 4 && (b = 2));
    mac && e.ctrlKey && 1 == b && (b = 3);
    return b;
  }
  function defineMode(name, mode) {
    2 < arguments.length && (mode.dependencies = Array.prototype.slice.call(arguments, 2));
    modes[name] = mode;
  }
  function resolveMode(spec) {
    if ("string" == typeof spec && mimeModes.hasOwnProperty(spec)) {
      spec = mimeModes[spec];
    } else if (spec && "string" == typeof spec.name && mimeModes.hasOwnProperty(spec.name)) {
      var found = mimeModes[spec.name];
      "string" == typeof found && (found = {name:found});
      spec = createObj(found, spec);
      spec.name = found.name;
    } else {
      if ("string" == typeof spec && /^[\w\-]+\/[\w\-]+\+xml$/.test(spec)) {
        return resolveMode("application/xml");
      }
      if ("string" == typeof spec && /^[\w\-]+\/[\w\-]+\+json$/.test(spec)) {
        return resolveMode("application/json");
      }
    }
    return "string" == typeof spec ? {name:spec} : spec || {name:"null"};
  }
  function getMode(modeObj_options, spec) {
    spec = resolveMode(spec);
    var exts_mfactory = modes[spec.name];
    if (!exts_mfactory) {
      return getMode(modeObj_options, "text/plain");
    }
    modeObj_options = exts_mfactory(modeObj_options, spec);
    if (modeExtensions.hasOwnProperty(spec.name)) {
      exts_mfactory = modeExtensions[spec.name];
      for (var prop in exts_mfactory) {
        exts_mfactory.hasOwnProperty(prop) && (modeObj_options.hasOwnProperty(prop) && (modeObj_options["_" + prop] = modeObj_options[prop]), modeObj_options[prop] = exts_mfactory[prop]);
      }
    }
    modeObj_options.name = spec.name;
    spec.helperType && (modeObj_options.helperType = spec.helperType);
    if (spec.modeProps) {
      for (var prop$1 in spec.modeProps) {
        modeObj_options[prop$1] = spec.modeProps[prop$1];
      }
    }
    return modeObj_options;
  }
  function extendMode(exts$jscomp$1_mode, properties) {
    exts$jscomp$1_mode = modeExtensions.hasOwnProperty(exts$jscomp$1_mode) ? modeExtensions[exts$jscomp$1_mode] : modeExtensions[exts$jscomp$1_mode] = {};
    copyObj(properties, exts$jscomp$1_mode);
  }
  function copyState(mode, state) {
    if (!0 === state) {
      return state;
    }
    if (mode.copyState) {
      return mode.copyState(state);
    }
    mode = {};
    for (var n in state) {
      var val = state[n];
      val instanceof Array && (val = val.concat([]));
      mode[n] = val;
    }
    return mode;
  }
  function innerMode(mode, state) {
    for (var info; mode.innerMode;) {
      info = mode.innerMode(state);
      if (!info || info.mode == mode) {
        break;
      }
      state = info.state;
      mode = info.mode;
    }
    return info || {mode, state};
  }
  function startState(mode, a1, a2) {
    return mode.startState ? mode.startState(a1, a2) : !0;
  }
  function getLine(chunk$jscomp$7_doc, n) {
    n -= chunk$jscomp$7_doc.first;
    if (0 > n || n >= chunk$jscomp$7_doc.size) {
      throw Error("There is no line " + (n + chunk$jscomp$7_doc.first) + " in the document.");
    }
    for (; !chunk$jscomp$7_doc.lines;) {
      for (var i = 0;; ++i) {
        var child = chunk$jscomp$7_doc.children[i], sz = child.chunkSize();
        if (n < sz) {
          chunk$jscomp$7_doc = child;
          break;
        }
        n -= sz;
      }
    }
    return chunk$jscomp$7_doc.lines[n];
  }
  function getBetween(doc, start, end) {
    var out = [], n = start.line;
    doc.iter(start.line, end.line + 1, function(line$jscomp$21_text) {
      line$jscomp$21_text = line$jscomp$21_text.text;
      n == end.line && (line$jscomp$21_text = line$jscomp$21_text.slice(0, end.ch));
      n == start.line && (line$jscomp$21_text = line$jscomp$21_text.slice(start.ch));
      out.push(line$jscomp$21_text);
      ++n;
    });
    return out;
  }
  function getLines(doc, from, to) {
    var out = [];
    doc.iter(from, to, function(line) {
      out.push(line.text);
    });
    return out;
  }
  function updateLineHeight(line$jscomp$23_n, diff_height) {
    if (diff_height -= line$jscomp$23_n.height) {
      for (; line$jscomp$23_n; line$jscomp$23_n = line$jscomp$23_n.parent) {
        line$jscomp$23_n.height += diff_height;
      }
    }
  }
  function lineNo(line) {
    if (null == line.parent) {
      return null;
    }
    var cur = line.parent;
    line = indexOf(cur.lines, line);
    for (var chunk = cur.parent; chunk; cur = chunk, chunk = chunk.parent) {
      for (var i = 0; chunk.children[i] != cur; ++i) {
        line += chunk.children[i].chunkSize();
      }
    }
    return line + cur.first;
  }
  function lineAtHeight(chunk, h) {
    var n = chunk.first;
    a: do {
      for (var i$1$jscomp$2_i = 0; i$1$jscomp$2_i < chunk.children.length; ++i$1$jscomp$2_i) {
        var child = chunk.children[i$1$jscomp$2_i], ch = child.height;
        if (h < ch) {
          chunk = child;
          continue a;
        }
        h -= ch;
        n += child.chunkSize();
      }
      return n;
    } while (!chunk.lines);
    for (i$1$jscomp$2_i = 0; i$1$jscomp$2_i < chunk.lines.length; ++i$1$jscomp$2_i) {
      child = chunk.lines[i$1$jscomp$2_i].height;
      if (h < child) {
        break;
      }
      h -= child;
    }
    return n + i$1$jscomp$2_i;
  }
  function isLine(doc, l) {
    return l >= doc.first && l < doc.first + doc.size;
  }
  function lineNumberFor(options, i) {
    return String(options.lineNumberFormatter(i + options.firstLineNumber));
  }
  function Pos(line, ch, sticky) {
    void 0 === sticky && (sticky = null);
    if (!(this instanceof Pos)) {
      return new Pos(line, ch, sticky);
    }
    this.line = line;
    this.ch = ch;
    this.sticky = sticky;
  }
  function cmp(a, b) {
    return a.line - b.line || a.ch - b.ch;
  }
  function equalCursorPos(a, b) {
    return a.sticky == b.sticky && 0 == cmp(a, b);
  }
  function copyPos(x) {
    return Pos(x.line, x.ch);
  }
  function maxPos(a, b) {
    return 0 > cmp(a, b) ? b : a;
  }
  function minPos(a, b) {
    return 0 > cmp(a, b) ? a : b;
  }
  function clipPos(doc$jscomp$22_linelen, JSCompiler_temp$jscomp$34_pos) {
    if (JSCompiler_temp$jscomp$34_pos.line < doc$jscomp$22_linelen.first) {
      return Pos(doc$jscomp$22_linelen.first, 0);
    }
    var ch = doc$jscomp$22_linelen.first + doc$jscomp$22_linelen.size - 1;
    JSCompiler_temp$jscomp$34_pos.line > ch ? JSCompiler_temp$jscomp$34_pos = Pos(ch, getLine(doc$jscomp$22_linelen, ch).text.length) : (doc$jscomp$22_linelen = getLine(doc$jscomp$22_linelen, JSCompiler_temp$jscomp$34_pos.line).text.length, ch = JSCompiler_temp$jscomp$34_pos.ch, JSCompiler_temp$jscomp$34_pos = null == ch || ch > doc$jscomp$22_linelen ? Pos(JSCompiler_temp$jscomp$34_pos.line, doc$jscomp$22_linelen) : 0 > ch ? Pos(JSCompiler_temp$jscomp$34_pos.line, 0) : JSCompiler_temp$jscomp$34_pos);
    return JSCompiler_temp$jscomp$34_pos;
  }
  function clipPosArray(doc, array) {
    for (var out = [], i = 0; i < array.length; i++) {
      out[i] = clipPos(doc, array[i]);
    }
    return out;
  }
  function highlightLine(cm, line, context, forceToEnd_o) {
    function loop(o) {
      context.baseTokens = st;
      var overlay = cm.state.overlays[o], i = 1, at = 0;
      context.state = !0;
      runMode(cm, line.text, overlay.mode, context, function(cur$jscomp$5_end, style) {
        for (var start = i; at < cur$jscomp$5_end;) {
          var i_end = st[i];
          i_end > cur$jscomp$5_end && st.splice(i, 1, cur$jscomp$5_end, st[i + 1], i_end);
          i += 2;
          at = Math.min(cur$jscomp$5_end, i_end);
        }
        if (style) {
          if (overlay.opaque) {
            st.splice(start, i - start, cur$jscomp$5_end, "overlay " + style), i = start + 2;
          } else {
            for (; start < i; start += 2) {
              cur$jscomp$5_end = st[start + 1], st[start + 1] = (cur$jscomp$5_end ? cur$jscomp$5_end + " " : "") + "overlay " + style;
            }
          }
        }
      }, lineClasses);
      context.state = state;
      context.baseTokens = null;
      context.baseTokenPos = 1;
    }
    var st = [cm.state.modeGen], lineClasses = {};
    runMode(cm, line.text, cm.doc.mode, context, function(end, style) {
      return st.push(end, style);
    }, lineClasses, forceToEnd_o);
    var state = context.state;
    for (forceToEnd_o = 0; forceToEnd_o < cm.state.overlays.length; ++forceToEnd_o) {
      loop(forceToEnd_o);
    }
    return {styles:st, classes:lineClasses.bgClass || lineClasses.textClass ? lineClasses : null};
  }
  function getLineStyles(cm, line, updateFrontier) {
    if (!line.styles || line.styles[0] != cm.state.modeGen) {
      var context = getContextBefore(cm, lineNo(line)), resetState = line.text.length > cm.options.maxHighlightLength && copyState(cm.doc.mode, context.state), result = highlightLine(cm, line, context);
      resetState && (context.state = resetState);
      line.stateAfter = context.save(!resetState);
      line.styles = result.styles;
      result.classes ? line.styleClasses = result.classes : line.styleClasses && (line.styleClasses = null);
      updateFrontier === cm.doc.highlightFrontier && (cm.doc.modeFrontier = Math.max(cm.doc.modeFrontier, ++cm.doc.highlightFrontier));
    }
    return line.styles;
  }
  function getContextBefore(cm, n, precise) {
    var doc = cm.doc, display = cm.display;
    if (!doc.mode.startState) {
      return new Context(doc, !0, n);
    }
    var start = findStartLine(cm, n, precise), saved = start > doc.first && getLine(doc, start - 1).stateAfter, context = saved ? Context.fromSaved(doc, saved, start) : new Context(doc, startState(doc.mode), start);
    doc.iter(start, n, function(line) {
      processLine(cm, line.text, context);
      var pos = context.line;
      line.stateAfter = pos == n - 1 || 0 == pos % 5 || pos >= display.viewFrom && pos < display.viewTo ? context.save() : null;
      context.nextLine();
    });
    precise && (doc.modeFrontier = context.line);
    return context;
  }
  function processLine(cm$jscomp$7_stream, text, context, startAt) {
    var mode = cm$jscomp$7_stream.doc.mode;
    cm$jscomp$7_stream = new StringStream(text, cm$jscomp$7_stream.options.tabSize, context);
    cm$jscomp$7_stream.start = cm$jscomp$7_stream.pos = startAt || 0;
    for ("" == text && callBlankLine(mode, context.state); !cm$jscomp$7_stream.eol();) {
      readToken(mode, cm$jscomp$7_stream, context.state), cm$jscomp$7_stream.start = cm$jscomp$7_stream.pos;
    }
  }
  function callBlankLine(inner$jscomp$2_mode, state) {
    if (inner$jscomp$2_mode.blankLine) {
      return inner$jscomp$2_mode.blankLine(state);
    }
    if (inner$jscomp$2_mode.innerMode && (inner$jscomp$2_mode = innerMode(inner$jscomp$2_mode, state), inner$jscomp$2_mode.mode.blankLine)) {
      return inner$jscomp$2_mode.mode.blankLine(inner$jscomp$2_mode.state);
    }
  }
  function readToken(mode, stream, state, inner) {
    for (var i = 0; 10 > i; i++) {
      inner && (inner[0] = innerMode(mode, state).mode);
      var style = mode.token(stream, state);
      if (stream.pos > stream.start) {
        return style;
      }
    }
    throw Error("Mode " + mode.name + " failed to advance stream.");
  }
  function takeToken(cm$jscomp$8_stream, pos, context$jscomp$9_precise, asArray) {
    var doc = cm$jscomp$8_stream.doc, mode = doc.mode;
    pos = clipPos(doc, pos);
    var line = getLine(doc, pos.line);
    context$jscomp$9_precise = getContextBefore(cm$jscomp$8_stream, pos.line, context$jscomp$9_precise);
    cm$jscomp$8_stream = new StringStream(line.text, cm$jscomp$8_stream.options.tabSize, context$jscomp$9_precise);
    var tokens;
    for (asArray && (tokens = []); (asArray || cm$jscomp$8_stream.pos < pos.ch) && !cm$jscomp$8_stream.eol();) {
      cm$jscomp$8_stream.start = cm$jscomp$8_stream.pos;
      var style = readToken(mode, cm$jscomp$8_stream, context$jscomp$9_precise.state);
      asArray && tokens.push(new Token(cm$jscomp$8_stream, style, copyState(doc.mode, context$jscomp$9_precise.state)));
    }
    return asArray ? tokens : new Token(cm$jscomp$8_stream, style, context$jscomp$9_precise.state);
  }
  function extractLineClasses(type, output) {
    if (type) {
      for (;;) {
        var lineClass = type.match(/(?:^|\s+)line-(background-)?(\S+)/);
        if (!lineClass) {
          break;
        }
        type = type.slice(0, lineClass.index) + type.slice(lineClass.index + lineClass[0].length);
        var prop = lineClass[1] ? "bgClass" : "textClass";
        null == output[prop] ? output[prop] = lineClass[2] : (new RegExp("(?:^|\\s)" + lineClass[2] + "(?:$|\\s)")).test(output[prop]) || (output[prop] += " " + lineClass[2]);
      }
    }
    return type;
  }
  function runMode(cm$jscomp$9_pos, text, mode, context, f, lineClasses, forceToEnd) {
    var flattenSpans = mode.flattenSpans;
    null == flattenSpans && (flattenSpans = cm$jscomp$9_pos.options.flattenSpans);
    var curStart = 0, curStyle = null, stream = new StringStream(text, cm$jscomp$9_pos.options.tabSize, context), inner = cm$jscomp$9_pos.options.addModeClass && [null];
    for ("" == text && extractLineClasses(callBlankLine(mode, context.state), lineClasses); !stream.eol();) {
      if (stream.pos > cm$jscomp$9_pos.options.maxHighlightLength) {
        flattenSpans = !1;
        forceToEnd && processLine(cm$jscomp$9_pos, text, context, stream.pos);
        stream.pos = text.length;
        var style = null;
      } else {
        style = extractLineClasses(readToken(mode, stream, context.state, inner), lineClasses);
      }
      if (inner) {
        var mName = inner[0].name;
        mName && (style = "m-" + (style ? mName + " " + style : mName));
      }
      if (!flattenSpans || curStyle != style) {
        for (; curStart < stream.start;) {
          curStart = Math.min(stream.start, curStart + 5E3), f(curStart, curStyle);
        }
        curStyle = style;
      }
      stream.start = stream.pos;
    }
    for (; curStart < stream.pos;) {
      cm$jscomp$9_pos = Math.min(stream.pos, curStart + 5E3), f(cm$jscomp$9_pos, curStyle), curStart = cm$jscomp$9_pos;
    }
  }
  function findStartLine(cm, n, precise) {
    for (var minindent, minline, doc = cm.doc, lim = precise ? -1 : n - (cm.doc.mode.innerMode ? 1E3 : 100); n > lim; --n) {
      if (n <= doc.first) {
        return doc.first;
      }
      var indented_line = getLine(doc, n - 1), after = indented_line.stateAfter;
      if (after && (!precise || n + (after instanceof SavedContext ? after.lookAhead : 0) <= doc.modeFrontier)) {
        return n;
      }
      indented_line = countColumn(indented_line.text, null, cm.options.tabSize);
      if (null == minline || minindent > indented_line) {
        minline = n - 1, minindent = indented_line;
      }
    }
    return minline;
  }
  function retreatFrontier(doc, n) {
    doc.modeFrontier = Math.min(doc.modeFrontier, n);
    if (!(doc.highlightFrontier < n - 10)) {
      for (var start = doc.first, line = n - 1; line > start; line--) {
        var saved = getLine(doc, line).stateAfter;
        if (saved && (!(saved instanceof SavedContext) || line + saved.lookAhead < n)) {
          start = line + 1;
          break;
        }
      }
      doc.highlightFrontier = Math.min(doc.highlightFrontier, start);
    }
  }
  function MarkedSpan(marker, from, to) {
    this.marker = marker;
    this.from = from;
    this.to = to;
  }
  function getMarkedSpanFor(spans, marker) {
    if (spans) {
      for (var i = 0; i < spans.length; ++i) {
        var span = spans[i];
        if (span.marker == marker) {
          return span;
        }
      }
    }
  }
  function stretchSpansOverChange(doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh, change) {
    if (change.full) {
      return null;
    }
    var first$jscomp$4_i$3 = isLine(doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh, change.from.line) && getLine(doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh, change.from.line).markedSpans, i$2 = isLine(doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh, change.to.line) && getLine(doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh, change.to.line).markedSpans;
    if (!first$jscomp$4_i$3 && !i$2) {
      return null;
    }
    doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh = change.from.ch;
    var endCh$jscomp$1_offset = change.to.ch, i$jscomp$144_isInsert = 0 == cmp(change.from, change.to), i$jscomp$inline_195_nw$jscomp$inline_185_span;
    if (first$jscomp$4_i$3) {
      for (var found$jscomp$5_i$jscomp$inline_186_span = 0; found$jscomp$5_i$jscomp$inline_186_span < first$jscomp$4_i$3.length; ++found$jscomp$5_i$jscomp$inline_186_span) {
        var marker$jscomp$inline_197_span = first$jscomp$4_i$3[found$jscomp$5_i$jscomp$inline_186_span], marker$jscomp$inline_188_startsBefore = marker$jscomp$inline_197_span.marker;
        if (null == marker$jscomp$inline_197_span.from || (marker$jscomp$inline_188_startsBefore.inclusiveLeft ? marker$jscomp$inline_197_span.from <= doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh : marker$jscomp$inline_197_span.from < doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh) || !(marker$jscomp$inline_197_span.from != doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh || "bookmark" != marker$jscomp$inline_188_startsBefore.type || i$jscomp$144_isInsert && marker$jscomp$inline_197_span.marker.insertLeft)) {
          var endsAfter = null == marker$jscomp$inline_197_span.to || (marker$jscomp$inline_188_startsBefore.inclusiveRight ? marker$jscomp$inline_197_span.to >= doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh : marker$jscomp$inline_197_span.to > doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh);
          (i$jscomp$inline_195_nw$jscomp$inline_185_span || (i$jscomp$inline_195_nw$jscomp$inline_185_span = [])).push(new MarkedSpan(marker$jscomp$inline_188_startsBefore, marker$jscomp$inline_197_span.from, endsAfter ? null : marker$jscomp$inline_197_span.to));
        }
      }
    }
    first$jscomp$4_i$3 = i$jscomp$inline_195_nw$jscomp$inline_185_span;
    var last$jscomp$1_nw;
    if (i$2) {
      for (i$jscomp$inline_195_nw$jscomp$inline_185_span = 0; i$jscomp$inline_195_nw$jscomp$inline_185_span < i$2.length; ++i$jscomp$inline_195_nw$jscomp$inline_185_span) {
        if (found$jscomp$5_i$jscomp$inline_186_span = i$2[i$jscomp$inline_195_nw$jscomp$inline_185_span], marker$jscomp$inline_197_span = found$jscomp$5_i$jscomp$inline_186_span.marker, null == found$jscomp$5_i$jscomp$inline_186_span.to || (marker$jscomp$inline_197_span.inclusiveRight ? found$jscomp$5_i$jscomp$inline_186_span.to >= endCh$jscomp$1_offset : found$jscomp$5_i$jscomp$inline_186_span.to > endCh$jscomp$1_offset) || found$jscomp$5_i$jscomp$inline_186_span.from == endCh$jscomp$1_offset && 
        "bookmark" == marker$jscomp$inline_197_span.type && (!i$jscomp$144_isInsert || found$jscomp$5_i$jscomp$inline_186_span.marker.insertLeft)) {
          marker$jscomp$inline_188_startsBefore = null == found$jscomp$5_i$jscomp$inline_186_span.from || (marker$jscomp$inline_197_span.inclusiveLeft ? found$jscomp$5_i$jscomp$inline_186_span.from <= endCh$jscomp$1_offset : found$jscomp$5_i$jscomp$inline_186_span.from < endCh$jscomp$1_offset), (last$jscomp$1_nw || (last$jscomp$1_nw = [])).push(new MarkedSpan(marker$jscomp$inline_197_span, marker$jscomp$inline_188_startsBefore ? null : found$jscomp$5_i$jscomp$inline_186_span.from - endCh$jscomp$1_offset, 
          null == found$jscomp$5_i$jscomp$inline_186_span.to ? null : found$jscomp$5_i$jscomp$inline_186_span.to - endCh$jscomp$1_offset));
        }
      }
    }
    i$2 = 1 == change.text.length;
    endCh$jscomp$1_offset = lst(change.text).length + (i$2 ? doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh : 0);
    if (first$jscomp$4_i$3) {
      for (i$jscomp$144_isInsert = 0; i$jscomp$144_isInsert < first$jscomp$4_i$3.length; ++i$jscomp$144_isInsert) {
        if (i$jscomp$inline_195_nw$jscomp$inline_185_span = first$jscomp$4_i$3[i$jscomp$144_isInsert], null == i$jscomp$inline_195_nw$jscomp$inline_185_span.to) {
          (found$jscomp$5_i$jscomp$inline_186_span = getMarkedSpanFor(last$jscomp$1_nw, i$jscomp$inline_195_nw$jscomp$inline_185_span.marker), found$jscomp$5_i$jscomp$inline_186_span) ? i$2 && (i$jscomp$inline_195_nw$jscomp$inline_185_span.to = null == found$jscomp$5_i$jscomp$inline_186_span.to ? null : found$jscomp$5_i$jscomp$inline_186_span.to + endCh$jscomp$1_offset) : i$jscomp$inline_195_nw$jscomp$inline_185_span.to = doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh;
        }
      }
    }
    if (last$jscomp$1_nw) {
      for (doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh = 0; doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh < last$jscomp$1_nw.length; ++doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh) {
        i$jscomp$144_isInsert = last$jscomp$1_nw[doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh], null != i$jscomp$144_isInsert.to && (i$jscomp$144_isInsert.to += endCh$jscomp$1_offset), null == i$jscomp$144_isInsert.from ? getMarkedSpanFor(first$jscomp$4_i$3, i$jscomp$144_isInsert.marker) || (i$jscomp$144_isInsert.from = endCh$jscomp$1_offset, i$2 && (first$jscomp$4_i$3 || (first$jscomp$4_i$3 = [])).push(i$jscomp$144_isInsert)) : (i$jscomp$144_isInsert.from += endCh$jscomp$1_offset, i$2 && (first$jscomp$4_i$3 || 
        (first$jscomp$4_i$3 = [])).push(i$jscomp$144_isInsert));
      }
    }
    first$jscomp$4_i$3 && (first$jscomp$4_i$3 = clearEmptySpans(first$jscomp$4_i$3));
    last$jscomp$1_nw && last$jscomp$1_nw != first$jscomp$4_i$3 && (last$jscomp$1_nw = clearEmptySpans(last$jscomp$1_nw));
    doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh = [first$jscomp$4_i$3];
    if (!i$2) {
      change = change.text.length - 2;
      var gapMarkers;
      if (0 < change && first$jscomp$4_i$3) {
        for (i$2 = 0; i$2 < first$jscomp$4_i$3.length; ++i$2) {
          null == first$jscomp$4_i$3[i$2].to && (gapMarkers || (gapMarkers = [])).push(new MarkedSpan(first$jscomp$4_i$3[i$2].marker, null, null));
        }
      }
      for (first$jscomp$4_i$3 = 0; first$jscomp$4_i$3 < change; ++first$jscomp$4_i$3) {
        doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh.push(gapMarkers);
      }
      doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh.push(last$jscomp$1_nw);
    }
    return doc$jscomp$30_i$1$jscomp$3_newMarkers_startCh;
  }
  function clearEmptySpans(spans) {
    for (var i = 0; i < spans.length; ++i) {
      var span = spans[i];
      null != span.from && span.from == span.to && !1 !== span.marker.clearWhenEmpty && spans.splice(i--, 1);
    }
    return spans.length ? spans : null;
  }
  function removeReadOnlyRanges(doc$jscomp$31_parts, from$jscomp$13_i, mk_to) {
    var markers = null;
    doc$jscomp$31_parts.iter(from$jscomp$13_i.line, mk_to.line + 1, function(line) {
      if (line.markedSpans) {
        for (var i = 0; i < line.markedSpans.length; ++i) {
          var mark = line.markedSpans[i].marker;
          !mark.readOnly || markers && -1 != indexOf(markers, mark) || (markers || (markers = [])).push(mark);
        }
      }
    });
    if (!markers) {
      return null;
    }
    doc$jscomp$31_parts = [{from:from$jscomp$13_i, to:mk_to}];
    for (from$jscomp$13_i = 0; from$jscomp$13_i < markers.length; ++from$jscomp$13_i) {
      mk_to = markers[from$jscomp$13_i];
      for (var m = mk_to.find(0), j = 0; j < doc$jscomp$31_parts.length; ++j) {
        var p = doc$jscomp$31_parts[j];
        if (!(0 > cmp(p.to, m.from) || 0 < cmp(p.from, m.to))) {
          var newParts = [j, 1], dfrom = cmp(p.from, m.from), dto = cmp(p.to, m.to);
          (0 > dfrom || !mk_to.inclusiveLeft && !dfrom) && newParts.push({from:p.from, to:m.from});
          (0 < dto || !mk_to.inclusiveRight && !dto) && newParts.push({from:m.to, to:p.to});
          doc$jscomp$31_parts.splice.apply(doc$jscomp$31_parts, newParts);
          j += newParts.length - 3;
        }
      }
    }
    return doc$jscomp$31_parts;
  }
  function detachMarkedSpans(line) {
    var spans = line.markedSpans;
    if (spans) {
      for (var i = 0; i < spans.length; ++i) {
        spans[i].marker.detachLine(line);
      }
      line.markedSpans = null;
    }
  }
  function attachMarkedSpans(line, spans) {
    if (spans) {
      for (var i = 0; i < spans.length; ++i) {
        spans[i].marker.attachLine(line);
      }
      line.markedSpans = spans;
    }
  }
  function compareCollapsedMarkers(a, b) {
    var aPos_lenDiff_toCmp = a.lines.length - b.lines.length;
    if (0 != aPos_lenDiff_toCmp) {
      return aPos_lenDiff_toCmp;
    }
    aPos_lenDiff_toCmp = a.find();
    var bPos = b.find(), fromCmp = cmp(aPos_lenDiff_toCmp.from, bPos.from) || (a.inclusiveLeft ? -1 : 0) - (b.inclusiveLeft ? -1 : 0);
    return fromCmp ? -fromCmp : (aPos_lenDiff_toCmp = cmp(aPos_lenDiff_toCmp.to, bPos.to) || (a.inclusiveRight ? 1 : 0) - (b.inclusiveRight ? 1 : 0)) ? aPos_lenDiff_toCmp : b.id - a.id;
  }
  function collapsedSpanAtSide(line, start) {
    line = sawCollapsedSpans && line.markedSpans;
    if (line) {
      for (var sp, i = 0; i < line.length; ++i) {
        if (sp = line[i], sp.marker.collapsed && null == (start ? sp.from : sp.to) && (!found || 0 > compareCollapsedMarkers(found, sp.marker))) {
          var found = sp.marker;
        }
      }
    }
    return found;
  }
  function conflictingCollapsedRange(doc$jscomp$32_line$jscomp$44_sps, i$jscomp$152_lineNo, from, to, marker) {
    doc$jscomp$32_line$jscomp$44_sps = getLine(doc$jscomp$32_line$jscomp$44_sps, i$jscomp$152_lineNo);
    if (doc$jscomp$32_line$jscomp$44_sps = sawCollapsedSpans && doc$jscomp$32_line$jscomp$44_sps.markedSpans) {
      for (i$jscomp$152_lineNo = 0; i$jscomp$152_lineNo < doc$jscomp$32_line$jscomp$44_sps.length; ++i$jscomp$152_lineNo) {
        var sp = doc$jscomp$32_line$jscomp$44_sps[i$jscomp$152_lineNo];
        if (sp.marker.collapsed) {
          var found = sp.marker.find(0), fromCmp = cmp(found.from, from) || (sp.marker.inclusiveLeft ? -1 : 0) - (marker.inclusiveLeft ? -1 : 0), toCmp = cmp(found.to, to) || (sp.marker.inclusiveRight ? 1 : 0) - (marker.inclusiveRight ? 1 : 0);
          if (!(0 <= fromCmp && 0 >= toCmp || 0 >= fromCmp && 0 <= toCmp) && (0 >= fromCmp && (sp.marker.inclusiveRight && marker.inclusiveLeft ? 0 <= cmp(found.to, from) : 0 < cmp(found.to, from)) || 0 <= fromCmp && (sp.marker.inclusiveRight && marker.inclusiveLeft ? 0 >= cmp(found.from, to) : 0 > cmp(found.from, to)))) {
            return !0;
          }
        }
      }
    }
  }
  function visualLine(line) {
    for (var merged; merged = collapsedSpanAtSide(line, !0);) {
      line = merged.find(-1, !0).line;
    }
    return line;
  }
  function visualLineNo(doc$jscomp$33_line, lineN) {
    doc$jscomp$33_line = getLine(doc$jscomp$33_line, lineN);
    var vis = visualLine(doc$jscomp$33_line);
    return doc$jscomp$33_line == vis ? lineN : lineNo(vis);
  }
  function visualLineEndNo(doc$jscomp$34_merged, lineN) {
    if (lineN > doc$jscomp$34_merged.lastLine()) {
      return lineN;
    }
    var line = getLine(doc$jscomp$34_merged, lineN);
    if (!lineIsHidden(doc$jscomp$34_merged, line)) {
      return lineN;
    }
    for (; doc$jscomp$34_merged = collapsedSpanAtSide(line, !1);) {
      line = doc$jscomp$34_merged.find(1, !0).line;
    }
    return lineNo(line) + 1;
  }
  function lineIsHidden(doc, line) {
    var sps = sawCollapsedSpans && line.markedSpans;
    if (sps) {
      for (var sp, i = 0; i < sps.length; ++i) {
        if (sp = sps[i], sp.marker.collapsed && (null == sp.from || !sp.marker.widgetNode && 0 == sp.from && sp.marker.inclusiveLeft && lineIsHiddenInner(doc, line, sp))) {
          return !0;
        }
      }
    }
  }
  function lineIsHiddenInner(doc, end$jscomp$21_line, span) {
    if (null == span.to) {
      return end$jscomp$21_line = span.marker.find(1, !0), lineIsHiddenInner(doc, end$jscomp$21_line.line, getMarkedSpanFor(end$jscomp$21_line.line.markedSpans, span.marker));
    }
    if (span.marker.inclusiveRight && span.to == end$jscomp$21_line.text.length) {
      return !0;
    }
    for (var sp, i = 0; i < end$jscomp$21_line.markedSpans.length; ++i) {
      if (sp = end$jscomp$21_line.markedSpans[i], sp.marker.collapsed && !sp.marker.widgetNode && sp.from == span.to && (null == sp.to || sp.to != span.from) && (sp.marker.inclusiveLeft || span.marker.inclusiveRight) && lineIsHiddenInner(doc, end$jscomp$21_line, sp)) {
        return !0;
      }
    }
  }
  function heightAtLine(lineObj_p) {
    lineObj_p = visualLine(lineObj_p);
    for (var h = 0, chunk = lineObj_p.parent, i$1$jscomp$4_i = 0; i$1$jscomp$4_i < chunk.lines.length; ++i$1$jscomp$4_i) {
      var cur$jscomp$6_line = chunk.lines[i$1$jscomp$4_i];
      if (cur$jscomp$6_line == lineObj_p) {
        break;
      } else {
        h += cur$jscomp$6_line.height;
      }
    }
    for (lineObj_p = chunk.parent; lineObj_p; chunk = lineObj_p, lineObj_p = chunk.parent) {
      for (i$1$jscomp$4_i = 0; i$1$jscomp$4_i < lineObj_p.children.length && (cur$jscomp$6_line = lineObj_p.children[i$1$jscomp$4_i], cur$jscomp$6_line != chunk); ++i$1$jscomp$4_i) {
        h += cur$jscomp$6_line.height;
      }
    }
    return h;
  }
  function lineLength(found$1$jscomp$1_line) {
    if (0 == found$1$jscomp$1_line.height) {
      return 0;
    }
    for (var len = found$1$jscomp$1_line.text.length, found$jscomp$9_merged, cur = found$1$jscomp$1_line; found$jscomp$9_merged = collapsedSpanAtSide(cur, !0);) {
      found$jscomp$9_merged = found$jscomp$9_merged.find(0, !0), cur = found$jscomp$9_merged.from.line, len += found$jscomp$9_merged.from.ch - found$jscomp$9_merged.to.ch;
    }
    for (cur = found$1$jscomp$1_line; found$jscomp$9_merged = collapsedSpanAtSide(cur, !1);) {
      found$1$jscomp$1_line = found$jscomp$9_merged.find(0, !0), len -= cur.text.length - found$1$jscomp$1_line.from.ch, cur = found$1$jscomp$1_line.to.line, len += cur.text.length - found$1$jscomp$1_line.to.ch;
    }
    return len;
  }
  function findMaxLine(cm$jscomp$11_doc) {
    var d = cm$jscomp$11_doc.display;
    cm$jscomp$11_doc = cm$jscomp$11_doc.doc;
    d.maxLine = getLine(cm$jscomp$11_doc, cm$jscomp$11_doc.first);
    d.maxLineLength = lineLength(d.maxLine);
    d.maxLineChanged = !0;
    cm$jscomp$11_doc.iter(function(line) {
      var len = lineLength(line);
      len > d.maxLineLength && (d.maxLineLength = len, d.maxLine = line);
    });
  }
  function interpretTokenStyle(style, cache$jscomp$1_options) {
    if (!style || /^\s*$/.test(style)) {
      return null;
    }
    cache$jscomp$1_options = cache$jscomp$1_options.addModeClass ? styleToClassCacheWithMode : styleToClassCache;
    return cache$jscomp$1_options[style] || (cache$jscomp$1_options[style] = style.replace(/\S+/g, "cm-$&"));
  }
  function buildLineContent(cm, lineView) {
    var builder_content = eltP("span", null, null, webkit ? "padding-right: .1px" : null);
    builder_content = {pre:eltP("pre", [builder_content], "CodeMirror-line"), content:builder_content, col:0, pos:0, cm, trailingSpace:!1, splitSpaces:cm.getOption("lineWrapping")};
    lineView.measure = {};
    for (var i = 0; i <= (lineView.rest ? lineView.rest.length : 0); i++) {
      var JSCompiler_temp_const$jscomp$29_line = i ? lineView.rest[i - 1] : lineView.line, JSCompiler_temp_const$jscomp$28_builder$jscomp$inline_206_order = void 0;
      builder_content.pos = 0;
      builder_content.addToken = buildToken;
      var JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure = cm.display.measure;
      if (null != badBidiRects) {
        JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure = badBidiRects;
      } else {
        var measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt = removeChildrenAndAdd(JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure, document.createTextNode("A\u062eA")), JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0 = range$jscomp$0(measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt, 0, 1).getBoundingClientRect();
        measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt = range$jscomp$0(measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt, 1, 2).getBoundingClientRect();
        removeChildren(JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure);
        JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure = JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0 && JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0.left != JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0.right ? badBidiRects = 3 > measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt.right - JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0.right : 
        !1;
      }
      JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure && (JSCompiler_temp_const$jscomp$28_builder$jscomp$inline_206_order = getOrder(JSCompiler_temp_const$jscomp$29_line, cm.doc.direction)) && (builder_content.addToken = buildTokenBadBidi(builder_content.addToken, JSCompiler_temp_const$jscomp$28_builder$jscomp$inline_206_order));
      builder_content.map = [];
      var allowFrontierUpdate_styles = lineView != cm.display.externalMeasured && lineNo(JSCompiler_temp_const$jscomp$29_line), spanEndStyle$jscomp$inline_219_test = measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt = JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0 = JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure = void 0, spanStyle = void 0, css = 
      void 0, style = void 0;
      JSCompiler_temp_const$jscomp$28_builder$jscomp$inline_206_order = builder_content;
      allowFrontierUpdate_styles = getLineStyles(cm, JSCompiler_temp_const$jscomp$29_line, allowFrontierUpdate_styles);
      var spans = JSCompiler_temp_const$jscomp$29_line.markedSpans, allText = JSCompiler_temp_const$jscomp$29_line.text, at = 0;
      if (spans) {
        for (var len = allText.length, pos = 0, i$jscomp$0 = 1, text = "", nextChange = 0;;) {
          if (nextChange == pos) {
            spanStyle = spanEndStyle$jscomp$inline_219_test = measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt = css = "";
            JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0 = JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure = null;
            nextChange = Infinity;
            for (var foundBookmarks$jscomp$inline_223_upto = [], end$jscomp$inline_232_endStyles$jscomp$inline_224_j$2 = void 0, j$1$jscomp$inline_229_j$jscomp$inline_225_tokenText = 0; j$1$jscomp$inline_229_j$jscomp$inline_225_tokenText < spans.length; ++j$1$jscomp$inline_229_j$jscomp$inline_225_tokenText) {
              var sp = spans[j$1$jscomp$inline_229_j$jscomp$inline_225_tokenText], m = sp.marker;
              if ("bookmark" == m.type && sp.from == pos && m.widgetNode) {
                foundBookmarks$jscomp$inline_223_upto.push(m);
              } else if (sp.from <= pos && (null == sp.to || sp.to > pos || m.collapsed && sp.to == pos && sp.from == pos)) {
                null != sp.to && sp.to != pos && nextChange > sp.to && (nextChange = sp.to, spanEndStyle$jscomp$inline_219_test = "");
                m.className && (spanStyle += " " + m.className);
                m.css && (css = (css ? css + ";" : "") + m.css);
                m.startStyle && sp.from == pos && (measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt += " " + m.startStyle);
                m.endStyle && sp.to == nextChange && (end$jscomp$inline_232_endStyles$jscomp$inline_224_j$2 || (end$jscomp$inline_232_endStyles$jscomp$inline_224_j$2 = [])).push(m.endStyle, sp.to);
                m.title && ((JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure || (JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure = {})).title = m.title);
                if (m.attributes) {
                  for (var attr$jscomp$inline_228_last in m.attributes) {
                    (JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure || (JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure = {}))[attr$jscomp$inline_228_last] = m.attributes[attr$jscomp$inline_228_last];
                  }
                }
                m.collapsed && (!JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0 || 0 > compareCollapsedMarkers(JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0.marker, m)) && (JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0 = sp);
              } else {
                sp.from > pos && nextChange > sp.from && (nextChange = sp.from);
              }
            }
            if (end$jscomp$inline_232_endStyles$jscomp$inline_224_j$2) {
              for (j$1$jscomp$inline_229_j$jscomp$inline_225_tokenText = 0; j$1$jscomp$inline_229_j$jscomp$inline_225_tokenText < end$jscomp$inline_232_endStyles$jscomp$inline_224_j$2.length; j$1$jscomp$inline_229_j$jscomp$inline_225_tokenText += 2) {
                end$jscomp$inline_232_endStyles$jscomp$inline_224_j$2[j$1$jscomp$inline_229_j$jscomp$inline_225_tokenText + 1] == nextChange && (spanEndStyle$jscomp$inline_219_test += " " + end$jscomp$inline_232_endStyles$jscomp$inline_224_j$2[j$1$jscomp$inline_229_j$jscomp$inline_225_tokenText]);
              }
            }
            if (!JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0 || JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0.from == pos) {
              for (end$jscomp$inline_232_endStyles$jscomp$inline_224_j$2 = 0; end$jscomp$inline_232_endStyles$jscomp$inline_224_j$2 < foundBookmarks$jscomp$inline_223_upto.length; ++end$jscomp$inline_232_endStyles$jscomp$inline_224_j$2) {
                buildCollapsedSpan(JSCompiler_temp_const$jscomp$28_builder$jscomp$inline_206_order, 0, foundBookmarks$jscomp$inline_223_upto[end$jscomp$inline_232_endStyles$jscomp$inline_224_j$2]);
              }
            }
            if (JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0 && (JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0.from || 0) == pos) {
              buildCollapsedSpan(JSCompiler_temp_const$jscomp$28_builder$jscomp$inline_206_order, (null == JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0.to ? len + 1 : JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0.to) - pos, JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0.marker, null == JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0.from);
              if (null == JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0.to) {
                break;
              }
              JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0.to == pos && (JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0 = !1);
            }
          }
          if (pos >= len) {
            break;
          }
          for (foundBookmarks$jscomp$inline_223_upto = Math.min(len, nextChange);;) {
            if (text) {
              end$jscomp$inline_232_endStyles$jscomp$inline_224_j$2 = pos + text.length;
              JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0 || (j$1$jscomp$inline_229_j$jscomp$inline_225_tokenText = end$jscomp$inline_232_endStyles$jscomp$inline_224_j$2 > foundBookmarks$jscomp$inline_223_upto ? text.slice(0, foundBookmarks$jscomp$inline_223_upto - pos) : text, JSCompiler_temp_const$jscomp$28_builder$jscomp$inline_206_order.addToken(JSCompiler_temp_const$jscomp$28_builder$jscomp$inline_206_order, j$1$jscomp$inline_229_j$jscomp$inline_225_tokenText, style ? style + 
              spanStyle : spanStyle, measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt, pos + j$1$jscomp$inline_229_j$jscomp$inline_225_tokenText.length == nextChange ? spanEndStyle$jscomp$inline_219_test : "", css, JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure));
              if (end$jscomp$inline_232_endStyles$jscomp$inline_224_j$2 >= foundBookmarks$jscomp$inline_223_upto) {
                text = text.slice(foundBookmarks$jscomp$inline_223_upto - pos);
                pos = foundBookmarks$jscomp$inline_223_upto;
                break;
              }
              pos = end$jscomp$inline_232_endStyles$jscomp$inline_224_j$2;
              measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt = "";
            }
            text = allText.slice(at, at = allowFrontierUpdate_styles[i$jscomp$0++]);
            style = interpretTokenStyle(allowFrontierUpdate_styles[i$jscomp$0++], JSCompiler_temp_const$jscomp$28_builder$jscomp$inline_206_order.cm.options);
          }
        }
      } else {
        for (JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure = 1; JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure < allowFrontierUpdate_styles.length; JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure += 2) {
          JSCompiler_temp_const$jscomp$28_builder$jscomp$inline_206_order.addToken(JSCompiler_temp_const$jscomp$28_builder$jscomp$inline_206_order, allText.slice(at, at = allowFrontierUpdate_styles[JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure]), interpretTokenStyle(allowFrontierUpdate_styles[JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure + 
          1], JSCompiler_temp_const$jscomp$28_builder$jscomp$inline_206_order.cm.options));
        }
      }
      JSCompiler_temp_const$jscomp$29_line.styleClasses && (JSCompiler_temp_const$jscomp$29_line.styleClasses.bgClass && (builder_content.bgClass = joinClasses(JSCompiler_temp_const$jscomp$29_line.styleClasses.bgClass, builder_content.bgClass || "")), JSCompiler_temp_const$jscomp$29_line.styleClasses.textClass && (builder_content.textClass = joinClasses(JSCompiler_temp_const$jscomp$29_line.styleClasses.textClass, builder_content.textClass || "")));
      0 == builder_content.map.length && (JSCompiler_temp_const$jscomp$29_line = builder_content.map, JSCompiler_temp_const$jscomp$28_builder$jscomp$inline_206_order = JSCompiler_temp_const$jscomp$29_line.push, JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure = builder_content.content, JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0 = JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure.appendChild, 
      measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt = cm.display.measure, null == zwspSupported && (spanEndStyle$jscomp$inline_219_test = elt$jscomp$0("span", "\u200b"), removeChildrenAndAdd(measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt, elt$jscomp$0("span", [spanEndStyle$jscomp$inline_219_test, document.createTextNode("x")])), 0 != measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt.firstChild.offsetHeight && 
      (zwspSupported = 1 >= spanEndStyle$jscomp$inline_219_test.offsetWidth && 2 < spanEndStyle$jscomp$inline_219_test.offsetHeight && !(ie && 8 > ie_version))), measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt = zwspSupported ? elt$jscomp$0("span", "\u200b") : elt$jscomp$0("span", "\u00a0", null, "display: inline-block; width: 1px; margin-right: -1px"), measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt.setAttribute("cm-text", 
      ""), JSCompiler_temp_const$jscomp$28_builder$jscomp$inline_206_order.call(JSCompiler_temp_const$jscomp$29_line, 0, 0, JSCompiler_temp_const$jscomp$26_collapsed$jscomp$inline_221_r0.call(JSCompiler_inline_result$jscomp$31_JSCompiler_temp_const$jscomp$27_attributes$jscomp$inline_222_i$1$jscomp$inline_234_measure, measure$jscomp$inline_236_node$jscomp$inline_238_r1$jscomp$inline_203_spanStartStyle$jscomp$inline_220_txt)));
      0 == i ? (lineView.measure.map = builder_content.map, lineView.measure.cache = {}) : ((lineView.measure.maps || (lineView.measure.maps = [])).push(builder_content.map), (lineView.measure.caches || (lineView.measure.caches = [])).push({}));
    }
    webkit && (attr$jscomp$inline_228_last = builder_content.content.lastChild, /\bcm-tab\b/.test(attr$jscomp$inline_228_last.className) || attr$jscomp$inline_228_last.querySelector && attr$jscomp$inline_228_last.querySelector(".cm-tab")) && (builder_content.content.className = "cm-tab-wrap-hack");
    signal(cm, "renderLine", cm, lineView.line, builder_content.pre);
    builder_content.pre.className && (builder_content.textClass = joinClasses(builder_content.pre.className, builder_content.textClass || ""));
    return builder_content;
  }
  function defaultSpecialCharPlaceholder(ch) {
    var token = elt$jscomp$0("span", "\u2022", "cm-invalidchar");
    token.title = "\\u" + ch.charCodeAt(0).toString(16);
    token.setAttribute("aria-label", token.title);
    return token;
  }
  function buildToken(builder, fullStyle_text, style, startStyle_token, endStyle, css, attributes) {
    if (fullStyle_text) {
      if (builder.splitSpaces) {
        if (1 < fullStyle_text.length && !/  /.test(fullStyle_text)) {
          var JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore = fullStyle_text;
        } else {
          JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore = builder.trailingSpace;
          for (var displayText_result = "", i = 0; i < fullStyle_text.length; i++) {
            var ch = fullStyle_text.charAt(i);
            " " != ch || !JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore || i != fullStyle_text.length - 1 && 32 != fullStyle_text.charCodeAt(i + 1) || (ch = "\u00a0");
            displayText_result += ch;
            JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore = " " == ch;
          }
          JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore = displayText_result;
        }
      } else {
        JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore = fullStyle_text;
      }
      displayText_result = JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore;
      i = builder.cm.state.specialChars;
      ch = !1;
      if (i.test(fullStyle_text)) {
        JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore = document.createDocumentFragment();
        for (var pos = 0;;) {
          i.lastIndex = pos;
          var m$jscomp$8_tabSize = i.exec(fullStyle_text), skipped = m$jscomp$8_tabSize ? m$jscomp$8_tabSize.index - pos : fullStyle_text.length - pos;
          if (skipped) {
            var txt = document.createTextNode(displayText_result.slice(pos, pos + skipped));
            ie && 9 > ie_version ? JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore.appendChild(elt$jscomp$0("span", [txt])) : JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore.appendChild(txt);
            builder.map.push(builder.pos, builder.pos + skipped, txt);
            builder.col += skipped;
            builder.pos += skipped;
          }
          if (!m$jscomp$8_tabSize) {
            break;
          }
          pos += skipped + 1;
          "\t" == m$jscomp$8_tabSize[0] ? (m$jscomp$8_tabSize = builder.cm.options.tabSize, m$jscomp$8_tabSize -= builder.col % m$jscomp$8_tabSize, skipped = JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore.appendChild(elt$jscomp$0("span", spaceStr(m$jscomp$8_tabSize), "cm-tab")), skipped.setAttribute("role", "presentation"), skipped.setAttribute("cm-text", "\t"), builder.col += m$jscomp$8_tabSize) : ("\r" == m$jscomp$8_tabSize[0] || "\n" == m$jscomp$8_tabSize[0] ? (skipped = JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore.appendChild(elt$jscomp$0("span", 
          "\r" == m$jscomp$8_tabSize[0] ? "\u240d" : "\u2424", "cm-invalidchar")), skipped.setAttribute("cm-text", m$jscomp$8_tabSize[0])) : (skipped = builder.cm.options.specialCharPlaceholder(m$jscomp$8_tabSize[0]), skipped.setAttribute("cm-text", m$jscomp$8_tabSize[0]), ie && 9 > ie_version ? JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore.appendChild(elt$jscomp$0("span", [skipped])) : JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore.appendChild(skipped)), builder.col += 1);
          builder.map.push(builder.pos, builder.pos + 1, skipped);
          builder.pos++;
        }
      } else {
        builder.col += fullStyle_text.length, JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore = document.createTextNode(displayText_result), builder.map.push(builder.pos, builder.pos + fullStyle_text.length, JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore), ie && 9 > ie_version && (ch = !0), builder.pos += fullStyle_text.length;
      }
      builder.trailingSpace = 32 == displayText_result.charCodeAt(fullStyle_text.length - 1);
      if (style || startStyle_token || endStyle || ch || css || attributes) {
        fullStyle_text = style || "";
        startStyle_token && (fullStyle_text += startStyle_token);
        endStyle && (fullStyle_text += endStyle);
        startStyle_token = elt$jscomp$0("span", [JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore], fullStyle_text, css);
        if (attributes) {
          for (var attr in attributes) {
            if (attributes.hasOwnProperty(attr) && "style" != attr && "class" != attr) {
              if ("title" === attr) {
                startStyle_token.setAttribute("title", attributes[attr]);
              } else {
                throw Error("attributes not supported for security reasons");
              }
            }
          }
        }
        return builder.content.appendChild(startStyle_token);
      }
      builder.content.appendChild(JSCompiler_temp$jscomp$38_content$jscomp$17_spaceBefore);
    }
  }
  function buildTokenBadBidi(inner, order) {
    return function(builder, text, style, startStyle, endStyle, css, attributes) {
      style = style ? style + " cm-force-border" : "cm-force-border";
      for (var start = builder.pos, end = start + text.length;;) {
        for (var part = void 0, i = 0; i < order.length && !(part = order[i], part.to > start && part.from <= start); i++) {
        }
        if (part.to >= end) {
          return inner(builder, text, style, startStyle, endStyle, css, attributes);
        }
        inner(builder, text.slice(0, part.to - start), style, startStyle, null, css, attributes);
        startStyle = null;
        text = text.slice(part.to - start);
        start = part.to;
      }
    };
  }
  function buildCollapsedSpan(builder, size, marker, ignoreWidget) {
    var widget = !ignoreWidget && marker.widgetNode;
    widget && builder.map.push(builder.pos, builder.pos + size, widget);
    !ignoreWidget && builder.cm.display.input.needsContentAttribute && (widget || (widget = builder.content.appendChild(document.createElement("span"))), widget.setAttribute("cm-marker", marker.id));
    widget && (builder.cm.display.input.setUneditable(widget), builder.content.appendChild(widget));
    builder.pos += size;
    builder.trailingSpace = !1;
  }
  function LineView(doc, line, lineN) {
    for (var line$jscomp$inline_247_merged = this.line = line, lines; line$jscomp$inline_247_merged = collapsedSpanAtSide(line$jscomp$inline_247_merged, !1);) {
      line$jscomp$inline_247_merged = line$jscomp$inline_247_merged.find(1, !0).line, (lines || (lines = [])).push(line$jscomp$inline_247_merged);
    }
    this.size = (this.rest = lines) ? lineNo(lst(this.rest)) - lineN + 1 : 1;
    this.node = this.text = null;
    this.hidden = lineIsHidden(doc, line);
  }
  function buildViewArray(cm, from$jscomp$15_view, to) {
    var array = [], nextPos_pos;
    for (nextPos_pos = from$jscomp$15_view; nextPos_pos < to;) {
      from$jscomp$15_view = new LineView(cm.doc, getLine(cm.doc, nextPos_pos), nextPos_pos), nextPos_pos += from$jscomp$15_view.size, array.push(from$jscomp$15_view);
    }
    return array;
  }
  function finishOperation(group$jscomp$1_op, endCb) {
    if (group$jscomp$1_op = group$jscomp$1_op.ownsGroup) {
      try {
        var callbacks = group$jscomp$1_op.delayedCallbacks, i = 0;
        do {
          for (; i < callbacks.length; i++) {
            callbacks[i].call(null);
          }
          for (var j = 0; j < group$jscomp$1_op.ops.length; j++) {
            var op = group$jscomp$1_op.ops[j];
            if (op.cursorActivityHandlers) {
              for (; op.cursorActivityCalled < op.cursorActivityHandlers.length;) {
                op.cursorActivityHandlers[op.cursorActivityCalled++].call(null, op.cm);
              }
            }
          }
        } while (i < callbacks.length);
      } finally {
        operationGroup = null, endCb(group$jscomp$1_op);
      }
    }
  }
  function signalLater(emitter, type) {
    var arr = emitter._handlers && emitter._handlers[type] || noHandlers;
    if (arr.length) {
      var args = Array.prototype.slice.call(arguments, 2);
      if (operationGroup) {
        var list = operationGroup.delayedCallbacks;
      } else {
        orphanDelayedCallbacks ? list = orphanDelayedCallbacks : (list = orphanDelayedCallbacks = [], setTimeout(fireOrphanDelayed, 0));
      }
      for (var loop = function(i) {
        list.push(function() {
          return arr[i].apply(null, args);
        });
      }, i$jscomp$0 = 0; i$jscomp$0 < arr.length; ++i$jscomp$0) {
        loop(i$jscomp$0);
      }
    }
  }
  function fireOrphanDelayed() {
    var delayed = orphanDelayedCallbacks;
    orphanDelayedCallbacks = null;
    for (var i = 0; i < delayed.length; ++i) {
      delayed[i]();
    }
  }
  function updateLineForChanges(cm, lineView, lineN, dims) {
    for (var j = 0; j < lineView.changes.length; j++) {
      var cm$jscomp$inline_257_cm$jscomp$inline_262_type = lineView.changes[j];
      if ("text" == cm$jscomp$inline_257_cm$jscomp$inline_262_type) {
        cm$jscomp$inline_257_cm$jscomp$inline_262_type = cm;
        var lineView$jscomp$inline_258_lineView = lineView, cls$jscomp$inline_259_dims = lineView$jscomp$inline_258_lineView.text.className, built$jscomp$inline_260_isWidget = getLineContent(cm$jscomp$inline_257_cm$jscomp$inline_262_type, lineView$jscomp$inline_258_lineView);
        lineView$jscomp$inline_258_lineView.text == lineView$jscomp$inline_258_lineView.node && (lineView$jscomp$inline_258_lineView.node = built$jscomp$inline_260_isWidget.pre);
        lineView$jscomp$inline_258_lineView.text.parentNode.replaceChild(built$jscomp$inline_260_isWidget.pre, lineView$jscomp$inline_258_lineView.text);
        lineView$jscomp$inline_258_lineView.text = built$jscomp$inline_260_isWidget.pre;
        built$jscomp$inline_260_isWidget.bgClass != lineView$jscomp$inline_258_lineView.bgClass || built$jscomp$inline_260_isWidget.textClass != lineView$jscomp$inline_258_lineView.textClass ? (lineView$jscomp$inline_258_lineView.bgClass = built$jscomp$inline_260_isWidget.bgClass, lineView$jscomp$inline_258_lineView.textClass = built$jscomp$inline_260_isWidget.textClass, updateLineClasses(cm$jscomp$inline_257_cm$jscomp$inline_262_type, lineView$jscomp$inline_258_lineView)) : cls$jscomp$inline_259_dims && 
        (lineView$jscomp$inline_258_lineView.text.className = cls$jscomp$inline_259_dims);
      } else {
        if ("gutter" == cm$jscomp$inline_257_cm$jscomp$inline_262_type) {
          updateLineGutter(cm, lineView, lineN, dims);
        } else {
          if ("class" == cm$jscomp$inline_257_cm$jscomp$inline_262_type) {
            updateLineClasses(cm, lineView);
          } else {
            if ("widget" == cm$jscomp$inline_257_cm$jscomp$inline_262_type) {
              cm$jscomp$inline_257_cm$jscomp$inline_262_type = cm;
              lineView$jscomp$inline_258_lineView = lineView;
              cls$jscomp$inline_259_dims = dims;
              lineView$jscomp$inline_258_lineView.alignable && (lineView$jscomp$inline_258_lineView.alignable = null);
              built$jscomp$inline_260_isWidget = classTest("CodeMirror-linewidget");
              for (var node = lineView$jscomp$inline_258_lineView.node.firstChild, next; node; node = next) {
                next = node.nextSibling, built$jscomp$inline_260_isWidget.test(node.className) && lineView$jscomp$inline_258_lineView.node.removeChild(node);
              }
              insertLineWidgets(cm$jscomp$inline_257_cm$jscomp$inline_262_type, lineView$jscomp$inline_258_lineView, cls$jscomp$inline_259_dims);
            }
          }
        }
      }
    }
    lineView.changes = null;
  }
  function ensureLineWrapped(lineView) {
    lineView.node == lineView.text && (lineView.node = elt$jscomp$0("div", null, null, "position: relative"), lineView.text.parentNode && lineView.text.parentNode.replaceChild(lineView.node, lineView.text), lineView.node.appendChild(lineView.text), ie && 8 > ie_version && (lineView.node.style.zIndex = 2));
    return lineView.node;
  }
  function getLineContent(cm, lineView) {
    var ext = cm.display.externalMeasured;
    return ext && ext.line == lineView.line ? (cm.display.externalMeasured = null, lineView.measure = ext.measure, ext.built) : buildLineContent(cm, lineView);
  }
  function updateLineClasses(cm, lineView) {
    var cls = lineView.bgClass ? lineView.bgClass + " " + (lineView.line.bgClass || "") : lineView.line.bgClass;
    cls && (cls += " CodeMirror-linebackground");
    if (lineView.background) {
      cls ? lineView.background.className = cls : (lineView.background.parentNode.removeChild(lineView.background), lineView.background = null);
    } else if (cls) {
      var wrap = ensureLineWrapped(lineView);
      lineView.background = wrap.insertBefore(elt$jscomp$0("div", null, cls), wrap.firstChild);
      cm.display.input.setUneditable(lineView.background);
    }
    lineView.line.wrapClass ? ensureLineWrapped(lineView).className = lineView.line.wrapClass : lineView.node != lineView.text && (lineView.node.className = "");
    lineView.text.className = (lineView.textClass ? lineView.textClass + " " + (lineView.line.textClass || "") : lineView.line.textClass) || "";
  }
  function updateLineGutter(cm, k$jscomp$7_lineView, id$jscomp$8_lineN, dims) {
    k$jscomp$7_lineView.gutter && (k$jscomp$7_lineView.node.removeChild(k$jscomp$7_lineView.gutter), k$jscomp$7_lineView.gutter = null);
    k$jscomp$7_lineView.gutterBackground && (k$jscomp$7_lineView.node.removeChild(k$jscomp$7_lineView.gutterBackground), k$jscomp$7_lineView.gutterBackground = null);
    if (k$jscomp$7_lineView.line.gutterClass) {
      var markers$jscomp$1_wrap = ensureLineWrapped(k$jscomp$7_lineView);
      k$jscomp$7_lineView.gutterBackground = elt$jscomp$0("div", null, "CodeMirror-gutter-background " + k$jscomp$7_lineView.line.gutterClass, "left: " + (cm.options.fixedGutter ? dims.fixedPos : -dims.gutterTotalWidth) + "px; width: " + dims.gutterTotalWidth + "px");
      cm.display.input.setUneditable(k$jscomp$7_lineView.gutterBackground);
      markers$jscomp$1_wrap.insertBefore(k$jscomp$7_lineView.gutterBackground, k$jscomp$7_lineView.text);
    }
    markers$jscomp$1_wrap = k$jscomp$7_lineView.line.gutterMarkers;
    if (cm.options.lineNumbers || markers$jscomp$1_wrap) {
      var found = ensureLineWrapped(k$jscomp$7_lineView), gutterWrap = k$jscomp$7_lineView.gutter = elt$jscomp$0("div", null, "CodeMirror-gutter-wrapper", "left: " + (cm.options.fixedGutter ? dims.fixedPos : -dims.gutterTotalWidth) + "px");
      gutterWrap.setAttribute("aria-hidden", "true");
      cm.display.input.setUneditable(gutterWrap);
      found.insertBefore(gutterWrap, k$jscomp$7_lineView.text);
      k$jscomp$7_lineView.line.gutterClass && (gutterWrap.className += " " + k$jscomp$7_lineView.line.gutterClass);
      !cm.options.lineNumbers || markers$jscomp$1_wrap && markers$jscomp$1_wrap["CodeMirror-linenumbers"] || (k$jscomp$7_lineView.lineNumber = gutterWrap.appendChild(elt$jscomp$0("div", lineNumberFor(cm.options, id$jscomp$8_lineN), "CodeMirror-linenumber CodeMirror-gutter-elt", "left: " + dims.gutterLeft["CodeMirror-linenumbers"] + "px; width: " + cm.display.lineNumInnerWidth + "px")));
      if (markers$jscomp$1_wrap) {
        for (k$jscomp$7_lineView = 0; k$jscomp$7_lineView < cm.display.gutterSpecs.length; ++k$jscomp$7_lineView) {
          id$jscomp$8_lineN = cm.display.gutterSpecs[k$jscomp$7_lineView].className, (found = markers$jscomp$1_wrap.hasOwnProperty(id$jscomp$8_lineN) && markers$jscomp$1_wrap[id$jscomp$8_lineN]) && gutterWrap.appendChild(elt$jscomp$0("div", [found], "CodeMirror-gutter-elt", "left: " + dims.gutterLeft[id$jscomp$8_lineN] + "px; width: " + dims.gutterWidth[id$jscomp$8_lineN] + "px"));
        }
      }
    }
  }
  function buildLineElement(cm, lineView, lineN, dims) {
    var built = getLineContent(cm, lineView);
    lineView.text = lineView.node = built.pre;
    built.bgClass && (lineView.bgClass = built.bgClass);
    built.textClass && (lineView.textClass = built.textClass);
    updateLineClasses(cm, lineView);
    updateLineGutter(cm, lineView, lineN, dims);
    insertLineWidgets(cm, lineView, dims);
    return lineView.node;
  }
  function insertLineWidgets(cm, lineView, dims) {
    insertLineWidgetsFor(cm, lineView.line, lineView, dims, !0);
    if (lineView.rest) {
      for (var i = 0; i < lineView.rest.length; i++) {
        insertLineWidgetsFor(cm, lineView.rest[i], lineView, dims, !1);
      }
    }
  }
  function insertLineWidgetsFor(cm, line, lineView, dims$jscomp$0, allowAbove) {
    if (line.widgets) {
      var wrap = ensureLineWrapped(lineView), i = 0;
      for (line = line.widgets; i < line.length; ++i) {
        var widget = line[i], node = elt$jscomp$0("div", [widget.node], "CodeMirror-linewidget" + (widget.className ? " " + widget.className : ""));
        widget.handleMouseEvents || node.setAttribute("cm-ignore-events", "true");
        var widget$jscomp$0 = widget, node$jscomp$0 = node, dims = dims$jscomp$0;
        if (widget$jscomp$0.noHScroll) {
          (lineView.alignable || (lineView.alignable = [])).push(node$jscomp$0);
          var width = dims.wrapperWidth;
          node$jscomp$0.style.left = dims.fixedPos + "px";
          widget$jscomp$0.coverGutter || (width -= dims.gutterTotalWidth, node$jscomp$0.style.paddingLeft = dims.gutterTotalWidth + "px");
          node$jscomp$0.style.width = width + "px";
        }
        widget$jscomp$0.coverGutter && (node$jscomp$0.style.zIndex = 5, node$jscomp$0.style.position = "relative", widget$jscomp$0.noHScroll || (node$jscomp$0.style.marginLeft = -dims.gutterTotalWidth + "px"));
        cm.display.input.setUneditable(node);
        allowAbove && widget.above ? wrap.insertBefore(node, lineView.gutter || lineView.text) : wrap.appendChild(node);
        signalLater(widget, "redraw");
      }
    }
  }
  function widgetHeight(widget) {
    if (null != widget.height) {
      return widget.height;
    }
    var cm = widget.doc.cm;
    if (!cm) {
      return 0;
    }
    if (!contains(document.body, widget.node)) {
      var parentStyle = "position: relative;";
      widget.coverGutter && (parentStyle += "margin-left: -" + cm.display.gutters.offsetWidth + "px;");
      widget.noHScroll && (parentStyle += "width: " + cm.display.wrapper.clientWidth + "px;");
      removeChildrenAndAdd(cm.display.measure, elt$jscomp$0("div", [widget.node], null, parentStyle));
    }
    return widget.height = widget.node.parentNode.offsetHeight;
  }
  function eventInWidget(display, e$jscomp$58_n) {
    for (e$jscomp$58_n = e$jscomp$58_n.target || e$jscomp$58_n.srcElement; e$jscomp$58_n != display.wrapper; e$jscomp$58_n = e$jscomp$58_n.parentNode) {
      if (!e$jscomp$58_n || 1 == e$jscomp$58_n.nodeType && "true" == e$jscomp$58_n.getAttribute("cm-ignore-events") || e$jscomp$58_n.parentNode == display.sizer && e$jscomp$58_n != display.mover) {
        return !0;
      }
    }
  }
  function paddingVert(display) {
    return display.mover.offsetHeight - display.lineSpace.offsetHeight;
  }
  function paddingH(display) {
    if (display.cachedPaddingH) {
      return display.cachedPaddingH;
    }
    var data$jscomp$87_e$jscomp$59_style = removeChildrenAndAdd(display.measure, elt$jscomp$0("pre", "x", "CodeMirror-line-like"));
    data$jscomp$87_e$jscomp$59_style = window.getComputedStyle ? window.getComputedStyle(data$jscomp$87_e$jscomp$59_style) : data$jscomp$87_e$jscomp$59_style.currentStyle;
    data$jscomp$87_e$jscomp$59_style = {left:parseInt(data$jscomp$87_e$jscomp$59_style.paddingLeft), right:parseInt(data$jscomp$87_e$jscomp$59_style.paddingRight)};
    isNaN(data$jscomp$87_e$jscomp$59_style.left) || isNaN(data$jscomp$87_e$jscomp$59_style.right) || (display.cachedPaddingH = data$jscomp$87_e$jscomp$59_style);
    return data$jscomp$87_e$jscomp$59_style;
  }
  function scrollGap(cm) {
    return 50 - cm.display.nativeBarWidth;
  }
  function displayWidth(cm) {
    return cm.display.scroller.clientWidth - scrollGap(cm) - cm.display.barWidth;
  }
  function displayHeight(cm) {
    return cm.display.scroller.clientHeight - scrollGap(cm) - cm.display.barHeight;
  }
  function mapFromLineView(lineView, i$1$jscomp$6_line, lineN) {
    if (lineView.line == i$1$jscomp$6_line) {
      return {map:lineView.measure.map, cache:lineView.measure.cache};
    }
    if (lineView.rest) {
      for (var i = 0; i < lineView.rest.length; i++) {
        if (lineView.rest[i] == i$1$jscomp$6_line) {
          return {map:lineView.measure.maps[i], cache:lineView.measure.caches[i]};
        }
      }
      for (i$1$jscomp$6_line = 0; i$1$jscomp$6_line < lineView.rest.length; i$1$jscomp$6_line++) {
        if (lineNo(lineView.rest[i$1$jscomp$6_line]) > lineN) {
          return {map:lineView.measure.maps[i$1$jscomp$6_line], cache:lineView.measure.caches[i$1$jscomp$6_line], before:!0};
        }
      }
    }
  }
  function findViewForLine(cm$jscomp$31_ext, lineN) {
    if (lineN >= cm$jscomp$31_ext.display.viewFrom && lineN < cm$jscomp$31_ext.display.viewTo) {
      return cm$jscomp$31_ext.display.view[findViewIndex(cm$jscomp$31_ext, lineN)];
    }
    if ((cm$jscomp$31_ext = cm$jscomp$31_ext.display.externalMeasured) && lineN >= cm$jscomp$31_ext.lineN && lineN < cm$jscomp$31_ext.lineN + cm$jscomp$31_ext.size) {
      return cm$jscomp$31_ext;
    }
  }
  function prepareMeasureForLine(cm$jscomp$32_info, line) {
    var lineN = lineNo(line), built$jscomp$inline_284_lineN$jscomp$inline_282_view = findViewForLine(cm$jscomp$32_info, lineN);
    built$jscomp$inline_284_lineN$jscomp$inline_282_view && !built$jscomp$inline_284_lineN$jscomp$inline_282_view.text ? built$jscomp$inline_284_lineN$jscomp$inline_282_view = null : built$jscomp$inline_284_lineN$jscomp$inline_282_view && built$jscomp$inline_284_lineN$jscomp$inline_282_view.changes && (updateLineForChanges(cm$jscomp$32_info, built$jscomp$inline_284_lineN$jscomp$inline_282_view, lineN, getDimensions(cm$jscomp$32_info)), cm$jscomp$32_info.curOp.forceUpdate = !0);
    if (!built$jscomp$inline_284_lineN$jscomp$inline_282_view) {
      var line$jscomp$inline_281_view = visualLine(line);
      built$jscomp$inline_284_lineN$jscomp$inline_282_view = lineNo(line$jscomp$inline_281_view);
      line$jscomp$inline_281_view = cm$jscomp$32_info.display.externalMeasured = new LineView(cm$jscomp$32_info.doc, line$jscomp$inline_281_view, built$jscomp$inline_284_lineN$jscomp$inline_282_view);
      line$jscomp$inline_281_view.lineN = built$jscomp$inline_284_lineN$jscomp$inline_282_view;
      built$jscomp$inline_284_lineN$jscomp$inline_282_view = line$jscomp$inline_281_view.built = buildLineContent(cm$jscomp$32_info, line$jscomp$inline_281_view);
      line$jscomp$inline_281_view.text = built$jscomp$inline_284_lineN$jscomp$inline_282_view.pre;
      removeChildrenAndAdd(cm$jscomp$32_info.display.lineMeasure, built$jscomp$inline_284_lineN$jscomp$inline_282_view.pre);
      built$jscomp$inline_284_lineN$jscomp$inline_282_view = line$jscomp$inline_281_view;
    }
    cm$jscomp$32_info = mapFromLineView(built$jscomp$inline_284_lineN$jscomp$inline_282_view, line, lineN);
    return {line, view:built$jscomp$inline_284_lineN$jscomp$inline_282_view, rect:null, map:cm$jscomp$32_info.map, cache:cm$jscomp$32_info.cache, before:cm$jscomp$32_info.before, hasHeights:!1};
  }
  function measureCharPrepared(cm$jscomp$33_found, prepared, ch$jscomp$19_collapse$jscomp$inline_305_result, bias$jscomp$1_node$jscomp$inline_302_rtop, varHeight) {
    prepared.before && (ch$jscomp$19_collapse$jscomp$inline_305_result = -1);
    var key = ch$jscomp$19_collapse$jscomp$inline_305_result + (bias$jscomp$1_node$jscomp$inline_302_rtop || "");
    if (prepared.cache.hasOwnProperty(key)) {
      cm$jscomp$33_found = prepared.cache[key];
    } else {
      prepared.rect || (prepared.rect = prepared.view.text.getBoundingClientRect());
      if (!prepared.hasHeights) {
        var i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects = prepared.view, rbot$jscomp$inline_311_rect$jscomp$inline_288_start = prepared.rect, end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping = cm$jscomp$33_found.options.lineWrapping, cur$jscomp$inline_294_curWidth$jscomp$inline_290_i$1$jscomp$inline_656_i = end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping && displayWidth(cm$jscomp$33_found);
        if (!i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.measure.heights || end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping && i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.measure.width != cur$jscomp$inline_294_curWidth$jscomp$inline_290_i$1$jscomp$inline_656_i) {
          var bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY = i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.measure.heights = [];
          if (end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping) {
            for (i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.measure.width = cur$jscomp$inline_294_curWidth$jscomp$inline_290_i$1$jscomp$inline_656_i, i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects = i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.text.firstChild.getClientRects(), end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping = 0; end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping < 
            i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.length - 1; end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping++) {
              cur$jscomp$inline_294_curWidth$jscomp$inline_290_i$1$jscomp$inline_656_i = i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects[end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping];
              var next = i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects[end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping + 1];
              2 < Math.abs(cur$jscomp$inline_294_curWidth$jscomp$inline_290_i$1$jscomp$inline_656_i.bottom - next.bottom) && bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY.push((cur$jscomp$inline_294_curWidth$jscomp$inline_290_i$1$jscomp$inline_656_i.bottom + next.top) / 2 - rbot$jscomp$inline_311_rect$jscomp$inline_288_start.top);
            }
          }
          bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY.push(rbot$jscomp$inline_311_rect$jscomp$inline_288_start.bottom - rbot$jscomp$inline_311_rect$jscomp$inline_288_start.top);
        }
        prepared.hasHeights = !0;
      }
      bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY = bias$jscomp$1_node$jscomp$inline_302_rtop;
      i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects = nodeAndOffsetInLineMap(prepared.map, ch$jscomp$19_collapse$jscomp$inline_305_result, bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY);
      bias$jscomp$1_node$jscomp$inline_302_rtop = i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.node;
      rbot$jscomp$inline_311_rect$jscomp$inline_288_start = i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.start;
      end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping = i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.end;
      ch$jscomp$19_collapse$jscomp$inline_305_result = i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.collapse;
      if (3 == bias$jscomp$1_node$jscomp$inline_302_rtop.nodeType) {
        for (var JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX = 0; 4 > JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX; JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX++) {
          for (; rbot$jscomp$inline_311_rect$jscomp$inline_288_start && isExtendingChar(prepared.line.text.charAt(i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.coverStart + rbot$jscomp$inline_311_rect$jscomp$inline_288_start));) {
            --rbot$jscomp$inline_311_rect$jscomp$inline_288_start;
          }
          for (; i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.coverStart + end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping < i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.coverEnd && isExtendingChar(prepared.line.text.charAt(i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.coverStart + end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping));) {
            ++end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping;
          }
          if (ie && 9 > ie_version && 0 == rbot$jscomp$inline_311_rect$jscomp$inline_288_start && end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping == i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.coverEnd - i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects.coverStart) {
            var JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects = bias$jscomp$1_node$jscomp$inline_302_rtop.parentNode.getBoundingClientRect();
          } else {
            JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects = range$jscomp$0(bias$jscomp$1_node$jscomp$inline_302_rtop, rbot$jscomp$inline_311_rect$jscomp$inline_288_start, end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping).getClientRects();
            end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping = nullRect;
            if ("left" == bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY) {
              for (cur$jscomp$inline_294_curWidth$jscomp$inline_290_i$1$jscomp$inline_656_i = 0; cur$jscomp$inline_294_curWidth$jscomp$inline_290_i$1$jscomp$inline_656_i < JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.length && (end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping = JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects[cur$jscomp$inline_294_curWidth$jscomp$inline_290_i$1$jscomp$inline_656_i]).left == 
              end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping.right; cur$jscomp$inline_294_curWidth$jscomp$inline_290_i$1$jscomp$inline_656_i++) {
              }
            } else {
              for (cur$jscomp$inline_294_curWidth$jscomp$inline_290_i$1$jscomp$inline_656_i = JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.length - 1; 0 <= cur$jscomp$inline_294_curWidth$jscomp$inline_290_i$1$jscomp$inline_656_i && (end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping = JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects[cur$jscomp$inline_294_curWidth$jscomp$inline_290_i$1$jscomp$inline_656_i]).left == 
              end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping.right; cur$jscomp$inline_294_curWidth$jscomp$inline_290_i$1$jscomp$inline_656_i--) {
              }
            }
            JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects = end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping;
          }
          if (JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.left || JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.right || 0 == rbot$jscomp$inline_311_rect$jscomp$inline_288_start) {
            break;
          }
          end$jscomp$inline_304_i$jscomp$inline_293_rect$jscomp$inline_654_wrapping = rbot$jscomp$inline_311_rect$jscomp$inline_288_start;
          --rbot$jscomp$inline_311_rect$jscomp$inline_288_start;
          ch$jscomp$19_collapse$jscomp$inline_305_result = "right";
        }
        ie && 11 > ie_version && ((JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX = !window.screen || null == screen.logicalXDPI || screen.logicalXDPI == screen.deviceXDPI) || (null != badZoomedRects ? JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX = 
        badZoomedRects : (bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY = removeChildrenAndAdd(cm$jscomp$33_found.display.measure, elt$jscomp$0("span", "x")), JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX = bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY.getBoundingClientRect(), 
        bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY = range$jscomp$0(bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY, 0, 1).getBoundingClientRect(), JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX = 
        badZoomedRects = 1 < Math.abs(JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX.left - bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY.left)), JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX = 
        !JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX), JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX || (JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX = 
        screen.logicalXDPI / screen.deviceXDPI, bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY = screen.logicalYDPI / screen.deviceYDPI, JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects = {left:JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.left * JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX, 
        right:JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.right * JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX, top:JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.top * bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY, 
        bottom:JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.bottom * bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY}));
      } else {
        0 < rbot$jscomp$inline_311_rect$jscomp$inline_288_start && (ch$jscomp$19_collapse$jscomp$inline_305_result = bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY = "right"), JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects = cm$jscomp$33_found.options.lineWrapping && 1 < (JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX = 
        bias$jscomp$1_node$jscomp$inline_302_rtop.getClientRects()).length ? JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX["right" == bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY ? JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX.length - 
        1 : 0] : bias$jscomp$1_node$jscomp$inline_302_rtop.getBoundingClientRect();
      }
      !(ie && 9 > ie_version) || rbot$jscomp$inline_311_rect$jscomp$inline_288_start || JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects && (JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.left || JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.right) || (JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects = 
      (JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects = bias$jscomp$1_node$jscomp$inline_302_rtop.parentNode.getClientRects()[0]) ? {left:JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.left, right:JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.left + charWidth(cm$jscomp$33_found.display), top:JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.top, 
      bottom:JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.bottom} : nullRect);
      bias$jscomp$1_node$jscomp$inline_302_rtop = JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.top - prepared.rect.top;
      rbot$jscomp$inline_311_rect$jscomp$inline_288_start = JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.bottom - prepared.rect.top;
      JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX = (bias$jscomp$1_node$jscomp$inline_302_rtop + rbot$jscomp$inline_311_rect$jscomp$inline_288_start) / 2;
      bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY = prepared.view.measure.heights;
      for (i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects = 0; i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects < bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY.length - 1 && !(JSCompiler_inline_result$jscomp$inline_661_JSCompiler_temp$jscomp$inline_660_i$1$jscomp$inline_307_mid$jscomp$inline_312_normal$jscomp$inline_663_rects$jscomp$inline_308_scaleX < bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY[i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects]); i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects++) {
      }
      ch$jscomp$19_collapse$jscomp$inline_305_result = {left:("right" == ch$jscomp$19_collapse$jscomp$inline_305_result ? JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.right : JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.left) - prepared.rect.left, right:("left" == ch$jscomp$19_collapse$jscomp$inline_305_result ? JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.left : 
      JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.right) - prepared.rect.left, top:i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects ? bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY[i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects - 1] : 0, bottom:bias$jscomp$inline_300_fromRange$jscomp$inline_664_heights$jscomp$inline_291_heights$jscomp$inline_313_node$jscomp$inline_662_scaleY[i$jscomp$inline_314_lineView$jscomp$inline_287_place$jscomp$inline_301_rects]};
      JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.left || JSCompiler_temp$jscomp$631_rSpan$jscomp$inline_309_rect$jscomp$inline_306_rect$jscomp$inline_659_rects.right || (ch$jscomp$19_collapse$jscomp$inline_305_result.bogus = !0);
      cm$jscomp$33_found.options.singleCursorHeightPerLine || (ch$jscomp$19_collapse$jscomp$inline_305_result.rtop = bias$jscomp$1_node$jscomp$inline_302_rtop, ch$jscomp$19_collapse$jscomp$inline_305_result.rbottom = rbot$jscomp$inline_311_rect$jscomp$inline_288_start);
      cm$jscomp$33_found = ch$jscomp$19_collapse$jscomp$inline_305_result;
      cm$jscomp$33_found.bogus || (prepared.cache[key] = cm$jscomp$33_found);
    }
    return {left:cm$jscomp$33_found.left, right:cm$jscomp$33_found.right, top:varHeight ? cm$jscomp$33_found.rtop : cm$jscomp$33_found.top, bottom:varHeight ? cm$jscomp$33_found.rbottom : cm$jscomp$33_found.bottom};
  }
  function nodeAndOffsetInLineMap(map, ch, bias) {
    for (var node, start, end, collapse, mStart, mEnd, i = 0; i < map.length; i += 3) {
      mStart = map[i];
      mEnd = map[i + 1];
      if (ch < mStart) {
        start = 0, end = 1, collapse = "left";
      } else if (ch < mEnd) {
        start = ch - mStart, end = start + 1;
      } else if (i == map.length - 3 || ch == mEnd && map[i + 3] > ch) {
        end = mEnd - mStart, start = end - 1, ch >= mEnd && (collapse = "right");
      }
      if (null != start) {
        node = map[i + 2];
        mStart == mEnd && bias == (node.insertLeft ? "left" : "right") && (collapse = bias);
        if ("left" == bias && 0 == start) {
          for (; i && map[i - 2] == map[i - 3] && map[i - 1].insertLeft;) {
            node = map[(i -= 3) + 2], collapse = "left";
          }
        }
        if ("right" == bias && start == mEnd - mStart) {
          for (; i < map.length - 3 && map[i + 3] == map[i + 4] && !map[i + 5].insertLeft;) {
            node = map[(i += 3) + 2], collapse = "right";
          }
        }
        break;
      }
    }
    return {node, start, end, collapse, coverStart:mStart, coverEnd:mEnd};
  }
  function clearLineMeasurementCacheFor(lineView) {
    if (lineView.measure && (lineView.measure.cache = {}, lineView.measure.heights = null, lineView.rest)) {
      for (var i = 0; i < lineView.rest.length; i++) {
        lineView.measure.caches[i] = {};
      }
    }
  }
  function clearLineMeasurementCache(cm) {
    cm.display.externalMeasure = null;
    removeChildren(cm.display.lineMeasure);
    for (var i = 0; i < cm.display.view.length; i++) {
      clearLineMeasurementCacheFor(cm.display.view[i]);
    }
  }
  function clearCaches(cm) {
    clearLineMeasurementCache(cm);
    cm.display.cachedCharWidth = cm.display.cachedTextHeight = cm.display.cachedPaddingH = null;
    cm.options.lineWrapping || (cm.display.maxLineChanged = !0);
    cm.display.lineNumChars = null;
  }
  function pageScrollX() {
    return chrome && android ? -(document.body.getBoundingClientRect().left - parseInt(getComputedStyle(document.body).marginLeft)) : window.pageXOffset || (document.documentElement || document.body).scrollLeft;
  }
  function pageScrollY() {
    return chrome && android ? -(document.body.getBoundingClientRect().top - parseInt(getComputedStyle(document.body).marginTop)) : window.pageYOffset || (document.documentElement || document.body).scrollTop;
  }
  function widgetTopHeight(lineObj) {
    lineObj = visualLine(lineObj).widgets;
    var height = 0;
    if (lineObj) {
      for (var i = 0; i < lineObj.length; ++i) {
        lineObj[i].above && (height += widgetHeight(lineObj[i]));
      }
    }
    return height;
  }
  function intoCoordSystem(cm, lineObj, rect, context, height$jscomp$29_includeWidgets) {
    height$jscomp$29_includeWidgets || (height$jscomp$29_includeWidgets = widgetTopHeight(lineObj), rect.top += height$jscomp$29_includeWidgets, rect.bottom += height$jscomp$29_includeWidgets);
    if ("line" == context) {
      return rect;
    }
    context || (context = "local");
    lineObj = heightAtLine(lineObj);
    lineObj = "local" == context ? lineObj + cm.display.lineSpace.offsetTop : lineObj - cm.display.viewOffset;
    if ("page" == context || "window" == context) {
      cm = cm.display.lineSpace.getBoundingClientRect(), lineObj += cm.top + ("window" == context ? 0 : pageScrollY()), context = cm.left + ("window" == context ? 0 : pageScrollX()), rect.left += context, rect.right += context;
    }
    rect.top += lineObj;
    rect.bottom += lineObj;
    return rect;
  }
  function fromCoordSystem(cm, coords_top, context) {
    if ("div" == context) {
      return coords_top;
    }
    var left = coords_top.left;
    coords_top = coords_top.top;
    "page" == context ? (left -= pageScrollX(), coords_top -= pageScrollY()) : "local" != context && context || (context = cm.display.sizer.getBoundingClientRect(), left += context.left, coords_top += context.top);
    cm = cm.display.lineSpace.getBoundingClientRect();
    return {left:left - cm.left, top:coords_top - cm.top};
  }
  function charCoords(cm, ch$jscomp$inline_324_pos, context, JSCompiler_inline_result$jscomp$45_lineObj, bias) {
    JSCompiler_inline_result$jscomp$45_lineObj || (JSCompiler_inline_result$jscomp$45_lineObj = getLine(cm.doc, ch$jscomp$inline_324_pos.line));
    var JSCompiler_temp_const = JSCompiler_inline_result$jscomp$45_lineObj;
    ch$jscomp$inline_324_pos = ch$jscomp$inline_324_pos.ch;
    JSCompiler_inline_result$jscomp$45_lineObj = measureCharPrepared(cm, prepareMeasureForLine(cm, JSCompiler_inline_result$jscomp$45_lineObj), ch$jscomp$inline_324_pos, bias);
    return intoCoordSystem(cm, JSCompiler_temp_const, JSCompiler_inline_result$jscomp$45_lineObj, context);
  }
  function cursorCoords(cm, pos$jscomp$24_sticky, context, lineObj, preparedMeasure, varHeight) {
    function get(ch$jscomp$23_m, right) {
      ch$jscomp$23_m = measureCharPrepared(cm, preparedMeasure, ch$jscomp$23_m, right ? "right" : "left", varHeight);
      right ? ch$jscomp$23_m.left = ch$jscomp$23_m.right : ch$jscomp$23_m.right = ch$jscomp$23_m.left;
      return intoCoordSystem(cm, lineObj, ch$jscomp$23_m, context);
    }
    function getBidi(ch, partPos, invert) {
      return get(invert ? ch - 1 : ch, 1 == order[partPos].level != invert);
    }
    lineObj = lineObj || getLine(cm.doc, pos$jscomp$24_sticky.line);
    preparedMeasure || (preparedMeasure = prepareMeasureForLine(cm, lineObj));
    var order = getOrder(lineObj, cm.doc.direction), ch$jscomp$0 = pos$jscomp$24_sticky.ch;
    pos$jscomp$24_sticky = pos$jscomp$24_sticky.sticky;
    ch$jscomp$0 >= lineObj.text.length ? (ch$jscomp$0 = lineObj.text.length, pos$jscomp$24_sticky = "before") : 0 >= ch$jscomp$0 && (ch$jscomp$0 = 0, pos$jscomp$24_sticky = "after");
    if (!order) {
      return get("before" == pos$jscomp$24_sticky ? ch$jscomp$0 - 1 : ch$jscomp$0, "before" == pos$jscomp$24_sticky);
    }
    var partPos_val = getBidiPartAt(order, ch$jscomp$0, pos$jscomp$24_sticky), other = bidiOther;
    partPos_val = getBidi(ch$jscomp$0, partPos_val, "before" == pos$jscomp$24_sticky);
    null != other && (partPos_val.other = getBidi(ch$jscomp$0, other, "before" != pos$jscomp$24_sticky));
    return partPos_val;
  }
  function estimateCoords(cm$jscomp$41_top, lineObj$jscomp$5_pos) {
    var left = 0;
    lineObj$jscomp$5_pos = clipPos(cm$jscomp$41_top.doc, lineObj$jscomp$5_pos);
    cm$jscomp$41_top.options.lineWrapping || (left = charWidth(cm$jscomp$41_top.display) * lineObj$jscomp$5_pos.ch);
    lineObj$jscomp$5_pos = getLine(cm$jscomp$41_top.doc, lineObj$jscomp$5_pos.line);
    cm$jscomp$41_top = heightAtLine(lineObj$jscomp$5_pos) + cm$jscomp$41_top.display.lineSpace.offsetTop;
    return {left, right:left, top:cm$jscomp$41_top, bottom:cm$jscomp$41_top + lineObj$jscomp$5_pos.height};
  }
  function PosWithInfo(line$jscomp$65_pos, ch, sticky, outside, xRel) {
    line$jscomp$65_pos = Pos(line$jscomp$65_pos, ch, sticky);
    line$jscomp$65_pos.xRel = xRel;
    outside && (line$jscomp$65_pos.outside = outside);
    return line$jscomp$65_pos;
  }
  function coordsChar(cm, x, y) {
    var doc = cm.doc;
    y += cm.display.viewOffset;
    if (0 > y) {
      return PosWithInfo(doc.first, 0, null, -1, -1);
    }
    var lineN = lineAtHeight(doc, y), found$jscomp$12_last = doc.first + doc.size - 1;
    if (lineN > found$jscomp$12_last) {
      return PosWithInfo(doc.first + doc.size - 1, getLine(doc, found$jscomp$12_last).text.length, null, 1, 1);
    }
    0 > x && (x = 0);
    for (var lineObj$jscomp$6_sps = getLine(doc, lineN);;) {
      found$jscomp$12_last = coordsCharInner(cm, lineObj$jscomp$6_sps, lineN, x, y);
      var collapsed$jscomp$1_found = void 0;
      var ch = found$jscomp$12_last.ch + (0 < found$jscomp$12_last.xRel || 0 < found$jscomp$12_last.outside ? 1 : 0);
      if (lineObj$jscomp$6_sps = sawCollapsedSpans && lineObj$jscomp$6_sps.markedSpans) {
        for (var i = 0; i < lineObj$jscomp$6_sps.length; ++i) {
          var sp = lineObj$jscomp$6_sps[i];
          sp.marker.collapsed && (null == sp.from || sp.from < ch) && (null == sp.to || sp.to > ch) && (!collapsed$jscomp$1_found || 0 > compareCollapsedMarkers(collapsed$jscomp$1_found, sp.marker)) && (collapsed$jscomp$1_found = sp.marker);
        }
      }
      if (!collapsed$jscomp$1_found) {
        return found$jscomp$12_last;
      }
      found$jscomp$12_last = collapsed$jscomp$1_found.find(1);
      if (found$jscomp$12_last.line == lineN) {
        return found$jscomp$12_last;
      }
      lineObj$jscomp$6_sps = getLine(doc, lineN = found$jscomp$12_last.line);
    }
  }
  function wrappedLineExtent(cm, end$jscomp$26_lineObj, preparedMeasure, y) {
    y -= widgetTopHeight(end$jscomp$26_lineObj);
    end$jscomp$26_lineObj = end$jscomp$26_lineObj.text.length;
    var begin = findFirst(function(ch) {
      return measureCharPrepared(cm, preparedMeasure, ch - 1).bottom <= y;
    }, end$jscomp$26_lineObj, 0);
    end$jscomp$26_lineObj = findFirst(function(ch) {
      return measureCharPrepared(cm, preparedMeasure, ch).top > y;
    }, begin, end$jscomp$26_lineObj);
    return {begin, end:end$jscomp$26_lineObj};
  }
  function wrappedLineExtentChar(cm, lineObj, preparedMeasure, target) {
    preparedMeasure || (preparedMeasure = prepareMeasureForLine(cm, lineObj));
    target = intoCoordSystem(cm, lineObj, measureCharPrepared(cm, preparedMeasure, target), "line").top;
    return wrappedLineExtent(cm, lineObj, preparedMeasure, target);
  }
  function boxIsAfter(box, x, y, left) {
    return box.bottom <= y ? !1 : box.top > y ? !0 : (left ? box.left : box.right) > x;
  }
  function coordsCharInner(cm, lineObj, lineNo, x, y) {
    y -= heightAtLine(lineObj);
    var preparedMeasure = prepareMeasureForLine(cm, lineObj), widgetHeight = widgetTopHeight(lineObj), atLeft_baseX_begin = 0, end = lineObj.text.length, atStart_ltr_sticky = !0, ch$jscomp$28_order$jscomp$8_part = getOrder(lineObj, cm.doc.direction);
    ch$jscomp$28_order$jscomp$8_part && (ch$jscomp$28_order$jscomp$8_part = (cm.options.lineWrapping ? coordsBidiPartWrapped : coordsBidiPart)(cm, lineObj, lineNo, preparedMeasure, ch$jscomp$28_order$jscomp$8_part, x, y), atLeft_baseX_begin = (atStart_ltr_sticky = 1 != ch$jscomp$28_order$jscomp$8_part.level) ? ch$jscomp$28_order$jscomp$8_part.from : ch$jscomp$28_order$jscomp$8_part.to - 1, end = atStart_ltr_sticky ? ch$jscomp$28_order$jscomp$8_part.to : ch$jscomp$28_order$jscomp$8_part.from - 1);
    var chAround = null, boxAround = null;
    ch$jscomp$28_order$jscomp$8_part = findFirst(function(ch) {
      var box = measureCharPrepared(cm, preparedMeasure, ch);
      box.top += widgetHeight;
      box.bottom += widgetHeight;
      if (!boxIsAfter(box, x, y, !1)) {
        return !1;
      }
      box.top <= y && box.left <= x && (chAround = ch, boxAround = box);
      return !0;
    }, atLeft_baseX_begin, end);
    var coords$jscomp$1_outside = !1;
    boxAround ? (atLeft_baseX_begin = x - boxAround.left < boxAround.right - x, atStart_ltr_sticky = atLeft_baseX_begin == atStart_ltr_sticky, ch$jscomp$28_order$jscomp$8_part = chAround + (atStart_ltr_sticky ? 0 : 1), atStart_ltr_sticky = atStart_ltr_sticky ? "after" : "before", atLeft_baseX_begin = atLeft_baseX_begin ? boxAround.left : boxAround.right) : (atStart_ltr_sticky || ch$jscomp$28_order$jscomp$8_part != end && ch$jscomp$28_order$jscomp$8_part != atLeft_baseX_begin || ch$jscomp$28_order$jscomp$8_part++, 
    atStart_ltr_sticky = 0 == ch$jscomp$28_order$jscomp$8_part ? "after" : ch$jscomp$28_order$jscomp$8_part == lineObj.text.length ? "before" : measureCharPrepared(cm, preparedMeasure, ch$jscomp$28_order$jscomp$8_part - (atStart_ltr_sticky ? 1 : 0)).bottom + widgetHeight <= y == atStart_ltr_sticky ? "after" : "before", coords$jscomp$1_outside = cursorCoords(cm, Pos(lineNo, ch$jscomp$28_order$jscomp$8_part, atStart_ltr_sticky), "line", lineObj, preparedMeasure), atLeft_baseX_begin = coords$jscomp$1_outside.left, 
    coords$jscomp$1_outside = y < coords$jscomp$1_outside.top ? -1 : y >= coords$jscomp$1_outside.bottom ? 1 : 0);
    ch$jscomp$28_order$jscomp$8_part = skipExtendingChars(lineObj.text, ch$jscomp$28_order$jscomp$8_part, 1);
    return PosWithInfo(lineNo, ch$jscomp$28_order$jscomp$8_part, atStart_ltr_sticky, coords$jscomp$1_outside, x - atLeft_baseX_begin);
  }
  function coordsBidiPart(cm, lineObj, lineNo, preparedMeasure, order, x, y) {
    var index = findFirst(function(i$jscomp$174_part) {
      i$jscomp$174_part = order[i$jscomp$174_part];
      var ltr = 1 != i$jscomp$174_part.level;
      return boxIsAfter(cursorCoords(cm, Pos(lineNo, ltr ? i$jscomp$174_part.to : i$jscomp$174_part.from, ltr ? "before" : "after"), "line", lineObj, preparedMeasure), x, y, !0);
    }, 0, order.length - 1), part = order[index];
    if (0 < index) {
      var ltr$jscomp$1_start = 1 != part.level;
      ltr$jscomp$1_start = cursorCoords(cm, Pos(lineNo, ltr$jscomp$1_start ? part.from : part.to, ltr$jscomp$1_start ? "after" : "before"), "line", lineObj, preparedMeasure);
      boxIsAfter(ltr$jscomp$1_start, x, y, !0) && ltr$jscomp$1_start.top > y && (part = order[index - 1]);
    }
    return part;
  }
  function coordsBidiPartWrapped(cm, lineObj$jscomp$11_part, _lineNo, preparedMeasure, order, x, begin$jscomp$8_y) {
    var end$jscomp$28_ref = wrappedLineExtent(cm, lineObj$jscomp$11_part, preparedMeasure, begin$jscomp$8_y);
    begin$jscomp$8_y = end$jscomp$28_ref.begin;
    end$jscomp$28_ref = end$jscomp$28_ref.end;
    /\s/.test(lineObj$jscomp$11_part.text.charAt(end$jscomp$28_ref - 1)) && end$jscomp$28_ref--;
    for (var closestDist = lineObj$jscomp$11_part = null, i = 0; i < order.length; i++) {
      var p = order[i];
      if (!(p.from >= end$jscomp$28_ref || p.to <= begin$jscomp$8_y)) {
        var dist_endX = measureCharPrepared(cm, preparedMeasure, 1 != p.level ? Math.min(end$jscomp$28_ref, p.to) - 1 : Math.max(begin$jscomp$8_y, p.from)).right;
        dist_endX = dist_endX < x ? x - dist_endX + 1E9 : dist_endX - x;
        if (!lineObj$jscomp$11_part || closestDist > dist_endX) {
          lineObj$jscomp$11_part = p, closestDist = dist_endX;
        }
      }
    }
    lineObj$jscomp$11_part || (lineObj$jscomp$11_part = order[order.length - 1]);
    lineObj$jscomp$11_part.from < begin$jscomp$8_y && (lineObj$jscomp$11_part = {from:begin$jscomp$8_y, to:lineObj$jscomp$11_part.to, level:lineObj$jscomp$11_part.level});
    lineObj$jscomp$11_part.to > end$jscomp$28_ref && (lineObj$jscomp$11_part = {from:lineObj$jscomp$11_part.from, to:end$jscomp$28_ref, level:lineObj$jscomp$11_part.level});
    return lineObj$jscomp$11_part;
  }
  function textHeight(display) {
    if (null != display.cachedTextHeight) {
      return display.cachedTextHeight;
    }
    if (null == measureText) {
      measureText = elt$jscomp$0("pre", null, "CodeMirror-line-like");
      for (var height$jscomp$30_i = 0; 49 > height$jscomp$30_i; ++height$jscomp$30_i) {
        measureText.appendChild(document.createTextNode("x")), measureText.appendChild(elt$jscomp$0("br"));
      }
      measureText.appendChild(document.createTextNode("x"));
    }
    removeChildrenAndAdd(display.measure, measureText);
    height$jscomp$30_i = measureText.offsetHeight / 50;
    3 < height$jscomp$30_i && (display.cachedTextHeight = height$jscomp$30_i);
    removeChildren(display.measure);
    return height$jscomp$30_i || 1;
  }
  function charWidth(display) {
    if (null != display.cachedCharWidth) {
      return display.cachedCharWidth;
    }
    var anchor$jscomp$2_rect$jscomp$5_width = elt$jscomp$0("span", "xxxxxxxxxx"), pre = elt$jscomp$0("pre", [anchor$jscomp$2_rect$jscomp$5_width], "CodeMirror-line-like");
    removeChildrenAndAdd(display.measure, pre);
    anchor$jscomp$2_rect$jscomp$5_width = anchor$jscomp$2_rect$jscomp$5_width.getBoundingClientRect();
    anchor$jscomp$2_rect$jscomp$5_width = (anchor$jscomp$2_rect$jscomp$5_width.right - anchor$jscomp$2_rect$jscomp$5_width.left) / 10;
    2 < anchor$jscomp$2_rect$jscomp$5_width && (display.cachedCharWidth = anchor$jscomp$2_rect$jscomp$5_width);
    return anchor$jscomp$2_rect$jscomp$5_width || 10;
  }
  function getDimensions(cm) {
    for (var d = cm.display, left = {}, width = {}, gutterLeft = d.gutters.clientLeft, n = d.gutters.firstChild, i = 0; n; n = n.nextSibling, ++i) {
      var id = cm.display.gutterSpecs[i].className;
      left[id] = n.offsetLeft + n.clientLeft + gutterLeft;
      width[id] = n.clientWidth;
    }
    return {fixedPos:compensateForHScroll(d), gutterTotalWidth:d.gutters.offsetWidth, gutterLeft:left, gutterWidth:width, wrapperWidth:d.wrapper.clientWidth};
  }
  function compensateForHScroll(display) {
    return display.scroller.getBoundingClientRect().left - display.sizer.getBoundingClientRect().left;
  }
  function estimateHeight(cm) {
    var th = textHeight(cm.display), wrapping = cm.options.lineWrapping, perLine = wrapping && Math.max(5, cm.display.scroller.clientWidth / charWidth(cm.display) - 3);
    return function(line) {
      if (lineIsHidden(cm.doc, line)) {
        return 0;
      }
      var widgetsHeight = 0;
      if (line.widgets) {
        for (var i = 0; i < line.widgets.length; i++) {
          line.widgets[i].height && (widgetsHeight += line.widgets[i].height);
        }
      }
      return wrapping ? widgetsHeight + (Math.ceil(line.text.length / perLine) || 1) * th : widgetsHeight + th;
    };
  }
  function estimateLineHeights(cm) {
    var doc = cm.doc, est = estimateHeight(cm);
    doc.iter(function(line) {
      var estHeight = est(line);
      estHeight != line.height && updateLineHeight(line, estHeight);
    });
  }
  function posFromMouse(cm, coords$jscomp$2_e, liberal_space, colDiff_forRect) {
    var display = cm.display;
    if (!liberal_space && "true" == (coords$jscomp$2_e.target || coords$jscomp$2_e.srcElement).getAttribute("cm-not-content")) {
      return null;
    }
    liberal_space = display.lineSpace.getBoundingClientRect();
    try {
      var x = coords$jscomp$2_e.clientX - liberal_space.left;
      var y = coords$jscomp$2_e.clientY - liberal_space.top;
    } catch (e$1) {
      return null;
    }
    coords$jscomp$2_e = coordsChar(cm, x, y);
    var line;
    colDiff_forRect && 0 < coords$jscomp$2_e.xRel && (line = getLine(cm.doc, coords$jscomp$2_e.line).text).length == coords$jscomp$2_e.ch && (colDiff_forRect = countColumn(line, line.length, cm.options.tabSize) - line.length, coords$jscomp$2_e = Pos(coords$jscomp$2_e.line, Math.max(0, Math.round((x - paddingH(cm.display).left) / charWidth(cm.display)) - colDiff_forRect)));
    return coords$jscomp$2_e;
  }
  function findViewIndex(cm$jscomp$52_view, n) {
    if (n >= cm$jscomp$52_view.display.viewTo) {
      return null;
    }
    n -= cm$jscomp$52_view.display.viewFrom;
    if (0 > n) {
      return null;
    }
    cm$jscomp$52_view = cm$jscomp$52_view.display.view;
    for (var i = 0; i < cm$jscomp$52_view.length; i++) {
      if (n -= cm$jscomp$52_view[i].size, 0 > n) {
        return i;
      }
    }
  }
  function regChange(cm$jscomp$53_ext, from, to, lendiff) {
    null == from && (from = cm$jscomp$53_ext.doc.first);
    null == to && (to = cm$jscomp$53_ext.doc.first + cm$jscomp$53_ext.doc.size);
    lendiff || (lendiff = 0);
    var display = cm$jscomp$53_ext.display;
    lendiff && to < display.viewTo && (null == display.updateLineNumbers || display.updateLineNumbers > from) && (display.updateLineNumbers = from);
    cm$jscomp$53_ext.curOp.viewChanged = !0;
    if (from >= display.viewTo) {
      sawCollapsedSpans && visualLineNo(cm$jscomp$53_ext.doc, from) < display.viewTo && resetView(cm$jscomp$53_ext);
    } else if (to <= display.viewFrom) {
      sawCollapsedSpans && visualLineEndNo(cm$jscomp$53_ext.doc, to + lendiff) > display.viewFrom ? resetView(cm$jscomp$53_ext) : (display.viewFrom += lendiff, display.viewTo += lendiff);
    } else if (from <= display.viewFrom && to >= display.viewTo) {
      resetView(cm$jscomp$53_ext);
    } else if (from <= display.viewFrom) {
      var cut_cut$1_cutTop = viewCuttingPoint(cm$jscomp$53_ext, to, to + lendiff, 1);
      cut_cut$1_cutTop ? (display.view = display.view.slice(cut_cut$1_cutTop.index), display.viewFrom = cut_cut$1_cutTop.lineN, display.viewTo += lendiff) : resetView(cm$jscomp$53_ext);
    } else if (to >= display.viewTo) {
      (cut_cut$1_cutTop = viewCuttingPoint(cm$jscomp$53_ext, from, from, -1)) ? (display.view = display.view.slice(0, cut_cut$1_cutTop.index), display.viewTo = cut_cut$1_cutTop.lineN) : resetView(cm$jscomp$53_ext);
    } else {
      cut_cut$1_cutTop = viewCuttingPoint(cm$jscomp$53_ext, from, from, -1);
      var cutBot = viewCuttingPoint(cm$jscomp$53_ext, to, to + lendiff, 1);
      cut_cut$1_cutTop && cutBot ? (display.view = display.view.slice(0, cut_cut$1_cutTop.index).concat(buildViewArray(cm$jscomp$53_ext, cut_cut$1_cutTop.lineN, cutBot.lineN)).concat(display.view.slice(cutBot.index)), display.viewTo += lendiff) : resetView(cm$jscomp$53_ext);
    }
    if (cm$jscomp$53_ext = display.externalMeasured) {
      to < cm$jscomp$53_ext.lineN ? cm$jscomp$53_ext.lineN += lendiff : from < cm$jscomp$53_ext.lineN + cm$jscomp$53_ext.size && (display.externalMeasured = null);
    }
  }
  function regLineChange(arr$jscomp$60_cm$jscomp$54_lineView, line, type) {
    arr$jscomp$60_cm$jscomp$54_lineView.curOp.viewChanged = !0;
    var display = arr$jscomp$60_cm$jscomp$54_lineView.display, ext = arr$jscomp$60_cm$jscomp$54_lineView.display.externalMeasured;
    ext && line >= ext.lineN && line < ext.lineN + ext.size && (display.externalMeasured = null);
    line < display.viewFrom || line >= display.viewTo || (arr$jscomp$60_cm$jscomp$54_lineView = display.view[findViewIndex(arr$jscomp$60_cm$jscomp$54_lineView, line)], null != arr$jscomp$60_cm$jscomp$54_lineView.node && (arr$jscomp$60_cm$jscomp$54_lineView = arr$jscomp$60_cm$jscomp$54_lineView.changes || (arr$jscomp$60_cm$jscomp$54_lineView.changes = []), -1 == indexOf(arr$jscomp$60_cm$jscomp$54_lineView, type) && arr$jscomp$60_cm$jscomp$54_lineView.push(type)));
  }
  function resetView(cm) {
    cm.display.viewFrom = cm.display.viewTo = cm.doc.first;
    cm.display.view = [];
    cm.display.viewOffset = 0;
  }
  function viewCuttingPoint(cm, diff, newN, dir) {
    var index = findViewIndex(cm, diff), view = cm.display.view;
    if (!sawCollapsedSpans || newN == cm.doc.first + cm.doc.size) {
      return {index, lineN:newN};
    }
    for (var n = cm.display.viewFrom, i = 0; i < index; i++) {
      n += view[i].size;
    }
    if (n != diff) {
      if (0 < dir) {
        if (index == view.length - 1) {
          return null;
        }
        diff = n + view[index].size - diff;
        index++;
      } else {
        diff = n - diff;
      }
      newN += diff;
    }
    for (; visualLineNo(cm.doc, newN) != newN;) {
      if (index == (0 > dir ? 0 : view.length - 1)) {
        return null;
      }
      newN += dir * view[index - (0 > dir ? 1 : 0)].size;
      index += dir;
    }
    return {index, lineN:newN};
  }
  function countDirtyView(cm$jscomp$58_view) {
    cm$jscomp$58_view = cm$jscomp$58_view.display.view;
    for (var dirty = 0, i = 0; i < cm$jscomp$58_view.length; i++) {
      var lineView = cm$jscomp$58_view[i];
      lineView.hidden || lineView.node && !lineView.changes || ++dirty;
    }
    return dirty;
  }
  function updateSelection(cm) {
    cm.display.input.showSelection(cm.display.input.prepareSelection());
  }
  function prepareSelection(cm, primary) {
    void 0 === primary && (primary = !0);
    var doc = cm.doc, result = {}, curFragment = result.cursors = document.createDocumentFragment(), selFragment = result.selection = document.createDocumentFragment(), customCursor = cm.options.$customCursor;
    customCursor && (primary = !0);
    for (var i = 0; i < doc.sel.ranges.length; i++) {
      if (primary || i != doc.sel.primIndex) {
        var range = doc.sel.ranges[i];
        if (!(range.from().line >= cm.display.viewTo || range.to().line < cm.display.viewFrom)) {
          var collapsed = range.empty();
          if (customCursor) {
            var head = customCursor(cm, range);
            head && drawSelectionCursor(cm, head, curFragment);
          } else {
            (collapsed || cm.options.showCursorWhenSelecting) && drawSelectionCursor(cm, range.head, curFragment);
          }
          collapsed || drawSelectionRange(cm, range, selFragment);
        }
      }
    }
    return result;
  }
  function drawSelectionCursor(cm, charPos_head$jscomp$2_width, output) {
    var pos = cursorCoords(cm, charPos_head$jscomp$2_width, "div", null, null, !cm.options.singleCursorHeightPerLine), cursor = output.appendChild(elt$jscomp$0("div", "\u00a0", "CodeMirror-cursor"));
    cursor.style.left = pos.left + "px";
    cursor.style.top = pos.top + "px";
    cursor.style.height = Math.max(0, pos.bottom - pos.top) * cm.options.cursorHeight + "px";
    /\bcm-fat-cursor\b/.test(cm.getWrapperElement().className) && (charPos_head$jscomp$2_width = charCoords(cm, charPos_head$jscomp$2_width, "div", null, null), charPos_head$jscomp$2_width = charPos_head$jscomp$2_width.right - charPos_head$jscomp$2_width.left, cursor.style.width = (0 < charPos_head$jscomp$2_width ? charPos_head$jscomp$2_width : cm.defaultCharWidth()) + "px");
    pos.other && (cm = output.appendChild(elt$jscomp$0("div", "\u00a0", "CodeMirror-cursor CodeMirror-secondarycursor")), cm.style.display = "", cm.style.left = pos.other.left + "px", cm.style.top = pos.other.top + "px", cm.style.height = .85 * (pos.other.bottom - pos.other.top) + "px");
  }
  function cmpCoords(a, b) {
    return a.top - b.top || a.left - b.left;
  }
  function drawSelectionRange(cm, range, output) {
    function add(left, top, width, bottom) {
      0 > top && (top = 0);
      top = Math.round(top);
      bottom = Math.round(bottom);
      fragment.appendChild(elt$jscomp$0("div", null, "CodeMirror-selected", "position: absolute; left: " + left + "px;\n                             top: " + top + "px; width: " + (null == width ? rightSide - left : width) + "px;\n                             height: " + (bottom - top) + "px"));
    }
    function drawForLine(line, fromArg, toArg) {
      function coords(ch, bias) {
        return charCoords(cm, Pos(line, ch), "div", lineObj, bias);
      }
      function wrapX(extent_pos, dir$jscomp$4_prop, side) {
        extent_pos = wrappedLineExtentChar(cm, lineObj, null, extent_pos);
        dir$jscomp$4_prop = "ltr" == dir$jscomp$4_prop == ("after" == side) ? "left" : "right";
        return coords("after" == side ? extent_pos.begin : extent_pos.end - (/\s/.test(lineObj.text.charAt(extent_pos.end - 1)) ? 2 : 1), dir$jscomp$4_prop)[dir$jscomp$4_prop];
      }
      var lineObj = getLine(doc, line), lineLen = lineObj.text.length, start, end, order = getOrder(lineObj, doc.direction);
      iterateBidiSections(order, fromArg || 0, null == toArg ? lineLen : toArg, function(botLeft_from, left$jscomp$9_to, dir, i$jscomp$183_last) {
        var ltr = "ltr" == dir, fromPos = coords(botLeft_from, ltr ? "left" : "right"), toPos = coords(left$jscomp$9_to - 1, ltr ? "right" : "left"), openStart_topRight = null == fromArg && 0 == botLeft_from, botRight_openEnd = null == toArg && left$jscomp$9_to == lineLen, first = 0 == i$jscomp$183_last;
        i$jscomp$183_last = !order || i$jscomp$183_last == order.length - 1;
        3 >= toPos.top - fromPos.top ? (left$jscomp$9_to = (docLTR ? openStart_topRight : botRight_openEnd) && first ? leftSide : (ltr ? fromPos : toPos).left, add(left$jscomp$9_to, fromPos.top, ((docLTR ? botRight_openEnd : openStart_topRight) && i$jscomp$183_last ? rightSide : (ltr ? toPos : fromPos).right) - left$jscomp$9_to, fromPos.bottom)) : (ltr ? (ltr = docLTR && openStart_topRight && first ? leftSide : fromPos.left, openStart_topRight = docLTR ? rightSide : wrapX(botLeft_from, dir, "before"), 
        botLeft_from = docLTR ? leftSide : wrapX(left$jscomp$9_to, dir, "after"), botRight_openEnd = docLTR && botRight_openEnd && i$jscomp$183_last ? rightSide : toPos.right) : (ltr = docLTR ? wrapX(botLeft_from, dir, "before") : leftSide, openStart_topRight = !docLTR && openStart_topRight && first ? rightSide : fromPos.right, botLeft_from = !docLTR && botRight_openEnd && i$jscomp$183_last ? leftSide : toPos.left, botRight_openEnd = docLTR ? wrapX(left$jscomp$9_to, dir, "after") : rightSide), add(ltr, 
        fromPos.top, openStart_topRight - ltr, fromPos.bottom), fromPos.bottom < toPos.top && add(leftSide, fromPos.bottom, null, toPos.top), add(botLeft_from, toPos.top, botRight_openEnd - botLeft_from, toPos.bottom));
        if (!start || 0 > cmpCoords(fromPos, start)) {
          start = fromPos;
        }
        0 > cmpCoords(toPos, start) && (start = toPos);
        if (!end || 0 > cmpCoords(fromPos, end)) {
          end = fromPos;
        }
        0 > cmpCoords(toPos, end) && (end = toPos);
      });
      return {start, end};
    }
    var display = cm.display, doc = cm.doc, fragment = document.createDocumentFragment(), padding_singleVLine_toLine = paddingH(cm.display), leftSide = padding_singleVLine_toLine.left, rightSide = Math.max(display.sizerWidth, displayWidth(cm) - display.sizer.offsetLeft) - padding_singleVLine_toLine.right, docLTR = "ltr" == doc.direction;
    display = range.from();
    range = range.to();
    if (display.line == range.line) {
      drawForLine(display.line, display.ch, range.ch);
    } else {
      var fromLine = getLine(doc, display.line);
      padding_singleVLine_toLine = getLine(doc, range.line);
      padding_singleVLine_toLine = visualLine(fromLine) == visualLine(padding_singleVLine_toLine);
      display = drawForLine(display.line, display.ch, padding_singleVLine_toLine ? fromLine.text.length + 1 : null).end;
      range = drawForLine(range.line, padding_singleVLine_toLine ? 0 : null, range.ch).start;
      padding_singleVLine_toLine && (display.top < range.top - 2 ? (add(display.right, display.top, null, display.bottom), add(leftSide, range.top, range.left, range.bottom)) : add(display.right, display.top, range.left - display.right, display.bottom));
      display.bottom < range.top && add(leftSide, display.bottom, null, range.top);
    }
    output.appendChild(fragment);
  }
  function restartBlink(cm) {
    if (cm.state.focused) {
      var display = cm.display;
      clearInterval(display.blinker);
      var on = !0;
      display.cursorDiv.style.visibility = "";
      0 < cm.options.cursorBlinkRate ? display.blinker = setInterval(function() {
        cm.hasFocus() || onBlur(cm);
        display.cursorDiv.style.visibility = (on = !on) ? "" : "hidden";
      }, cm.options.cursorBlinkRate) : 0 > cm.options.cursorBlinkRate && (display.cursorDiv.style.visibility = "hidden");
    }
  }
  function ensureFocus(cm) {
    cm.hasFocus() || (cm.display.input.focus(), cm.state.focused || onFocus(cm));
  }
  function delayBlurEvent(cm) {
    cm.state.delayingBlurEvent = !0;
    setTimeout(function() {
      cm.state.delayingBlurEvent && (cm.state.delayingBlurEvent = !1, cm.state.focused && onBlur(cm));
    }, 100);
  }
  function onFocus(cm, e) {
    cm.state.delayingBlurEvent && !cm.state.draggingText && (cm.state.delayingBlurEvent = !1);
    "nocursor" != cm.options.readOnly && (cm.state.focused || (signal(cm, "focus", cm, e), cm.state.focused = !0, addClass(cm.display.wrapper, "CodeMirror-focused"), cm.curOp || cm.display.selForContextMenu == cm.doc.sel || (cm.display.input.reset(), webkit && setTimeout(function() {
      return cm.display.input.reset(!0);
    }, 20)), cm.display.input.receivedFocus()), restartBlink(cm));
  }
  function onBlur(cm, e) {
    cm.state.delayingBlurEvent || (cm.state.focused && (signal(cm, "blur", cm, e), cm.state.focused = !1, rmClass(cm.display.wrapper, "CodeMirror-focused")), clearInterval(cm.display.blinker), setTimeout(function() {
      cm.state.focused || (cm.display.shift = !1);
    }, 150));
  }
  function updateHeightsInViewport(cm) {
    for (var display = cm.display, prevBottom = display.lineDiv.offsetTop, viewTop = Math.max(0, display.scroller.getBoundingClientRect().top), oldHeight = display.lineDiv.getBoundingClientRect().top, mustScroll = 0, i = 0; i < display.view.length; i++) {
      var cur = display.view[i], bot$jscomp$1_diff$jscomp$2_wrapping = cm.options.lineWrapping, chWidth_width = 0;
      if (!cur.hidden) {
        oldHeight += cur.line.height;
        if (ie && 8 > ie_version) {
          bot$jscomp$1_diff$jscomp$2_wrapping = cur.node.offsetTop + cur.node.offsetHeight;
          var height$jscomp$31_j = bot$jscomp$1_diff$jscomp$2_wrapping - prevBottom;
          prevBottom = bot$jscomp$1_diff$jscomp$2_wrapping;
        } else {
          var box = cur.node.getBoundingClientRect();
          height$jscomp$31_j = box.bottom - box.top;
          !bot$jscomp$1_diff$jscomp$2_wrapping && cur.text.firstChild && (chWidth_width = cur.text.firstChild.getBoundingClientRect().right - box.left - 1);
        }
        bot$jscomp$1_diff$jscomp$2_wrapping = cur.line.height - height$jscomp$31_j;
        if (.005 < bot$jscomp$1_diff$jscomp$2_wrapping || -.005 > bot$jscomp$1_diff$jscomp$2_wrapping) {
          if (oldHeight < viewTop && (mustScroll -= bot$jscomp$1_diff$jscomp$2_wrapping), updateLineHeight(cur.line, height$jscomp$31_j), updateWidgetHeight(cur.line), cur.rest) {
            for (height$jscomp$31_j = 0; height$jscomp$31_j < cur.rest.length; height$jscomp$31_j++) {
              updateWidgetHeight(cur.rest[height$jscomp$31_j]);
            }
          }
        }
        chWidth_width > cm.display.sizerWidth && (chWidth_width = Math.ceil(chWidth_width / charWidth(cm.display)), chWidth_width > cm.display.maxLineLength && (cm.display.maxLineLength = chWidth_width, cm.display.maxLine = cur.line, cm.display.maxLineChanged = !0));
      }
    }
    2 < Math.abs(mustScroll) && (display.scroller.scrollTop += mustScroll);
  }
  function updateWidgetHeight(line) {
    if (line.widgets) {
      for (var i = 0; i < line.widgets.length; ++i) {
        var w = line.widgets[i], parent = w.node.parentNode;
        parent && (w.height = parent.offsetHeight);
      }
    }
  }
  function visibleLines(display, doc, ensureTo_viewport) {
    var from$jscomp$19_top = ensureTo_viewport && null != ensureTo_viewport.top ? Math.max(0, ensureTo_viewport.top) : display.scroller.scrollTop;
    from$jscomp$19_top = Math.floor(from$jscomp$19_top - display.lineSpace.offsetTop);
    var bottom$jscomp$3_to = ensureTo_viewport && null != ensureTo_viewport.bottom ? ensureTo_viewport.bottom : from$jscomp$19_top + display.wrapper.clientHeight;
    from$jscomp$19_top = lineAtHeight(doc, from$jscomp$19_top);
    bottom$jscomp$3_to = lineAtHeight(doc, bottom$jscomp$3_to);
    if (ensureTo_viewport && ensureTo_viewport.ensure) {
      var ensureFrom = ensureTo_viewport.ensure.from.line;
      ensureTo_viewport = ensureTo_viewport.ensure.to.line;
      ensureFrom < from$jscomp$19_top ? (from$jscomp$19_top = ensureFrom, bottom$jscomp$3_to = lineAtHeight(doc, heightAtLine(getLine(doc, ensureFrom)) + display.wrapper.clientHeight)) : Math.min(ensureTo_viewport, doc.lastLine()) >= bottom$jscomp$3_to && (from$jscomp$19_top = lineAtHeight(doc, heightAtLine(getLine(doc, ensureTo_viewport)) - display.wrapper.clientHeight), bottom$jscomp$3_to = ensureTo_viewport);
    }
    return {from:from$jscomp$19_top, to:Math.max(bottom$jscomp$3_to, from$jscomp$19_top + 1)};
  }
  function calculateScrollPos(cm, rect) {
    var display = cm.display, atBottom_snapMargin = textHeight(cm.display);
    0 > rect.top && (rect.top = 0);
    var gutterSpace_screentop = cm.curOp && null != cm.curOp.scrollTop ? cm.curOp.scrollTop : display.scroller.scrollTop, newTop_screen = displayHeight(cm), result = {};
    rect.bottom - rect.top > newTop_screen && (rect.bottom = rect.top + newTop_screen);
    var docBottom = cm.doc.height + paddingVert(display), atTop = rect.top < atBottom_snapMargin;
    atBottom_snapMargin = rect.bottom > docBottom - atBottom_snapMargin;
    rect.top < gutterSpace_screentop ? result.scrollTop = atTop ? 0 : rect.top : rect.bottom > gutterSpace_screentop + newTop_screen && (newTop_screen = Math.min(rect.top, (atBottom_snapMargin ? docBottom : rect.bottom) - newTop_screen), newTop_screen != gutterSpace_screentop && (result.scrollTop = newTop_screen));
    gutterSpace_screentop = cm.options.fixedGutter ? 0 : display.gutters.offsetWidth;
    newTop_screen = cm.curOp && null != cm.curOp.scrollLeft ? cm.curOp.scrollLeft : display.scroller.scrollLeft - gutterSpace_screentop;
    cm = displayWidth(cm) - display.gutters.offsetWidth;
    if (display = rect.right - rect.left > cm) {
      rect.right = rect.left + cm;
    }
    10 > rect.left ? result.scrollLeft = 0 : rect.left < newTop_screen ? result.scrollLeft = Math.max(0, rect.left + gutterSpace_screentop - (display ? 0 : 10)) : rect.right > cm + newTop_screen - 3 && (result.scrollLeft = rect.right + (display ? 0 : 10) - cm);
    return result;
  }
  function addToScrollTop(cm, top) {
    null != top && (resolveScrollToPos(cm), cm.curOp.scrollTop = (null == cm.curOp.scrollTop ? cm.doc.scrollTop : cm.curOp.scrollTop) + top);
  }
  function ensureCursorVisible(cm) {
    resolveScrollToPos(cm);
    var cur = cm.getCursor();
    cm.curOp.scrollToPos = {from:cur, to:cur, margin:cm.options.cursorScrollMargin};
  }
  function scrollToCoords(cm, x, y) {
    null == x && null == y || resolveScrollToPos(cm);
    null != x && (cm.curOp.scrollLeft = x);
    null != y && (cm.curOp.scrollTop = y);
  }
  function resolveScrollToPos(cm) {
    var range = cm.curOp.scrollToPos;
    if (range) {
      cm.curOp.scrollToPos = null;
      var from = estimateCoords(cm, range.from), to = estimateCoords(cm, range.to);
      scrollToCoordsRange(cm, from, to, range.margin);
    }
  }
  function scrollToCoordsRange(cm, from, to, margin) {
    from = calculateScrollPos(cm, {left:Math.min(from.left, to.left), top:Math.min(from.top, to.top) - margin, right:Math.max(from.right, to.right), bottom:Math.max(from.bottom, to.bottom) + margin});
    scrollToCoords(cm, from.scrollLeft, from.scrollTop);
  }
  function updateScrollTop(cm, val) {
    2 > Math.abs(cm.doc.scrollTop - val) || (gecko || updateDisplaySimple(cm, {top:val}), setScrollTop(cm, val, !0), gecko && updateDisplaySimple(cm), startWorker(cm, 100));
  }
  function setScrollTop(cm, val, forceScroll) {
    val = Math.max(0, Math.min(cm.display.scroller.scrollHeight - cm.display.scroller.clientHeight, val));
    if (cm.display.scroller.scrollTop != val || forceScroll) {
      cm.doc.scrollTop = val, cm.display.scrollbars.setScrollTop(val), cm.display.scroller.scrollTop != val && (cm.display.scroller.scrollTop = val);
    }
  }
  function setScrollLeft(cm, val, isScroller, forceScroll) {
    val = Math.max(0, Math.min(val, cm.display.scroller.scrollWidth - cm.display.scroller.clientWidth));
    (isScroller ? val == cm.doc.scrollLeft : 2 > Math.abs(cm.doc.scrollLeft - val)) && !forceScroll || (cm.doc.scrollLeft = val, alignHorizontally(cm), cm.display.scroller.scrollLeft != val && (cm.display.scroller.scrollLeft = val), cm.display.scrollbars.setScrollLeft(val));
  }
  function measureForScrollbars(cm) {
    var d = cm.display, gutterW = d.gutters.offsetWidth, docH = Math.round(cm.doc.height + paddingVert(cm.display));
    return {clientHeight:d.scroller.clientHeight, viewHeight:d.wrapper.clientHeight, scrollWidth:d.scroller.scrollWidth, clientWidth:d.scroller.clientWidth, viewWidth:d.wrapper.clientWidth, barLeft:cm.options.fixedGutter ? gutterW : 0, docHeight:docH, scrollHeight:docH + scrollGap(cm) + d.barHeight, nativeBarWidth:d.nativeBarWidth, gutterWidth:gutterW};
  }
  function updateScrollbars(cm, i$jscomp$186_measure) {
    i$jscomp$186_measure || (i$jscomp$186_measure = measureForScrollbars(cm));
    var startWidth = cm.display.barWidth, startHeight = cm.display.barHeight;
    updateScrollbarsInner(cm, i$jscomp$186_measure);
    for (i$jscomp$186_measure = 0; 4 > i$jscomp$186_measure && startWidth != cm.display.barWidth || startHeight != cm.display.barHeight; i$jscomp$186_measure++) {
      startWidth != cm.display.barWidth && cm.options.lineWrapping && updateHeightsInViewport(cm), updateScrollbarsInner(cm, measureForScrollbars(cm)), startWidth = cm.display.barWidth, startHeight = cm.display.barHeight;
    }
  }
  function updateScrollbarsInner(cm, measure) {
    var d = cm.display, sizes = d.scrollbars.update(measure);
    d.sizer.style.paddingRight = (d.barWidth = sizes.right) + "px";
    d.sizer.style.paddingBottom = (d.barHeight = sizes.bottom) + "px";
    d.heightForcer.style.borderBottom = sizes.bottom + "px solid transparent";
    sizes.right && sizes.bottom ? (d.scrollbarFiller.style.display = "block", d.scrollbarFiller.style.height = sizes.bottom + "px", d.scrollbarFiller.style.width = sizes.right + "px") : d.scrollbarFiller.style.display = "";
    sizes.bottom && cm.options.coverGutterNextToScrollbar && cm.options.fixedGutter ? (d.gutterFiller.style.display = "block", d.gutterFiller.style.height = sizes.bottom + "px", d.gutterFiller.style.width = measure.gutterWidth + "px") : d.gutterFiller.style.display = "";
  }
  function initScrollbars(cm) {
    cm.display.scrollbars && (cm.display.scrollbars.clear(), cm.display.scrollbars.addClass && rmClass(cm.display.wrapper, cm.display.scrollbars.addClass));
    cm.display.scrollbars = new scrollbarModel[cm.options.scrollbarStyle](function(node) {
      cm.display.wrapper.insertBefore(node, cm.display.scrollbarFiller);
      on(node, "mousedown", function() {
        cm.state.focused && setTimeout(function() {
          return cm.display.input.focus();
        }, 0);
      });
      node.setAttribute("cm-not-content", "true");
    }, function(pos, axis) {
      "horizontal" == axis ? setScrollLeft(cm, pos) : updateScrollTop(cm, pos);
    }, cm);
    cm.display.scrollbars.addClass && addClass(cm.display.wrapper, cm.display.scrollbars.addClass);
  }
  function startOperation(cm$jscomp$87_op) {
    cm$jscomp$87_op.curOp = {cm:cm$jscomp$87_op, viewChanged:!1, startHeight:cm$jscomp$87_op.doc.height, forceUpdate:!1, updateInput:0, typing:!1, changeObjs:null, cursorActivityHandlers:null, cursorActivityCalled:0, selectionChanged:!1, updateMaxLine:!1, scrollLeft:null, scrollTop:null, scrollToPos:null, focus:!1, id:++nextOpId, markArrays:null};
    cm$jscomp$87_op = cm$jscomp$87_op.curOp;
    operationGroup ? operationGroup.ops.push(cm$jscomp$87_op) : cm$jscomp$87_op.ownsGroup = operationGroup = {ops:[cm$jscomp$87_op], delayedCallbacks:[]};
  }
  function endOperation(cm$jscomp$88_op) {
    (cm$jscomp$88_op = cm$jscomp$88_op.curOp) && finishOperation(cm$jscomp$88_op, function(group$jscomp$2_ops) {
      for (var i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i = 0; i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i < group$jscomp$2_ops.ops.length; i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i++) {
        group$jscomp$2_ops.ops[i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i].cm.curOp = null;
      }
      group$jscomp$2_ops = group$jscomp$2_ops.ops;
      for (i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i = 0; i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i < group$jscomp$2_ops.length; i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i++) {
        var op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op = group$jscomp$2_ops[i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i], cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm = op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.cm, display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus = cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display, 
        JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc = cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display;
        !JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc.scrollbarsClipped && JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc.scroller.offsetWidth && (JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc.nativeBarWidth = JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc.scroller.offsetWidth - JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc.scroller.clientWidth, 
        JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc.heightForcer.style.height = scrollGap(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm) + "px", JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc.sizer.style.marginBottom = -JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc.nativeBarWidth + "px", JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc.sizer.style.borderRightWidth = 
        scrollGap(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm) + "px", JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc.scrollbarsClipped = !0);
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.updateMaxLine && findMaxLine(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm);
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.mustUpdate = op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.viewChanged || op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.forceUpdate || null != op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollTop || op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollToPos && 
        (op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollToPos.from.line < display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus.viewFrom || op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollToPos.to.line >= display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus.viewTo) || display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus.maxLineChanged && 
        cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.options.lineWrapping;
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.update = op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.mustUpdate && new DisplayUpdate(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.mustUpdate && {top:op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollTop, ensure:op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollToPos}, 
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.forceUpdate);
      }
      for (i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i = 0; i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i < group$jscomp$2_ops.length; i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i++) {
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op = group$jscomp$2_ops[i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i], op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.updatedDisplay = op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.mustUpdate && updateDisplayIfNeeded(op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.cm, 
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.update);
      }
      for (i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i = 0; i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i < group$jscomp$2_ops.length; i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i++) {
        if (op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op = group$jscomp$2_ops[i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i], cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm = op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.cm, display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus = cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display, 
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.updatedDisplay && updateHeightsInViewport(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm), op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.barMeasure = measureForScrollbars(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm), display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus.maxLineChanged && !cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.options.lineWrapping && 
        (JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc = display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus.maxLine.text.length, JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc = measureCharPrepared(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, prepareMeasureForLine(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus.maxLine), 
        JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc, void 0), op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.adjustWidthTo = JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc.left + 3, cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display.sizerWidth = op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.adjustWidthTo, 
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.barMeasure.scrollWidth = Math.max(display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus.scroller.clientWidth, display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus.sizer.offsetLeft + op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.adjustWidthTo + scrollGap(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm) + 
        cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display.barWidth), op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.maxScrollLeft = Math.max(0, display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus.sizer.offsetLeft + op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.adjustWidthTo - displayWidth(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm))), 
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.updatedDisplay || op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.selectionChanged) {
          op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.preparedSelection = display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus.input.prepareSelection();
        }
      }
      for (i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i = 0; i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i < group$jscomp$2_ops.length; i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i++) {
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op = group$jscomp$2_ops[i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i], cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm = op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.cm, null != op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.adjustWidthTo && (cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display.sizer.style.minWidth = 
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.adjustWidthTo + "px", op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.maxScrollLeft < cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.doc.scrollLeft && setScrollLeft(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, Math.min(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display.scroller.scrollLeft, op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.maxScrollLeft), 
        !0), cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display.maxLineChanged = !1), display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus = op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.focus && op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.focus == activeElt(), op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.preparedSelection && 
        cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display.input.showSelection(op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.preparedSelection, display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus), (op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.updatedDisplay || op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.startHeight != 
        cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.doc.height) && updateScrollbars(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.barMeasure), op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.updatedDisplay && setDocumentHeight(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.barMeasure), 
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.selectionChanged && restartBlink(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm), cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.state.focused && op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.updateInput && cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display.input.reset(op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.typing), 
        display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus && ensureFocus(op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.cm);
      }
      for (i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i = 0; i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i < group$jscomp$2_ops.length; i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i++) {
        var coords$jscomp$inline_697_rect = void 0;
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op = group$jscomp$2_ops[i$1$jscomp$inline_339_i$2$jscomp$inline_340_i$3$jscomp$inline_341_i$4$jscomp$inline_342_i$jscomp$187_i];
        cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm = op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.cm;
        display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus = cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display;
        JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc = cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.doc;
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.updatedDisplay && postUpdateDisplay(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.update);
        null == display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus.wheelStartX || null == op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollTop && null == op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollLeft && !op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollToPos || (display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus.wheelStartX = 
        display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus.wheelStartY = null);
        null != op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollTop && setScrollTop(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollTop, op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.forceScroll);
        null != op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollLeft && setScrollLeft(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollLeft, !0, !0);
        if (op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollToPos) {
          var doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden = clipPos(JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc, op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollToPos.from);
          var end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode = clipPos(JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc, op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollToPos.to);
          var display$jscomp$inline_702_i$jscomp$inline_708_margin = op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.scrollToPos.margin;
          null == display$jscomp$inline_702_i$jscomp$inline_708_margin && (display$jscomp$inline_702_i$jscomp$inline_708_margin = 0);
          cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.options.lineWrapping || doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden != end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode || (end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode = "before" == doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden.sticky ? Pos(doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden.line, 
          doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden.ch + 1, "before") : doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden, doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden = doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden.ch ? Pos(doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden.line, "before" == doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden.sticky ? doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden.ch - 1 : doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden.ch, 
          "after") : doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden);
          for (var box$jscomp$inline_703_limit = 0; 5 > box$jscomp$inline_703_limit; box$jscomp$inline_703_limit++) {
            var changed = !1;
            coords$jscomp$inline_697_rect = cursorCoords(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden);
            var endCoords$jscomp$inline_698_scrollPos = end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode && end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode != doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden ? cursorCoords(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode) : 
            coords$jscomp$inline_697_rect;
            coords$jscomp$inline_697_rect = {left:Math.min(coords$jscomp$inline_697_rect.left, endCoords$jscomp$inline_698_scrollPos.left), top:Math.min(coords$jscomp$inline_697_rect.top, endCoords$jscomp$inline_698_scrollPos.top) - display$jscomp$inline_702_i$jscomp$inline_708_margin, right:Math.max(coords$jscomp$inline_697_rect.left, endCoords$jscomp$inline_698_scrollPos.left), bottom:Math.max(coords$jscomp$inline_697_rect.bottom, endCoords$jscomp$inline_698_scrollPos.bottom) + display$jscomp$inline_702_i$jscomp$inline_708_margin};
            endCoords$jscomp$inline_698_scrollPos = calculateScrollPos(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, coords$jscomp$inline_697_rect);
            var startTop = cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.doc.scrollTop, startLeft = cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.doc.scrollLeft;
            null != endCoords$jscomp$inline_698_scrollPos.scrollTop && (updateScrollTop(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, endCoords$jscomp$inline_698_scrollPos.scrollTop), 1 < Math.abs(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.doc.scrollTop - startTop) && (changed = !0));
            null != endCoords$jscomp$inline_698_scrollPos.scrollLeft && (setScrollLeft(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, endCoords$jscomp$inline_698_scrollPos.scrollLeft), 1 < Math.abs(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.doc.scrollLeft - startLeft) && (changed = !0));
            if (!changed) {
              break;
            }
          }
          end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode = coords$jscomp$inline_697_rect;
          signalDOMEvent(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, "scrollCursorIntoView") || (display$jscomp$inline_702_i$jscomp$inline_708_margin = cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display, box$jscomp$inline_703_limit = display$jscomp$inline_702_i$jscomp$inline_708_margin.sizer.getBoundingClientRect(), doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden = null, 0 > end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode.top + 
          box$jscomp$inline_703_limit.top ? doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden = !0 : end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode.bottom + box$jscomp$inline_703_limit.top > (window.innerHeight || document.documentElement.clientHeight) && (doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden = !1), null == doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden || phantom || (end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode = 
          elt$jscomp$0("div", "\u200b", null, "position: absolute;\n                         top: " + (end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode.top - display$jscomp$inline_702_i$jscomp$inline_708_margin.viewOffset - cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display.lineSpace.offsetTop) + "px;\n                         height: " + (end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode.bottom - 
          end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode.top + scrollGap(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm) + display$jscomp$inline_702_i$jscomp$inline_708_margin.barHeight) + "px;\n                         left: " + end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode.left + "px; width: " + Math.max(2, end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode.right - 
          end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode.left) + "px;"), cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display.lineSpace.appendChild(end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode), end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode.scrollIntoView(doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden), 
          cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display.lineSpace.removeChild(end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode)));
        }
        end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode = op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.maybeHiddenMarkers;
        doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden = op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.maybeUnhiddenMarkers;
        if (end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode) {
          for (display$jscomp$inline_702_i$jscomp$inline_708_margin = 0; display$jscomp$inline_702_i$jscomp$inline_708_margin < end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode.length; ++display$jscomp$inline_702_i$jscomp$inline_708_margin) {
            end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode[display$jscomp$inline_702_i$jscomp$inline_708_margin].lines.length || signal(end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode[display$jscomp$inline_702_i$jscomp$inline_708_margin], "hide");
          }
        }
        if (doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden) {
          for (end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode = 0; end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode < doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden.length; ++end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode) {
            doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden[end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode].lines.length && signal(doScroll$jscomp$inline_704_pos$jscomp$inline_691_unhidden[end$jscomp$inline_692_hidden$jscomp$inline_706_i$1$jscomp$inline_709_rect$jscomp$inline_690_scrollNode], "unhide");
          }
        }
        display$jscomp$inline_671_display$jscomp$inline_678_display$jscomp$inline_688_takeFocus.wrapper.offsetHeight && (JSCompiler_inline_result$jscomp$inline_679_ch$jscomp$inline_680_display$jscomp$inline_672_doc.scrollTop = cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm.display.scroller.scrollTop);
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.changeObjs && signal(cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, "changes", cm$jscomp$inline_670_cm$jscomp$inline_677_cm$jscomp$inline_683_cm, op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.changeObjs);
        op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.update && op$jscomp$inline_669_op$jscomp$inline_674_op$jscomp$inline_676_op$jscomp$inline_682_op.update.finish();
      }
    });
  }
  function runInOp(cm, f) {
    if (cm.curOp) {
      return f();
    }
    startOperation(cm);
    try {
      return f();
    } finally {
      endOperation(cm);
    }
  }
  function operation(cm, f) {
    return function() {
      if (cm.curOp) {
        return f.apply(cm, arguments);
      }
      startOperation(cm);
      try {
        return f.apply(cm, arguments);
      } finally {
        endOperation(cm);
      }
    };
  }
  function methodOp(f) {
    return function() {
      if (this.curOp) {
        return f.apply(this, arguments);
      }
      startOperation(this);
      try {
        return f.apply(this, arguments);
      } finally {
        endOperation(this);
      }
    };
  }
  function docMethodOp(f) {
    return function() {
      var cm = this.cm;
      if (!cm || cm.curOp) {
        return f.apply(this, arguments);
      }
      startOperation(cm);
      try {
        return f.apply(this, arguments);
      } finally {
        endOperation(cm);
      }
    };
  }
  function startWorker(cm, time) {
    cm.doc.highlightFrontier < cm.display.viewTo && cm.state.highlight.set(time, bind(highlightWorker, cm));
  }
  function highlightWorker(cm) {
    var doc = cm.doc;
    if (!(doc.highlightFrontier >= cm.display.viewTo)) {
      var end = +new Date() + cm.options.workTime, context = getContextBefore(cm, doc.highlightFrontier), changedLines = [];
      doc.iter(context.line, Math.min(doc.first + doc.size, cm.display.viewTo + 500), function(line) {
        if (context.line >= cm.display.viewFrom) {
          var oldStyles = line.styles, i$jscomp$190_oldCls_resetState = line.text.length > cm.options.maxHighlightLength ? copyState(doc.mode, context.state) : null, highlighted_ischange_newCls = highlightLine(cm, line, context, !0);
          i$jscomp$190_oldCls_resetState && (context.state = i$jscomp$190_oldCls_resetState);
          line.styles = highlighted_ischange_newCls.styles;
          i$jscomp$190_oldCls_resetState = line.styleClasses;
          (highlighted_ischange_newCls = highlighted_ischange_newCls.classes) ? line.styleClasses = highlighted_ischange_newCls : i$jscomp$190_oldCls_resetState && (line.styleClasses = null);
          highlighted_ischange_newCls = !oldStyles || oldStyles.length != line.styles.length || i$jscomp$190_oldCls_resetState != highlighted_ischange_newCls && (!i$jscomp$190_oldCls_resetState || !highlighted_ischange_newCls || i$jscomp$190_oldCls_resetState.bgClass != highlighted_ischange_newCls.bgClass || i$jscomp$190_oldCls_resetState.textClass != highlighted_ischange_newCls.textClass);
          for (i$jscomp$190_oldCls_resetState = 0; !highlighted_ischange_newCls && i$jscomp$190_oldCls_resetState < oldStyles.length; ++i$jscomp$190_oldCls_resetState) {
            highlighted_ischange_newCls = oldStyles[i$jscomp$190_oldCls_resetState] != line.styles[i$jscomp$190_oldCls_resetState];
          }
          highlighted_ischange_newCls && changedLines.push(context.line);
          line.stateAfter = context.save();
        } else {
          line.text.length <= cm.options.maxHighlightLength && processLine(cm, line.text, context), line.stateAfter = 0 == context.line % 5 ? context.save() : null;
        }
        context.nextLine();
        if (+new Date() > end) {
          return startWorker(cm, cm.options.workDelay), !0;
        }
      });
      doc.highlightFrontier = context.line;
      doc.modeFrontier = Math.max(doc.modeFrontier, context.line);
      changedLines.length && runInOp(cm, function() {
        for (var i = 0; i < changedLines.length; i++) {
          regLineChange(cm, changedLines[i], "text");
        }
      });
    }
  }
  function updateDisplayIfNeeded(cm, update) {
    var display = cm.display, different_doc = cm.doc;
    if (update.editorIsHidden) {
      return resetView(cm), !1;
    }
    if (!update.force && update.visible.from >= display.viewFrom && update.visible.to <= display.viewTo && (null == display.updateLineNumbers || display.updateLineNumbers >= display.viewTo) && display.renderedView == display.view && 0 == countDirtyView(cm)) {
      return !1;
    }
    maybeUpdateLineNumberWidth(cm) && (resetView(cm), update.dims = getDimensions(cm));
    var display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel = different_doc.first + different_doc.size, active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel = Math.max(update.visible.from - cm.options.viewportMargin, different_doc.first), snapshot$jscomp$inline_382_to$jscomp$21_to = Math.min(display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel, update.visible.to + cm.options.viewportMargin);
    display.viewFrom < active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel && 20 > active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel - display.viewFrom && (active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel = Math.max(different_doc.first, display.viewFrom));
    display.viewTo > snapshot$jscomp$inline_382_to$jscomp$21_to && 20 > display.viewTo - snapshot$jscomp$inline_382_to$jscomp$21_to && (snapshot$jscomp$inline_382_to$jscomp$21_to = Math.min(display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel, display.viewTo));
    sawCollapsedSpans && (active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel = visualLineNo(cm.doc, active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel), snapshot$jscomp$inline_382_to$jscomp$21_to = visualLineEndNo(cm.doc, snapshot$jscomp$inline_382_to$jscomp$21_to));
    different_doc = active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel != display.viewFrom || snapshot$jscomp$inline_382_to$jscomp$21_to != display.viewTo || display.lastWrapHeight != update.wrapperHeight || display.lastWrapWidth != update.wrapperWidth;
    display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel = cm.display;
    0 == display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.view.length || active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel >= display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.viewTo || snapshot$jscomp$inline_382_to$jscomp$21_to <= display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.viewFrom ? (display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.view = buildViewArray(cm, active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel, 
    snapshot$jscomp$inline_382_to$jscomp$21_to), display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.viewFrom = active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel) : (display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.viewFrom > active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel ? display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.view = buildViewArray(cm, active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel, 
    display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.viewFrom).concat(display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.view) : display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.viewFrom < active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel && (display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.view = display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.view.slice(findViewIndex(cm, 
    active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel))), display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.viewFrom = active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel, display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.viewTo < snapshot$jscomp$inline_382_to$jscomp$21_to ? display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.view = display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.view.concat(buildViewArray(cm, 
    display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.viewTo, snapshot$jscomp$inline_382_to$jscomp$21_to)) : display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.viewTo > snapshot$jscomp$inline_382_to$jscomp$21_to && (display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.view = display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.view.slice(0, findViewIndex(cm, snapshot$jscomp$inline_382_to$jscomp$21_to))));
    display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.viewTo = snapshot$jscomp$inline_382_to$jscomp$21_to;
    display.viewOffset = heightAtLine(getLine(cm.doc, display.viewFrom));
    cm.display.mover.style.top = display.viewOffset + "px";
    snapshot$jscomp$inline_382_to$jscomp$21_to = countDirtyView(cm);
    if (!different_doc && 0 == snapshot$jscomp$inline_382_to$jscomp$21_to && !update.force && display.renderedView == display.view && (null == display.updateLineNumbers || display.updateLineNumbers >= display.viewTo)) {
      return !1;
    }
    cm.hasFocus() ? active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel = null : (active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel = activeElt()) && contains(cm.display.lineDiv, active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel) ? (active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel = {activeElt:active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel}, 
    window.getSelection && (display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel = window.getSelection(), display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.anchorNode && display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.extend && contains(cm.display.lineDiv, display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.anchorNode) && (active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel.anchorNode = display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.anchorNode, 
    active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel.anchorOffset = display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.anchorOffset, active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel.focusNode = display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.focusNode, active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel.focusOffset = display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.focusOffset))) : 
    active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel = null;
    4 < snapshot$jscomp$inline_382_to$jscomp$21_to && (display.lineDiv.style.display = "none");
    patchDisplay(cm, display.updateLineNumbers, update.dims);
    4 < snapshot$jscomp$inline_382_to$jscomp$21_to && (display.lineDiv.style.display = "");
    display.renderedView = display.view;
    (snapshot$jscomp$inline_382_to$jscomp$21_to = active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel) && snapshot$jscomp$inline_382_to$jscomp$21_to.activeElt && snapshot$jscomp$inline_382_to$jscomp$21_to.activeElt != activeElt() && (snapshot$jscomp$inline_382_to$jscomp$21_to.activeElt.focus(), !/^(INPUT|TEXTAREA)$/.test(snapshot$jscomp$inline_382_to$jscomp$21_to.activeElt.nodeName) && snapshot$jscomp$inline_382_to$jscomp$21_to.anchorNode && contains(document.body, 
    snapshot$jscomp$inline_382_to$jscomp$21_to.anchorNode) && contains(document.body, snapshot$jscomp$inline_382_to$jscomp$21_to.focusNode) && (active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel = window.getSelection(), display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel = document.createRange(), display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.setEnd(snapshot$jscomp$inline_382_to$jscomp$21_to.anchorNode, snapshot$jscomp$inline_382_to$jscomp$21_to.anchorOffset), 
    display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel.collapse(!1), active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel.removeAllRanges(), active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel.addRange(display$jscomp$inline_375_end$jscomp$32_range$jscomp$inline_384_sel), active$jscomp$inline_378_from$jscomp$22_from$jscomp$inline_373_result$jscomp$inline_379_sel.extend(snapshot$jscomp$inline_382_to$jscomp$21_to.focusNode, 
    snapshot$jscomp$inline_382_to$jscomp$21_to.focusOffset)));
    removeChildren(display.cursorDiv);
    removeChildren(display.selectionDiv);
    display.gutters.style.height = display.sizer.style.minHeight = 0;
    different_doc && (display.lastWrapHeight = update.wrapperHeight, display.lastWrapWidth = update.wrapperWidth, startWorker(cm, 400));
    display.updateLineNumbers = null;
    return !0;
  }
  function postUpdateDisplay(cm, update) {
    for (var viewport = update.viewport, barMeasure_first = !0;; barMeasure_first = !1) {
      if (barMeasure_first && cm.options.lineWrapping && update.oldDisplayWidth != displayWidth(cm)) {
        barMeasure_first && (update.visible = visibleLines(cm.display, cm.doc, viewport));
      } else {
        if (viewport && null != viewport.top && (viewport = {top:Math.min(cm.doc.height + paddingVert(cm.display) - displayHeight(cm), viewport.top)}), update.visible = visibleLines(cm.display, cm.doc, viewport), update.visible.from >= cm.display.viewFrom && update.visible.to <= cm.display.viewTo) {
          break;
        }
      }
      if (!updateDisplayIfNeeded(cm, update)) {
        break;
      }
      updateHeightsInViewport(cm);
      barMeasure_first = measureForScrollbars(cm);
      updateSelection(cm);
      updateScrollbars(cm, barMeasure_first);
      setDocumentHeight(cm, barMeasure_first);
      update.force = !1;
    }
    update.signal(cm, "update", cm);
    if (cm.display.viewFrom != cm.display.reportedViewFrom || cm.display.viewTo != cm.display.reportedViewTo) {
      update.signal(cm, "viewportChange", cm, cm.display.viewFrom, cm.display.viewTo), cm.display.reportedViewFrom = cm.display.viewFrom, cm.display.reportedViewTo = cm.display.viewTo;
    }
  }
  function updateDisplaySimple(cm, update$jscomp$2_viewport) {
    update$jscomp$2_viewport = new DisplayUpdate(cm, update$jscomp$2_viewport);
    if (updateDisplayIfNeeded(cm, update$jscomp$2_viewport)) {
      updateHeightsInViewport(cm);
      postUpdateDisplay(cm, update$jscomp$2_viewport);
      var barMeasure = measureForScrollbars(cm);
      updateSelection(cm);
      updateScrollbars(cm, barMeasure);
      setDocumentHeight(cm, barMeasure);
      update$jscomp$2_viewport.finish();
    }
  }
  function patchDisplay(cm, updateNumbersFrom, dims) {
    function rm(node) {
      var next = node.nextSibling;
      webkit && mac && cm.display.currentWheelTarget == node ? node.style.display = "none" : node.parentNode.removeChild(node);
      return next;
    }
    var display$jscomp$24_lineN = cm.display, lineNumbers = cm.options.lineNumbers, container = display$jscomp$24_lineN.lineDiv, cur = container.firstChild, view = display$jscomp$24_lineN.view;
    display$jscomp$24_lineN = display$jscomp$24_lineN.viewFrom;
    for (var i = 0; i < view.length; i++) {
      var lineView = view[i];
      if (!lineView.hidden) {
        if (lineView.node && lineView.node.parentNode == container) {
          for (; cur != lineView.node;) {
            cur = rm(cur);
          }
          cur = lineNumbers && null != updateNumbersFrom && updateNumbersFrom <= display$jscomp$24_lineN && lineView.lineNumber;
          lineView.changes && (-1 < indexOf(lineView.changes, "gutter") && (cur = !1), updateLineForChanges(cm, lineView, display$jscomp$24_lineN, dims));
          cur && (removeChildren(lineView.lineNumber), lineView.lineNumber.appendChild(document.createTextNode(lineNumberFor(cm.options, display$jscomp$24_lineN))));
          cur = lineView.node.nextSibling;
        } else {
          var node$jscomp$0 = buildLineElement(cm, lineView, display$jscomp$24_lineN, dims);
          container.insertBefore(node$jscomp$0, cur);
        }
      }
      display$jscomp$24_lineN += lineView.size;
    }
    for (; cur;) {
      cur = rm(cur);
    }
  }
  function updateGutterSpace(display) {
    display.sizer.style.marginLeft = display.gutters.offsetWidth + "px";
    signalLater(display, "gutterChanged", display);
  }
  function setDocumentHeight(cm, measure) {
    cm.display.sizer.style.minHeight = measure.docHeight + "px";
    cm.display.heightForcer.style.top = measure.docHeight + "px";
    cm.display.gutters.style.height = measure.docHeight + cm.display.barHeight + scrollGap(cm) + "px";
  }
  function alignHorizontally(cm) {
    var display = cm.display, view = display.view;
    if (display.alignWidgets || display.gutters.firstChild && cm.options.fixedGutter) {
      for (var comp = compensateForHScroll(display) - display.scroller.scrollLeft + cm.doc.scrollLeft, gutterW = display.gutters.offsetWidth, left = comp + "px", i = 0; i < view.length; i++) {
        if (!view[i].hidden) {
          cm.options.fixedGutter && (view[i].gutter && (view[i].gutter.style.left = left), view[i].gutterBackground && (view[i].gutterBackground.style.left = left));
          var align = view[i].alignable;
          if (align) {
            for (var j = 0; j < align.length; j++) {
              align[j].style.left = left;
            }
          }
        }
      }
      cm.options.fixedGutter && (display.gutters.style.left = comp + gutterW + "px");
    }
  }
  function maybeUpdateLineNumberWidth(cm) {
    if (!cm.options.lineNumbers) {
      return !1;
    }
    var doc$jscomp$47_last = cm.doc;
    doc$jscomp$47_last = lineNumberFor(cm.options, doc$jscomp$47_last.first + doc$jscomp$47_last.size - 1);
    var display = cm.display;
    if (doc$jscomp$47_last.length != display.lineNumChars) {
      var padding$jscomp$1_test = display.measure.appendChild(elt$jscomp$0("div", [elt$jscomp$0("div", doc$jscomp$47_last)], "CodeMirror-linenumber CodeMirror-gutter-elt")), innerW = padding$jscomp$1_test.firstChild.offsetWidth;
      padding$jscomp$1_test = padding$jscomp$1_test.offsetWidth - innerW;
      display.lineGutter.style.width = "";
      display.lineNumInnerWidth = Math.max(innerW, display.lineGutter.offsetWidth - padding$jscomp$1_test) + 1;
      display.lineNumWidth = display.lineNumInnerWidth + padding$jscomp$1_test;
      display.lineNumChars = display.lineNumInnerWidth ? doc$jscomp$47_last.length : -1;
      display.lineGutter.style.width = display.lineNumWidth + "px";
      updateGutterSpace(cm.display);
      return !0;
    }
    return !1;
  }
  function getGutters(gutters, lineNumbers) {
    for (var result = [], sawLineNumbers = !1, i = 0; i < gutters.length; i++) {
      var name = gutters[i], style = null;
      "string" != typeof name && (style = name.style, name = name.className);
      if ("CodeMirror-linenumbers" == name) {
        if (lineNumbers) {
          sawLineNumbers = !0;
        } else {
          continue;
        }
      }
      result.push({className:name, style});
    }
    lineNumbers && !sawLineNumbers && result.push({className:"CodeMirror-linenumbers", style:null});
    return result;
  }
  function renderGutters(display) {
    var gutters = display.gutters, specs = display.gutterSpecs;
    removeChildren(gutters);
    display.lineGutter = null;
    for (var i = 0; i < specs.length; ++i) {
      var ref$jscomp$5_style = specs[i], className = ref$jscomp$5_style.className;
      ref$jscomp$5_style = ref$jscomp$5_style.style;
      var gElt = gutters.appendChild(elt$jscomp$0("div", null, "CodeMirror-gutter " + className));
      ref$jscomp$5_style && (gElt.style.cssText = ref$jscomp$5_style);
      "CodeMirror-linenumbers" == className && (display.lineGutter = gElt, gElt.style.width = (display.lineNumWidth || 1) + "px");
    }
    gutters.style.display = specs.length ? "" : "none";
    updateGutterSpace(display);
  }
  function updateGutters(cm) {
    renderGutters(cm.display);
    regChange(cm);
    alignHorizontally(cm);
  }
  function Display(place, doc, input, options) {
    this.input = input;
    this.scrollbarFiller = elt$jscomp$0("div", null, "CodeMirror-scrollbar-filler");
    this.scrollbarFiller.setAttribute("cm-not-content", "true");
    this.gutterFiller = elt$jscomp$0("div", null, "CodeMirror-gutter-filler");
    this.gutterFiller.setAttribute("cm-not-content", "true");
    this.lineDiv = eltP("div", null, "CodeMirror-code");
    this.selectionDiv = elt$jscomp$0("div", null, null, "position: relative; z-index: 1");
    this.cursorDiv = elt$jscomp$0("div", null, "CodeMirror-cursors");
    this.measure = elt$jscomp$0("div", null, "CodeMirror-measure");
    this.lineMeasure = elt$jscomp$0("div", null, "CodeMirror-measure");
    this.lineSpace = eltP("div", [this.measure, this.lineMeasure, this.selectionDiv, this.cursorDiv, this.lineDiv], null, "position: relative; outline: none");
    var lines = eltP("div", [this.lineSpace], "CodeMirror-lines");
    this.mover = elt$jscomp$0("div", [lines], null, "position: relative");
    this.sizer = elt$jscomp$0("div", [this.mover], "CodeMirror-sizer");
    this.sizerWidth = null;
    this.heightForcer = elt$jscomp$0("div", null, null, "position: absolute; height: 50px; width: 1px;");
    this.gutters = elt$jscomp$0("div", null, "CodeMirror-gutters");
    this.lineGutter = null;
    this.scroller = elt$jscomp$0("div", [this.sizer, this.heightForcer, this.gutters], "CodeMirror-scroll");
    this.scroller.setAttribute("tabIndex", "-1");
    this.wrapper = elt$jscomp$0("div", [this.scrollbarFiller, this.gutterFiller, this.scroller], "CodeMirror");
    this.wrapper.setAttribute("translate", "no");
    ie && 8 > ie_version && (this.gutters.style.zIndex = -1, this.scroller.style.paddingRight = 0);
    webkit || gecko && mobile || (this.scroller.draggable = !0);
    place && (place.appendChild ? place.appendChild(this.wrapper) : place(this.wrapper));
    this.reportedViewFrom = this.reportedViewTo = this.viewFrom = this.viewTo = doc.first;
    this.view = [];
    this.externalMeasured = this.renderedView = null;
    this.lastWrapHeight = this.lastWrapWidth = this.viewOffset = 0;
    this.updateLineNumbers = null;
    this.nativeBarWidth = this.barHeight = this.barWidth = 0;
    this.scrollbarsClipped = !1;
    this.lineNumWidth = this.lineNumInnerWidth = this.lineNumChars = null;
    this.alignWidgets = !1;
    this.maxLine = this.cachedCharWidth = this.cachedTextHeight = this.cachedPaddingH = null;
    this.maxLineLength = 0;
    this.maxLineChanged = !1;
    this.wheelDX = this.wheelDY = this.wheelStartX = this.wheelStartY = null;
    this.shift = !1;
    this.activeTouch = this.selForContextMenu = null;
    this.gutterSpecs = getGutters(options.gutters, options.lineNumbers);
    renderGutters(this);
    input.init(this);
  }
  function wheelEventDelta(e) {
    var dx = e.wheelDeltaX, dy = e.wheelDeltaY;
    null == dx && e.detail && e.axis == e.HORIZONTAL_AXIS && (dx = e.detail);
    null == dy && e.detail && e.axis == e.VERTICAL_AXIS ? dy = e.detail : null == dy && (dy = e.wheelDelta);
    return {x:dx, y:dy};
  }
  function wheelEventPixels(delta$jscomp$3_e) {
    delta$jscomp$3_e = wheelEventDelta(delta$jscomp$3_e);
    delta$jscomp$3_e.x *= wheelPixelsPerUnit;
    delta$jscomp$3_e.y *= wheelPixelsPerUnit;
    return delta$jscomp$3_e;
  }
  function onScrollWheel(cm, e) {
    chrome && 102 == chrome_version && (null == cm.display.chromeScrollHack ? cm.display.sizer.style.pointerEvents = "none" : clearTimeout(cm.display.chromeScrollHack), cm.display.chromeScrollHack = setTimeout(function() {
      cm.display.chromeScrollHack = null;
      cm.display.sizer.style.pointerEvents = "";
    }, 100));
    var delta$jscomp$4_dy = wheelEventDelta(e), dx = delta$jscomp$4_dy.x;
    delta$jscomp$4_dy = delta$jscomp$4_dy.y;
    var pixels = wheelPixelsPerUnit;
    0 === e.deltaMode && (dx = e.deltaX, delta$jscomp$4_dy = e.deltaY, pixels = 1);
    var display = cm.display, scroll = display.scroller, bot$jscomp$2_canScrollX_cur = scroll.scrollWidth > scroll.clientWidth, canScrollY_top = scroll.scrollHeight > scroll.clientHeight;
    if (dx && bot$jscomp$2_canScrollX_cur || delta$jscomp$4_dy && canScrollY_top) {
      if (delta$jscomp$4_dy && mac && webkit) {
        bot$jscomp$2_canScrollX_cur = e.target;
        var view = display.view;
        a: for (; bot$jscomp$2_canScrollX_cur != scroll; bot$jscomp$2_canScrollX_cur = bot$jscomp$2_canScrollX_cur.parentNode) {
          for (var i = 0; i < view.length; i++) {
            if (view[i].node == bot$jscomp$2_canScrollX_cur) {
              cm.display.currentWheelTarget = bot$jscomp$2_canScrollX_cur;
              break a;
            }
          }
        }
      }
      !dx || gecko || presto || null == pixels ? (delta$jscomp$4_dy && null != pixels && (pixels = delta$jscomp$4_dy * pixels, canScrollY_top = cm.doc.scrollTop, bot$jscomp$2_canScrollX_cur = canScrollY_top + display.wrapper.clientHeight, 0 > pixels ? canScrollY_top = Math.max(0, canScrollY_top + pixels - 50) : bot$jscomp$2_canScrollX_cur = Math.min(cm.doc.height, bot$jscomp$2_canScrollX_cur + pixels + 50), updateDisplaySimple(cm, {top:canScrollY_top, bottom:bot$jscomp$2_canScrollX_cur})), 20 > wheelSamples && 
      0 !== e.deltaMode && (null == display.wheelStartX ? (display.wheelStartX = scroll.scrollLeft, display.wheelStartY = scroll.scrollTop, display.wheelDX = dx, display.wheelDY = delta$jscomp$4_dy, setTimeout(function() {
        if (null != display.wheelStartX) {
          var movedX_sample = scroll.scrollLeft - display.wheelStartX, movedY = scroll.scrollTop - display.wheelStartY;
          movedX_sample = movedY && display.wheelDY && movedY / display.wheelDY || movedX_sample && display.wheelDX && movedX_sample / display.wheelDX;
          display.wheelStartX = display.wheelStartY = null;
          movedX_sample && (wheelPixelsPerUnit = (wheelPixelsPerUnit * wheelSamples + movedX_sample) / (wheelSamples + 1), ++wheelSamples);
        }
      }, 200)) : (display.wheelDX += dx, display.wheelDY += delta$jscomp$4_dy))) : (delta$jscomp$4_dy && canScrollY_top && updateScrollTop(cm, Math.max(0, scroll.scrollTop + delta$jscomp$4_dy * pixels)), setScrollLeft(cm, Math.max(0, scroll.scrollLeft + dx * pixels)), (!delta$jscomp$4_dy || delta$jscomp$4_dy && canScrollY_top) && e_preventDefault(e), display.wheelStartX = null);
    }
  }
  function normalizeSelection(cm, ranges, prim_primIndex) {
    cm = cm && cm.options.selectionsMayTouch;
    prim_primIndex = ranges[prim_primIndex];
    ranges.sort(function(a, b) {
      return cmp(a.from(), b.from());
    });
    prim_primIndex = indexOf(ranges, prim_primIndex);
    for (var i = 1; i < ranges.length; i++) {
      var cur = ranges[i], prev = ranges[i - 1], diff$jscomp$3_from = cmp(prev.to(), cur.from());
      if (cm && !cur.empty() ? 0 < diff$jscomp$3_from : 0 <= diff$jscomp$3_from) {
        diff$jscomp$3_from = minPos(prev.from(), cur.from());
        var to = maxPos(prev.to(), cur.to());
        cur = prev.empty() ? cur.from() == cur.head : prev.from() == prev.head;
        i <= prim_primIndex && --prim_primIndex;
        ranges.splice(--i, 2, new Range(cur ? to : diff$jscomp$3_from, cur ? diff$jscomp$3_from : to));
      }
    }
    return new Selection(ranges, prim_primIndex);
  }
  function simpleSelection(anchor, head) {
    return new Selection([new Range(anchor, head || anchor)], 0);
  }
  function changeEnd(change) {
    return change.text ? Pos(change.from.line + change.text.length - 1, lst(change.text).length + (1 == change.text.length ? change.from.ch : 0)) : change.to;
  }
  function adjustForChange(pos, change) {
    if (0 > cmp(pos, change.from)) {
      return pos;
    }
    if (0 >= cmp(pos, change.to)) {
      return changeEnd(change);
    }
    var line = pos.line + change.text.length - (change.to.line - change.from.line) - 1, ch = pos.ch;
    pos.line == change.to.line && (ch += changeEnd(change).ch - change.to.ch);
    return Pos(line, ch);
  }
  function computeSelAfterChange(doc, change) {
    for (var out = [], i = 0; i < doc.sel.ranges.length; i++) {
      var range = doc.sel.ranges[i];
      out.push(new Range(adjustForChange(range.anchor, change), adjustForChange(range.head, change)));
    }
    return normalizeSelection(doc.cm, out, doc.sel.primIndex);
  }
  function offsetPos(pos, old, nw) {
    return pos.line == old.line ? Pos(nw.line, pos.ch - old.ch + nw.ch) : Pos(nw.line + (pos.line - old.line), pos.ch);
  }
  function loadMode(cm) {
    cm.doc.mode = getMode(cm.options, cm.doc.modeOption);
    resetModeState(cm);
  }
  function resetModeState(cm) {
    cm.doc.iter(function(line) {
      line.stateAfter && (line.stateAfter = null);
      line.styles && (line.styles = null);
    });
    cm.doc.modeFrontier = cm.doc.highlightFrontier = cm.doc.first;
    startWorker(cm, 100);
    cm.state.modeGen++;
    cm.curOp && regChange(cm);
  }
  function isWholeLineUpdate(doc, change) {
    return 0 == change.from.ch && 0 == change.to.ch && "" == lst(change.text) && (!doc.cm || doc.cm.options.wholeLineUpdateBefore);
  }
  function updateDoc(doc, change, markedSpans, estimateHeight) {
    function update(line, estHeight$jscomp$inline_390_text, spans) {
      line.text = estHeight$jscomp$inline_390_text;
      line.stateAfter && (line.stateAfter = null);
      line.styles && (line.styles = null);
      null != line.order && (line.order = null);
      detachMarkedSpans(line);
      attachMarkedSpans(line, spans);
      estHeight$jscomp$inline_390_text = estimateHeight ? estimateHeight(line) : 1;
      estHeight$jscomp$inline_390_text != line.height && updateLineHeight(line, estHeight$jscomp$inline_390_text);
      signalLater(line, "change", line, change);
    }
    function linesFor(i$jscomp$205_start, end) {
      for (var result = []; i$jscomp$205_start < end; ++i$jscomp$205_start) {
        result.push(new Line(text[i$jscomp$205_start], markedSpans ? markedSpans[i$jscomp$205_start] : null, estimateHeight));
      }
      return result;
    }
    var from = change.from, added_to = change.to, text = change.text, firstLine = getLine(doc, from.line), lastLine = getLine(doc, added_to.line), lastText = lst(text), added$2_lastSpans = markedSpans ? markedSpans[text.length - 1] : null, added$1_nlines = added_to.line - from.line;
    change.full ? (doc.insert(0, linesFor(0, text.length)), doc.remove(text.length, doc.size - text.length)) : isWholeLineUpdate(doc, change) ? (added_to = linesFor(0, text.length - 1), update(lastLine, lastLine.text, added$2_lastSpans), added$1_nlines && doc.remove(from.line, added$1_nlines), added_to.length && doc.insert(from.line, added_to)) : firstLine == lastLine ? 1 == text.length ? update(firstLine, firstLine.text.slice(0, from.ch) + lastText + firstLine.text.slice(added_to.ch), added$2_lastSpans) : 
    (added$1_nlines = linesFor(1, text.length - 1), added$1_nlines.push(new Line(lastText + firstLine.text.slice(added_to.ch), added$2_lastSpans, estimateHeight)), update(firstLine, firstLine.text.slice(0, from.ch) + text[0], markedSpans ? markedSpans[0] : null), doc.insert(from.line + 1, added$1_nlines)) : 1 == text.length ? (update(firstLine, firstLine.text.slice(0, from.ch) + text[0] + lastLine.text.slice(added_to.ch), markedSpans ? markedSpans[0] : null), doc.remove(from.line + 1, added$1_nlines)) : 
    (update(firstLine, firstLine.text.slice(0, from.ch) + text[0], markedSpans ? markedSpans[0] : null), update(lastLine, lastText + lastLine.text.slice(added_to.ch), added$2_lastSpans), added$2_lastSpans = linesFor(1, text.length - 1), 1 < added$1_nlines && doc.remove(from.line + 1, added$1_nlines - 1), doc.insert(from.line + 1, added$2_lastSpans));
    signalLater(doc, "change", doc, change);
  }
  function linkedDocs(doc$jscomp$0, f, sharedHistOnly) {
    function propagate(doc, skip, sharedHist) {
      if (doc.linked) {
        for (var i = 0; i < doc.linked.length; ++i) {
          var rel = doc.linked[i];
          if (rel.doc != skip) {
            var shared = sharedHist && rel.sharedHist;
            if (!sharedHistOnly || shared) {
              f(rel.doc, shared), propagate(rel.doc, doc, shared);
            }
          }
        }
      }
    }
    propagate(doc$jscomp$0, null, !0);
  }
  function attachDoc(cm, doc) {
    if (doc.cm) {
      throw Error("This document is already in use.");
    }
    cm.doc = doc;
    doc.cm = cm;
    estimateLineHeights(cm);
    loadMode(cm);
    setDirectionClass(cm);
    cm.options.direction = doc.direction;
    cm.options.lineWrapping || findMaxLine(cm);
    cm.options.mode = doc.modeOption;
    regChange(cm);
  }
  function setDirectionClass(cm) {
    ("rtl" == cm.doc.direction ? addClass : rmClass)(cm.display.lineDiv, "CodeMirror-rtl");
  }
  function directionChanged(cm) {
    runInOp(cm, function() {
      setDirectionClass(cm);
      regChange(cm);
    });
  }
  function History(prev) {
    this.done = [];
    this.undone = [];
    this.undoDepth = prev ? prev.undoDepth : Infinity;
    this.lastModTime = this.lastSelTime = 0;
    this.lastOrigin = this.lastSelOrigin = this.lastOp = this.lastSelOp = null;
    this.generation = this.maxGeneration = prev ? prev.maxGeneration : 1;
  }
  function historyChangeFromChange(doc$jscomp$0, change) {
    var histChange = {from:copyPos(change.from), to:changeEnd(change), text:getBetween(doc$jscomp$0, change.from, change.to)};
    attachLocalSpans(doc$jscomp$0, histChange, change.from.line, change.to.line + 1);
    linkedDocs(doc$jscomp$0, function(doc) {
      return attachLocalSpans(doc, histChange, change.from.line, change.to.line + 1);
    }, !0);
    return histChange;
  }
  function clearSelectionEvents(array) {
    for (; array.length;) {
      if (lst(array).ranges) {
        array.pop();
      } else {
        break;
      }
    }
  }
  function addChangeToHistory(doc, change, selAfter, opId) {
    var hist = doc.history;
    hist.undone.length = 0;
    var time = +new Date(), JSCompiler_temp;
    if (JSCompiler_temp = hist.lastOp == opId || hist.lastOrigin == change.origin && change.origin && ("+" == change.origin.charAt(0) && hist.lastModTime > time - (doc.cm ? doc.cm.options.historyEventDelay : 500) || "*" == change.origin.charAt(0))) {
      if (hist.lastOp == opId) {
        clearSelectionEvents(hist.done);
        var JSCompiler_inline_result$jscomp$51_before$jscomp$3_cur = lst(hist.done);
      } else {
        hist.done.length && !lst(hist.done).ranges ? JSCompiler_inline_result$jscomp$51_before$jscomp$3_cur = lst(hist.done) : 1 < hist.done.length && !hist.done[hist.done.length - 2].ranges ? (hist.done.pop(), JSCompiler_inline_result$jscomp$51_before$jscomp$3_cur = lst(hist.done)) : JSCompiler_inline_result$jscomp$51_before$jscomp$3_cur = void 0;
      }
      JSCompiler_temp = JSCompiler_inline_result$jscomp$51_before$jscomp$3_cur;
    }
    if (JSCompiler_temp) {
      var last = lst(JSCompiler_inline_result$jscomp$51_before$jscomp$3_cur.changes);
      0 == cmp(change.from, change.to) && 0 == cmp(change.from, last.to) ? last.to = changeEnd(change) : JSCompiler_inline_result$jscomp$51_before$jscomp$3_cur.changes.push(historyChangeFromChange(doc, change));
    } else {
      for ((JSCompiler_inline_result$jscomp$51_before$jscomp$3_cur = lst(hist.done)) && JSCompiler_inline_result$jscomp$51_before$jscomp$3_cur.ranges || pushSelectionToHistory(doc.sel, hist.done), JSCompiler_inline_result$jscomp$51_before$jscomp$3_cur = {changes:[historyChangeFromChange(doc, change)], generation:hist.generation}, hist.done.push(JSCompiler_inline_result$jscomp$51_before$jscomp$3_cur); hist.done.length > hist.undoDepth;) {
        hist.done.shift(), hist.done[0].ranges || hist.done.shift();
      }
    }
    hist.done.push(selAfter);
    hist.generation = ++hist.maxGeneration;
    hist.lastModTime = hist.lastSelTime = time;
    hist.lastOp = hist.lastSelOp = opId;
    hist.lastOrigin = hist.lastSelOrigin = change.origin;
    last || signal(doc, "historyAdded");
  }
  function pushSelectionToHistory(sel, dest) {
    var top = lst(dest);
    top && top.ranges && top.equals(sel) || dest.push(sel);
  }
  function attachLocalSpans(doc, change, from, to) {
    var existing = change["spans_" + doc.id], n = 0;
    doc.iter(Math.max(doc.first, from), Math.min(doc.first + doc.size, to), function(line) {
      line.markedSpans && ((existing || (existing = change["spans_" + doc.id] = {}))[n] = line.markedSpans);
      ++n;
    });
  }
  function mergeOldSpans(doc, change$jscomp$13_i) {
    var found$jscomp$inline_397_old;
    if (found$jscomp$inline_397_old = change$jscomp$13_i["spans_" + doc.id]) {
      for (var nw = [], i = 0; i < change$jscomp$13_i.text.length; ++i) {
        var JSCompiler_temp_const$jscomp$637_j = nw, JSCompiler_temp_const$jscomp$636_span = JSCompiler_temp_const$jscomp$637_j.push;
        var JSCompiler_inline_result$jscomp$638_k$jscomp$8_out = void 0;
        var spans = found$jscomp$inline_397_old[i];
        if (spans) {
          for (var i$jscomp$0 = 0; i$jscomp$0 < spans.length; ++i$jscomp$0) {
            spans[i$jscomp$0].marker.explicitlyCleared ? JSCompiler_inline_result$jscomp$638_k$jscomp$8_out || (JSCompiler_inline_result$jscomp$638_k$jscomp$8_out = spans.slice(0, i$jscomp$0)) : JSCompiler_inline_result$jscomp$638_k$jscomp$8_out && JSCompiler_inline_result$jscomp$638_k$jscomp$8_out.push(spans[i$jscomp$0]);
          }
          JSCompiler_inline_result$jscomp$638_k$jscomp$8_out = JSCompiler_inline_result$jscomp$638_k$jscomp$8_out ? JSCompiler_inline_result$jscomp$638_k$jscomp$8_out.length ? JSCompiler_inline_result$jscomp$638_k$jscomp$8_out : null : spans;
        } else {
          JSCompiler_inline_result$jscomp$638_k$jscomp$8_out = null;
        }
        JSCompiler_temp_const$jscomp$636_span.call(JSCompiler_temp_const$jscomp$637_j, JSCompiler_inline_result$jscomp$638_k$jscomp$8_out);
      }
      found$jscomp$inline_397_old = nw;
    } else {
      found$jscomp$inline_397_old = null;
    }
    doc = stretchSpansOverChange(doc, change$jscomp$13_i);
    if (!found$jscomp$inline_397_old) {
      return doc;
    }
    if (!doc) {
      return found$jscomp$inline_397_old;
    }
    for (change$jscomp$13_i = 0; change$jscomp$13_i < found$jscomp$inline_397_old.length; ++change$jscomp$13_i) {
      if (nw = found$jscomp$inline_397_old[change$jscomp$13_i], i = doc[change$jscomp$13_i], nw && i) {
        a: for (JSCompiler_temp_const$jscomp$637_j = 0; JSCompiler_temp_const$jscomp$637_j < i.length; ++JSCompiler_temp_const$jscomp$637_j) {
          JSCompiler_temp_const$jscomp$636_span = i[JSCompiler_temp_const$jscomp$637_j];
          for (JSCompiler_inline_result$jscomp$638_k$jscomp$8_out = 0; JSCompiler_inline_result$jscomp$638_k$jscomp$8_out < nw.length; ++JSCompiler_inline_result$jscomp$638_k$jscomp$8_out) {
            if (nw[JSCompiler_inline_result$jscomp$638_k$jscomp$8_out].marker == JSCompiler_temp_const$jscomp$636_span.marker) {
              continue a;
            }
          }
          nw.push(JSCompiler_temp_const$jscomp$636_span);
        }
      } else {
        i && (found$jscomp$inline_397_old[change$jscomp$13_i] = i);
      }
    }
    return found$jscomp$inline_397_old;
  }
  function copyHistoryArray(events, newGroup, instantiateSel) {
    for (var copy = [], i = 0; i < events.length; ++i) {
      var changes$jscomp$1_event = events[i];
      if (changes$jscomp$1_event.ranges) {
        copy.push(instantiateSel ? Selection.prototype.deepCopy.call(changes$jscomp$1_event) : changes$jscomp$1_event);
      } else {
        changes$jscomp$1_event = changes$jscomp$1_event.changes;
        var newChanges = [];
        copy.push({changes:newChanges});
        for (var j = 0; j < changes$jscomp$1_event.length; ++j) {
          var change = changes$jscomp$1_event[j], m = void 0;
          newChanges.push({from:change.from, to:change.to, text:change.text});
          if (newGroup) {
            for (var prop in change) {
              (m = prop.match(/^spans_(\d+)$/)) && -1 < indexOf(newGroup, Number(m[1])) && (lst(newChanges)[prop] = change[prop], delete change[prop]);
            }
          }
        }
      }
    }
    return copy;
  }
  function extendRange(anchor$jscomp$5_range, head, other, extend_posBefore) {
    return extend_posBefore ? (anchor$jscomp$5_range = anchor$jscomp$5_range.anchor, other && (extend_posBefore = 0 > cmp(head, anchor$jscomp$5_range), extend_posBefore != 0 > cmp(other, anchor$jscomp$5_range) ? (anchor$jscomp$5_range = head, head = other) : extend_posBefore != 0 > cmp(head, other) && (head = other)), new Range(anchor$jscomp$5_range, head)) : new Range(other || head, head);
  }
  function extendSelection(doc, head, other, options, extend) {
    null == extend && (extend = doc.cm && (doc.cm.display.shift || doc.extend));
    setSelection(doc, new Selection([extendRange(doc.sel.primary(), head, other, extend)], 0), options);
  }
  function extendSelections(doc, heads_newSel, options) {
    for (var out = [], extend = doc.cm && (doc.cm.display.shift || doc.extend), i = 0; i < doc.sel.ranges.length; i++) {
      out[i] = extendRange(doc.sel.ranges[i], heads_newSel[i], null, extend);
    }
    heads_newSel = normalizeSelection(doc.cm, out, doc.sel.primIndex);
    setSelection(doc, heads_newSel, options);
  }
  function replaceOneSelection(doc, i, range, options) {
    var ranges = doc.sel.ranges.slice(0);
    ranges[i] = range;
    setSelection(doc, normalizeSelection(doc.cm, ranges, doc.sel.primIndex), options);
  }
  function filterSelectionChange(doc, sel, obj$jscomp$87_options) {
    obj$jscomp$87_options = {ranges:sel.ranges, update:function(ranges) {
      this.ranges = [];
      for (var i = 0; i < ranges.length; i++) {
        this.ranges[i] = new Range(clipPos(doc, ranges[i].anchor), clipPos(doc, ranges[i].head));
      }
    }, origin:obj$jscomp$87_options && obj$jscomp$87_options.origin};
    signal(doc, "beforeSelectionChange", doc, obj$jscomp$87_options);
    doc.cm && signal(doc.cm, "beforeSelectionChange", doc.cm, obj$jscomp$87_options);
    return obj$jscomp$87_options.ranges != sel.ranges ? normalizeSelection(doc.cm, obj$jscomp$87_options.ranges, obj$jscomp$87_options.ranges.length - 1) : sel;
  }
  function setSelectionReplaceHistory(doc, sel, options) {
    var done = doc.history.done, last = lst(done);
    last && last.ranges ? (done[done.length - 1] = sel, setSelectionNoUndo(doc, sel, options)) : setSelection(doc, sel, options);
  }
  function setSelection(doc, sel$jscomp$7_sel, options) {
    setSelectionNoUndo(doc, sel$jscomp$7_sel, options);
    sel$jscomp$7_sel = doc.sel;
    var opId = doc.cm ? doc.cm.curOp.id : NaN, hist = doc.history, origin = options && options.origin, JSCompiler_temp$jscomp$633_JSCompiler_temp$jscomp$634_JSCompiler_temp$jscomp$635_prev;
    if (!(JSCompiler_temp$jscomp$633_JSCompiler_temp$jscomp$634_JSCompiler_temp$jscomp$635_prev = opId == hist.lastSelOp) && (JSCompiler_temp$jscomp$633_JSCompiler_temp$jscomp$634_JSCompiler_temp$jscomp$635_prev = origin && hist.lastSelOrigin == origin) && !(JSCompiler_temp$jscomp$633_JSCompiler_temp$jscomp$634_JSCompiler_temp$jscomp$635_prev = hist.lastModTime == hist.lastSelTime && hist.lastOrigin == origin)) {
      JSCompiler_temp$jscomp$633_JSCompiler_temp$jscomp$634_JSCompiler_temp$jscomp$635_prev = lst(hist.done);
      var ch = origin.charAt(0);
      JSCompiler_temp$jscomp$633_JSCompiler_temp$jscomp$634_JSCompiler_temp$jscomp$635_prev = "*" == ch || "+" == ch && JSCompiler_temp$jscomp$633_JSCompiler_temp$jscomp$634_JSCompiler_temp$jscomp$635_prev.ranges.length == sel$jscomp$7_sel.ranges.length && JSCompiler_temp$jscomp$633_JSCompiler_temp$jscomp$634_JSCompiler_temp$jscomp$635_prev.somethingSelected() == sel$jscomp$7_sel.somethingSelected() && new Date() - doc.history.lastSelTime <= (doc.cm ? doc.cm.options.historyEventDelay : 500);
    }
    JSCompiler_temp$jscomp$633_JSCompiler_temp$jscomp$634_JSCompiler_temp$jscomp$635_prev ? hist.done[hist.done.length - 1] = sel$jscomp$7_sel : pushSelectionToHistory(sel$jscomp$7_sel, hist.done);
    hist.lastSelTime = +new Date();
    hist.lastSelOrigin = origin;
    hist.lastSelOp = opId;
    options && !1 !== options.clearRedo && clearSelectionEvents(hist.undone);
  }
  function setSelectionNoUndo(doc, sel, options) {
    if (hasHandler(doc, "beforeSelectionChange") || doc.cm && hasHandler(doc.cm, "beforeSelectionChange")) {
      sel = filterSelectionChange(doc, sel, options);
    }
    var bias = options && options.bias || (0 > cmp(sel.primary().head, doc.sel.primary().head) ? -1 : 1);
    setSelectionInner(doc, skipAtomicInSelection(doc, sel, bias, !0));
    options && !1 === options.scroll || !doc.cm || "nocursor" == doc.cm.getOption("readOnly") || ensureCursorVisible(doc.cm);
  }
  function setSelectionInner(doc, sel) {
    sel.equals(doc.sel) || (doc.sel = sel, doc.cm && (doc.cm.curOp.updateInput = 1, doc.cm.curOp.selectionChanged = !0, signalCursorActivity(doc.cm)), signalLater(doc, "cursorActivity", doc));
  }
  function reCheckSelection(doc) {
    setSelectionInner(doc, skipAtomicInSelection(doc, doc.sel, null, !1));
  }
  function skipAtomicInSelection(doc, sel, bias, mayClear) {
    for (var out, i = 0; i < sel.ranges.length; i++) {
      var range = sel.ranges[i], newHead_old = sel.ranges.length == doc.sel.ranges.length && doc.sel.ranges[i], newAnchor = skipAtomic(doc, range.anchor, newHead_old && newHead_old.anchor, bias, mayClear);
      newHead_old = range.head == range.anchor ? newAnchor : skipAtomic(doc, range.head, newHead_old && newHead_old.head, bias, mayClear);
      if (out || newAnchor != range.anchor || newHead_old != range.head) {
        out || (out = sel.ranges.slice(0, i)), out[i] = new Range(newAnchor, newHead_old);
      }
    }
    return out ? normalizeSelection(doc.cm, out, sel.primIndex) : sel;
  }
  function skipAtomicInner(doc, pos, far_oldPos, dir, mayClear) {
    var line = getLine(doc, pos.line);
    if (line.markedSpans) {
      for (var i = 0; i < line.markedSpans.length; ++i) {
        var diff$jscomp$4_sp = line.markedSpans[i], m = diff$jscomp$4_sp.marker, preventCursorLeft = "selectLeft" in m ? !m.selectLeft : m.inclusiveLeft, preventCursorRight = "selectRight" in m ? !m.selectRight : m.inclusiveRight;
        if ((null == diff$jscomp$4_sp.from || (preventCursorLeft ? diff$jscomp$4_sp.from <= pos.ch : diff$jscomp$4_sp.from < pos.ch)) && (null == diff$jscomp$4_sp.to || (preventCursorRight ? diff$jscomp$4_sp.to >= pos.ch : diff$jscomp$4_sp.to > pos.ch))) {
          if (mayClear && (signal(m, "beforeCursorEnter"), m.explicitlyCleared)) {
            if (line.markedSpans) {
              --i;
              continue;
            } else {
              break;
            }
          }
          if (m.atomic) {
            if (far_oldPos) {
              i = m.find(0 > dir ? 1 : -1);
              diff$jscomp$4_sp = void 0;
              if (0 > dir ? preventCursorRight : preventCursorLeft) {
                i = movePos(doc, i, -dir, i && i.line == pos.line ? line : null);
              }
              if (i && i.line == pos.line && (diff$jscomp$4_sp = cmp(i, far_oldPos)) && (0 > dir ? 0 > diff$jscomp$4_sp : 0 < diff$jscomp$4_sp)) {
                return skipAtomicInner(doc, i, pos, dir, mayClear);
              }
            }
            far_oldPos = m.find(0 > dir ? -1 : 1);
            if (0 > dir ? preventCursorLeft : preventCursorRight) {
              far_oldPos = movePos(doc, far_oldPos, dir, far_oldPos.line == pos.line ? line : null);
            }
            return far_oldPos ? skipAtomicInner(doc, far_oldPos, pos, dir, mayClear) : null;
          }
        }
      }
    }
    return pos;
  }
  function skipAtomic(doc, found$jscomp$14_pos, oldPos, bias$jscomp$9_dir, mayClear) {
    bias$jscomp$9_dir = bias$jscomp$9_dir || 1;
    found$jscomp$14_pos = skipAtomicInner(doc, found$jscomp$14_pos, oldPos, bias$jscomp$9_dir, mayClear) || !mayClear && skipAtomicInner(doc, found$jscomp$14_pos, oldPos, bias$jscomp$9_dir, !0) || skipAtomicInner(doc, found$jscomp$14_pos, oldPos, -bias$jscomp$9_dir, mayClear) || !mayClear && skipAtomicInner(doc, found$jscomp$14_pos, oldPos, -bias$jscomp$9_dir, !0);
    return found$jscomp$14_pos ? found$jscomp$14_pos : (doc.cantEdit = !0, Pos(doc.first, 0));
  }
  function movePos(doc, pos, dir, line) {
    return 0 > dir && 0 == pos.ch ? pos.line > doc.first ? clipPos(doc, Pos(pos.line - 1)) : null : 0 < dir && pos.ch == (line || getLine(doc, pos.line)).text.length ? pos.line < doc.first + doc.size - 1 ? Pos(pos.line + 1, 0) : null : new Pos(pos.line, pos.ch + dir);
  }
  function selectAll(cm) {
    cm.setSelection(Pos(cm.firstLine(), 0), Pos(cm.lastLine()), sel_dontScroll);
  }
  function filterChange(doc, change, update) {
    var obj = {canceled:!1, from:change.from, to:change.to, text:change.text, origin:change.origin, cancel:function() {
      return obj.canceled = !0;
    }};
    update && (obj.update = function(from, to, text, origin) {
      from && (obj.from = clipPos(doc, from));
      to && (obj.to = clipPos(doc, to));
      text && (obj.text = text);
      void 0 !== origin && (obj.origin = origin);
    });
    signal(doc, "beforeChange", doc, obj);
    doc.cm && signal(doc.cm, "beforeChange", doc.cm, obj);
    return obj.canceled ? (doc.cm && (doc.cm.curOp.updateInput = 2), null) : {from:obj.from, to:obj.to, text:obj.text, origin:obj.origin};
  }
  function makeChange(doc, change, ignoreReadOnly_split) {
    if (doc.cm) {
      if (!doc.cm.curOp) {
        return operation(doc.cm, makeChange)(doc, change, ignoreReadOnly_split);
      }
      if (doc.cm.state.suppressEdits) {
        return;
      }
    }
    if (hasHandler(doc, "beforeChange") || doc.cm && hasHandler(doc.cm, "beforeChange")) {
      if (change = filterChange(doc, change, !0), !change) {
        return;
      }
    }
    if (ignoreReadOnly_split = sawReadOnlySpans && !ignoreReadOnly_split && removeReadOnlyRanges(doc, change.from, change.to)) {
      for (var i = ignoreReadOnly_split.length - 1; 0 <= i; --i) {
        makeChangeInner(doc, {from:ignoreReadOnly_split[i].from, to:ignoreReadOnly_split[i].to, text:i ? [""] : change.text, origin:change.origin});
      }
    } else {
      makeChangeInner(doc, change);
    }
  }
  function makeChangeInner(doc$jscomp$0, change) {
    if (1 != change.text.length || "" != change.text[0] || 0 != cmp(change.from, change.to)) {
      var selAfter = computeSelAfterChange(doc$jscomp$0, change);
      addChangeToHistory(doc$jscomp$0, change, selAfter, doc$jscomp$0.cm ? doc$jscomp$0.cm.curOp.id : NaN);
      makeChangeSingleDoc(doc$jscomp$0, change, selAfter, stretchSpansOverChange(doc$jscomp$0, change));
      var rebased = [];
      linkedDocs(doc$jscomp$0, function(doc, sharedHist) {
        sharedHist || -1 != indexOf(rebased, doc.history) || (rebaseHist(doc.history, change), rebased.push(doc.history));
        makeChangeSingleDoc(doc, change, null, stretchSpansOverChange(doc, change));
      });
    }
  }
  function makeChangeFromHistory(doc$jscomp$0, type, allowSelectionOnly_loop) {
    var i$1 = doc$jscomp$0.cm && doc$jscomp$0.cm.state.suppressEdits;
    if (!i$1 || allowSelectionOnly_loop) {
      for (var hist = doc$jscomp$0.history, event, selAfter = doc$jscomp$0.sel, source = "undo" == type ? hist.done : hist.undone, dest = "undo" == type ? hist.undone : hist.done, i$jscomp$0 = 0; i$jscomp$0 < source.length && (event = source[i$jscomp$0], allowSelectionOnly_loop ? !event.ranges || event.equals(doc$jscomp$0.sel) : event.ranges); i$jscomp$0++) {
      }
      if (i$jscomp$0 != source.length) {
        for (hist.lastOrigin = hist.lastSelOrigin = null;;) {
          if (event = source.pop(), event.ranges) {
            pushSelectionToHistory(event, dest);
            if (allowSelectionOnly_loop && !event.equals(doc$jscomp$0.sel)) {
              setSelection(doc$jscomp$0, event, {clearRedo:!1});
              return;
            }
            selAfter = event;
          } else {
            if (i$1) {
              source.push(event);
              return;
            }
            break;
          }
        }
        var antiChanges = [];
        pushSelectionToHistory(selAfter, dest);
        dest.push({changes:antiChanges, generation:hist.generation});
        hist.generation = event.generation || ++hist.maxGeneration;
        var filter = hasHandler(doc$jscomp$0, "beforeChange") || doc$jscomp$0.cm && hasHandler(doc$jscomp$0.cm, "beforeChange");
        allowSelectionOnly_loop = function(i) {
          var change = event.changes[i];
          change.origin = type;
          if (filter && !filterChange(doc$jscomp$0, change, !1)) {
            return source.length = 0, {};
          }
          antiChanges.push(historyChangeFromChange(doc$jscomp$0, change));
          var after = i ? computeSelAfterChange(doc$jscomp$0, change) : lst(source);
          makeChangeSingleDoc(doc$jscomp$0, change, after, mergeOldSpans(doc$jscomp$0, change));
          !i && doc$jscomp$0.cm && doc$jscomp$0.cm.scrollIntoView({from:change.from, to:changeEnd(change)});
          var rebased = [];
          linkedDocs(doc$jscomp$0, function(doc, sharedHist) {
            sharedHist || -1 != indexOf(rebased, doc.history) || (rebaseHist(doc.history, change), rebased.push(doc.history));
            makeChangeSingleDoc(doc, change, null, mergeOldSpans(doc, change));
          });
        };
        for (i$1 = event.changes.length - 1; 0 <= i$1; --i$1) {
          if (hist = allowSelectionOnly_loop(i$1)) {
            return hist.v;
          }
        }
      }
    }
  }
  function shiftDoc(doc, distance) {
    if (0 != distance && (doc.first += distance, doc.sel = new Selection(map$jscomp$0(doc.sel.ranges, function(range) {
      return new Range(Pos(range.anchor.line + distance, range.anchor.ch), Pos(range.head.line + distance, range.head.ch));
    }), doc.sel.primIndex), doc.cm)) {
      regChange(doc.cm, doc.first, doc.first - distance, distance);
      for (var d = doc.cm.display, l = d.viewFrom; l < d.viewTo; l++) {
        regLineChange(doc.cm, l, "gutter");
      }
    }
  }
  function makeChangeSingleDoc(doc, change, selAfter, spans) {
    if (doc.cm && !doc.cm.curOp) {
      return operation(doc.cm, makeChangeSingleDoc)(doc, change, selAfter, spans);
    }
    if (change.to.line < doc.first) {
      shiftDoc(doc, change.text.length - 1 - (change.to.line - change.from.line));
    } else {
      if (!(change.from.line > doc.lastLine())) {
        if (change.from.line < doc.first) {
          var last = change.text.length - 1 - (doc.first - change.from.line);
          shiftDoc(doc, last);
          change = {from:Pos(doc.first, 0), to:Pos(change.to.line + last, change.to.ch), text:[lst(change.text)], origin:change.origin};
        }
        last = doc.lastLine();
        change.to.line > last && (change = {from:change.from, to:Pos(last, getLine(doc, last).text.length), text:[change.text[0]], origin:change.origin});
        change.removed = getBetween(doc, change.from, change.to);
        selAfter || (selAfter = computeSelAfterChange(doc, change));
        doc.cm ? makeChangeSingleDocInEditor(doc.cm, change, spans) : updateDoc(doc, change, spans);
        setSelectionNoUndo(doc, selAfter, sel_dontScroll);
        doc.cantEdit && skipAtomic(doc, Pos(doc.firstLine(), 0)) && (doc.cantEdit = !1);
      }
    }
  }
  function makeChangeSingleDocInEditor(cm, change$jscomp$20_obj, changesHandler_lendiff$jscomp$1_spans) {
    var changeHandler_doc = cm.doc, display = cm.display, from = change$jscomp$20_obj.from, to = change$jscomp$20_obj.to, recomputeMaxLength = !1, checkWidthStart = from.line;
    cm.options.lineWrapping || (checkWidthStart = lineNo(visualLine(getLine(changeHandler_doc, from.line))), changeHandler_doc.iter(checkWidthStart, to.line + 1, function(line) {
      if (line == display.maxLine) {
        return recomputeMaxLength = !0;
      }
    }));
    -1 < changeHandler_doc.sel.contains(change$jscomp$20_obj.from, change$jscomp$20_obj.to) && signalCursorActivity(cm);
    updateDoc(changeHandler_doc, change$jscomp$20_obj, changesHandler_lendiff$jscomp$1_spans, estimateHeight(cm));
    cm.options.lineWrapping || (changeHandler_doc.iter(checkWidthStart, from.line + change$jscomp$20_obj.text.length, function(line) {
      var len = lineLength(line);
      len > display.maxLineLength && (display.maxLine = line, display.maxLineLength = len, display.maxLineChanged = !0, recomputeMaxLength = !1);
    }), recomputeMaxLength && (cm.curOp.updateMaxLine = !0));
    retreatFrontier(changeHandler_doc, from.line);
    startWorker(cm, 400);
    changesHandler_lendiff$jscomp$1_spans = change$jscomp$20_obj.text.length - (to.line - from.line) - 1;
    change$jscomp$20_obj.full ? regChange(cm) : from.line != to.line || 1 != change$jscomp$20_obj.text.length || isWholeLineUpdate(cm.doc, change$jscomp$20_obj) ? regChange(cm, from.line, to.line + 1, changesHandler_lendiff$jscomp$1_spans) : regLineChange(cm, from.line, "text");
    changesHandler_lendiff$jscomp$1_spans = hasHandler(cm, "changes");
    if ((changeHandler_doc = hasHandler(cm, "change")) || changesHandler_lendiff$jscomp$1_spans) {
      change$jscomp$20_obj = {from, to, text:change$jscomp$20_obj.text, removed:change$jscomp$20_obj.removed, origin:change$jscomp$20_obj.origin}, changeHandler_doc && signalLater(cm, "change", cm, change$jscomp$20_obj), changesHandler_lendiff$jscomp$1_spans && (cm.curOp.changeObjs || (cm.curOp.changeObjs = [])).push(change$jscomp$20_obj);
    }
    cm.display.selForContextMenu = null;
  }
  function replaceRange(doc, code, from, assign_to, origin) {
    assign_to || (assign_to = from);
    0 > cmp(assign_to, from) && (assign_to = [assign_to, from], from = assign_to[0], assign_to = assign_to[1]);
    "string" == typeof code && (code = doc.splitLines(code));
    makeChange(doc, {from, to:assign_to, text:code, origin});
  }
  function rebaseHistSelSingle(pos, from, to, diff) {
    to < pos.line ? pos.line += diff : from < pos.line && (pos.line = from, pos.ch = 0);
  }
  function rebaseHistArray(array, from, to, diff) {
    for (var i = 0; i < array.length; ++i) {
      var sub = array[i], j$jscomp$17_ok = !0;
      if (sub.ranges) {
        for (sub.copied || (sub = array[i] = sub.deepCopy(), sub.copied = !0), j$jscomp$17_ok = 0; j$jscomp$17_ok < sub.ranges.length; j$jscomp$17_ok++) {
          rebaseHistSelSingle(sub.ranges[j$jscomp$17_ok].anchor, from, to, diff), rebaseHistSelSingle(sub.ranges[j$jscomp$17_ok].head, from, to, diff);
        }
      } else {
        for (var j$1 = 0; j$1 < sub.changes.length; ++j$1) {
          var cur = sub.changes[j$1];
          if (to < cur.from.line) {
            cur.from = Pos(cur.from.line + diff, cur.from.ch), cur.to = Pos(cur.to.line + diff, cur.to.ch);
          } else if (from <= cur.to.line) {
            j$jscomp$17_ok = !1;
            break;
          }
        }
        j$jscomp$17_ok || (array.splice(0, i + 1), i = 0);
      }
    }
  }
  function rebaseHist(hist, change$jscomp$21_diff) {
    var from = change$jscomp$21_diff.from.line, to = change$jscomp$21_diff.to.line;
    change$jscomp$21_diff = change$jscomp$21_diff.text.length - (to - from) - 1;
    rebaseHistArray(hist.done, from, to, change$jscomp$21_diff);
    rebaseHistArray(hist.undone, from, to, change$jscomp$21_diff);
  }
  function changeLine(doc, handle, changeType, op) {
    var no = handle, line = handle;
    "number" == typeof handle ? line = getLine(doc, Math.max(doc.first, Math.min(handle, doc.first + doc.size - 1))) : no = lineNo(handle);
    if (null == no) {
      return null;
    }
    op(line, no) && doc.cm && regLineChange(doc.cm, no, changeType);
    return line;
  }
  function LeafChunk(lines) {
    this.lines = lines;
    this.parent = null;
    for (var height = 0, i = 0; i < lines.length; ++i) {
      lines[i].parent = this, height += lines[i].height;
    }
    this.height = height;
  }
  function BranchChunk(children) {
    this.children = children;
    for (var size = 0, height = 0, i = 0; i < children.length; ++i) {
      var ch = children[i];
      size += ch.chunkSize();
      height += ch.height;
      ch.parent = this;
    }
    this.size = size;
    this.height = height;
    this.parent = null;
  }
  function addLineWidget(doc, handle, node, options) {
    var widget = new LineWidget(doc, node, options), cm = doc.cm;
    cm && widget.noHScroll && (cm.display.alignWidgets = !0);
    changeLine(doc, handle, "widget", function(line) {
      var aboveVisible_widgets = line.widgets || (line.widgets = []);
      null == widget.insertAt ? aboveVisible_widgets.push(widget) : aboveVisible_widgets.splice(Math.min(aboveVisible_widgets.length, Math.max(0, widget.insertAt)), 0, widget);
      widget.line = line;
      cm && !lineIsHidden(doc, line) && (aboveVisible_widgets = heightAtLine(line) < doc.scrollTop, updateLineHeight(line, line.height + widgetHeight(widget)), aboveVisible_widgets && addToScrollTop(cm, widget.height), cm.curOp.forceUpdate = !0);
      return !0;
    });
    cm && signalLater(cm, "lineWidgetAdded", cm, widget, "number" == typeof handle ? handle : lineNo(handle));
    return widget;
  }
  function markText(doc, from, to, i$jscomp$231_options, diff$jscomp$10_type) {
    if (i$jscomp$231_options && i$jscomp$231_options.shared) {
      return markTextShared(doc, from, to, i$jscomp$231_options, diff$jscomp$10_type);
    }
    if (doc.cm && !doc.cm.curOp) {
      return operation(doc.cm, markText)(doc, from, to, i$jscomp$231_options, diff$jscomp$10_type);
    }
    var marker = new TextMarker(doc, diff$jscomp$10_type);
    diff$jscomp$10_type = cmp(from, to);
    i$jscomp$231_options && copyObj(i$jscomp$231_options, marker, !1);
    if (0 < diff$jscomp$10_type || 0 == diff$jscomp$10_type && !1 !== marker.clearWhenEmpty) {
      return marker;
    }
    marker.replacedWith && (marker.collapsed = !0, marker.widgetNode = eltP("span", [marker.replacedWith], "CodeMirror-widget"), i$jscomp$231_options.handleMouseEvents || marker.widgetNode.setAttribute("cm-ignore-events", "true"), i$jscomp$231_options.insertLeft && (marker.widgetNode.insertLeft = !0));
    if (marker.collapsed) {
      if (conflictingCollapsedRange(doc, from.line, from, to, marker) || from.line != to.line && conflictingCollapsedRange(doc, to.line, from, to, marker)) {
        throw Error("Inserting collapsed marker partially overlapping an existing one");
      }
      sawCollapsedSpans = !0;
    }
    marker.addToHistory && addChangeToHistory(doc, {from, to, origin:"markText"}, doc.sel, NaN);
    var curLine = from.line, cm = doc.cm, updateMaxLine;
    doc.iter(curLine, to.line + 1, function(line) {
      cm && marker.collapsed && !cm.options.lineWrapping && visualLine(line) == cm.display.maxLine && (updateMaxLine = !0);
      marker.collapsed && curLine != from.line && updateLineHeight(line, 0);
      var span = new MarkedSpan(marker, curLine == from.line ? from.ch : null, curLine == to.line ? to.ch : null), inThisOp$jscomp$inline_412_op = doc.cm && doc.cm.curOp;
      (inThisOp$jscomp$inline_412_op = inThisOp$jscomp$inline_412_op && window.WeakSet && (inThisOp$jscomp$inline_412_op.markedSpans || (inThisOp$jscomp$inline_412_op.markedSpans = new WeakSet()))) && line.markedSpans && inThisOp$jscomp$inline_412_op.has(line.markedSpans) ? line.markedSpans.push(span) : (line.markedSpans = line.markedSpans ? line.markedSpans.concat([span]) : [span], inThisOp$jscomp$inline_412_op && inThisOp$jscomp$inline_412_op.add(line.markedSpans));
      span.marker.attachLine(line);
      ++curLine;
    });
    marker.collapsed && doc.iter(from.line, to.line + 1, function(line) {
      lineIsHidden(doc, line) && updateLineHeight(line, 0);
    });
    marker.clearOnEnter && on(marker, "beforeCursorEnter", function() {
      return marker.clear();
    });
    marker.readOnly && (sawReadOnlySpans = !0, (doc.history.done.length || doc.history.undone.length) && doc.clearHistory());
    marker.collapsed && (marker.id = ++nextMarkerId, marker.atomic = !0);
    if (cm) {
      updateMaxLine && (cm.curOp.updateMaxLine = !0);
      if (marker.collapsed) {
        regChange(cm, from.line, to.line + 1);
      } else if (marker.className || marker.startStyle || marker.endStyle || marker.css || marker.attributes || marker.title) {
        for (i$jscomp$231_options = from.line; i$jscomp$231_options <= to.line; i$jscomp$231_options++) {
          regLineChange(cm, i$jscomp$231_options, "text");
        }
      }
      marker.atomic && reCheckSelection(cm.doc);
      signalLater(cm, "markerAdded", cm, marker);
    }
    return marker;
  }
  function markTextShared(doc$jscomp$0, from, to, options, type) {
    options = copyObj(options);
    options.shared = !1;
    var markers = [markText(doc$jscomp$0, from, to, options, type)], primary = markers[0], widget = options.widgetNode;
    linkedDocs(doc$jscomp$0, function(doc) {
      widget && (options.widgetNode = widget.cloneNode(!0));
      markers.push(markText(doc, clipPos(doc, from), clipPos(doc, to), options, type));
      for (var i = 0; i < doc.linked.length; ++i) {
        if (doc.linked[i].isParent) {
          return;
        }
      }
      primary = lst(markers);
    });
    return new SharedTextMarker(markers, primary);
  }
  function findSharedMarkers(doc) {
    return doc.findMarks(Pos(doc.first, 0), doc.clipPos(Pos(doc.lastLine())), function(m) {
      return m.parent;
    });
  }
  function detachSharedMarkers(markers) {
    function loop(i$jscomp$237_marker) {
      i$jscomp$237_marker = markers[i$jscomp$237_marker];
      var linked = [i$jscomp$237_marker.primary.doc];
      linkedDocs(i$jscomp$237_marker.primary.doc, function(d) {
        return linked.push(d);
      });
      for (var j = 0; j < i$jscomp$237_marker.markers.length; j++) {
        var subMarker = i$jscomp$237_marker.markers[j];
        -1 == indexOf(linked, subMarker.doc) && (subMarker.parent = null, i$jscomp$237_marker.markers.splice(j--, 1));
      }
    }
    for (var i = 0; i < markers.length; i++) {
      loop(i);
    }
  }
  function onDrop(e) {
    var cm = this;
    clearDragCursor(cm);
    if (!signalDOMEvent(cm, e) && !eventInWidget(cm.display, e)) {
      e_preventDefault(e);
      ie && (lastDrop = +new Date());
      var pos = posFromMouse(cm, e, !0), files_i$1 = e.dataTransfer.files;
      if (pos && !cm.isReadOnly()) {
        if (files_i$1 && files_i$1.length && window.FileReader && window.File) {
          for (var n = files_i$1.length, text = Array(n), read = 0, markAsReadAndPasteIfAllFilesAreRead = function() {
            ++read == n && operation(cm, function() {
              pos = clipPos(cm.doc, pos);
              var change = {from:pos, to:pos, text:cm.doc.splitLines(text.filter(function(t) {
                return null != t;
              }).join(cm.doc.lineSeparator())), origin:"paste"};
              makeChange(cm.doc, change);
              setSelectionReplaceHistory(cm.doc, simpleSelection(clipPos(cm.doc, pos), clipPos(cm.doc, changeEnd(change))));
            })();
          }, readTextFromFile_text$1 = function(file, i) {
            if (cm.options.allowDropFileTypes && -1 == indexOf(cm.options.allowDropFileTypes, file.type)) {
              markAsReadAndPasteIfAllFilesAreRead();
            } else {
              var reader = new FileReader();
              reader.onerror = function() {
                return markAsReadAndPasteIfAllFilesAreRead();
              };
              reader.onload = function() {
                var content = reader.result;
                /[\x00-\x08\x0e-\x1f]{2}/.test(content) || (text[i] = content);
                markAsReadAndPasteIfAllFilesAreRead();
              };
              reader.readAsText(file);
            }
          }, i$jscomp$0 = 0; i$jscomp$0 < files_i$1.length; i$jscomp$0++) {
            readTextFromFile_text$1(files_i$1[i$jscomp$0], i$jscomp$0);
          }
        } else {
          if (cm.state.draggingText && -1 < cm.doc.sel.contains(pos)) {
            cm.state.draggingText(e), setTimeout(function() {
              return cm.display.input.focus();
            }, 20);
          } else {
            try {
              if (readTextFromFile_text$1 = e.dataTransfer.getData("Text")) {
                cm.state.draggingText && !cm.state.draggingText.copy && (i$jscomp$0 = cm.listSelections());
                setSelectionNoUndo(cm.doc, simpleSelection(pos, pos));
                if (i$jscomp$0) {
                  for (files_i$1 = 0; files_i$1 < i$jscomp$0.length; ++files_i$1) {
                    replaceRange(cm.doc, "", i$jscomp$0[files_i$1].anchor, i$jscomp$0[files_i$1].head, "drag");
                  }
                }
                cm.replaceSelection(readTextFromFile_text$1, "around", "paste");
                cm.display.input.focus();
              }
            } catch (e$1) {
            }
          }
        }
      }
    }
  }
  function clearDragCursor(cm) {
    cm.display.dragCursor && (cm.display.lineSpace.removeChild(cm.display.dragCursor), cm.display.dragCursor = null);
  }
  function forEachCodeMirror(f) {
    if (document.getElementsByClassName) {
      for (var byClass = document.getElementsByClassName("CodeMirror"), editors = [], i$jscomp$0 = 0; i$jscomp$0 < byClass.length; i$jscomp$0++) {
        var cm = byClass[i$jscomp$0].CodeMirror;
        cm && editors.push(cm);
      }
      editors.length && editors[0].operation(function() {
        for (var i = 0; i < editors.length; i++) {
          f(editors[i]);
        }
      });
    }
  }
  function registerGlobalHandlers() {
    var resizeTimer;
    on(window, "resize", function() {
      null == resizeTimer && (resizeTimer = setTimeout(function() {
        resizeTimer = null;
        forEachCodeMirror(onResize);
      }, 100));
    });
    on(window, "blur", function() {
      return forEachCodeMirror(onBlur);
    });
  }
  function onResize(cm) {
    var d = cm.display;
    d.cachedCharWidth = d.cachedTextHeight = d.cachedPaddingH = null;
    d.scrollbarsClipped = !1;
    cm.setSize();
  }
  function normalizeKeyName(name) {
    var parts = name.split(/-(?!$)/);
    name = parts[parts.length - 1];
    for (var alt, ctrl, shift, cmd, i = 0; i < parts.length - 1; i++) {
      var mod = parts[i];
      if (/^(cmd|meta|m)$/i.test(mod)) {
        cmd = !0;
      } else if (/^a(lt)?$/i.test(mod)) {
        alt = !0;
      } else if (/^(c|ctrl|control)$/i.test(mod)) {
        ctrl = !0;
      } else if (/^s(hift)?$/i.test(mod)) {
        shift = !0;
      } else {
        throw Error("Unrecognized modifier name: " + mod);
      }
    }
    alt && (name = "Alt-" + name);
    ctrl && (name = "Ctrl-" + name);
    cmd && (name = "Cmd-" + name);
    shift && (name = "Shift-" + name);
    return name;
  }
  function normalizeKeyMap(keymap) {
    var copy = {}, keyname;
    for (keyname in keymap) {
      if (keymap.hasOwnProperty(keyname)) {
        var value = keymap[keyname];
        if (!/^(name|fallthrough|(de|at)tach)$/.test(keyname)) {
          if ("..." != value) {
            for (var keys = map$jscomp$0(keyname.split(" "), normalizeKeyName), i = 0; i < keys.length; i++) {
              if (i == keys.length - 1) {
                var name = keys.join(" ");
                var val = value;
              } else {
                name = keys.slice(0, i + 1).join(" "), val = "...";
              }
              var prev = copy[name];
              if (!prev) {
                copy[name] = val;
              } else if (prev != val) {
                throw Error("Inconsistent bindings for " + name);
              }
            }
          }
          delete keymap[keyname];
        }
      }
    }
    for (var prop in copy) {
      keymap[prop] = copy[prop];
    }
    return keymap;
  }
  function lookupKey(key, map, handle, context) {
    map = getKeyMap(map);
    var found$jscomp$18_i = map.call ? map.call(key, context) : map[key];
    if (!1 === found$jscomp$18_i) {
      return "nothing";
    }
    if ("..." === found$jscomp$18_i) {
      return "multi";
    }
    if (null != found$jscomp$18_i && handle(found$jscomp$18_i)) {
      return "handled";
    }
    if (map.fallthrough) {
      if ("[object Array]" != Object.prototype.toString.call(map.fallthrough)) {
        return lookupKey(key, map.fallthrough, handle, context);
      }
      for (found$jscomp$18_i = 0; found$jscomp$18_i < map.fallthrough.length; found$jscomp$18_i++) {
        var result = lookupKey(key, map.fallthrough[found$jscomp$18_i], handle, context);
        if (result) {
          return result;
        }
      }
    }
  }
  function isModifierKey(name$jscomp$147_value) {
    name$jscomp$147_value = "string" == typeof name$jscomp$147_value ? name$jscomp$147_value : keyNames[name$jscomp$147_value.keyCode];
    return "Ctrl" == name$jscomp$147_value || "Alt" == name$jscomp$147_value || "Shift" == name$jscomp$147_value || "Mod" == name$jscomp$147_value;
  }
  function addModifierNames(name, event, noShift) {
    var base = name;
    event.altKey && "Alt" != base && (name = "Alt-" + name);
    (flipCtrlCmd ? event.metaKey : event.ctrlKey) && "Ctrl" != base && (name = "Ctrl-" + name);
    (flipCtrlCmd ? event.ctrlKey : event.metaKey) && "Mod" != base && (name = "Cmd-" + name);
    !noShift && event.shiftKey && "Shift" != base && (name = "Shift-" + name);
    return name;
  }
  function keyName(event, noShift) {
    if (presto && 34 == event.keyCode && event["char"]) {
      return !1;
    }
    var name = keyNames[event.keyCode];
    if (null == name || event.altGraphKey) {
      return !1;
    }
    3 == event.keyCode && event.code && (name = event.code);
    return addModifierNames(name, event, noShift);
  }
  function getKeyMap(val) {
    return "string" == typeof val ? keyMap[val] : val;
  }
  function deleteNearSelection(cm, compute) {
    for (var ranges = cm.doc.sel.ranges, kill = [], i$jscomp$0 = 0; i$jscomp$0 < ranges.length; i$jscomp$0++) {
      for (var toKill = compute(ranges[i$jscomp$0]); kill.length && 0 >= cmp(toKill.from, lst(kill).to);) {
        var replaced = kill.pop();
        if (0 > cmp(replaced.from, toKill.from)) {
          toKill.from = replaced.from;
          break;
        }
      }
      kill.push(toKill);
    }
    runInOp(cm, function() {
      for (var i = kill.length - 1; 0 <= i; i--) {
        replaceRange(cm.doc, "", kill[i].from, kill[i].to, "+delete");
      }
      ensureCursorVisible(cm);
    });
  }
  function moveCharLogically(line, ch$jscomp$37_target, dir) {
    ch$jscomp$37_target = skipExtendingChars(line.text, ch$jscomp$37_target + dir, dir);
    return 0 > ch$jscomp$37_target || ch$jscomp$37_target > line.text.length ? null : ch$jscomp$37_target;
  }
  function moveLogically(ch$jscomp$38_line, start, dir) {
    ch$jscomp$38_line = moveCharLogically(ch$jscomp$38_line, start.ch, dir);
    return null == ch$jscomp$38_line ? null : new Pos(start.line, ch$jscomp$38_line, 0 > dir ? "after" : "before");
  }
  function endOfLine(order$jscomp$12_part$jscomp$12_visually, cm, lineObj, lineNo, dir) {
    if (order$jscomp$12_part$jscomp$12_visually && ("rtl" == cm.doc.direction && (dir = -dir), order$jscomp$12_part$jscomp$12_visually = getOrder(lineObj, cm.doc.direction))) {
      order$jscomp$12_part$jscomp$12_visually = 0 > dir ? lst(order$jscomp$12_part$jscomp$12_visually) : order$jscomp$12_part$jscomp$12_visually[0];
      var sticky = 0 > dir == (1 == order$jscomp$12_part$jscomp$12_visually.level) ? "after" : "before";
      if (0 < order$jscomp$12_part$jscomp$12_visually.level || "rtl" == cm.doc.direction) {
        var prep = prepareMeasureForLine(cm, lineObj);
        var ch$jscomp$0 = 0 > dir ? lineObj.text.length - 1 : 0;
        var targetTop = measureCharPrepared(cm, prep, ch$jscomp$0).top;
        ch$jscomp$0 = findFirst(function(ch) {
          return measureCharPrepared(cm, prep, ch).top == targetTop;
        }, 0 > dir == (1 == order$jscomp$12_part$jscomp$12_visually.level) ? order$jscomp$12_part$jscomp$12_visually.from : order$jscomp$12_part$jscomp$12_visually.to - 1, ch$jscomp$0);
        "before" == sticky && (ch$jscomp$0 = moveCharLogically(lineObj, ch$jscomp$0, 1));
      } else {
        ch$jscomp$0 = 0 > dir ? order$jscomp$12_part$jscomp$12_visually.to : order$jscomp$12_part$jscomp$12_visually.from;
      }
      return new Pos(lineNo, ch$jscomp$0, sticky);
    }
    return new Pos(lineNo, 0 > dir ? lineObj.text.length : 0, 0 > dir ? "before" : "after");
  }
  function moveVisually(cm, line, start, dir$jscomp$0) {
    function searchInVisualLine(partPos, dir, wrappedLineExtent) {
      function getRes(ch, moveInStorageOrder) {
        return moveInStorageOrder ? new Pos(start.line, mv(ch, 1), "before") : new Pos(start.line, ch, "after");
      }
      for (; 0 <= partPos && partPos < bidi.length; partPos += dir) {
        var part = bidi[partPos], moveInStorageOrder$jscomp$0 = 0 < dir == (1 != part.level), ch$jscomp$0 = moveInStorageOrder$jscomp$0 ? wrappedLineExtent.begin : mv(wrappedLineExtent.end, -1);
        if (part.from <= ch$jscomp$0 && ch$jscomp$0 < part.to) {
          return getRes(ch$jscomp$0, moveInStorageOrder$jscomp$0);
        }
        ch$jscomp$0 = moveInStorageOrder$jscomp$0 ? part.from : mv(part.to, -1);
        if (wrappedLineExtent.begin <= ch$jscomp$0 && ch$jscomp$0 < wrappedLineExtent.end) {
          return getRes(ch$jscomp$0, moveInStorageOrder$jscomp$0);
        }
      }
    }
    function getWrappedLineExtent(ch) {
      if (!cm.options.lineWrapping) {
        return {begin:0, end:line.text.length};
      }
      prep = prep || prepareMeasureForLine(cm, line);
      return wrappedLineExtentChar(cm, line, prep, ch);
    }
    function mv(pos, dir) {
      return moveCharLogically(line, pos instanceof Pos ? pos.ch : pos, dir);
    }
    var bidi = getOrder(line, cm.doc.direction);
    if (!bidi) {
      return moveLogically(line, start, dir$jscomp$0);
    }
    start.ch >= line.text.length ? (start.ch = line.text.length, start.sticky = "before") : 0 >= start.ch && (start.ch = 0, start.sticky = "after");
    var partPos$jscomp$2_res = getBidiPartAt(bidi, start.ch, start.sticky), part$jscomp$0 = bidi[partPos$jscomp$2_res];
    if ("ltr" == cm.doc.direction && 0 == part$jscomp$0.level % 2 && (0 < dir$jscomp$0 ? part$jscomp$0.to > start.ch : part$jscomp$0.from < start.ch)) {
      return moveLogically(line, start, dir$jscomp$0);
    }
    var prep, nextCh_wrappedLineExtent = getWrappedLineExtent("before" == start.sticky ? mv(start, -1) : start.ch);
    if ("rtl" == cm.doc.direction || 1 == part$jscomp$0.level) {
      var moveInStorageOrder$jscomp$2 = 1 == part$jscomp$0.level == 0 > dir$jscomp$0, ch$jscomp$1 = mv(start, moveInStorageOrder$jscomp$2 ? 1 : -1);
      if (null != ch$jscomp$1 && (moveInStorageOrder$jscomp$2 ? ch$jscomp$1 <= part$jscomp$0.to && ch$jscomp$1 <= nextCh_wrappedLineExtent.end : ch$jscomp$1 >= part$jscomp$0.from && ch$jscomp$1 >= nextCh_wrappedLineExtent.begin)) {
        return new Pos(start.line, ch$jscomp$1, moveInStorageOrder$jscomp$2 ? "before" : "after");
      }
    }
    if (partPos$jscomp$2_res = searchInVisualLine(partPos$jscomp$2_res + dir$jscomp$0, dir$jscomp$0, nextCh_wrappedLineExtent)) {
      return partPos$jscomp$2_res;
    }
    nextCh_wrappedLineExtent = 0 < dir$jscomp$0 ? nextCh_wrappedLineExtent.end : mv(nextCh_wrappedLineExtent.begin, -1);
    return null == nextCh_wrappedLineExtent || 0 < dir$jscomp$0 && nextCh_wrappedLineExtent == line.text.length || !(partPos$jscomp$2_res = searchInVisualLine(0 < dir$jscomp$0 ? 0 : bidi.length - 1, dir$jscomp$0, getWrappedLineExtent(nextCh_wrappedLineExtent))) ? null : partPos$jscomp$2_res;
  }
  function lineStart(cm, lineN) {
    var line = getLine(cm.doc, lineN), visual = visualLine(line);
    visual != line && (lineN = lineNo(visual));
    return endOfLine(!0, cm, visual, lineN, 1);
  }
  function lineStartSmart(cm$jscomp$182_order, pos) {
    var start = lineStart(cm$jscomp$182_order, pos.line), firstNonWS_line = getLine(cm$jscomp$182_order.doc, start.line);
    cm$jscomp$182_order = getOrder(firstNonWS_line, cm$jscomp$182_order.doc.direction);
    return cm$jscomp$182_order && 0 != cm$jscomp$182_order[0].level ? start : (firstNonWS_line = Math.max(start.ch, firstNonWS_line.text.search(/\S/)), Pos(start.line, pos.line == start.line && pos.ch <= firstNonWS_line && pos.ch ? 0 : firstNonWS_line, start.sticky));
  }
  function doHandleBinding(cm, bound, dropShift) {
    if ("string" == typeof bound && (bound = commands[bound], !bound)) {
      return !1;
    }
    cm.display.input.ensurePolled();
    var prevShift = cm.display.shift, done = !1;
    try {
      cm.isReadOnly() && (cm.state.suppressEdits = !0), dropShift && (cm.display.shift = !1), done = bound(cm) != Pass;
    } finally {
      cm.display.shift = prevShift, cm.state.suppressEdits = !1;
    }
    return done;
  }
  function dispatchKey(cm, name, e, handle) {
    var seq = cm.state.keySeq;
    if (seq) {
      if (isModifierKey(name)) {
        return "handled";
      }
      /'$/.test(name) ? cm.state.keySeq = null : stopSeq.set(50, function() {
        cm.state.keySeq == seq && (cm.state.keySeq = null, cm.display.input.reset());
      });
      if (dispatchKeyInner(cm, seq + " " + name, e, handle)) {
        return !0;
      }
    }
    return dispatchKeyInner(cm, name, e, handle);
  }
  function dispatchKeyInner(cm, name, e, handle$jscomp$21_result) {
    a: {
      for (var i = 0; i < cm.state.keyMaps.length; i++) {
        var result = lookupKey(name, cm.state.keyMaps[i], handle$jscomp$21_result, cm);
        if (result) {
          handle$jscomp$21_result = result;
          break a;
        }
      }
      handle$jscomp$21_result = cm.options.extraKeys && lookupKey(name, cm.options.extraKeys, handle$jscomp$21_result, cm) || lookupKey(name, cm.options.keyMap, handle$jscomp$21_result, cm);
    }
    "multi" == handle$jscomp$21_result && (cm.state.keySeq = name);
    "handled" == handle$jscomp$21_result && signalLater(cm, "keyHandled", cm, name, e);
    if ("handled" == handle$jscomp$21_result || "multi" == handle$jscomp$21_result) {
      e_preventDefault(e), restartBlink(cm);
    }
    return !!handle$jscomp$21_result;
  }
  function handleKeyBinding(cm, e) {
    var name = keyName(e, !0);
    return name ? e.shiftKey && !cm.state.keySeq ? dispatchKey(cm, "Shift-" + name, e, function(b) {
      return doHandleBinding(cm, b, !0);
    }) || dispatchKey(cm, name, e, function(b) {
      if ("string" == typeof b ? /^go[A-Z]/.test(b) : b.motion) {
        return doHandleBinding(cm, b);
      }
    }) : dispatchKey(cm, name, e, function(b) {
      return doHandleBinding(cm, b);
    }) : !1;
  }
  function handleCharBinding(cm, e, ch) {
    return dispatchKey(cm, "'" + ch + "'", e, function(b) {
      return doHandleBinding(cm, b, !0);
    });
  }
  function onKeyDown(e) {
    if (!e.target || e.target == this.display.input.getField()) {
      if (this.curOp.focus = activeElt(), !signalDOMEvent(this, e)) {
        ie && 11 > ie_version && 27 == e.keyCode && (e.returnValue = !1);
        var code = e.keyCode;
        this.display.shift = 16 == code || e.shiftKey;
        var handled = handleKeyBinding(this, e);
        presto && (lastStoppedKey = handled ? code : null, !handled && 88 == code && !hasCopyEvent && (mac ? e.metaKey : e.ctrlKey) && this.replaceSelection("", null, "cut"));
        gecko && !mac && !handled && 46 == code && e.shiftKey && !e.ctrlKey && document.execCommand && document.execCommand("cut");
        18 != code || /\bCodeMirror-crosshair\b/.test(this.display.lineDiv.className) || showCrossHair(this);
      }
    }
  }
  function showCrossHair(cm) {
    function up(e) {
      18 != e.keyCode && e.altKey || (rmClass(lineDiv, "CodeMirror-crosshair"), off(document, "keyup", up), off(document, "mouseover", up));
    }
    var lineDiv = cm.display.lineDiv;
    addClass(lineDiv, "CodeMirror-crosshair");
    on(document, "keyup", up);
    on(document, "mouseover", up);
  }
  function onKeyPress(e) {
    if (!(e.target && e.target != this.display.input.getField() || eventInWidget(this.display, e) || signalDOMEvent(this, e) || e.ctrlKey && !e.altKey || mac && e.metaKey)) {
      var ch = e.keyCode, charCode = e.charCode;
      if (presto && ch == lastStoppedKey) {
        lastStoppedKey = null, e_preventDefault(e);
      } else {
        if (!presto || e.which && !(10 > e.which) || !handleKeyBinding(this, e)) {
          if (ch = String.fromCharCode(null == charCode ? ch : charCode), "\b" != ch && !handleCharBinding(this, e, ch)) {
            this.display.input.onKeyPress(e);
          }
        }
      }
    }
  }
  function clickRepeat(pos, button) {
    var now = +new Date();
    if (lastDoubleClick && lastDoubleClick.compare(now, pos, button)) {
      return lastClick = lastDoubleClick = null, "triple";
    }
    if (lastClick && lastClick.compare(now, pos, button)) {
      return lastDoubleClick = new PastClick(now, pos, button), lastClick = null, "double";
    }
    lastClick = new PastClick(now, pos, button);
    lastDoubleClick = null;
    return "single";
  }
  function onMouseDown(e) {
    var display = this.display;
    if (!(signalDOMEvent(this, e) || display.activeTouch && display.input.supportsTouch())) {
      if (display.input.ensurePolled(), display.shift = e.shiftKey, eventInWidget(display, e)) {
        webkit || (display.scroller.draggable = !1, setTimeout(function() {
          return display.scroller.draggable = !0;
        }, 100));
      } else {
        if (!gutterEvent(this, e, "gutterClick", !0)) {
          var pos = posFromMouse(this, e), button = e_button(e), repeat = pos ? clickRepeat(pos, button) : "single";
          window.focus();
          1 == button && this.state.selectingText && this.state.selectingText(e);
          if (!pos || !handleMappedButton(this, button, pos, repeat, e)) {
            if (1 == button) {
              pos ? leftButtonDown(this, pos, repeat, e) : (e.target || e.srcElement) == display.scroller && e_preventDefault(e);
            } else if (2 == button) {
              pos && extendSelection(this.doc, pos), setTimeout(function() {
                return display.input.focus();
              }, 20);
            } else if (3 == button) {
              if (captureRightClick) {
                this.display.input.onContextMenu(e);
              } else {
                delayBlurEvent(this);
              }
            }
          }
        }
      }
    }
  }
  function handleMappedButton(cm, button, pos, repeat, event) {
    var name = "Click";
    "double" == repeat ? name = "Double" + name : "triple" == repeat && (name = "Triple" + name);
    return dispatchKey(cm, addModifierNames((1 == button ? "Left" : 2 == button ? "Middle" : "Right") + name, event), event, function(bound) {
      "string" == typeof bound && (bound = commands[bound]);
      if (!bound) {
        return !1;
      }
      var done = !1;
      try {
        cm.isReadOnly() && (cm.state.suppressEdits = !0), done = bound(cm, pos) != Pass;
      } finally {
        cm.state.suppressEdits = !1;
      }
      return done;
    });
  }
  function leftButtonDown(cm, pos, repeat, event) {
    ie ? setTimeout(bind(ensureFocus, cm), 0) : cm.curOp.focus = activeElt();
    var option$jscomp$inline_424_value = cm.getOption("configureMouse");
    option$jscomp$inline_424_value = option$jscomp$inline_424_value ? option$jscomp$inline_424_value(cm, repeat, event) : {};
    null == option$jscomp$inline_424_value.unit && (option$jscomp$inline_424_value.unit = (chromeOS ? event.shiftKey && event.metaKey : event.altKey) ? "rectangle" : "single" == repeat ? "char" : "double" == repeat ? "word" : "line");
    if (null == option$jscomp$inline_424_value.extend || cm.doc.extend) {
      option$jscomp$inline_424_value.extend = cm.doc.extend || event.shiftKey;
    }
    null == option$jscomp$inline_424_value.addNew && (option$jscomp$inline_424_value.addNew = mac ? event.metaKey : event.ctrlKey);
    null == option$jscomp$inline_424_value.moveOnDrag && (option$jscomp$inline_424_value.moveOnDrag = !(mac ? event.altKey : event.ctrlKey));
    var sel = cm.doc.sel, contained;
    cm.options.dragDrop && dragAndDrop && !cm.isReadOnly() && "single" == repeat && -1 < (contained = sel.contains(pos)) && (0 > cmp((contained = sel.ranges[contained]).from(), pos) || 0 < pos.xRel) && (0 < cmp(contained.to(), pos) || 0 > pos.xRel) ? leftButtonStartDrag(cm, event, pos, option$jscomp$inline_424_value) : leftButtonSelect(cm, event, pos, option$jscomp$inline_424_value);
  }
  function leftButtonStartDrag(cm, event, pos, behavior) {
    function dragStart() {
      return moved = !0;
    }
    function mouseMove(e2) {
      moved = moved || 10 <= Math.abs(event.clientX - e2.clientX) + Math.abs(event.clientY - e2.clientY);
    }
    var display = cm.display, moved = !1, dragEnd = operation(cm, function(e) {
      webkit && (display.scroller.draggable = !1);
      cm.state.draggingText = !1;
      cm.state.delayingBlurEvent && (cm.hasFocus() ? cm.state.delayingBlurEvent = !1 : delayBlurEvent(cm));
      off(display.wrapper.ownerDocument, "mouseup", dragEnd);
      off(display.wrapper.ownerDocument, "mousemove", mouseMove);
      off(display.scroller, "dragstart", dragStart);
      off(display.scroller, "drop", dragEnd);
      moved || (e_preventDefault(e), behavior.addNew || extendSelection(cm.doc, pos, null, null, behavior.extend), webkit && !safari || ie && 9 == ie_version ? setTimeout(function() {
        display.wrapper.ownerDocument.body.focus({preventScroll:!0});
        display.input.focus();
      }, 20) : display.input.focus());
    });
    webkit && (display.scroller.draggable = !0);
    cm.state.draggingText = dragEnd;
    dragEnd.copy = !behavior.moveOnDrag;
    on(display.wrapper.ownerDocument, "mouseup", dragEnd);
    on(display.wrapper.ownerDocument, "mousemove", mouseMove);
    on(display.scroller, "dragstart", dragStart);
    on(display.scroller, "drop", dragEnd);
    cm.state.delayingBlurEvent = !0;
    setTimeout(function() {
      return display.input.focus();
    }, 20);
    display.scroller.dragDrop && display.scroller.dragDrop();
  }
  function rangeForUnit(cm$jscomp$197_result, pos, unit) {
    if ("char" == unit) {
      return new Range(pos, pos);
    }
    if ("word" == unit) {
      return cm$jscomp$197_result.findWordAt(pos);
    }
    if ("line" == unit) {
      return new Range(Pos(pos.line, 0), clipPos(cm$jscomp$197_result.doc, Pos(pos.line + 1, 0)));
    }
    cm$jscomp$197_result = unit(cm$jscomp$197_result, pos);
    return new Range(cm$jscomp$197_result.from, cm$jscomp$197_result.to);
  }
  function leftButtonSelect(cm, event$jscomp$16_range, start, behavior) {
    function extendTo(anchor$jscomp$9_pos) {
      if (0 != cmp(lastPos, anchor$jscomp$9_pos)) {
        if (lastPos = anchor$jscomp$9_pos, "rectangle" == behavior.unit) {
          var oldRange_ranges$1_ranges = [], head$jscomp$11_tabSize = cm.options.tabSize, right = countColumn(getLine(doc, start.line).text, start.ch, head$jscomp$11_tabSize), line = countColumn(getLine(doc, anchor$jscomp$9_pos.line).text, anchor$jscomp$9_pos.ch, head$jscomp$11_tabSize), left$jscomp$11_range = Math.min(right, line);
          right = Math.max(right, line);
          line = Math.min(start.line, anchor$jscomp$9_pos.line);
          for (var end = Math.min(cm.lastLine(), Math.max(start.line, anchor$jscomp$9_pos.line)); line <= end; line++) {
            var text = getLine(doc, line).text, leftPos = findColumn(text, left$jscomp$11_range, head$jscomp$11_tabSize);
            left$jscomp$11_range == right ? oldRange_ranges$1_ranges.push(new Range(Pos(line, leftPos), Pos(line, leftPos))) : text.length > leftPos && oldRange_ranges$1_ranges.push(new Range(Pos(line, leftPos), Pos(line, findColumn(text, right, head$jscomp$11_tabSize))));
          }
          oldRange_ranges$1_ranges.length || oldRange_ranges$1_ranges.push(new Range(start, start));
          setSelection(doc, normalizeSelection(cm, startSel.ranges.slice(0, ourIndex).concat(oldRange_ranges$1_ranges), ourIndex), {origin:"*mouse", scroll:!1});
          cm.scrollIntoView(anchor$jscomp$9_pos);
        } else {
          oldRange_ranges$1_ranges = ourRange, left$jscomp$11_range = rangeForUnit(cm, anchor$jscomp$9_pos, behavior.unit), anchor$jscomp$9_pos = oldRange_ranges$1_ranges.anchor, 0 < cmp(left$jscomp$11_range.anchor, anchor$jscomp$9_pos) ? (head$jscomp$11_tabSize = left$jscomp$11_range.head, anchor$jscomp$9_pos = minPos(oldRange_ranges$1_ranges.from(), left$jscomp$11_range.anchor)) : (head$jscomp$11_tabSize = left$jscomp$11_range.anchor, anchor$jscomp$9_pos = maxPos(oldRange_ranges$1_ranges.to(), 
          left$jscomp$11_range.head)), oldRange_ranges$1_ranges = startSel.ranges.slice(0), oldRange_ranges$1_ranges[ourIndex] = bidiSimplify(cm, new Range(clipPos(doc, anchor$jscomp$9_pos), head$jscomp$11_tabSize)), setSelection(doc, normalizeSelection(cm, oldRange_ranges$1_ranges, ourIndex), sel_mouse);
        }
      }
    }
    function extend(e) {
      var curCount = ++counter, cur = posFromMouse(cm, e, !0, "rectangle" == behavior.unit);
      if (cur) {
        if (0 != cmp(cur, lastPos)) {
          cm.curOp.focus = activeElt();
          extendTo(cur);
          var visible = visibleLines(display, doc);
          (cur.line >= visible.to || cur.line < visible.from) && setTimeout(operation(cm, function() {
            counter == curCount && extend(e);
          }), 150);
        } else {
          var outside = e.clientY < editorSize.top ? -20 : e.clientY > editorSize.bottom ? 20 : 0;
          outside && setTimeout(operation(cm, function() {
            counter == curCount && (display.scroller.scrollTop += outside, extend(e));
          }), 50);
        }
      }
    }
    function done(e) {
      cm.state.selectingText = !1;
      counter = Infinity;
      e && (e_preventDefault(e), display.input.focus());
      off(display.wrapper.ownerDocument, "mousemove", move);
      off(display.wrapper.ownerDocument, "mouseup", up);
      doc.history.lastSelOrigin = null;
    }
    ie && delayBlurEvent(cm);
    var display = cm.display, doc = cm.doc;
    e_preventDefault(event$jscomp$16_range);
    var startSel = doc.sel, ranges = startSel.ranges;
    if (behavior.addNew && !behavior.extend) {
      var ourIndex = doc.sel.contains(start);
      var ourRange = -1 < ourIndex ? ranges[ourIndex] : new Range(start, start);
    } else {
      ourRange = doc.sel.primary(), ourIndex = doc.sel.primIndex;
    }
    "rectangle" == behavior.unit ? (behavior.addNew || (ourRange = new Range(start, start)), start = posFromMouse(cm, event$jscomp$16_range, !0, !0), ourIndex = -1) : (event$jscomp$16_range = rangeForUnit(cm, start, behavior.unit), ourRange = behavior.extend ? extendRange(ourRange, event$jscomp$16_range.anchor, event$jscomp$16_range.head, behavior.extend) : event$jscomp$16_range);
    behavior.addNew ? -1 == ourIndex ? (ourIndex = ranges.length, setSelection(doc, normalizeSelection(cm, ranges.concat([ourRange]), ourIndex), {scroll:!1, origin:"*mouse"})) : 1 < ranges.length && ranges[ourIndex].empty() && "char" == behavior.unit && !behavior.extend ? (setSelection(doc, normalizeSelection(cm, ranges.slice(0, ourIndex).concat(ranges.slice(ourIndex + 1)), 0), {scroll:!1, origin:"*mouse"}), startSel = doc.sel) : replaceOneSelection(doc, ourIndex, ourRange, sel_mouse) : (ourIndex = 
    0, setSelection(doc, new Selection([ourRange], 0), sel_mouse), startSel = doc.sel);
    var lastPos = start, editorSize = display.wrapper.getBoundingClientRect(), counter = 0, move = operation(cm, function(e) {
      0 !== e.buttons && e_button(e) ? extend(e) : done(e);
    }), up = operation(cm, done);
    cm.state.selectingText = up;
    on(display.wrapper.ownerDocument, "mousemove", move);
    on(display.wrapper.ownerDocument, "mouseup", up);
  }
  function bidiSimplify(cm$jscomp$199_headIndex_leftSide, range) {
    var anchor = range.anchor, head = range.head, anchorLine_ch$jscomp$47_order = getLine(cm$jscomp$199_headIndex_leftSide.doc, anchor.line);
    if (0 == cmp(anchor, head) && anchor.sticky == head.sticky) {
      return range;
    }
    anchorLine_ch$jscomp$47_order = getOrder(anchorLine_ch$jscomp$47_order);
    if (!anchorLine_ch$jscomp$47_order) {
      return range;
    }
    var dir$jscomp$16_index = getBidiPartAt(anchorLine_ch$jscomp$47_order, anchor.ch, anchor.sticky), part = anchorLine_ch$jscomp$47_order[dir$jscomp$16_index];
    if (part.from != anchor.ch && part.to != anchor.ch) {
      return range;
    }
    var boundary_from$jscomp$42_sticky = dir$jscomp$16_index + (part.from == anchor.ch == (1 != part.level) ? 0 : 1);
    if (0 == boundary_from$jscomp$42_sticky || boundary_from$jscomp$42_sticky == anchorLine_ch$jscomp$47_order.length) {
      return range;
    }
    head.line != anchor.line ? cm$jscomp$199_headIndex_leftSide = 0 < (head.line - anchor.line) * ("ltr" == cm$jscomp$199_headIndex_leftSide.doc.direction ? 1 : -1) : (cm$jscomp$199_headIndex_leftSide = getBidiPartAt(anchorLine_ch$jscomp$47_order, head.ch, head.sticky), dir$jscomp$16_index = cm$jscomp$199_headIndex_leftSide - dir$jscomp$16_index || (head.ch - anchor.ch) * (1 == part.level ? -1 : 1), cm$jscomp$199_headIndex_leftSide = cm$jscomp$199_headIndex_leftSide == boundary_from$jscomp$42_sticky - 
    1 || cm$jscomp$199_headIndex_leftSide == boundary_from$jscomp$42_sticky ? 0 > dir$jscomp$16_index : 0 < dir$jscomp$16_index);
    anchorLine_ch$jscomp$47_order = anchorLine_ch$jscomp$47_order[boundary_from$jscomp$42_sticky + (cm$jscomp$199_headIndex_leftSide ? -1 : 0)];
    anchorLine_ch$jscomp$47_order = (boundary_from$jscomp$42_sticky = cm$jscomp$199_headIndex_leftSide == (1 == anchorLine_ch$jscomp$47_order.level)) ? anchorLine_ch$jscomp$47_order.from : anchorLine_ch$jscomp$47_order.to;
    boundary_from$jscomp$42_sticky = boundary_from$jscomp$42_sticky ? "after" : "before";
    return anchor.ch == anchorLine_ch$jscomp$47_order && anchor.sticky == boundary_from$jscomp$42_sticky ? range : new Range(new Pos(anchor.line, anchorLine_ch$jscomp$47_order, boundary_from$jscomp$42_sticky), head);
  }
  function gutterEvent(cm, e, type, display) {
    if (e.touches) {
      var line = e.touches[0].clientX;
      var mY = e.touches[0].clientY;
    } else {
      try {
        line = e.clientX, mY = e.clientY;
      } catch (e$1) {
        return !1;
      }
    }
    if (line >= Math.floor(cm.display.gutters.getBoundingClientRect().right)) {
      return !1;
    }
    display && e_preventDefault(e);
    display = cm.display;
    var i = display.lineDiv.getBoundingClientRect();
    if (mY > i.bottom || !hasHandler(cm, type)) {
      return e_defaultPrevented(e);
    }
    mY -= i.top - display.viewOffset;
    for (i = 0; i < cm.display.gutterSpecs.length; ++i) {
      var g = display.gutters.childNodes[i];
      if (g && g.getBoundingClientRect().right >= line) {
        return line = lineAtHeight(cm.doc, mY), signal(cm, type, cm, line, cm.display.gutterSpecs[i].className, e), e_defaultPrevented(e);
      }
    }
  }
  function onContextMenu(cm, e) {
    var JSCompiler_temp;
    (JSCompiler_temp = eventInWidget(cm.display, e)) || (JSCompiler_temp = hasHandler(cm, "gutterContextMenu") ? gutterEvent(cm, e, "gutterContextMenu", !1) : !1);
    if (!(JSCompiler_temp || signalDOMEvent(cm, e, "contextmenu") || captureRightClick)) {
      cm.display.input.onContextMenu(e);
    }
  }
  function themeChanged(cm) {
    cm.display.wrapper.className = cm.display.wrapper.className.replace(/\s*cm-s-\S+/g, "") + cm.options.theme.replace(/(^|\s)\s*/g, " cm-s-");
    clearCaches(cm);
  }
  function dragDropChanged(cm, toggle_value, funcs_old) {
    !toggle_value != !(funcs_old && funcs_old != Init) && (funcs_old = cm.display.dragFunctions, toggle_value = toggle_value ? on : off, toggle_value(cm.display.scroller, "dragstart", funcs_old.start), toggle_value(cm.display.scroller, "dragenter", funcs_old.enter), toggle_value(cm.display.scroller, "dragover", funcs_old.over), toggle_value(cm.display.scroller, "dragleave", funcs_old.leave), toggle_value(cm.display.scroller, "drop", funcs_old.drop));
  }
  function wrappingChanged(cm) {
    cm.options.lineWrapping ? (addClass(cm.display.wrapper, "CodeMirror-wrap"), cm.display.sizer.style.minWidth = "", cm.display.sizerWidth = null) : (rmClass(cm.display.wrapper, "CodeMirror-wrap"), findMaxLine(cm));
    estimateLineHeights(cm);
    regChange(cm);
    clearCaches(cm);
    setTimeout(function() {
      return updateScrollbars(cm);
    }, 100);
  }
  function CodeMirror$jscomp$0(display$jscomp$35_place, options) {
    var this$1 = this;
    if (!(this instanceof CodeMirror$jscomp$0)) {
      return new CodeMirror$jscomp$0(display$jscomp$35_place, options);
    }
    this.options = options = options ? copyObj(options) : {};
    copyObj(defaults, options, !1);
    var doc$jscomp$101_i = options.value;
    "string" == typeof doc$jscomp$101_i ? doc$jscomp$101_i = new Doc(doc$jscomp$101_i, options.mode, null, options.lineSeparator, options.direction) : options.mode && (doc$jscomp$101_i.modeOption = options.mode);
    this.doc = doc$jscomp$101_i;
    var input = new CodeMirror$jscomp$0.inputStyles[options.inputStyle](this);
    display$jscomp$35_place = this.display = new Display(display$jscomp$35_place, doc$jscomp$101_i, input, options);
    display$jscomp$35_place.wrapper.CodeMirror = this;
    themeChanged(this);
    options.lineWrapping && (this.display.wrapper.className += " CodeMirror-wrap");
    initScrollbars(this);
    this.state = {keyMaps:[], overlays:[], modeGen:0, overwrite:!1, delayingBlurEvent:!1, focused:!1, suppressEdits:!1, pasteIncoming:-1, cutIncoming:-1, selectingText:!1, draggingText:!1, highlight:new Delayed(), keySeq:null, specialChars:null};
    options.autofocus && !mobile && display$jscomp$35_place.input.focus();
    ie && 11 > ie_version && setTimeout(function() {
      return this$1.display.input.reset(!0);
    }, 20);
    registerEventHandlers(this);
    globalsRegistered || (registerGlobalHandlers(), globalsRegistered = !0);
    startOperation(this);
    this.curOp.forceUpdate = !0;
    attachDoc(this, doc$jscomp$101_i);
    options.autofocus && !mobile || this.hasFocus() ? setTimeout(function() {
      this$1.hasFocus() && !this$1.state.focused && onFocus(this$1);
    }, 20) : onBlur(this);
    for (var opt in optionHandlers) {
      if (optionHandlers.hasOwnProperty(opt)) {
        optionHandlers[opt](this, options[opt], Init);
      }
    }
    maybeUpdateLineNumberWidth(this);
    options.finishInit && options.finishInit(this);
    for (doc$jscomp$101_i = 0; doc$jscomp$101_i < initHooks.length; ++doc$jscomp$101_i) {
      initHooks[doc$jscomp$101_i](this);
    }
    endOperation(this);
    webkit && options.lineWrapping && "optimizelegibility" == getComputedStyle(display$jscomp$35_place.lineDiv).textRendering && (display$jscomp$35_place.lineDiv.style.textRendering = "auto");
  }
  function registerEventHandlers(cm) {
    function finishTouch() {
      d.activeTouch && (touchFinished = setTimeout(function() {
        return d.activeTouch = null;
      }, 1E3), prevTouch = d.activeTouch, prevTouch.end = +new Date());
    }
    function farAway(dy$jscomp$6_touch, other) {
      if (null == other.left) {
        return !0;
      }
      var dx = other.left - dy$jscomp$6_touch.left;
      dy$jscomp$6_touch = other.top - dy$jscomp$6_touch.top;
      return 400 < dx * dx + dy$jscomp$6_touch * dy$jscomp$6_touch;
    }
    var d = cm.display;
    on(d.scroller, "mousedown", operation(cm, onMouseDown));
    ie && 11 > ie_version ? on(d.scroller, "dblclick", operation(cm, function(e) {
      if (!signalDOMEvent(cm, e)) {
        var pos = posFromMouse(cm, e);
        !pos || gutterEvent(cm, e, "gutterClick", !0) || eventInWidget(cm.display, e) || (e_preventDefault(e), e = cm.findWordAt(pos), extendSelection(cm.doc, e.anchor, e.head));
      }
    })) : on(d.scroller, "dblclick", function(e) {
      return signalDOMEvent(cm, e) || e_preventDefault(e);
    });
    on(d.scroller, "contextmenu", function(e) {
      return onContextMenu(cm, e);
    });
    on(d.input.getField(), "contextmenu", function(e) {
      d.scroller.contains(e.target) || onContextMenu(cm, e);
    });
    var touchFinished, prevTouch = {end:0};
    on(d.scroller, "touchstart", function(e) {
      var JSCompiler_inline_result$jscomp$57_JSCompiler_temp$jscomp$56_now$jscomp$1_touch;
      if (JSCompiler_inline_result$jscomp$57_JSCompiler_temp$jscomp$56_now$jscomp$1_touch = !signalDOMEvent(cm, e)) {
        1 != e.touches.length ? JSCompiler_inline_result$jscomp$57_JSCompiler_temp$jscomp$56_now$jscomp$1_touch = !1 : (JSCompiler_inline_result$jscomp$57_JSCompiler_temp$jscomp$56_now$jscomp$1_touch = e.touches[0], JSCompiler_inline_result$jscomp$57_JSCompiler_temp$jscomp$56_now$jscomp$1_touch = 1 >= JSCompiler_inline_result$jscomp$57_JSCompiler_temp$jscomp$56_now$jscomp$1_touch.radiusX && 1 >= JSCompiler_inline_result$jscomp$57_JSCompiler_temp$jscomp$56_now$jscomp$1_touch.radiusY), JSCompiler_inline_result$jscomp$57_JSCompiler_temp$jscomp$56_now$jscomp$1_touch = 
        !JSCompiler_inline_result$jscomp$57_JSCompiler_temp$jscomp$56_now$jscomp$1_touch;
      }
      JSCompiler_inline_result$jscomp$57_JSCompiler_temp$jscomp$56_now$jscomp$1_touch && !gutterEvent(cm, e, "gutterClick", !0) && (d.input.ensurePolled(), clearTimeout(touchFinished), JSCompiler_inline_result$jscomp$57_JSCompiler_temp$jscomp$56_now$jscomp$1_touch = +new Date(), d.activeTouch = {start:JSCompiler_inline_result$jscomp$57_JSCompiler_temp$jscomp$56_now$jscomp$1_touch, moved:!1, prev:300 >= JSCompiler_inline_result$jscomp$57_JSCompiler_temp$jscomp$56_now$jscomp$1_touch - prevTouch.end ? 
      prevTouch : null}, 1 == e.touches.length && (d.activeTouch.left = e.touches[0].pageX, d.activeTouch.top = e.touches[0].pageY));
    });
    on(d.scroller, "touchmove", function() {
      d.activeTouch && (d.activeTouch.moved = !0);
    });
    on(d.scroller, "touchend", function(e) {
      var range$jscomp$36_touch = d.activeTouch;
      if (range$jscomp$36_touch && !eventInWidget(d, e) && null != range$jscomp$36_touch.left && !range$jscomp$36_touch.moved && 300 > new Date() - range$jscomp$36_touch.start) {
        var pos = cm.coordsChar(d.activeTouch, "page");
        range$jscomp$36_touch = !range$jscomp$36_touch.prev || farAway(range$jscomp$36_touch, range$jscomp$36_touch.prev) ? new Range(pos, pos) : !range$jscomp$36_touch.prev.prev || farAway(range$jscomp$36_touch, range$jscomp$36_touch.prev.prev) ? cm.findWordAt(pos) : new Range(Pos(pos.line, 0), clipPos(cm.doc, Pos(pos.line + 1, 0)));
        cm.setSelection(range$jscomp$36_touch.anchor, range$jscomp$36_touch.head);
        cm.focus();
        e_preventDefault(e);
      }
      finishTouch();
    });
    on(d.scroller, "touchcancel", finishTouch);
    on(d.scroller, "scroll", function() {
      d.scroller.clientHeight && (updateScrollTop(cm, d.scroller.scrollTop), setScrollLeft(cm, d.scroller.scrollLeft, !0), signal(cm, "scroll", cm));
    });
    on(d.scroller, "mousewheel", function(e) {
      return onScrollWheel(cm, e);
    });
    on(d.scroller, "DOMMouseScroll", function(e) {
      return onScrollWheel(cm, e);
    });
    on(d.wrapper, "scroll", function() {
      return d.wrapper.scrollTop = d.wrapper.scrollLeft = 0;
    });
    d.dragFunctions = {enter:function(e) {
      signalDOMEvent(cm, e) || e_stop(e);
    }, over:function(e) {
      if (!signalDOMEvent(cm, e)) {
        var pos = posFromMouse(cm, e);
        if (pos) {
          var frag = document.createDocumentFragment();
          drawSelectionCursor(cm, pos, frag);
          cm.display.dragCursor || (cm.display.dragCursor = elt$jscomp$0("div", null, "CodeMirror-cursors CodeMirror-dragcursors"), cm.display.lineSpace.insertBefore(cm.display.dragCursor, cm.display.cursorDiv));
          removeChildrenAndAdd(cm.display.dragCursor, frag);
        }
        e_stop(e);
      }
    }, start:function(e) {
      if (ie && (!cm.state.draggingText || 100 > +new Date() - lastDrop)) {
        e_stop(e);
      } else {
        if (!signalDOMEvent(cm, e) && !eventInWidget(cm.display, e) && (e.dataTransfer.setData("Text", cm.getSelection()), e.dataTransfer.effectAllowed = "copyMove", e.dataTransfer.setDragImage && !safari)) {
          var img = elt$jscomp$0("img", null, null, "position: fixed; left: 0; top: 0;");
          img.src = "data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==";
          presto && (img.width = img.height = 1, cm.display.wrapper.appendChild(img), img._top = img.offsetTop);
          e.dataTransfer.setDragImage(img, 0, 0);
          presto && img.parentNode.removeChild(img);
        }
      }
    }, drop:operation(cm, onDrop), leave:function(e) {
      signalDOMEvent(cm, e) || clearDragCursor(cm);
    }};
    var inp = d.input.getField();
    on(inp, "keyup", function(e) {
      16 == e.keyCode && (cm.doc.sel.shift = !1);
      signalDOMEvent(cm, e);
    });
    on(inp, "keydown", operation(cm, onKeyDown));
    on(inp, "keypress", operation(cm, onKeyPress));
    on(inp, "focus", function(e) {
      return onFocus(cm, e);
    });
    on(inp, "blur", function(e) {
      return onBlur(cm, e);
    });
  }
  function indentLine(cm$jscomp$233_i, n, how, aggressive_pos) {
    var doc = cm$jscomp$233_i.doc, state;
    null == how && (how = "add");
    "smart" == how && (doc.mode.indent ? state = getContextBefore(cm$jscomp$233_i, n).state : how = "prev");
    var i$1$jscomp$17_tabSize = cm$jscomp$233_i.options.tabSize, line$jscomp$120_range = getLine(doc, n), curSpace = countColumn(line$jscomp$120_range.text, null, i$1$jscomp$17_tabSize);
    line$jscomp$120_range.stateAfter && (line$jscomp$120_range.stateAfter = null);
    var curSpaceString = line$jscomp$120_range.text.match(/^\s*/)[0];
    if (!aggressive_pos && !/\S/.test(line$jscomp$120_range.text)) {
      var indentation = 0;
      how = "not";
    } else if ("smart" == how && (indentation = doc.mode.indent(state, line$jscomp$120_range.text.slice(curSpaceString.length), line$jscomp$120_range.text), indentation == Pass || 150 < indentation)) {
      if (!aggressive_pos) {
        return;
      }
      how = "prev";
    }
    "prev" == how ? n > doc.first ? indentation = countColumn(getLine(doc, n - 1).text, null, i$1$jscomp$17_tabSize) : indentation = 0 : "add" == how ? indentation = curSpace + cm$jscomp$233_i.options.indentUnit : "subtract" == how ? indentation = curSpace - cm$jscomp$233_i.options.indentUnit : "number" == typeof how && (indentation = curSpace + how);
    indentation = Math.max(0, indentation);
    how = "";
    aggressive_pos = 0;
    if (cm$jscomp$233_i.options.indentWithTabs) {
      for (cm$jscomp$233_i = Math.floor(indentation / i$1$jscomp$17_tabSize); cm$jscomp$233_i; --cm$jscomp$233_i) {
        aggressive_pos += i$1$jscomp$17_tabSize, how += "\t";
      }
    }
    aggressive_pos < indentation && (how += spaceStr(indentation - aggressive_pos));
    if (how != curSpaceString) {
      return replaceRange(doc, how, Pos(n, 0), Pos(n, curSpaceString.length), "+input"), line$jscomp$120_range.stateAfter = null, !0;
    }
    for (i$1$jscomp$17_tabSize = 0; i$1$jscomp$17_tabSize < doc.sel.ranges.length; i$1$jscomp$17_tabSize++) {
      if (line$jscomp$120_range = doc.sel.ranges[i$1$jscomp$17_tabSize], line$jscomp$120_range.head.line == n && line$jscomp$120_range.head.ch < curSpaceString.length) {
        n = Pos(n, curSpaceString.length);
        replaceOneSelection(doc, i$1$jscomp$17_tabSize, new Range(n, n));
        break;
      }
    }
  }
  function applyTextInput(cm, inserted, deleted, sel, origin) {
    var doc = cm.doc;
    cm.display.shift = !1;
    sel || (sel = doc.sel);
    var recent = +new Date() - 200, paste = "paste" == origin || cm.state.pasteIncoming > recent, textLines = splitLinesAuto(inserted), multiPaste = null;
    if (paste && 1 < sel.ranges.length) {
      if (lastCopied && lastCopied.text.join("\n") == inserted) {
        if (0 == sel.ranges.length % lastCopied.text.length) {
          multiPaste = [];
          for (var i = 0; i < lastCopied.text.length; i++) {
            multiPaste.push(doc.splitLines(lastCopied.text[i]));
          }
        }
      } else {
        textLines.length == sel.ranges.length && cm.options.pasteLinesPerSelection && (multiPaste = map$jscomp$0(textLines, function(l) {
          return [l];
        }));
      }
    }
    i = cm.curOp.updateInput;
    for (var i$1 = sel.ranges.length - 1; 0 <= i$1; i$1--) {
      var changeEvent_range = sel.ranges[i$1], from = changeEvent_range.from(), to = changeEvent_range.to();
      changeEvent_range.empty() && (deleted && 0 < deleted ? from = Pos(from.line, from.ch - deleted) : cm.state.overwrite && !paste ? to = Pos(to.line, Math.min(getLine(doc, to.line).text.length, to.ch + lst(textLines).length)) : paste && lastCopied && lastCopied.lineWise && lastCopied.text.join("\n") == textLines.join("\n") && (from = to = Pos(from.line, 0)));
      changeEvent_range = {from, to, text:multiPaste ? multiPaste[i$1 % multiPaste.length] : textLines, origin:origin || (paste ? "paste" : cm.state.cutIncoming > recent ? "cut" : "+input")};
      makeChange(cm.doc, changeEvent_range);
      signalLater(cm, "inputRead", cm, changeEvent_range);
    }
    inserted && !paste && triggerElectric(cm, inserted);
    ensureCursorVisible(cm);
    2 > cm.curOp.updateInput && (cm.curOp.updateInput = i);
    cm.curOp.typing = !0;
    cm.state.pasteIncoming = cm.state.cutIncoming = -1;
  }
  function handlePaste(e, cm) {
    var pasted = e.clipboardData && e.clipboardData.getData("Text");
    if (pasted) {
      return e.preventDefault(), cm.isReadOnly() || cm.options.disableInput || !cm.hasFocus() || runInOp(cm, function() {
        return applyTextInput(cm, pasted, 0, null, "paste");
      }), !0;
    }
  }
  function triggerElectric(cm, inserted) {
    if (cm.options.electricChars && cm.options.smartIndent) {
      for (var sel = cm.doc.sel, i = sel.ranges.length - 1; 0 <= i; i--) {
        var range = sel.ranges[i];
        if (!(100 < range.head.ch || i && sel.ranges[i - 1].head.line == range.head.line)) {
          var mode = cm.getModeAt(range.head), indented = !1;
          if (mode.electricChars) {
            for (var j = 0; j < mode.electricChars.length; j++) {
              if (-1 < inserted.indexOf(mode.electricChars.charAt(j))) {
                indented = indentLine(cm, range.head.line, "smart");
                break;
              }
            }
          } else {
            mode.electricInput && mode.electricInput.test(getLine(cm.doc, range.head.line).text.slice(0, range.head.ch)) && (indented = indentLine(cm, range.head.line, "smart"));
          }
          indented && signalLater(cm, "electricInput", cm, range.head.line);
        }
      }
    }
  }
  function copyableRanges(cm) {
    for (var text = [], ranges = [], i = 0; i < cm.doc.sel.ranges.length; i++) {
      var line = cm.doc.sel.ranges[i].head.line;
      line = {anchor:Pos(line, 0), head:Pos(line + 1, 0)};
      ranges.push(line);
      text.push(cm.getRange(line.anchor, line.head));
    }
    return {text, ranges};
  }
  function disableBrowserMagic(field, spellcheck, autocorrect, autocapitalize) {
    field.setAttribute("autocorrect", autocorrect ? "" : "off");
    field.setAttribute("autocapitalize", autocapitalize ? "" : "off");
    field.setAttribute("spellcheck", !!spellcheck);
  }
  function hiddenTextarea() {
    var te = elt$jscomp$0("textarea", null, null, "position: absolute; bottom: -1em; padding: 0; width: 1px; height: 1em; min-height: 1em; outline: none"), div = elt$jscomp$0("div", [te], null, "overflow: hidden; position: relative; width: 3px; height: 0px;");
    webkit ? te.style.width = "1000px" : te.setAttribute("wrap", "off");
    ios && (te.style.border = "1px solid black");
    disableBrowserMagic(te);
    return div;
  }
  function findPosH(doc, pos, dir, unit, visually) {
    function moveOnce(JSCompiler_temp$jscomp$58_boundToLine_l) {
      var next;
      if ("codepoint" == unit) {
        var ch = lineObj.text.charCodeAt(pos.ch + (0 < dir ? 0 : -1));
        isNaN(ch) ? next = null : next = new Pos(pos.line, Math.max(0, Math.min(lineObj.text.length, pos.ch + dir * ((0 < dir ? 55296 <= ch && 56320 > ch : 56320 <= ch && 57343 > ch) ? 2 : 1))), -dir);
      } else {
        next = visually ? moveVisually(doc.cm, lineObj, pos, dir) : moveLogically(lineObj, pos, dir);
      }
      if (null == next) {
        if (JSCompiler_temp$jscomp$58_boundToLine_l = !JSCompiler_temp$jscomp$58_boundToLine_l) {
          JSCompiler_temp$jscomp$58_boundToLine_l = pos.line + lineDir, JSCompiler_temp$jscomp$58_boundToLine_l < doc.first || JSCompiler_temp$jscomp$58_boundToLine_l >= doc.first + doc.size ? JSCompiler_temp$jscomp$58_boundToLine_l = !1 : (pos = new Pos(JSCompiler_temp$jscomp$58_boundToLine_l, pos.ch, pos.sticky), JSCompiler_temp$jscomp$58_boundToLine_l = lineObj = getLine(doc, JSCompiler_temp$jscomp$58_boundToLine_l));
        }
        if (JSCompiler_temp$jscomp$58_boundToLine_l) {
          pos = endOfLine(visually, doc.cm, lineObj, pos.line, lineDir);
        } else {
          return !1;
        }
      } else {
        pos = next;
      }
      return !0;
    }
    var oldPos = pos, origDir_result = dir, lineObj = getLine(doc, pos.line), lineDir = visually && "rtl" == doc.direction ? -dir : dir;
    if ("char" == unit || "codepoint" == unit) {
      moveOnce();
    } else if ("column" == unit) {
      moveOnce(!0);
    } else if ("word" == unit || "group" == unit) {
      for (var sawType = null, group = "group" == unit, helper = doc.cm && doc.cm.getHelper(pos, "wordChars"), first = !0; !(0 > dir) || moveOnce(!first); first = !1) {
        var cur$jscomp$23_type = lineObj.text.charAt(pos.ch) || "\n";
        cur$jscomp$23_type = isWordChar(cur$jscomp$23_type, helper) ? "w" : group && "\n" == cur$jscomp$23_type ? "n" : !group || /\s/.test(cur$jscomp$23_type) ? null : "p";
        !group || first || cur$jscomp$23_type || (cur$jscomp$23_type = "s");
        if (sawType && sawType != cur$jscomp$23_type) {
          0 > dir && (dir = 1, moveOnce(), pos.sticky = "after");
          break;
        }
        cur$jscomp$23_type && (sawType = cur$jscomp$23_type);
        if (0 < dir && !moveOnce(!first)) {
          break;
        }
      }
    }
    origDir_result = skipAtomic(doc, pos, oldPos, origDir_result, !0);
    equalCursorPos(oldPos, origDir_result) && (origDir_result.hitSide = !0);
    return origDir_result;
  }
  function findPosV(cm, pos$jscomp$77_target, dir, unit) {
    var doc = cm.doc, x = pos$jscomp$77_target.left;
    if ("page" == unit) {
      var moveAmount_y = Math.max(Math.min(cm.display.wrapper.clientHeight, window.innerHeight || document.documentElement.clientHeight) - .5 * textHeight(cm.display), 3);
      moveAmount_y = (0 < dir ? pos$jscomp$77_target.bottom : pos$jscomp$77_target.top) + dir * moveAmount_y;
    } else {
      "line" == unit && (moveAmount_y = 0 < dir ? pos$jscomp$77_target.bottom + 3 : pos$jscomp$77_target.top - 3);
    }
    for (;;) {
      pos$jscomp$77_target = coordsChar(cm, x, moveAmount_y);
      if (!pos$jscomp$77_target.outside) {
        break;
      }
      if (0 > dir ? 0 >= moveAmount_y : moveAmount_y >= doc.height) {
        pos$jscomp$77_target.hitSide = !0;
        break;
      }
      moveAmount_y += 5 * dir;
    }
    return pos$jscomp$77_target;
  }
  function posToDOM(cm$jscomp$244_order, pos$jscomp$78_result) {
    var info$jscomp$5_view = findViewForLine(cm$jscomp$244_order, pos$jscomp$78_result.line);
    if (!info$jscomp$5_view || info$jscomp$5_view.hidden) {
      return null;
    }
    var line$jscomp$127_side = getLine(cm$jscomp$244_order.doc, pos$jscomp$78_result.line);
    info$jscomp$5_view = mapFromLineView(info$jscomp$5_view, line$jscomp$127_side, pos$jscomp$78_result.line);
    cm$jscomp$244_order = getOrder(line$jscomp$127_side, cm$jscomp$244_order.doc.direction);
    line$jscomp$127_side = "left";
    cm$jscomp$244_order && (line$jscomp$127_side = getBidiPartAt(cm$jscomp$244_order, pos$jscomp$78_result.ch) % 2 ? "right" : "left");
    pos$jscomp$78_result = nodeAndOffsetInLineMap(info$jscomp$5_view.map, pos$jscomp$78_result.ch, line$jscomp$127_side);
    pos$jscomp$78_result.offset = "right" == pos$jscomp$78_result.collapse ? pos$jscomp$78_result.end : pos$jscomp$78_result.start;
    return pos$jscomp$78_result;
  }
  function isInGutter(node) {
    for (; node; node = node.parentNode) {
      if (/CodeMirror-gutter-wrapper/.test(node.className)) {
        return !0;
      }
    }
    return !1;
  }
  function badPos(pos, bad) {
    bad && (pos.bad = !0);
    return pos;
  }
  function domTextBetween(cm, from, to, fromLine, toLine) {
    function recognizeMarker(id) {
      return function(marker) {
        return marker.id == id;
      };
    }
    function close() {
      closing && (text += lineSep, extraLinebreak && (text += lineSep), closing = extraLinebreak = !1);
    }
    function addText(str) {
      str && (close(), text += str);
    }
    function walk(found$jscomp$21_node) {
      if (1 == found$jscomp$21_node.nodeType) {
        var cmText_i = found$jscomp$21_node.getAttribute("cm-text");
        if (cmText_i) {
          addText(cmText_i);
        } else {
          cmText_i = found$jscomp$21_node.getAttribute("cm-marker");
          var isBlock_range;
          if (cmText_i) {
            found$jscomp$21_node = cm.findMarks(Pos(fromLine, 0), Pos(toLine + 1, 0), recognizeMarker(+cmText_i)), found$jscomp$21_node.length && (isBlock_range = found$jscomp$21_node[0].find(0)) && addText(getBetween(cm.doc, isBlock_range.from, isBlock_range.to).join(lineSep));
          } else {
            if ("false" != found$jscomp$21_node.getAttribute("contenteditable") && (isBlock_range = /^(pre|div|p|li|table|br)$/i.test(found$jscomp$21_node.nodeName), /^br$/i.test(found$jscomp$21_node.nodeName) || 0 != found$jscomp$21_node.textContent.length)) {
              isBlock_range && close();
              for (cmText_i = 0; cmText_i < found$jscomp$21_node.childNodes.length; cmText_i++) {
                walk(found$jscomp$21_node.childNodes[cmText_i]);
              }
              /^(pre|p)$/i.test(found$jscomp$21_node.nodeName) && (extraLinebreak = !0);
              isBlock_range && (closing = !0);
            }
          }
        }
      } else {
        3 == found$jscomp$21_node.nodeType && addText(found$jscomp$21_node.nodeValue.replace(/\u200b/g, "").replace(/\u00a0/g, " "));
      }
    }
    for (var text = "", closing = !1, lineSep = cm.doc.lineSeparator(), extraLinebreak = !1;;) {
      walk(from);
      if (from == to) {
        break;
      }
      from = from.nextSibling;
      extraLinebreak = !1;
    }
    return text;
  }
  function domToPos(cm, node, offset) {
    if (node == cm.display.lineDiv) {
      var lineNode = cm.display.lineDiv.childNodes[offset];
      if (!lineNode) {
        return badPos(cm.clipPos(Pos(cm.display.viewTo - 1)), !0);
      }
      node = null;
      offset = 0;
    } else {
      for (lineNode = node;; lineNode = lineNode.parentNode) {
        if (!lineNode || lineNode == cm.display.lineDiv) {
          return null;
        }
        if (lineNode.parentNode && lineNode.parentNode == cm.display.lineDiv) {
          break;
        }
      }
    }
    for (var i = 0; i < cm.display.view.length; i++) {
      var lineView = cm.display.view[i];
      if (lineView.node == lineNode) {
        return locateNodeInLineView(lineView, node, offset);
      }
    }
  }
  function locateNodeInLineView(lineView, found$jscomp$22_node, dist$1_line$jscomp$128_offset) {
    function find(textNode, line$jscomp$129_topNode, offset) {
      for (var ch$jscomp$53_i = -1; ch$jscomp$53_i < (maps ? maps.length : 0); ch$jscomp$53_i++) {
        for (var map = 0 > ch$jscomp$53_i ? measure.map : maps[ch$jscomp$53_i], j = 0; j < map.length; j += 3) {
          var curNode = map[j + 2];
          if (curNode == textNode || curNode == line$jscomp$129_topNode) {
            line$jscomp$129_topNode = lineNo(0 > ch$jscomp$53_i ? lineView.line : lineView.rest[ch$jscomp$53_i]);
            ch$jscomp$53_i = map[j] + offset;
            if (0 > offset || curNode != textNode) {
              ch$jscomp$53_i = map[j + (offset ? 1 : 0)];
            }
            return Pos(line$jscomp$129_topNode, ch$jscomp$53_i);
          }
        }
      }
    }
    var after = lineView.text.firstChild, bad = !1;
    if (!found$jscomp$22_node || !contains(after, found$jscomp$22_node)) {
      return badPos(Pos(lineNo(lineView.line), 0), !0);
    }
    if (found$jscomp$22_node == after && (bad = !0, found$jscomp$22_node = after.childNodes[dist$1_line$jscomp$128_offset], dist$1_line$jscomp$128_offset = 0, !found$jscomp$22_node)) {
      return dist$1_line$jscomp$128_offset = lineView.rest ? lst(lineView.rest) : lineView.line, badPos(Pos(lineNo(dist$1_line$jscomp$128_offset), dist$1_line$jscomp$128_offset.text.length), bad);
    }
    var dist$jscomp$1_textNode = 3 == found$jscomp$22_node.nodeType ? found$jscomp$22_node : null, before = found$jscomp$22_node;
    dist$jscomp$1_textNode || 1 != found$jscomp$22_node.childNodes.length || 3 != found$jscomp$22_node.firstChild.nodeType || (dist$jscomp$1_textNode = found$jscomp$22_node.firstChild, dist$1_line$jscomp$128_offset && (dist$1_line$jscomp$128_offset = dist$jscomp$1_textNode.nodeValue.length));
    for (; before.parentNode != after;) {
      before = before.parentNode;
    }
    var measure = lineView.measure, maps = measure.maps;
    if (found$jscomp$22_node = find(dist$jscomp$1_textNode, before, dist$1_line$jscomp$128_offset)) {
      return badPos(found$jscomp$22_node, bad);
    }
    after = before.nextSibling;
    for (dist$jscomp$1_textNode = dist$jscomp$1_textNode ? dist$jscomp$1_textNode.nodeValue.length - dist$1_line$jscomp$128_offset : 0; after; after = after.nextSibling) {
      if (found$jscomp$22_node = find(after, after.firstChild, 0)) {
        return badPos(Pos(found$jscomp$22_node.line, found$jscomp$22_node.ch - dist$jscomp$1_textNode), bad);
      }
      dist$jscomp$1_textNode += after.textContent.length;
    }
    for (before = before.previousSibling; before; before = before.previousSibling) {
      if (found$jscomp$22_node = find(before, before.firstChild, -1)) {
        return badPos(Pos(found$jscomp$22_node.line, found$jscomp$22_node.ch + dist$1_line$jscomp$128_offset), bad);
      }
      dist$1_line$jscomp$128_offset += before.textContent.length;
    }
  }
  var userAgent = navigator.userAgent, platform = navigator.platform, gecko = /gecko\/\d/i.test(userAgent), ie_upto10 = /MSIE \d/.test(userAgent), ie_11up = /Trident\/(?:[7-9]|\d{2,})\..*rv:(\d+)/.exec(userAgent), edge = /Edge\/(\d+)/.exec(userAgent), ie = ie_upto10 || ie_11up || edge, ie_version = ie && (ie_upto10 ? document.documentMode || 6 : +(edge || ie_11up)[1]), webkit = !edge && /WebKit\//.test(userAgent), qtwebkit = webkit && /Qt\/\d+\.\d+/.test(userAgent), chrome = !edge && /Chrome\/(\d+)/.exec(userAgent), 
  chrome_version = chrome && +chrome[1], presto = /Opera\//.test(userAgent), safari = /Apple Computer/.test(navigator.vendor), mac_geMountainLion = /Mac OS X 1\d\D([8-9]|\d\d)\D/.test(userAgent), phantom = /PhantomJS/.test(userAgent), ios = safari && (/Mobile\/\w+/.test(userAgent) || 2 < navigator.maxTouchPoints), android = /Android/.test(userAgent), mobile = ios || android || /webOS|BlackBerry|Opera Mini|Opera Mobi|IEMobile/i.test(userAgent), mac = ios || /Mac/.test(platform), chromeOS = /\bCrOS\b/.test(userAgent), 
  windows = /win/i.test(platform), presto_version = presto && userAgent.match(/Version\/(\d*\.\d*)/);
  presto_version && (presto_version = Number(presto_version[1]));
  presto_version && 15 <= presto_version && (presto = !1, webkit = !0);
  var flipCtrlCmd = mac && (qtwebkit || presto && (null == presto_version || 12.11 > presto_version)), captureRightClick = gecko || ie && 9 <= ie_version;
  var range$jscomp$0 = document.createRange ? function(node, start, end, endNode) {
    var r = document.createRange();
    r.setEnd(endNode || node, end);
    r.setStart(node, start);
    return r;
  } : function(node, start, end) {
    var r = document.body.createTextRange();
    try {
      r.moveToElementText(node.parentNode);
    } catch (e) {
      return r;
    }
    r.collapse(!0);
    r.moveEnd("character", end);
    r.moveStart("character", start);
    return r;
  };
  ios ? selectInput = function(node) {
    node.selectionStart = 0;
    node.selectionEnd = node.value.length;
  } : ie && (selectInput = function(node) {
    try {
      node.select();
    } catch (_e) {
    }
  });
  Delayed.prototype.onTimeout = function(self) {
    self.id = 0;
    self.time <= +new Date() ? self.f() : setTimeout(self.handler, self.time - +new Date());
  };
  Delayed.prototype.set = function(ms, f$jscomp$46_time) {
    this.f = f$jscomp$46_time;
    f$jscomp$46_time = +new Date() + ms;
    if (!this.id || f$jscomp$46_time < this.time) {
      clearTimeout(this.id), this.id = setTimeout(this.handler, ms), this.time = f$jscomp$46_time;
    }
  };
  var Pass = {toString:function() {
    return "CodeMirror.Pass";
  }}, sel_dontScroll = {scroll:!1}, sel_mouse = {origin:"*mouse"}, sel_move = {origin:"+move"}, spaceStrs = [""], nonASCIISingleCaseWordChar = /[\u00df\u0587\u0590-\u05f4\u0600-\u06ff\u3040-\u309f\u30a0-\u30ff\u3400-\u4db5\u4e00-\u9fcc\uac00-\ud7af]/, extendingChars = /[\u0300-\u036f\u0483-\u0489\u0591-\u05bd\u05bf\u05c1\u05c2\u05c4\u05c5\u05c7\u0610-\u061a\u064b-\u065e\u0670\u06d6-\u06dc\u06de-\u06e4\u06e7\u06e8\u06ea-\u06ed\u0711\u0730-\u074a\u07a6-\u07b0\u07eb-\u07f3\u0816-\u0819\u081b-\u0823\u0825-\u0827\u0829-\u082d\u0900-\u0902\u093c\u0941-\u0948\u094d\u0951-\u0955\u0962\u0963\u0981\u09bc\u09be\u09c1-\u09c4\u09cd\u09d7\u09e2\u09e3\u0a01\u0a02\u0a3c\u0a41\u0a42\u0a47\u0a48\u0a4b-\u0a4d\u0a51\u0a70\u0a71\u0a75\u0a81\u0a82\u0abc\u0ac1-\u0ac5\u0ac7\u0ac8\u0acd\u0ae2\u0ae3\u0b01\u0b3c\u0b3e\u0b3f\u0b41-\u0b44\u0b4d\u0b56\u0b57\u0b62\u0b63\u0b82\u0bbe\u0bc0\u0bcd\u0bd7\u0c3e-\u0c40\u0c46-\u0c48\u0c4a-\u0c4d\u0c55\u0c56\u0c62\u0c63\u0cbc\u0cbf\u0cc2\u0cc6\u0ccc\u0ccd\u0cd5\u0cd6\u0ce2\u0ce3\u0d3e\u0d41-\u0d44\u0d4d\u0d57\u0d62\u0d63\u0dca\u0dcf\u0dd2-\u0dd4\u0dd6\u0ddf\u0e31\u0e34-\u0e3a\u0e47-\u0e4e\u0eb1\u0eb4-\u0eb9\u0ebb\u0ebc\u0ec8-\u0ecd\u0f18\u0f19\u0f35\u0f37\u0f39\u0f71-\u0f7e\u0f80-\u0f84\u0f86\u0f87\u0f90-\u0f97\u0f99-\u0fbc\u0fc6\u102d-\u1030\u1032-\u1037\u1039\u103a\u103d\u103e\u1058\u1059\u105e-\u1060\u1071-\u1074\u1082\u1085\u1086\u108d\u109d\u135f\u1712-\u1714\u1732-\u1734\u1752\u1753\u1772\u1773\u17b7-\u17bd\u17c6\u17c9-\u17d3\u17dd\u180b-\u180d\u18a9\u1920-\u1922\u1927\u1928\u1932\u1939-\u193b\u1a17\u1a18\u1a56\u1a58-\u1a5e\u1a60\u1a62\u1a65-\u1a6c\u1a73-\u1a7c\u1a7f\u1b00-\u1b03\u1b34\u1b36-\u1b3a\u1b3c\u1b42\u1b6b-\u1b73\u1b80\u1b81\u1ba2-\u1ba5\u1ba8\u1ba9\u1c2c-\u1c33\u1c36\u1c37\u1cd0-\u1cd2\u1cd4-\u1ce0\u1ce2-\u1ce8\u1ced\u1dc0-\u1de6\u1dfd-\u1dff\u200c\u200d\u20d0-\u20f0\u2cef-\u2cf1\u2de0-\u2dff\u302a-\u302f\u3099\u309a\ua66f-\ua672\ua67c\ua67d\ua6f0\ua6f1\ua802\ua806\ua80b\ua825\ua826\ua8c4\ua8e0-\ua8f1\ua926-\ua92d\ua947-\ua951\ua980-\ua982\ua9b3\ua9b6-\ua9b9\ua9bc\uaa29-\uaa2e\uaa31\uaa32\uaa35\uaa36\uaa43\uaa4c\uaab0\uaab2-\uaab4\uaab7\uaab8\uaabe\uaabf\uaac1\uabe5\uabe8\uabed\udc00-\udfff\ufb1e\ufe00-\ufe0f\ufe20-\ufe26\uff9e\uff9f]/, 
  bidiOther = null, bidiOrdering = function() {
    function BidiSpan(level, from, to) {
      this.level = level;
      this.from = from;
      this.to = to;
    }
    var bidiRE = /[\u0590-\u05f4\u0600-\u06ff\u0700-\u08ac]/, isNeutral = /[stwN]/, isStrong = /[LRr]/, countsAsLeft = /[Lb1n]/, countsAsNum = /[1n]/;
    return function(str, direction) {
      var order = "ltr" == direction ? "L" : "R";
      if (0 == str.length || "ltr" == direction && !bidiRE.test(str)) {
        return !1;
      }
      for (var len = str.length, types = [], end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j = 0; end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j < len; ++end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j) {
        var JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start = types, before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type = str.charCodeAt(end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j);
        JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start.push.call(JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start, 247 >= before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type ? "bbbbbbbbbtstwsbbbbbbbbbbbbbbssstwNN%%%NNNNNN,N,N1111111111NNNNNNNLLLLLLLLLLLLLLLLLLLLLLLLLLNNNNNNLLLLLLLLLLLLLLLLLLLLLLLLLLNNNNbbbbbbsbbbbbbbbbbbbbbbbbbbbbbbbbb,N%%%%NNNNLNNNNN%%11NLNNN1LNNNNNLLLLLLLLLLLLLLLLLLLLLLLNLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLN".charAt(before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type) : 
        1424 <= before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type && 1524 >= before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type ? "R" : 1536 <= before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type && 1785 >= before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type ? "nnnnnnNNr%%r,rNNmmmmmmmmmmmrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrmmmmmmmmmmmmmmmmmmmmmnnnnnnnnnn%nnrrrmrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrmmmmmmmnNmmmmmmrrmmNmmmmrr1111111111".charAt(before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type - 
        1536) : 1774 <= before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type && 2220 >= before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type ? "r" : 8192 <= before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type && 8203 >= before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type ? "w" : 8204 == before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type ? 
        "b" : "L");
      }
      end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j = 0;
      for (JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start = order; end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j < len; ++end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j) {
        before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type = types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j], "m" == before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type ? types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j] = JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start : JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start = 
        before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type;
      }
      end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j = 0;
      for (JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start = order; end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j < len; ++end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j) {
        before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type = types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j], "1" == before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type && "r" == JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start ? types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j] = "n" : isStrong.test(before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type) && 
        (JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start = before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type, "r" == before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type && (types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j] = "R"));
      }
      end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j = 1;
      for (JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start = types[0]; end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j < len - 1; ++end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j) {
        before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type = types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j], "+" == before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type && "1" == JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start && "1" == types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j + 1] ? types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j] = 
        "1" : "," != before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type || JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start != types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j + 1] || "1" != JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start && "n" != JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start || (types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j] = 
        JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start), JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start = before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type;
      }
      for (end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j = 0; end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j < len; ++end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j) {
        if (JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start = types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j], "," == JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start) {
          types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j] = "N";
        } else if ("%" == JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start) {
          for (JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start = end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j + 1; JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start < len && "%" == types[JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start]; ++JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start) {
          }
          for (before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type = end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j && "!" == types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j - 1] || JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start < len && "1" == types[JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start] ? "1" : "N"; end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j < 
          JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start; ++end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j) {
            types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j] = before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type;
          }
          end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j = JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start - 1;
        }
      }
      end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j = 0;
      for (JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start = order; end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j < len; ++end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j) {
        before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type = types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j], "L" == JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start && "1" == before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type ? types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j] = "L" : isStrong.test(before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type) && 
        (JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start = before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type);
      }
      for (JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start = 0; JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start < len; ++JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start) {
        if (isNeutral.test(types[JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start])) {
          for (end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j = JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start + 1; end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j < len && isNeutral.test(types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j]); ++end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j) {
          }
          before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type = "L" == (JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start ? types[JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start - 1] : order);
          for (before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type = before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type == ("L" == (end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j < len ? types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j] : order)) ? before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type ? "L" : "R" : order; JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start < 
          end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j; ++JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start) {
            types[JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start] = before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type;
          }
          JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start = end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j - 1;
        }
      }
      order = [];
      var m;
      for (end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j = 0; end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j < len;) {
        if (countsAsLeft.test(types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j])) {
          JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start = end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j;
          for (++end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j; end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j < len && countsAsLeft.test(types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j]); ++end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j) {
          }
          order.push(new BidiSpan(0, JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start, end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j));
        } else {
          var nstart_pos = end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j;
          JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start = order.length;
          before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type = "rtl" == direction ? 1 : 0;
          for (++end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j; end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j < len && "L" != types[end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j]; ++end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j) {
          }
          for (var j$2 = nstart_pos; j$2 < end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j;) {
            if (countsAsNum.test(types[j$2])) {
              nstart_pos < j$2 && (order.splice(JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start, 0, new BidiSpan(1, nstart_pos, j$2)), JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start += before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type);
              nstart_pos = j$2;
              for (++j$2; j$2 < end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j && countsAsNum.test(types[j$2]); ++j$2) {
              }
              order.splice(JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start, 0, new BidiSpan(2, nstart_pos, j$2));
              JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start += before$jscomp$2_code$jscomp$inline_447_isRTL_replace_replace$1_type$1_type$2_type$4_type;
              nstart_pos = j$2;
            } else {
              ++j$2;
            }
          }
          nstart_pos < end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j && order.splice(JSCompiler_temp_const$jscomp$60_at_cur$1_cur$jscomp$3_end$jscomp$17_i$6_j$1_prev_prev$1_start, 0, new BidiSpan(1, nstart_pos, end$1_i$1$jscomp$1_i$2$jscomp$1_i$3_i$4_i$5_i$7_i$jscomp$130_j));
        }
      }
      "ltr" == direction && (1 == order[0].level && (m = str.match(/^\s+/)) && (order[0].from = m[0].length, order.unshift(new BidiSpan(0, 0, m[0].length))), 1 == lst(order).level && (m = str.match(/\s+$/)) && (lst(order).to -= m[0].length, order.push(new BidiSpan(0, len - m[0].length, len))));
      return "rtl" == direction ? order.reverse() : order;
    };
  }(), noHandlers = [], dragAndDrop = function() {
    if (ie && 9 > ie_version) {
      return !1;
    }
    var div = elt$jscomp$0("div");
    return "draggable" in div || "dragDrop" in div;
  }(), zwspSupported, badBidiRects, splitLinesAuto = 3 != "\n\nb".split(/\n/).length ? function(string) {
    for (var pos = 0, result = [], l = string.length; pos <= l;) {
      var nl = string.indexOf("\n", pos);
      -1 == nl && (nl = string.length);
      var line = string.slice(pos, "\r" == string.charAt(nl - 1) ? nl - 1 : nl), rt = line.indexOf("\r");
      -1 != rt ? (result.push(line.slice(0, rt)), pos += rt + 1) : (result.push(line), pos = nl + 1);
    }
    return result;
  } : function(string) {
    return string.split(/\r\n?|\n/);
  }, hasSelection = window.getSelection ? function(te) {
    try {
      return te.selectionStart != te.selectionEnd;
    } catch (e) {
      return !1;
    }
  } : function(te) {
    try {
      var range = te.ownerDocument.selection.createRange();
    } catch (e) {
    }
    return range && range.parentElement() == te ? 0 != range.compareEndPoints("StartToEnd", range) : !1;
  }, hasCopyEvent = function() {
    var e = elt$jscomp$0("div");
    if ("oncopy" in e) {
      return !0;
    }
    var policy = {createScript:function() {
      return "return;";
    }};
    "undefined" !== typeof trustedTypes && (policy = trustedTypes.createPolicy("codemirror#return", policy));
    e.setAttribute("oncopy", policy.createScript(""));
    return "function" == typeof e.oncopy;
  }(), badZoomedRects = null, modes = {}, mimeModes = {}, modeExtensions = {};
  StringStream.prototype.eol = function() {
    return this.pos >= this.string.length;
  };
  StringStream.prototype.sol = function() {
    return this.pos == this.lineStart;
  };
  StringStream.prototype.peek = function() {
    return this.string.charAt(this.pos) || void 0;
  };
  StringStream.prototype.next = function() {
    if (this.pos < this.string.length) {
      return this.string.charAt(this.pos++);
    }
  };
  StringStream.prototype.eat = function(match) {
    var ch = this.string.charAt(this.pos), ok;
    "string" == typeof match ? ok = ch == match : ok = ch && (match.test ? match.test(ch) : match(ch));
    if (ok) {
      return ++this.pos, ch;
    }
  };
  StringStream.prototype.eatWhile = function(match) {
    for (var start = this.pos; this.eat(match);) {
    }
    return this.pos > start;
  };
  StringStream.prototype.eatSpace = function() {
    for (var start = this.pos; /[\s\u00a0]/.test(this.string.charAt(this.pos));) {
      ++this.pos;
    }
    return this.pos > start;
  };
  StringStream.prototype.skipToEnd = function() {
    this.pos = this.string.length;
  };
  StringStream.prototype.skipTo = function(ch$jscomp$11_found) {
    ch$jscomp$11_found = this.string.indexOf(ch$jscomp$11_found, this.pos);
    if (-1 < ch$jscomp$11_found) {
      return this.pos = ch$jscomp$11_found, !0;
    }
  };
  StringStream.prototype.backUp = function(n) {
    this.pos -= n;
  };
  StringStream.prototype.column = function() {
    this.lastColumnPos < this.start && (this.lastColumnValue = countColumn(this.string, this.start, this.tabSize, this.lastColumnPos, this.lastColumnValue), this.lastColumnPos = this.start);
    return this.lastColumnValue - (this.lineStart ? countColumn(this.string, this.lineStart, this.tabSize) : 0);
  };
  StringStream.prototype.indentation = function() {
    return countColumn(this.string, null, this.tabSize) - (this.lineStart ? countColumn(this.string, this.lineStart, this.tabSize) : 0);
  };
  StringStream.prototype.match = function(match$jscomp$17_pattern, consume, caseInsensitive) {
    if ("string" == typeof match$jscomp$17_pattern) {
      var str = this.string.substr(this.pos, match$jscomp$17_pattern.length);
      if ((caseInsensitive ? str.toLowerCase() : str) == (caseInsensitive ? match$jscomp$17_pattern.toLowerCase() : match$jscomp$17_pattern)) {
        return !1 !== consume && (this.pos += match$jscomp$17_pattern.length), !0;
      }
    } else {
      if ((match$jscomp$17_pattern = this.string.slice(this.pos).match(match$jscomp$17_pattern)) && 0 < match$jscomp$17_pattern.index) {
        return null;
      }
      match$jscomp$17_pattern && !1 !== consume && (this.pos += match$jscomp$17_pattern[0].length);
      return match$jscomp$17_pattern;
    }
  };
  StringStream.prototype.current = function() {
    return this.string.slice(this.start, this.pos);
  };
  StringStream.prototype.lookAhead = function(n) {
    var oracle = this.lineOracle;
    return oracle && oracle.lookAhead(n);
  };
  Context.prototype.lookAhead = function(n) {
    var line = this.doc.getLine(this.line + n);
    null != line && n > this.maxLookAhead && (this.maxLookAhead = n);
    return line;
  };
  Context.prototype.nextLine = function() {
    this.line++;
    0 < this.maxLookAhead && this.maxLookAhead--;
  };
  Context.fromSaved = function(doc, saved, line) {
    return saved instanceof SavedContext ? new Context(doc, copyState(doc.mode, saved.state), line, saved.lookAhead) : new Context(doc, copyState(doc.mode, saved), line);
  };
  Context.prototype.save = function(copy_state) {
    copy_state = !1 !== copy_state ? copyState(this.doc.mode, this.state) : this.state;
    return 0 < this.maxLookAhead ? new SavedContext(copy_state, this.maxLookAhead) : copy_state;
  };
  var sawReadOnlySpans = !1, sawCollapsedSpans = !1;
  eventMixin(Line);
  var styleToClassCache = {}, styleToClassCacheWithMode = {}, operationGroup = null, orphanDelayedCallbacks = null, nullRect = {left:0, right:0, top:0, bottom:0}, measureText;
  NativeScrollbars.prototype.update = function(measure) {
    var needsH = measure.scrollWidth > measure.clientWidth + 1, needsV = measure.scrollHeight > measure.clientHeight + 1, sWidth = measure.nativeBarWidth;
    needsV ? (this.vert.style.display = "block", this.vert.style.bottom = needsH ? sWidth + "px" : "0", this.vert.firstChild.style.height = Math.max(0, measure.scrollHeight - measure.clientHeight + (measure.viewHeight - (needsH ? sWidth : 0))) + "px") : (this.vert.scrollTop = 0, this.vert.style.display = "", this.vert.firstChild.style.height = "0");
    needsH ? (this.horiz.style.display = "block", this.horiz.style.right = needsV ? sWidth + "px" : "0", this.horiz.style.left = measure.barLeft + "px", this.horiz.firstChild.style.width = Math.max(0, measure.scrollWidth - measure.clientWidth + (measure.viewWidth - measure.barLeft - (needsV ? sWidth : 0))) + "px") : (this.horiz.style.display = "", this.horiz.firstChild.style.width = "0");
    !this.checkedZeroWidth && 0 < measure.clientHeight && (0 == sWidth && this.zeroWidthHack(), this.checkedZeroWidth = !0);
    return {right:needsV ? sWidth : 0, bottom:needsH ? sWidth : 0};
  };
  NativeScrollbars.prototype.setScrollLeft = function(pos) {
    this.horiz.scrollLeft != pos && (this.horiz.scrollLeft = pos);
    this.disableHoriz && this.enableZeroWidthBar(this.horiz, this.disableHoriz, "horiz");
  };
  NativeScrollbars.prototype.setScrollTop = function(pos) {
    this.vert.scrollTop != pos && (this.vert.scrollTop = pos);
    this.disableVert && this.enableZeroWidthBar(this.vert, this.disableVert, "vert");
  };
  NativeScrollbars.prototype.zeroWidthHack = function() {
    this.horiz.style.height = this.vert.style.width = mac && !mac_geMountainLion ? "12px" : "18px";
    this.horiz.style.visibility = this.vert.style.visibility = "hidden";
    this.disableHoriz = new Delayed();
    this.disableVert = new Delayed();
  };
  NativeScrollbars.prototype.enableZeroWidthBar = function(bar, delay, type) {
    function maybeDisable() {
      var box = bar.getBoundingClientRect();
      ("vert" == type ? document.elementFromPoint(box.right - 1, (box.top + box.bottom) / 2) : document.elementFromPoint((box.right + box.left) / 2, box.bottom - 1)) != bar ? bar.style.visibility = "hidden" : delay.set(1E3, maybeDisable);
    }
    bar.style.visibility = "";
    delay.set(1E3, maybeDisable);
  };
  NativeScrollbars.prototype.clear = function() {
    var parent = this.horiz.parentNode;
    parent.removeChild(this.horiz);
    parent.removeChild(this.vert);
  };
  NullScrollbars.prototype.update = function() {
    return {bottom:0, right:0};
  };
  NullScrollbars.prototype.setScrollLeft = function() {
  };
  NullScrollbars.prototype.setScrollTop = function() {
  };
  NullScrollbars.prototype.clear = function() {
  };
  var scrollbarModel = {"native":NativeScrollbars, "null":NullScrollbars}, nextOpId = 0;
  DisplayUpdate.prototype.signal = function(emitter, type) {
    hasHandler(emitter, type) && this.events.push(arguments);
  };
  DisplayUpdate.prototype.finish = function() {
    for (var i = 0; i < this.events.length; i++) {
      signal.apply(null, this.events[i]);
    }
  };
  var wheelSamples = 0, wheelPixelsPerUnit = null;
  ie ? wheelPixelsPerUnit = -.53 : gecko ? wheelPixelsPerUnit = 15 : chrome ? wheelPixelsPerUnit = -.7 : safari && (wheelPixelsPerUnit = -1 / 3);
  Selection.prototype.primary = function() {
    return this.ranges[this.primIndex];
  };
  Selection.prototype.equals = function(other) {
    if (other == this) {
      return !0;
    }
    if (other.primIndex != this.primIndex || other.ranges.length != this.ranges.length) {
      return !1;
    }
    for (var i = 0; i < this.ranges.length; i++) {
      var here = this.ranges[i], there = other.ranges[i];
      if (!equalCursorPos(here.anchor, there.anchor) || !equalCursorPos(here.head, there.head)) {
        return !1;
      }
    }
    return !0;
  };
  Selection.prototype.deepCopy = function() {
    for (var out = [], i = 0; i < this.ranges.length; i++) {
      out[i] = new Range(copyPos(this.ranges[i].anchor), copyPos(this.ranges[i].head));
    }
    return new Selection(out, this.primIndex);
  };
  Selection.prototype.somethingSelected = function() {
    for (var i = 0; i < this.ranges.length; i++) {
      if (!this.ranges[i].empty()) {
        return !0;
      }
    }
    return !1;
  };
  Selection.prototype.contains = function(pos, end) {
    end || (end = pos);
    for (var i = 0; i < this.ranges.length; i++) {
      var range = this.ranges[i];
      if (0 <= cmp(end, range.from()) && 0 >= cmp(pos, range.to())) {
        return i;
      }
    }
    return -1;
  };
  Range.prototype.from = function() {
    return minPos(this.anchor, this.head);
  };
  Range.prototype.to = function() {
    return maxPos(this.anchor, this.head);
  };
  Range.prototype.empty = function() {
    return this.head.line == this.anchor.line && this.head.ch == this.anchor.ch;
  };
  LeafChunk.prototype = {chunkSize:function() {
    return this.lines.length;
  }, removeInner:function(at, n) {
    for (var i = at, e = at + n; i < e; ++i) {
      var line = this.lines[i];
      this.height -= line.height;
      var line$jscomp$0 = line;
      line$jscomp$0.parent = null;
      detachMarkedSpans(line$jscomp$0);
      signalLater(line, "delete");
    }
    this.lines.splice(at, n);
  }, collapse:function(lines) {
    lines.push.apply(lines, this.lines);
  }, insertInner:function(at$jscomp$4_i, lines, height) {
    this.height += height;
    this.lines = this.lines.slice(0, at$jscomp$4_i).concat(lines).concat(this.lines.slice(at$jscomp$4_i));
    for (at$jscomp$4_i = 0; at$jscomp$4_i < lines.length; ++at$jscomp$4_i) {
      lines[at$jscomp$4_i].parent = this;
    }
  }, iterN:function(at, e$jscomp$67_n, op) {
    for (e$jscomp$67_n = at + e$jscomp$67_n; at < e$jscomp$67_n; ++at) {
      if (op(this.lines[at])) {
        return !0;
      }
    }
  }};
  BranchChunk.prototype = {chunkSize:function() {
    return this.size;
  }, removeInner:function(at$jscomp$6_lines, n) {
    this.size -= n;
    for (var i = 0; i < this.children.length; ++i) {
      var child = this.children[i], sz = child.chunkSize();
      if (at$jscomp$6_lines < sz) {
        var rm = Math.min(n, sz - at$jscomp$6_lines), oldHeight = child.height;
        child.removeInner(at$jscomp$6_lines, rm);
        this.height -= oldHeight - child.height;
        sz == rm && (this.children.splice(i--, 1), child.parent = null);
        if (0 == (n -= rm)) {
          break;
        }
        at$jscomp$6_lines = 0;
      } else {
        at$jscomp$6_lines -= sz;
      }
    }
    25 > this.size - n && (1 < this.children.length || !(this.children[0] instanceof LeafChunk)) && (at$jscomp$6_lines = [], this.collapse(at$jscomp$6_lines), this.children = [new LeafChunk(at$jscomp$6_lines)], this.children[0].parent = this);
  }, collapse:function(lines) {
    for (var i = 0; i < this.children.length; ++i) {
      this.children[i].collapse(lines);
    }
  }, insertInner:function(at, lines$jscomp$7_pos, height) {
    this.size += lines$jscomp$7_pos.length;
    this.height += height;
    for (var i = 0; i < this.children.length; ++i) {
      var child = this.children[i], sz = child.chunkSize();
      if (at <= sz) {
        child.insertInner(at, lines$jscomp$7_pos, height);
        if (child.lines && 50 < child.lines.length) {
          for (lines$jscomp$7_pos = at = child.lines.length % 25 + 25; lines$jscomp$7_pos < child.lines.length;) {
            height = new LeafChunk(child.lines.slice(lines$jscomp$7_pos, lines$jscomp$7_pos += 25)), child.height -= height.height, this.children.splice(++i, 0, height), height.parent = this;
          }
          child.lines = child.lines.slice(0, at);
          this.maybeSpill();
        }
        break;
      }
      at -= sz;
    }
  }, maybeSpill:function() {
    if (!(10 >= this.children.length)) {
      var me = this;
      do {
        var sibling_spilled = me.children.splice(me.children.length - 5, 5);
        sibling_spilled = new BranchChunk(sibling_spilled);
        if (me.parent) {
          me.size -= sibling_spilled.size;
          me.height -= sibling_spilled.height;
          var copy = indexOf(me.parent.children, me);
          me.parent.children.splice(copy + 1, 0, sibling_spilled);
        } else {
          copy = new BranchChunk(me.children), copy.parent = me, me.children = [copy, sibling_spilled], me = copy;
        }
        sibling_spilled.parent = me.parent;
      } while (10 < me.children.length);
      me.parent.maybeSpill();
    }
  }, iterN:function(at, n, op) {
    for (var i = 0; i < this.children.length; ++i) {
      var child = this.children[i], sz = child.chunkSize();
      if (at < sz) {
        sz = Math.min(n, sz - at);
        if (child.iterN(at, sz, op)) {
          return !0;
        }
        if (0 == (n -= sz)) {
          break;
        }
        at = 0;
      } else {
        at -= sz;
      }
    }
  }};
  LineWidget.prototype.clear = function() {
    var cm = this.doc.cm, ws = this.line.widgets, line = this.line, no = lineNo(line);
    if (null != no && ws) {
      for (var i = 0; i < ws.length; ++i) {
        ws[i] == this && ws.splice(i--, 1);
      }
      ws.length || (line.widgets = null);
      var height = widgetHeight(this);
      updateLineHeight(line, Math.max(0, line.height - height));
      cm && (runInOp(cm, function() {
        var diff = -height;
        heightAtLine(line) < (cm.curOp && cm.curOp.scrollTop || cm.doc.scrollTop) && addToScrollTop(cm, diff);
        regLineChange(cm, no, "widget");
      }), signalLater(cm, "lineWidgetCleared", cm, this, no));
    }
  };
  LineWidget.prototype.changed = function() {
    var this$1 = this, oldH = this.height, cm = this.doc.cm, line = this.line;
    this.height = null;
    var diff = widgetHeight(this) - oldH;
    diff && (lineIsHidden(this.doc, line) || updateLineHeight(line, line.height + diff), cm && runInOp(cm, function() {
      cm.curOp.forceUpdate = !0;
      heightAtLine(line) < (cm.curOp && cm.curOp.scrollTop || cm.doc.scrollTop) && addToScrollTop(cm, diff);
      signalLater(cm, "lineWidgetChanged", cm, this$1, lineNo(line));
    }));
  };
  eventMixin(LineWidget);
  var nextMarkerId = 0;
  TextMarker.prototype.clear = function() {
    if (!this.explicitlyCleared) {
      var cm = this.doc.cm, withOp = cm && !cm.curOp;
      withOp && startOperation(cm);
      if (hasHandler(this, "clear")) {
        var found = this.find();
        found && signalLater(this, "clear", found.from, found.to);
      }
      for (var max = found = null, i$1$jscomp$12_i = 0; i$1$jscomp$12_i < this.lines.length; ++i$1$jscomp$12_i) {
        var line = this.lines[i$1$jscomp$12_i], len$jscomp$5_span = getMarkedSpanFor(line.markedSpans, this);
        cm && !this.collapsed ? regLineChange(cm, lineNo(line), "text") : cm && (null != len$jscomp$5_span.to && (max = lineNo(line)), null != len$jscomp$5_span.from && (found = lineNo(line)));
        for (var JSCompiler_temp_const = line, r = void 0, spans = line.markedSpans, span = len$jscomp$5_span, i = 0; i < spans.length; ++i) {
          spans[i] != span && (r || (r = [])).push(spans[i]);
        }
        JSCompiler_temp_const.markedSpans = r;
        null == len$jscomp$5_span.from && this.collapsed && !lineIsHidden(this.doc, line) && cm && updateLineHeight(line, textHeight(cm.display));
      }
      if (cm && this.collapsed && !cm.options.lineWrapping) {
        for (i$1$jscomp$12_i = 0; i$1$jscomp$12_i < this.lines.length; ++i$1$jscomp$12_i) {
          line = visualLine(this.lines[i$1$jscomp$12_i]), len$jscomp$5_span = lineLength(line), len$jscomp$5_span > cm.display.maxLineLength && (cm.display.maxLine = line, cm.display.maxLineLength = len$jscomp$5_span, cm.display.maxLineChanged = !0);
        }
      }
      null != found && cm && this.collapsed && regChange(cm, found, max + 1);
      this.lines.length = 0;
      this.explicitlyCleared = !0;
      this.atomic && this.doc.cantEdit && (this.doc.cantEdit = !1, cm && reCheckSelection(cm.doc));
      cm && signalLater(cm, "markerCleared", cm, this, found, max);
      withOp && endOperation(cm);
      this.parent && this.parent.clear();
    }
  };
  TextMarker.prototype.find = function(side, lineObj) {
    null == side && "bookmark" == this.type && (side = 1);
    for (var from, to, i = 0; i < this.lines.length; ++i) {
      var line = this.lines[i], span = getMarkedSpanFor(line.markedSpans, this);
      if (null != span.from && (from = Pos(lineObj ? line : lineNo(line), span.from), -1 == side)) {
        return from;
      }
      if (null != span.to && (to = Pos(lineObj ? line : lineNo(line), span.to), 1 == side)) {
        return to;
      }
    }
    return from && {from, to};
  };
  TextMarker.prototype.changed = function() {
    var this$1 = this, pos = this.find(-1, !0), widget = this, cm = this.doc.cm;
    pos && cm && runInOp(cm, function() {
      var line = pos.line, dHeight_lineN$jscomp$12_oldHeight$jscomp$2_view = lineNo(pos.line);
      if (dHeight_lineN$jscomp$12_oldHeight$jscomp$2_view = findViewForLine(cm, dHeight_lineN$jscomp$12_oldHeight$jscomp$2_view)) {
        clearLineMeasurementCacheFor(dHeight_lineN$jscomp$12_oldHeight$jscomp$2_view), cm.curOp.selectionChanged = cm.curOp.forceUpdate = !0;
      }
      cm.curOp.updateMaxLine = !0;
      lineIsHidden(widget.doc, line) || null == widget.height || (dHeight_lineN$jscomp$12_oldHeight$jscomp$2_view = widget.height, widget.height = null, (dHeight_lineN$jscomp$12_oldHeight$jscomp$2_view = widgetHeight(widget) - dHeight_lineN$jscomp$12_oldHeight$jscomp$2_view) && updateLineHeight(line, line.height + dHeight_lineN$jscomp$12_oldHeight$jscomp$2_view));
      signalLater(cm, "markerChanged", cm, this$1);
    });
  };
  TextMarker.prototype.attachLine = function(line) {
    if (!this.lines.length && this.doc.cm) {
      var op = this.doc.cm.curOp;
      op.maybeHiddenMarkers && -1 != indexOf(op.maybeHiddenMarkers, this) || (op.maybeUnhiddenMarkers || (op.maybeUnhiddenMarkers = [])).push(this);
    }
    this.lines.push(line);
  };
  TextMarker.prototype.detachLine = function(line$jscomp$91_op) {
    this.lines.splice(indexOf(this.lines, line$jscomp$91_op), 1);
    !this.lines.length && this.doc.cm && (line$jscomp$91_op = this.doc.cm.curOp, (line$jscomp$91_op.maybeHiddenMarkers || (line$jscomp$91_op.maybeHiddenMarkers = [])).push(this));
  };
  eventMixin(TextMarker);
  SharedTextMarker.prototype.clear = function() {
    if (!this.explicitlyCleared) {
      this.explicitlyCleared = !0;
      for (var i = 0; i < this.markers.length; ++i) {
        this.markers[i].clear();
      }
      signalLater(this, "clear");
    }
  };
  SharedTextMarker.prototype.find = function(side, lineObj) {
    return this.primary.find(side, lineObj);
  };
  eventMixin(SharedTextMarker);
  var nextDocId = 0;
  Doc.prototype = createObj(BranchChunk.prototype, {constructor:Doc, iter:function(from, to, op) {
    op ? this.iterN(from - this.first, to - from, op) : this.iterN(this.first, this.first + this.size, from);
  }, insert:function(at, lines) {
    for (var height = 0, i = 0; i < lines.length; ++i) {
      height += lines[i].height;
    }
    this.insertInner(at - this.first, lines, height);
  }, remove:function(at, n) {
    this.removeInner(at - this.first, n);
  }, getValue:function(lineSep) {
    var lines = getLines(this, this.first, this.first + this.size);
    return !1 === lineSep ? lines : lines.join(lineSep || this.lineSeparator());
  }, setValue:docMethodOp(function(code) {
    var top = Pos(this.first, 0), last = this.first + this.size - 1;
    makeChange(this, {from:top, to:Pos(last, getLine(this, last).text.length), text:this.splitLines(code), origin:"setValue", full:!0}, !0);
    this.cm && scrollToCoords(this.cm, 0, 0);
    setSelection(this, simpleSelection(top), sel_dontScroll);
  }), replaceRange:function(code, from, to, origin) {
    from = clipPos(this, from);
    to = to ? clipPos(this, to) : from;
    replaceRange(this, code, from, to, origin);
  }, getRange:function(from$jscomp$38_lines, to, lineSep) {
    from$jscomp$38_lines = getBetween(this, clipPos(this, from$jscomp$38_lines), clipPos(this, to));
    return !1 === lineSep ? from$jscomp$38_lines : "" === lineSep ? from$jscomp$38_lines.join("") : from$jscomp$38_lines.join(lineSep || this.lineSeparator());
  }, getLine:function(l$jscomp$17_line) {
    return (l$jscomp$17_line = this.getLineHandle(l$jscomp$17_line)) && l$jscomp$17_line.text;
  }, getLineHandle:function(line) {
    if (isLine(this, line)) {
      return getLine(this, line);
    }
  }, getLineNumber:function(line) {
    return lineNo(line);
  }, getLineHandleVisualStart:function(line) {
    "number" == typeof line && (line = getLine(this, line));
    return visualLine(line);
  }, lineCount:function() {
    return this.size;
  }, firstLine:function() {
    return this.first;
  }, lastLine:function() {
    return this.first + this.size - 1;
  }, clipPos:function(pos) {
    return clipPos(this, pos);
  }, getCursor:function(start) {
    var range = this.sel.primary();
    return null == start || "head" == start ? range.head : "anchor" == start ? range.anchor : "end" == start || "to" == start || !1 === start ? range.to() : range.from();
  }, listSelections:function() {
    return this.sel.ranges;
  }, somethingSelected:function() {
    return this.sel.somethingSelected();
  }, setCursor:docMethodOp(function(anchor$jscomp$inline_467_line, ch, options) {
    anchor$jscomp$inline_467_line = clipPos(this, "number" == typeof anchor$jscomp$inline_467_line ? Pos(anchor$jscomp$inline_467_line, ch || 0) : anchor$jscomp$inline_467_line);
    setSelection(this, simpleSelection(anchor$jscomp$inline_467_line, null), options);
  }), setSelection:docMethodOp(function(anchor$jscomp$7_head, head, options) {
    var anchor = clipPos(this, anchor$jscomp$7_head);
    anchor$jscomp$7_head = clipPos(this, head || anchor$jscomp$7_head);
    setSelection(this, simpleSelection(anchor, anchor$jscomp$7_head), options);
  }), extendSelection:docMethodOp(function(head, other, options) {
    extendSelection(this, clipPos(this, head), other && clipPos(this, other), options);
  }), extendSelections:docMethodOp(function(heads, options) {
    extendSelections(this, clipPosArray(this, heads), options);
  }), extendSelectionsBy:docMethodOp(function(f$jscomp$59_heads, options) {
    f$jscomp$59_heads = map$jscomp$0(this.sel.ranges, f$jscomp$59_heads);
    extendSelections(this, clipPosArray(this, f$jscomp$59_heads), options);
  }), setSelections:docMethodOp(function(ranges, primary, options) {
    if (ranges.length) {
      for (var out = [], i = 0; i < ranges.length; i++) {
        out[i] = new Range(clipPos(this, ranges[i].anchor), clipPos(this, ranges[i].head || ranges[i].anchor));
      }
      null == primary && (primary = Math.min(ranges.length - 1, this.sel.primIndex));
      setSelection(this, normalizeSelection(this.cm, out, primary), options);
    }
  }), addSelection:docMethodOp(function(anchor, head, options) {
    var ranges = this.sel.ranges.slice(0);
    ranges.push(new Range(clipPos(this, anchor), clipPos(this, head || anchor)));
    setSelection(this, normalizeSelection(this.cm, ranges, ranges.length - 1), options);
  }), getSelection:function(lineSep) {
    for (var ranges = this.sel.ranges, lines, i = 0; i < ranges.length; i++) {
      var sel = getBetween(this, ranges[i].from(), ranges[i].to());
      lines = lines ? lines.concat(sel) : sel;
    }
    return !1 === lineSep ? lines : lines.join(lineSep || this.lineSeparator());
  }, getSelections:function(lineSep) {
    for (var parts = [], ranges = this.sel.ranges, i = 0; i < ranges.length; i++) {
      var sel = getBetween(this, ranges[i].from(), ranges[i].to());
      !1 !== lineSep && (sel = sel.join(lineSep || this.lineSeparator()));
      parts[i] = sel;
    }
    return parts;
  }, replaceSelection:function(code, collapse, origin) {
    for (var dup = [], i = 0; i < this.sel.ranges.length; i++) {
      dup[i] = code;
    }
    this.replaceSelections(dup, collapse, origin || "+input");
  }, replaceSelections:docMethodOp(function(JSCompiler_temp$jscomp$49_code$jscomp$10_i$1$jscomp$13_out, collapse$jscomp$4_newSel, oldPrev$jscomp$inline_480_origin) {
    for (var changes = [], newPrev$jscomp$inline_481_sel = this.sel, i$jscomp$243_i = 0; i$jscomp$243_i < newPrev$jscomp$inline_481_sel.ranges.length; i$jscomp$243_i++) {
      var from$jscomp$inline_484_range = newPrev$jscomp$inline_481_sel.ranges[i$jscomp$243_i];
      changes[i$jscomp$243_i] = {from:from$jscomp$inline_484_range.from(), to:from$jscomp$inline_484_range.to(), text:this.splitLines(JSCompiler_temp$jscomp$49_code$jscomp$10_i$1$jscomp$13_out[i$jscomp$243_i]), origin:oldPrev$jscomp$inline_480_origin};
    }
    if (JSCompiler_temp$jscomp$49_code$jscomp$10_i$1$jscomp$13_out = collapse$jscomp$4_newSel && "end" != collapse$jscomp$4_newSel) {
      JSCompiler_temp$jscomp$49_code$jscomp$10_i$1$jscomp$13_out = [];
      newPrev$jscomp$inline_481_sel = oldPrev$jscomp$inline_480_origin = Pos(this.first, 0);
      for (i$jscomp$243_i = 0; i$jscomp$243_i < changes.length; i$jscomp$243_i++) {
        var change$jscomp$inline_483_inv$jscomp$inline_487_range = changes[i$jscomp$243_i];
        from$jscomp$inline_484_range = offsetPos(change$jscomp$inline_483_inv$jscomp$inline_487_range.from, oldPrev$jscomp$inline_480_origin, newPrev$jscomp$inline_481_sel);
        var to = offsetPos(changeEnd(change$jscomp$inline_483_inv$jscomp$inline_487_range), oldPrev$jscomp$inline_480_origin, newPrev$jscomp$inline_481_sel);
        oldPrev$jscomp$inline_480_origin = change$jscomp$inline_483_inv$jscomp$inline_487_range.to;
        newPrev$jscomp$inline_481_sel = to;
        "around" == collapse$jscomp$4_newSel ? (change$jscomp$inline_483_inv$jscomp$inline_487_range = this.sel.ranges[i$jscomp$243_i], change$jscomp$inline_483_inv$jscomp$inline_487_range = 0 > cmp(change$jscomp$inline_483_inv$jscomp$inline_487_range.head, change$jscomp$inline_483_inv$jscomp$inline_487_range.anchor), JSCompiler_temp$jscomp$49_code$jscomp$10_i$1$jscomp$13_out[i$jscomp$243_i] = new Range(change$jscomp$inline_483_inv$jscomp$inline_487_range ? to : from$jscomp$inline_484_range, change$jscomp$inline_483_inv$jscomp$inline_487_range ? 
        from$jscomp$inline_484_range : to)) : JSCompiler_temp$jscomp$49_code$jscomp$10_i$1$jscomp$13_out[i$jscomp$243_i] = new Range(from$jscomp$inline_484_range, from$jscomp$inline_484_range);
      }
      JSCompiler_temp$jscomp$49_code$jscomp$10_i$1$jscomp$13_out = new Selection(JSCompiler_temp$jscomp$49_code$jscomp$10_i$1$jscomp$13_out, this.sel.primIndex);
    }
    collapse$jscomp$4_newSel = JSCompiler_temp$jscomp$49_code$jscomp$10_i$1$jscomp$13_out;
    for (JSCompiler_temp$jscomp$49_code$jscomp$10_i$1$jscomp$13_out = changes.length - 1; 0 <= JSCompiler_temp$jscomp$49_code$jscomp$10_i$1$jscomp$13_out; JSCompiler_temp$jscomp$49_code$jscomp$10_i$1$jscomp$13_out--) {
      makeChange(this, changes[JSCompiler_temp$jscomp$49_code$jscomp$10_i$1$jscomp$13_out]);
    }
    collapse$jscomp$4_newSel ? setSelectionReplaceHistory(this, collapse$jscomp$4_newSel) : this.cm && ensureCursorVisible(this.cm);
  }), undo:docMethodOp(function() {
    makeChangeFromHistory(this, "undo");
  }), redo:docMethodOp(function() {
    makeChangeFromHistory(this, "redo");
  }), undoSelection:docMethodOp(function() {
    makeChangeFromHistory(this, "undo", !0);
  }), redoSelection:docMethodOp(function() {
    makeChangeFromHistory(this, "redo", !0);
  }), setExtending:function(val) {
    this.extend = val;
  }, getExtending:function() {
    return this.extend;
  }, historySize:function() {
    for (var hist = this.history, done = 0, undone = 0, i$1$jscomp$14_i = 0; i$1$jscomp$14_i < hist.done.length; i$1$jscomp$14_i++) {
      hist.done[i$1$jscomp$14_i].ranges || ++done;
    }
    for (i$1$jscomp$14_i = 0; i$1$jscomp$14_i < hist.undone.length; i$1$jscomp$14_i++) {
      hist.undone[i$1$jscomp$14_i].ranges || ++undone;
    }
    return {undo:done, redo:undone};
  }, clearHistory:function() {
    var this$1 = this;
    this.history = new History(this.history);
    linkedDocs(this, function(doc) {
      return doc.history = this$1.history;
    }, !0);
  }, markClean:function() {
    this.cleanGeneration = this.changeGeneration(!0);
  }, changeGeneration:function(forceSplit) {
    forceSplit && (this.history.lastOp = this.history.lastSelOp = this.history.lastOrigin = null);
    return this.history.generation;
  }, isClean:function(gen) {
    return this.history.generation == (gen || this.cleanGeneration);
  }, getHistory:function() {
    return {done:copyHistoryArray(this.history.done), undone:copyHistoryArray(this.history.undone)};
  }, setHistory:function(histData) {
    var hist = this.history = new History(this.history);
    hist.done = copyHistoryArray(histData.done.slice(0), null, !0);
    hist.undone = copyHistoryArray(histData.undone.slice(0), null, !0);
  }, setGutterMarker:docMethodOp(function(line$jscomp$0, gutterID, value) {
    return changeLine(this, line$jscomp$0, "gutter", function(line) {
      var markers = line.gutterMarkers || (line.gutterMarkers = {});
      markers[gutterID] = value;
      !value && isEmpty(markers) && (line.gutterMarkers = null);
      return !0;
    });
  }), clearGutter:docMethodOp(function(gutterID) {
    var this$1 = this;
    this.iter(function(line) {
      line.gutterMarkers && line.gutterMarkers[gutterID] && changeLine(this$1, line, "gutter", function() {
        line.gutterMarkers[gutterID] = null;
        isEmpty(line.gutterMarkers) && (line.gutterMarkers = null);
        return !0;
      });
    });
  }), lineInfo:function(line) {
    if ("number" == typeof line) {
      if (!isLine(this, line)) {
        return null;
      }
      var n = line;
      line = getLine(this, line);
      if (!line) {
        return null;
      }
    } else {
      if (n = lineNo(line), null == n) {
        return null;
      }
    }
    return {line:n, handle:line, text:line.text, gutterMarkers:line.gutterMarkers, textClass:line.textClass, bgClass:line.bgClass, wrapClass:line.wrapClass, widgets:line.widgets};
  }, addLineClass:docMethodOp(function(handle, where, cls) {
    return changeLine(this, handle, "gutter" == where ? "gutter" : "class", function(line) {
      var prop = "text" == where ? "textClass" : "background" == where ? "bgClass" : "gutter" == where ? "gutterClass" : "wrapClass";
      if (line[prop]) {
        if (classTest(cls).test(line[prop])) {
          return !1;
        }
        line[prop] += " " + cls;
      } else {
        line[prop] = cls;
      }
      return !0;
    });
  }), removeLineClass:docMethodOp(function(handle, where, cls) {
    return changeLine(this, handle, "gutter" == where ? "gutter" : "class", function(line) {
      var prop = "text" == where ? "textClass" : "background" == where ? "bgClass" : "gutter" == where ? "gutterClass" : "wrapClass", cur = line[prop];
      if (cur) {
        if (null == cls) {
          line[prop] = null;
        } else {
          var found = cur.match(classTest(cls));
          if (!found) {
            return !1;
          }
          var end = found.index + found[0].length;
          line[prop] = cur.slice(0, found.index) + (found.index && end != cur.length ? " " : "") + cur.slice(end) || null;
        }
      } else {
        return !1;
      }
      return !0;
    });
  }), addLineWidget:docMethodOp(function(handle, node, options) {
    return addLineWidget(this, handle, node, options);
  }), removeLineWidget:function(widget) {
    widget.clear();
  }, markText:function(from, to, options) {
    return markText(this, clipPos(this, from), clipPos(this, to), options, options && options.type || "range");
  }, setBookmark:function(pos, options) {
    options = {replacedWith:options && (null == options.nodeType ? options.widget : options), insertLeft:options && options.insertLeft, clearWhenEmpty:!1, shared:options && options.shared, handleMouseEvents:options && options.handleMouseEvents};
    pos = clipPos(this, pos);
    return markText(this, pos, pos, options, "bookmark");
  }, findMarksAt:function(pos) {
    pos = clipPos(this, pos);
    var markers = [], spans = getLine(this, pos.line).markedSpans;
    if (spans) {
      for (var i = 0; i < spans.length; ++i) {
        var span = spans[i];
        (null == span.from || span.from <= pos.ch) && (null == span.to || span.to >= pos.ch) && markers.push(span.marker.parent || span.marker);
      }
    }
    return markers;
  }, findMarks:function(from, to, filter) {
    from = clipPos(this, from);
    to = clipPos(this, to);
    var found = [], lineNo = from.line;
    this.iter(from.line, to.line + 1, function(line$jscomp$105_spans) {
      if (line$jscomp$105_spans = line$jscomp$105_spans.markedSpans) {
        for (var i = 0; i < line$jscomp$105_spans.length; i++) {
          var span = line$jscomp$105_spans[i];
          null != span.to && lineNo == from.line && from.ch >= span.to || null == span.from && lineNo != from.line || null != span.from && lineNo == to.line && span.from >= to.ch || filter && !filter(span.marker) || found.push(span.marker.parent || span.marker);
        }
      }
      ++lineNo;
    });
    return found;
  }, getAllMarks:function() {
    var markers = [];
    this.iter(function(line$jscomp$106_sps) {
      if (line$jscomp$106_sps = line$jscomp$106_sps.markedSpans) {
        for (var i = 0; i < line$jscomp$106_sps.length; ++i) {
          null != line$jscomp$106_sps[i].from && markers.push(line$jscomp$106_sps[i].marker);
        }
      }
    });
    return markers;
  }, posFromIndex:function(off) {
    var ch, lineNo = this.first, sepSize = this.lineSeparator().length;
    this.iter(function(line$jscomp$107_sz) {
      line$jscomp$107_sz = line$jscomp$107_sz.text.length + sepSize;
      if (line$jscomp$107_sz > off) {
        return ch = off, !0;
      }
      off -= line$jscomp$107_sz;
      ++lineNo;
    });
    return clipPos(this, Pos(lineNo, ch));
  }, indexFromPos:function(coords) {
    coords = clipPos(this, coords);
    var index = coords.ch;
    if (coords.line < this.first || 0 > coords.ch) {
      return 0;
    }
    var sepSize = this.lineSeparator().length;
    this.iter(this.first, coords.line, function(line) {
      index += line.text.length + sepSize;
    });
    return index;
  }, copy:function(copyHistory) {
    var doc = new Doc(getLines(this, this.first, this.first + this.size), this.modeOption, this.first, this.lineSep, this.direction);
    doc.scrollTop = this.scrollTop;
    doc.scrollLeft = this.scrollLeft;
    doc.sel = this.sel;
    doc.extend = !1;
    copyHistory && (doc.history.undoDepth = this.history.undoDepth, doc.setHistory(this.getHistory()));
    return doc;
  }, linkedDoc:function(markers$jscomp$inline_490_options) {
    markers$jscomp$inline_490_options || (markers$jscomp$inline_490_options = {});
    var copy$jscomp$3_from = this.first, i$jscomp$inline_491_to = this.first + this.size;
    null != markers$jscomp$inline_490_options.from && markers$jscomp$inline_490_options.from > copy$jscomp$3_from && (copy$jscomp$3_from = markers$jscomp$inline_490_options.from);
    null != markers$jscomp$inline_490_options.to && markers$jscomp$inline_490_options.to < i$jscomp$inline_491_to && (i$jscomp$inline_491_to = markers$jscomp$inline_490_options.to);
    copy$jscomp$3_from = new Doc(getLines(this, copy$jscomp$3_from, i$jscomp$inline_491_to), markers$jscomp$inline_490_options.mode || this.modeOption, copy$jscomp$3_from, this.lineSep, this.direction);
    markers$jscomp$inline_490_options.sharedHist && (copy$jscomp$3_from.history = this.history);
    (this.linked || (this.linked = [])).push({doc:copy$jscomp$3_from, sharedHist:markers$jscomp$inline_490_options.sharedHist});
    copy$jscomp$3_from.linked = [{doc:this, isParent:!0, sharedHist:markers$jscomp$inline_490_options.sharedHist}];
    markers$jscomp$inline_490_options = findSharedMarkers(this);
    for (i$jscomp$inline_491_to = 0; i$jscomp$inline_491_to < markers$jscomp$inline_490_options.length; i$jscomp$inline_491_to++) {
      var marker = markers$jscomp$inline_490_options[i$jscomp$inline_491_to], mTo$jscomp$inline_495_pos = marker.find(), mFrom$jscomp$inline_494_subMark = copy$jscomp$3_from.clipPos(mTo$jscomp$inline_495_pos.from);
      mTo$jscomp$inline_495_pos = copy$jscomp$3_from.clipPos(mTo$jscomp$inline_495_pos.to);
      cmp(mFrom$jscomp$inline_494_subMark, mTo$jscomp$inline_495_pos) && (mFrom$jscomp$inline_494_subMark = markText(copy$jscomp$3_from, mFrom$jscomp$inline_494_subMark, mTo$jscomp$inline_495_pos, marker.primary, marker.primary.type), marker.markers.push(mFrom$jscomp$inline_494_subMark), mFrom$jscomp$inline_494_subMark.parent = marker);
    }
    return copy$jscomp$3_from;
  }, unlinkDoc:function(other) {
    other instanceof CodeMirror$jscomp$0 && (other = other.doc);
    if (this.linked) {
      for (var i = 0; i < this.linked.length; ++i) {
        if (this.linked[i].doc == other) {
          this.linked.splice(i, 1);
          other.unlinkDoc(this);
          detachSharedMarkers(findSharedMarkers(this));
          break;
        }
      }
    }
    if (other.history == this.history) {
      var splitIds = [other.id];
      linkedDocs(other, function(doc) {
        return splitIds.push(doc.id);
      }, !0);
      other.history = new History(null);
      other.history.done = copyHistoryArray(this.history.done, splitIds);
      other.history.undone = copyHistoryArray(this.history.undone, splitIds);
    }
  }, iterLinkedDocs:function(f) {
    linkedDocs(this, f);
  }, getMode:function() {
    return this.mode;
  }, getEditor:function() {
    return this.cm;
  }, splitLines:function(str) {
    return this.lineSep ? str.split(this.lineSep) : splitLinesAuto(str);
  }, lineSeparator:function() {
    return this.lineSep || "\n";
  }, setDirection:docMethodOp(function(dir) {
    "rtl" != dir && (dir = "ltr");
    dir != this.direction && (this.direction = dir, this.iter(function(line) {
      return line.order = null;
    }), this.cm && directionChanged(this.cm));
  })});
  Doc.prototype.eachLine = Doc.prototype.iter;
  for (var lastDrop = 0, globalsRegistered = !1, keyNames = {3:"Pause", 8:"Backspace", 9:"Tab", 13:"Enter", 16:"Shift", 17:"Ctrl", 18:"Alt", 19:"Pause", 20:"CapsLock", 27:"Esc", 32:"Space", 33:"PageUp", 34:"PageDown", 35:"End", 36:"Home", 37:"Left", 38:"Up", 39:"Right", 40:"Down", 44:"PrintScrn", 45:"Insert", 46:"Delete", 59:";", 61:"=", 91:"Mod", 92:"Mod", 93:"Mod", 106:"*", 107:"=", 109:"-", 110:".", 111:"/", 145:"ScrollLock", 173:"-", 186:";", 187:"=", 188:",", 189:"-", 190:".", 191:"/", 192:"`", 
  219:"[", 220:"\\", 221:"]", 222:"'", 224:"Mod", 63232:"Up", 63233:"Down", 63234:"Left", 63235:"Right", 63272:"Delete", 63273:"Home", 63275:"End", 63276:"PageUp", 63277:"PageDown", 63302:"Insert"}, i$jscomp$1 = 0; 10 > i$jscomp$1; i$jscomp$1++) {
    keyNames[i$jscomp$1 + 48] = keyNames[i$jscomp$1 + 96] = String(i$jscomp$1);
  }
  for (var i$1 = 65; 90 >= i$1; i$1++) {
    keyNames[i$1] = String.fromCharCode(i$1);
  }
  for (var i$2 = 1; 12 >= i$2; i$2++) {
    keyNames[i$2 + 111] = keyNames[i$2 + 63235] = "F" + i$2;
  }
  var keyMap = {basic:{Left:"goCharLeft", Right:"goCharRight", Up:"goLineUp", Down:"goLineDown", End:"goLineEnd", Home:"goLineStartSmart", PageUp:"goPageUp", PageDown:"goPageDown", Delete:"delCharAfter", Backspace:"delCharBefore", "Shift-Backspace":"delCharBefore", Tab:"defaultTab", "Shift-Tab":"indentAuto", Enter:"newlineAndIndent", Insert:"toggleOverwrite", Esc:"singleSelection"}, pcDefault:{"Ctrl-A":"selectAll", "Ctrl-D":"deleteLine", "Ctrl-Z":"undo", "Shift-Ctrl-Z":"redo", "Ctrl-Y":"redo", "Ctrl-Home":"goDocStart", 
  "Ctrl-End":"goDocEnd", "Ctrl-Up":"goLineUp", "Ctrl-Down":"goLineDown", "Ctrl-Left":"goGroupLeft", "Ctrl-Right":"goGroupRight", "Alt-Left":"goLineStart", "Alt-Right":"goLineEnd", "Ctrl-Backspace":"delGroupBefore", "Ctrl-Delete":"delGroupAfter", "Ctrl-S":"save", "Ctrl-F":"find", "Ctrl-G":"findNext", "Shift-Ctrl-G":"findPrev", "Shift-Ctrl-F":"replace", "Shift-Ctrl-R":"replaceAll", "Ctrl-[":"indentLess", "Ctrl-]":"indentMore", "Ctrl-U":"undoSelection", "Shift-Ctrl-U":"redoSelection", "Alt-U":"redoSelection", 
  fallthrough:"basic"}, emacsy:{"Ctrl-F":"goCharRight", "Ctrl-B":"goCharLeft", "Ctrl-P":"goLineUp", "Ctrl-N":"goLineDown", "Ctrl-A":"goLineStart", "Ctrl-E":"goLineEnd", "Ctrl-V":"goPageDown", "Shift-Ctrl-V":"goPageUp", "Ctrl-D":"delCharAfter", "Ctrl-H":"delCharBefore", "Alt-Backspace":"delWordBefore", "Ctrl-K":"killLine", "Ctrl-T":"transposeChars", "Ctrl-O":"openLine"}, macDefault:{"Cmd-A":"selectAll", "Cmd-D":"deleteLine", "Cmd-Z":"undo", "Shift-Cmd-Z":"redo", "Cmd-Y":"redo", "Cmd-Home":"goDocStart", 
  "Cmd-Up":"goDocStart", "Cmd-End":"goDocEnd", "Cmd-Down":"goDocEnd", "Alt-Left":"goGroupLeft", "Alt-Right":"goGroupRight", "Cmd-Left":"goLineLeft", "Cmd-Right":"goLineRight", "Alt-Backspace":"delGroupBefore", "Ctrl-Alt-Backspace":"delGroupAfter", "Alt-Delete":"delGroupAfter", "Cmd-S":"save", "Cmd-F":"find", "Cmd-G":"findNext", "Shift-Cmd-G":"findPrev", "Cmd-Alt-F":"replace", "Shift-Cmd-Alt-F":"replaceAll", "Cmd-[":"indentLess", "Cmd-]":"indentMore", "Cmd-Backspace":"delWrappedLineLeft", "Cmd-Delete":"delWrappedLineRight", 
  "Cmd-U":"undoSelection", "Shift-Cmd-U":"redoSelection", "Ctrl-Up":"goDocStart", "Ctrl-Down":"goDocEnd", fallthrough:["basic", "emacsy"]}};
  keyMap["default"] = mac ? keyMap.macDefault : keyMap.pcDefault;
  var commands = {selectAll, singleSelection:function(cm) {
    return cm.setSelection(cm.getCursor("anchor"), cm.getCursor("head"), sel_dontScroll);
  }, killLine:function(cm) {
    return deleteNearSelection(cm, function(range) {
      if (range.empty()) {
        var len = getLine(cm.doc, range.head.line).text.length;
        return range.head.ch == len && range.head.line < cm.lastLine() ? {from:range.head, to:Pos(range.head.line + 1, 0)} : {from:range.head, to:Pos(range.head.line, len)};
      }
      return {from:range.from(), to:range.to()};
    });
  }, deleteLine:function(cm) {
    return deleteNearSelection(cm, function(range) {
      return {from:Pos(range.from().line, 0), to:clipPos(cm.doc, Pos(range.to().line + 1, 0))};
    });
  }, delLineLeft:function(cm) {
    return deleteNearSelection(cm, function(range) {
      return {from:Pos(range.from().line, 0), to:range.from()};
    });
  }, delWrappedLineLeft:function(cm) {
    return deleteNearSelection(cm, function(range) {
      var top = cm.charCoords(range.head, "div").top + 5;
      return {from:cm.coordsChar({left:0, top}, "div"), to:range.from()};
    });
  }, delWrappedLineRight:function(cm) {
    return deleteNearSelection(cm, function(range) {
      var rightPos_top = cm.charCoords(range.head, "div").top + 5;
      rightPos_top = cm.coordsChar({left:cm.display.lineDiv.offsetWidth + 100, top:rightPos_top}, "div");
      return {from:range.from(), to:rightPos_top};
    });
  }, undo:function(cm) {
    return cm.undo();
  }, redo:function(cm) {
    return cm.redo();
  }, undoSelection:function(cm) {
    return cm.undoSelection();
  }, redoSelection:function(cm) {
    return cm.redoSelection();
  }, goDocStart:function(cm) {
    return cm.extendSelection(Pos(cm.firstLine(), 0));
  }, goDocEnd:function(cm) {
    return cm.extendSelection(Pos(cm.lastLine()));
  }, goLineStart:function(cm) {
    return cm.extendSelectionsBy(function(range) {
      return lineStart(cm, range.head.line);
    }, {origin:"+move", bias:1});
  }, goLineStartSmart:function(cm) {
    return cm.extendSelectionsBy(function(range) {
      return lineStartSmart(cm, range.head);
    }, {origin:"+move", bias:1});
  }, goLineEnd:function(cm) {
    return cm.extendSelectionsBy(function(lineN$jscomp$inline_499_range) {
      lineN$jscomp$inline_499_range = lineN$jscomp$inline_499_range.head.line;
      var line = getLine(cm.doc, lineN$jscomp$inline_499_range);
      var line$jscomp$inline_721_visual = line;
      for (var merged; merged = collapsedSpanAtSide(line$jscomp$inline_721_visual, !1);) {
        line$jscomp$inline_721_visual = merged.find(1, !0).line;
      }
      line$jscomp$inline_721_visual != line && (lineN$jscomp$inline_499_range = lineNo(line$jscomp$inline_721_visual));
      return endOfLine(!0, cm, line, lineN$jscomp$inline_499_range, -1);
    }, {origin:"+move", bias:-1});
  }, goLineRight:function(cm) {
    return cm.extendSelectionsBy(function(range$jscomp$30_top) {
      range$jscomp$30_top = cm.cursorCoords(range$jscomp$30_top.head, "div").top + 5;
      return cm.coordsChar({left:cm.display.lineDiv.offsetWidth + 100, top:range$jscomp$30_top}, "div");
    }, sel_move);
  }, goLineLeft:function(cm) {
    return cm.extendSelectionsBy(function(range$jscomp$31_top) {
      range$jscomp$31_top = cm.cursorCoords(range$jscomp$31_top.head, "div").top + 5;
      return cm.coordsChar({left:0, top:range$jscomp$31_top}, "div");
    }, sel_move);
  }, goLineLeftSmart:function(cm) {
    return cm.extendSelectionsBy(function(range) {
      var pos$jscomp$50_top = cm.cursorCoords(range.head, "div").top + 5;
      pos$jscomp$50_top = cm.coordsChar({left:0, top:pos$jscomp$50_top}, "div");
      return pos$jscomp$50_top.ch < cm.getLine(pos$jscomp$50_top.line).search(/\S/) ? lineStartSmart(cm, range.head) : pos$jscomp$50_top;
    }, sel_move);
  }, goLineUp:function(cm) {
    return cm.moveV(-1, "line");
  }, goLineDown:function(cm) {
    return cm.moveV(1, "line");
  }, goPageUp:function(cm) {
    return cm.moveV(-1, "page");
  }, goPageDown:function(cm) {
    return cm.moveV(1, "page");
  }, goCharLeft:function(cm) {
    return cm.moveH(-1, "char");
  }, goCharRight:function(cm) {
    return cm.moveH(1, "char");
  }, goColumnLeft:function(cm) {
    return cm.moveH(-1, "column");
  }, goColumnRight:function(cm) {
    return cm.moveH(1, "column");
  }, goWordLeft:function(cm) {
    return cm.moveH(-1, "word");
  }, goGroupRight:function(cm) {
    return cm.moveH(1, "group");
  }, goGroupLeft:function(cm) {
    return cm.moveH(-1, "group");
  }, goWordRight:function(cm) {
    return cm.moveH(1, "word");
  }, delCharBefore:function(cm) {
    return cm.deleteH(-1, "codepoint");
  }, delCharAfter:function(cm) {
    return cm.deleteH(1, "char");
  }, delWordBefore:function(cm) {
    return cm.deleteH(-1, "word");
  }, delWordAfter:function(cm) {
    return cm.deleteH(1, "word");
  }, delGroupBefore:function(cm) {
    return cm.deleteH(-1, "group");
  }, delGroupAfter:function(cm) {
    return cm.deleteH(1, "group");
  }, indentAuto:function(cm) {
    return cm.indentSelection("smart");
  }, indentMore:function(cm) {
    return cm.indentSelection("add");
  }, indentLess:function(cm) {
    return cm.indentSelection("subtract");
  }, insertTab:function(cm) {
    return cm.replaceSelection("\t");
  }, insertSoftTab:function(cm) {
    for (var spaces = [], ranges = cm.listSelections(), tabSize = cm.options.tabSize, i = 0; i < ranges.length; i++) {
      var col$jscomp$1_pos = ranges[i].from();
      col$jscomp$1_pos = countColumn(cm.getLine(col$jscomp$1_pos.line), col$jscomp$1_pos.ch, tabSize);
      spaces.push(spaceStr(tabSize - col$jscomp$1_pos % tabSize));
    }
    cm.replaceSelections(spaces);
  }, defaultTab:function(cm) {
    cm.somethingSelected() ? cm.indentSelection("add") : cm.execCommand("insertTab");
  }, transposeChars:function(cm) {
    return runInOp(cm, function() {
      for (var ranges = cm.listSelections(), newSel = [], i = 0; i < ranges.length; i++) {
        if (ranges[i].empty()) {
          var cur = ranges[i].head, line = getLine(cm.doc, cur.line).text;
          if (line) {
            if (cur.ch == line.length && (cur = new Pos(cur.line, cur.ch - 1)), 0 < cur.ch) {
              cur = new Pos(cur.line, cur.ch + 1), cm.replaceRange(line.charAt(cur.ch - 1) + line.charAt(cur.ch - 2), Pos(cur.line, cur.ch - 2), cur, "+transpose");
            } else if (cur.line > cm.doc.first) {
              var prev = getLine(cm.doc, cur.line - 1).text;
              prev && (cur = new Pos(cur.line, 1), cm.replaceRange(line.charAt(0) + cm.doc.lineSeparator() + prev.charAt(prev.length - 1), Pos(cur.line - 1, prev.length - 1), cur, "+transpose"));
            }
          }
          newSel.push(new Range(cur, cur));
        }
      }
      cm.setSelections(newSel);
    });
  }, newlineAndIndent:function(cm) {
    return runInOp(cm, function() {
      for (var sels = cm.listSelections(), i$1$jscomp$16_i = sels.length - 1; 0 <= i$1$jscomp$16_i; i$1$jscomp$16_i--) {
        cm.replaceRange(cm.doc.lineSeparator(), sels[i$1$jscomp$16_i].anchor, sels[i$1$jscomp$16_i].head, "+input");
      }
      sels = cm.listSelections();
      for (i$1$jscomp$16_i = 0; i$1$jscomp$16_i < sels.length; i$1$jscomp$16_i++) {
        cm.indentLine(sels[i$1$jscomp$16_i].from().line, null, !0);
      }
      ensureCursorVisible(cm);
    });
  }, openLine:function(cm) {
    return cm.replaceSelection("\n", "start");
  }, toggleOverwrite:function(cm) {
    return cm.toggleOverwrite();
  }}, stopSeq = new Delayed(), lastStoppedKey = null;
  PastClick.prototype.compare = function(time, pos, button) {
    return this.time + 400 > time && 0 == cmp(pos, this.pos) && button == this.button;
  };
  var lastClick, lastDoubleClick, Init = {toString:function() {
    return "CodeMirror.Init";
  }}, defaults = {}, optionHandlers = {};
  CodeMirror$jscomp$0.defaults = defaults;
  CodeMirror$jscomp$0.optionHandlers = optionHandlers;
  var initHooks = [];
  CodeMirror$jscomp$0.defineInitHook = function(f) {
    return initHooks.push(f);
  };
  var lastCopied = null;
  ContentEditableInput.prototype.init = function(display) {
    function belongsToInput(e$jscomp$105_t) {
      for (e$jscomp$105_t = e$jscomp$105_t.target; e$jscomp$105_t; e$jscomp$105_t = e$jscomp$105_t.parentNode) {
        if (e$jscomp$105_t == div) {
          return !0;
        }
        if (/\bCodeMirror-(?:line)?widget\b/.test(e$jscomp$105_t.className)) {
          break;
        }
      }
      return !1;
    }
    function onCopyCut(e$jscomp$110_te) {
      if (belongsToInput(e$jscomp$110_te) && !signalDOMEvent(cm, e$jscomp$110_te)) {
        if (cm.somethingSelected()) {
          lastCopied = {lineWise:!1, text:cm.getSelections()}, "cut" == e$jscomp$110_te.type && cm.replaceSelection("", null, "cut");
        } else if (cm.options.lineWiseCopyCut) {
          var ranges = copyableRanges(cm);
          lastCopied = {lineWise:!0, text:ranges.text};
          "cut" == e$jscomp$110_te.type && cm.operation(function() {
            cm.setSelections(ranges.ranges, 0, sel_dontScroll);
            cm.replaceSelection("", null, "cut");
          });
        } else {
          return;
        }
        if (e$jscomp$110_te.clipboardData) {
          e$jscomp$110_te.clipboardData.clearData();
          var content = lastCopied.text.join("\n");
          e$jscomp$110_te.clipboardData.setData("Text", content);
          if (e$jscomp$110_te.clipboardData.getData("Text") == content) {
            e$jscomp$110_te.preventDefault();
            return;
          }
        }
        var kludge = hiddenTextarea();
        e$jscomp$110_te = kludge.firstChild;
        cm.display.lineSpace.insertBefore(kludge, cm.display.lineSpace.firstChild);
        e$jscomp$110_te.value = lastCopied.text.join("\n");
        var hadFocus = activeElt();
        selectInput(e$jscomp$110_te);
        setTimeout(function() {
          cm.display.lineSpace.removeChild(kludge);
          hadFocus.focus();
          hadFocus == div && input.showPrimarySelection();
        }, 50);
      }
    }
    var this$1 = this, input = this, cm = input.cm, div = input.div = display.lineDiv;
    div.contentEditable = !0;
    disableBrowserMagic(div, cm.options.spellcheck, cm.options.autocorrect, cm.options.autocapitalize);
    on(div, "paste", function(e) {
      !belongsToInput(e) || signalDOMEvent(cm, e) || handlePaste(e, cm) || 11 >= ie_version && setTimeout(operation(cm, function() {
        return this$1.updateFromDOM();
      }), 20);
    });
    on(div, "compositionstart", function(e) {
      this$1.composing = {data:e.data, done:!1};
    });
    on(div, "compositionupdate", function(e) {
      this$1.composing || (this$1.composing = {data:e.data, done:!1});
    });
    on(div, "compositionend", function(e) {
      this$1.composing && (e.data != this$1.composing.data && this$1.readFromDOMSoon(), this$1.composing.done = !0);
    });
    on(div, "touchstart", function() {
      return input.forceCompositionEnd();
    });
    on(div, "input", function() {
      this$1.composing || this$1.readFromDOMSoon();
    });
    on(div, "copy", onCopyCut);
    on(div, "cut", onCopyCut);
  };
  ContentEditableInput.prototype.screenReaderLabelChanged = function(label) {
    label ? this.div.setAttribute("aria-label", label) : this.div.removeAttribute("aria-label");
  };
  ContentEditableInput.prototype.prepareSelection = function() {
    var result = prepareSelection(this.cm, !1);
    result.focus = activeElt() == this.div;
    return result;
  };
  ContentEditableInput.prototype.showSelection = function(info, takeFocus) {
    info && this.cm.display.view.length && ((info.focus || takeFocus) && this.showPrimarySelection(), this.showMultipleSelections(info));
  };
  ContentEditableInput.prototype.getSelection = function() {
    return this.cm.display.wrapper.ownerDocument.getSelection();
  };
  ContentEditableInput.prototype.showPrimarySelection = function() {
    var sel = this.getSelection(), cm = this.cm, end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to = cm.doc.sel.primary(), from$jscomp$47_start = end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to.from();
    end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to = end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to.to();
    if (cm.display.viewTo == cm.display.viewFrom || from$jscomp$47_start.line >= cm.display.viewTo || end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to.line < cm.display.viewFrom) {
      sel.removeAllRanges();
    } else {
      var curAnchor_old$jscomp$12_view = domToPos(cm, sel.anchorNode, sel.anchorOffset), curFocus = domToPos(cm, sel.focusNode, sel.focusOffset);
      if (!curAnchor_old$jscomp$12_view || curAnchor_old$jscomp$12_view.bad || !curFocus || curFocus.bad || 0 != cmp(minPos(curAnchor_old$jscomp$12_view, curFocus), from$jscomp$47_start) || 0 != cmp(maxPos(curAnchor_old$jscomp$12_view, curFocus), end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to)) {
        if (curAnchor_old$jscomp$12_view = cm.display.view, from$jscomp$47_start = from$jscomp$47_start.line >= cm.display.viewFrom && posToDOM(cm, from$jscomp$47_start) || {node:curAnchor_old$jscomp$12_view[0].measure.map[2], offset:0}, end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to = end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to.line < cm.display.viewTo && posToDOM(cm, end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to), end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to || 
        (end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to = curAnchor_old$jscomp$12_view[curAnchor_old$jscomp$12_view.length - 1].measure, end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to = end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to.maps ? end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to.maps[end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to.maps.length - 1] : end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to.map, end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to = 
        {node:end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to[end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to.length - 1], offset:end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to[end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to.length - 2] - end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to[end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to.length - 3]}), from$jscomp$47_start && end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to) {
          curAnchor_old$jscomp$12_view = sel.rangeCount && sel.getRangeAt(0);
          try {
            var rng = range$jscomp$0(from$jscomp$47_start.node, from$jscomp$47_start.offset, end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to.offset, end$jscomp$40_map$jscomp$12_measure$jscomp$8_prim$jscomp$1_to.node);
          } catch (e) {
          }
          rng && (!gecko && cm.state.focused ? (sel.collapse(from$jscomp$47_start.node, from$jscomp$47_start.offset), rng.collapsed || (sel.removeAllRanges(), sel.addRange(rng))) : (sel.removeAllRanges(), sel.addRange(rng)), curAnchor_old$jscomp$12_view && null == sel.anchorNode ? sel.addRange(curAnchor_old$jscomp$12_view) : gecko && this.startGracePeriod());
          this.rememberSelection();
        } else {
          sel.removeAllRanges();
        }
      }
    }
  };
  ContentEditableInput.prototype.startGracePeriod = function() {
    var this$1 = this;
    clearTimeout(this.gracePeriod);
    this.gracePeriod = setTimeout(function() {
      this$1.gracePeriod = !1;
      this$1.selectionChanged() && this$1.cm.operation(function() {
        return this$1.cm.curOp.selectionChanged = !0;
      });
    }, 20);
  };
  ContentEditableInput.prototype.showMultipleSelections = function(info) {
    removeChildrenAndAdd(this.cm.display.cursorDiv, info.cursors);
    removeChildrenAndAdd(this.cm.display.selectionDiv, info.selection);
  };
  ContentEditableInput.prototype.rememberSelection = function() {
    var sel = this.getSelection();
    this.lastAnchorNode = sel.anchorNode;
    this.lastAnchorOffset = sel.anchorOffset;
    this.lastFocusNode = sel.focusNode;
    this.lastFocusOffset = sel.focusOffset;
  };
  ContentEditableInput.prototype.selectionInEditor = function() {
    var sel = this.getSelection();
    return sel.rangeCount ? contains(this.div, sel.getRangeAt(0).commonAncestorContainer) : !1;
  };
  ContentEditableInput.prototype.focus = function() {
    "nocursor" != this.cm.options.readOnly && (this.selectionInEditor() && activeElt() == this.div || this.showSelection(this.prepareSelection(), !0), this.div.focus());
  };
  ContentEditableInput.prototype.blur = function() {
    this.div.blur();
  };
  ContentEditableInput.prototype.getField = function() {
    return this.div;
  };
  ContentEditableInput.prototype.supportsTouch = function() {
    return !0;
  };
  ContentEditableInput.prototype.receivedFocus = function() {
    function poll() {
      input.cm.state.focused && (input.pollSelection(), input.polling.set(input.cm.options.pollInterval, poll));
    }
    var this$1 = this, input = this;
    this.selectionInEditor() ? setTimeout(function() {
      return this$1.pollSelection();
    }, 20) : runInOp(this.cm, function() {
      return input.cm.curOp.selectionChanged = !0;
    });
    this.polling.set(this.cm.options.pollInterval, poll);
  };
  ContentEditableInput.prototype.selectionChanged = function() {
    var sel = this.getSelection();
    return sel.anchorNode != this.lastAnchorNode || sel.anchorOffset != this.lastAnchorOffset || sel.focusNode != this.lastFocusNode || sel.focusOffset != this.lastFocusOffset;
  };
  ContentEditableInput.prototype.pollSelection = function() {
    if (null == this.readDOMTimeout && !this.gracePeriod && this.selectionChanged()) {
      var sel = this.getSelection(), cm = this.cm;
      if (android && chrome && this.cm.display.gutterSpecs.length && isInGutter(sel.anchorNode)) {
        this.cm.triggerOnKeyDown({type:"keydown", keyCode:8, preventDefault:Math.abs}), this.blur(), this.focus();
      } else {
        if (!this.composing) {
          this.rememberSelection();
          var anchor = domToPos(cm, sel.anchorNode, sel.anchorOffset), head = domToPos(cm, sel.focusNode, sel.focusOffset);
          anchor && head && runInOp(cm, function() {
            setSelection(cm.doc, simpleSelection(anchor, head), sel_dontScroll);
            if (anchor.bad || head.bad) {
              cm.curOp.selectionChanged = !0;
            }
          });
        }
      }
    }
  };
  ContentEditableInput.prototype.pollContent = function() {
    null != this.readDOMTimeout && (clearTimeout(this.readDOMTimeout), this.readDOMTimeout = null);
    var cm = this.cm, display = cm.display, chTo_fromLine$jscomp$1_sel = cm.doc.sel.primary(), chFrom_from = chTo_fromLine$jscomp$1_sel.from(), to$jscomp$44_toLine = chTo_fromLine$jscomp$1_sel.to();
    0 == chFrom_from.ch && chFrom_from.line > cm.firstLine() && (chFrom_from = Pos(chFrom_from.line - 1, getLine(cm.doc, chFrom_from.line - 1).length));
    to$jscomp$44_toLine.ch == getLine(cm.doc, to$jscomp$44_toLine.line).text.length && to$jscomp$44_toLine.line < cm.lastLine() && (to$jscomp$44_toLine = Pos(to$jscomp$44_toLine.line + 1, 0));
    if (chFrom_from.line < display.viewFrom || to$jscomp$44_toLine.line > display.viewTo - 1) {
      return !1;
    }
    var fromIndex;
    chFrom_from.line == display.viewFrom || 0 == (fromIndex = findViewIndex(cm, chFrom_from.line)) ? (chTo_fromLine$jscomp$1_sel = lineNo(display.view[0].line), fromIndex = display.view[0].node) : (chTo_fromLine$jscomp$1_sel = lineNo(display.view[fromIndex].line), fromIndex = display.view[fromIndex - 1].node.nextSibling);
    var cutEnd_toIndex = findViewIndex(cm, to$jscomp$44_toLine.line);
    cutEnd_toIndex == display.view.length - 1 ? (to$jscomp$44_toLine = display.viewTo - 1, display = display.lineDiv.lastChild) : (to$jscomp$44_toLine = lineNo(display.view[cutEnd_toIndex + 1].line) - 1, display = display.view[cutEnd_toIndex + 1].node.previousSibling);
    if (!fromIndex) {
      return !1;
    }
    display = cm.doc.splitLines(domTextBetween(cm, fromIndex, display, chTo_fromLine$jscomp$1_sel, to$jscomp$44_toLine));
    for (fromIndex = getBetween(cm.doc, Pos(chTo_fromLine$jscomp$1_sel, 0), Pos(to$jscomp$44_toLine, getLine(cm.doc, to$jscomp$44_toLine).text.length)); 1 < display.length && 1 < fromIndex.length;) {
      if (lst(display) == lst(fromIndex)) {
        display.pop(), fromIndex.pop(), to$jscomp$44_toLine--;
      } else if (display[0] == fromIndex[0]) {
        display.shift(), fromIndex.shift(), chTo_fromLine$jscomp$1_sel++;
      } else {
        break;
      }
    }
    var cutFront = 0;
    cutEnd_toIndex = 0;
    for (var newBot_newTop = display[0], oldBot_oldTop = fromIndex[0], maxCutEnd_maxCutFront = Math.min(newBot_newTop.length, oldBot_oldTop.length); cutFront < maxCutEnd_maxCutFront && newBot_newTop.charCodeAt(cutFront) == oldBot_oldTop.charCodeAt(cutFront);) {
      ++cutFront;
    }
    newBot_newTop = lst(display);
    oldBot_oldTop = lst(fromIndex);
    for (maxCutEnd_maxCutFront = Math.min(newBot_newTop.length - (1 == display.length ? cutFront : 0), oldBot_oldTop.length - (1 == fromIndex.length ? cutFront : 0)); cutEnd_toIndex < maxCutEnd_maxCutFront && newBot_newTop.charCodeAt(newBot_newTop.length - cutEnd_toIndex - 1) == oldBot_oldTop.charCodeAt(oldBot_oldTop.length - cutEnd_toIndex - 1);) {
      ++cutEnd_toIndex;
    }
    if (1 == display.length && 1 == fromIndex.length && chTo_fromLine$jscomp$1_sel == chFrom_from.line) {
      for (; cutFront && cutFront > chFrom_from.ch && newBot_newTop.charCodeAt(newBot_newTop.length - cutEnd_toIndex - 1) == oldBot_oldTop.charCodeAt(oldBot_oldTop.length - cutEnd_toIndex - 1);) {
        cutFront--, cutEnd_toIndex++;
      }
    }
    display[display.length - 1] = newBot_newTop.slice(0, newBot_newTop.length - cutEnd_toIndex).replace(/^\u200b+/, "");
    display[0] = display[0].slice(cutFront).replace(/\u200b+$/, "");
    chFrom_from = Pos(chTo_fromLine$jscomp$1_sel, cutFront);
    chTo_fromLine$jscomp$1_sel = Pos(to$jscomp$44_toLine, fromIndex.length ? lst(fromIndex).length - cutEnd_toIndex : 0);
    if (1 < display.length || display[0] || cmp(chFrom_from, chTo_fromLine$jscomp$1_sel)) {
      return replaceRange(cm.doc, display, chFrom_from, chTo_fromLine$jscomp$1_sel, "+input"), !0;
    }
  };
  ContentEditableInput.prototype.ensurePolled = function() {
    this.forceCompositionEnd();
  };
  ContentEditableInput.prototype.reset = function() {
    this.forceCompositionEnd();
  };
  ContentEditableInput.prototype.forceCompositionEnd = function() {
    this.composing && (clearTimeout(this.readDOMTimeout), this.composing = null, this.updateFromDOM(), this.div.blur(), this.div.focus());
  };
  ContentEditableInput.prototype.readFromDOMSoon = function() {
    var this$1 = this;
    null == this.readDOMTimeout && (this.readDOMTimeout = setTimeout(function() {
      this$1.readDOMTimeout = null;
      if (this$1.composing) {
        if (this$1.composing.done) {
          this$1.composing = null;
        } else {
          return;
        }
      }
      this$1.updateFromDOM();
    }, 80));
  };
  ContentEditableInput.prototype.updateFromDOM = function() {
    var this$1 = this;
    !this.cm.isReadOnly() && this.pollContent() || runInOp(this.cm, function() {
      return regChange(this$1.cm);
    });
  };
  ContentEditableInput.prototype.setUneditable = function(node) {
    node.contentEditable = "false";
  };
  ContentEditableInput.prototype.onKeyPress = function(e) {
    0 == e.charCode || this.composing || (e.preventDefault(), this.cm.isReadOnly() || operation(this.cm, applyTextInput)(this.cm, String.fromCharCode(null == e.charCode ? e.keyCode : e.charCode), 0));
  };
  ContentEditableInput.prototype.readOnlyChanged = function(val) {
    this.div.contentEditable = String("nocursor" != val);
  };
  ContentEditableInput.prototype.onContextMenu = function() {
  };
  ContentEditableInput.prototype.resetPosition = function() {
  };
  ContentEditableInput.prototype.needsContentAttribute = !0;
  TextareaInput.prototype.init = function(display) {
    function prepareCopyCut(e) {
      if (!signalDOMEvent(cm, e)) {
        if (cm.somethingSelected()) {
          lastCopied = {lineWise:!1, text:cm.getSelections()};
        } else if (cm.options.lineWiseCopyCut) {
          var ranges = copyableRanges(cm);
          lastCopied = {lineWise:!0, text:ranges.text};
          "cut" == e.type ? cm.setSelections(ranges.ranges, null, sel_dontScroll) : (input.prevInput = "", te.value = ranges.text.join("\n"), selectInput(te));
        } else {
          return;
        }
        "cut" == e.type && (cm.state.cutIncoming = +new Date());
      }
    }
    var this$1 = this, input = this, cm = this.cm;
    this.createField();
    var te = this.textarea;
    display.wrapper.insertBefore(this.wrapper, display.wrapper.firstChild);
    ios && (te.style.width = "0px");
    on(te, "input", function() {
      ie && 9 <= ie_version && this$1.hasSelection && (this$1.hasSelection = null);
      input.poll();
    });
    on(te, "paste", function(e) {
      signalDOMEvent(cm, e) || handlePaste(e, cm) || (cm.state.pasteIncoming = +new Date(), input.fastPoll());
    });
    on(te, "cut", prepareCopyCut);
    on(te, "copy", prepareCopyCut);
    on(display.scroller, "paste", function(e) {
      if (!eventInWidget(display, e) && !signalDOMEvent(cm, e)) {
        if (te.dispatchEvent) {
          var event = new Event("paste");
          event.clipboardData = e.clipboardData;
          te.dispatchEvent(event);
        } else {
          cm.state.pasteIncoming = +new Date(), input.focus();
        }
      }
    });
    on(display.lineSpace, "selectstart", function(e) {
      eventInWidget(display, e) || e_preventDefault(e);
    });
    on(te, "compositionstart", function() {
      var start = cm.getCursor("from");
      input.composing && input.composing.range.clear();
      input.composing = {start, range:cm.markText(start, cm.getCursor("to"), {className:"CodeMirror-composing"})};
    });
    on(te, "compositionend", function() {
      input.composing && (input.poll(), input.composing.range.clear(), input.composing = null);
    });
  };
  TextareaInput.prototype.createField = function() {
    this.wrapper = hiddenTextarea();
    this.textarea = this.wrapper.firstChild;
  };
  TextareaInput.prototype.screenReaderLabelChanged = function(label) {
    label ? this.textarea.setAttribute("aria-label", label) : this.textarea.removeAttribute("aria-label");
  };
  TextareaInput.prototype.prepareSelection = function() {
    var cm$jscomp$249_headPos = this.cm, display = cm$jscomp$249_headPos.display, doc = cm$jscomp$249_headPos.doc, result = prepareSelection(cm$jscomp$249_headPos);
    if (cm$jscomp$249_headPos.options.moveInputWithCursor) {
      cm$jscomp$249_headPos = cursorCoords(cm$jscomp$249_headPos, doc.sel.primary().head, "div");
      doc = display.wrapper.getBoundingClientRect();
      var lineOff = display.lineDiv.getBoundingClientRect();
      result.teTop = Math.max(0, Math.min(display.wrapper.clientHeight - 10, cm$jscomp$249_headPos.top + lineOff.top - doc.top));
      result.teLeft = Math.max(0, Math.min(display.wrapper.clientWidth - 10, cm$jscomp$249_headPos.left + lineOff.left - doc.left));
    }
    return result;
  };
  TextareaInput.prototype.showSelection = function(drawn) {
    var display = this.cm.display;
    removeChildrenAndAdd(display.cursorDiv, drawn.cursors);
    removeChildrenAndAdd(display.selectionDiv, drawn.selection);
    null != drawn.teTop && (this.wrapper.style.top = drawn.teTop + "px", this.wrapper.style.left = drawn.teLeft + "px");
  };
  TextareaInput.prototype.reset = function(content) {
    if (!this.contextMenuPending && !this.composing) {
      var cm = this.cm;
      cm.somethingSelected() ? (this.prevInput = "", content = cm.getSelection(), this.textarea.value = content, cm.state.focused && selectInput(this.textarea), ie && 9 <= ie_version && (this.hasSelection = content)) : content || (this.prevInput = this.textarea.value = "", ie && 9 <= ie_version && (this.hasSelection = null));
    }
  };
  TextareaInput.prototype.getField = function() {
    return this.textarea;
  };
  TextareaInput.prototype.supportsTouch = function() {
    return !1;
  };
  TextareaInput.prototype.focus = function() {
    if ("nocursor" != this.cm.options.readOnly && (!mobile || activeElt() != this.textarea)) {
      try {
        this.textarea.focus();
      } catch (e) {
      }
    }
  };
  TextareaInput.prototype.blur = function() {
    this.textarea.blur();
  };
  TextareaInput.prototype.resetPosition = function() {
    this.wrapper.style.top = this.wrapper.style.left = 0;
  };
  TextareaInput.prototype.receivedFocus = function() {
    this.slowPoll();
  };
  TextareaInput.prototype.slowPoll = function() {
    var this$1 = this;
    this.pollingFast || this.polling.set(this.cm.options.pollInterval, function() {
      this$1.poll();
      this$1.cm.state.focused && this$1.slowPoll();
    });
  };
  TextareaInput.prototype.fastPoll = function() {
    function p() {
      input.poll() || missed ? (input.pollingFast = !1, input.slowPoll()) : (missed = !0, input.polling.set(60, p));
    }
    var missed = !1, input = this;
    input.pollingFast = !0;
    input.polling.set(20, p);
  };
  TextareaInput.prototype.poll = function() {
    var this$1 = this, cm = this.cm, input = this.textarea, prevInput = this.prevInput;
    if (this.contextMenuPending || !cm.state.focused || hasSelection(input) && !prevInput && !this.composing || cm.isReadOnly() || cm.options.disableInput || cm.state.keySeq) {
      return !1;
    }
    var text = input.value;
    if (text == prevInput && !cm.somethingSelected()) {
      return !1;
    }
    if (ie && 9 <= ie_version && this.hasSelection === text || mac && /[\uf700-\uf7ff]/.test(text)) {
      return cm.display.input.reset(), !1;
    }
    if (cm.doc.sel == cm.display.selForContextMenu) {
      var first$jscomp$8_l = text.charCodeAt(0);
      8203 != first$jscomp$8_l || prevInput || (prevInput = "\u200b");
      if (8666 == first$jscomp$8_l) {
        return this.reset(), this.cm.execCommand("undo");
      }
    }
    var same = 0;
    for (first$jscomp$8_l = Math.min(prevInput.length, text.length); same < first$jscomp$8_l && prevInput.charCodeAt(same) == text.charCodeAt(same);) {
      ++same;
    }
    runInOp(cm, function() {
      applyTextInput(cm, text.slice(same), prevInput.length - same, null, this$1.composing ? "*compose" : null);
      1E3 < text.length || -1 < text.indexOf("\n") ? input.value = this$1.prevInput = "" : this$1.prevInput = text;
      this$1.composing && (this$1.composing.range.clear(), this$1.composing.range = cm.markText(this$1.composing.start, cm.getCursor("to"), {className:"CodeMirror-composing"}));
    });
    return !0;
  };
  TextareaInput.prototype.ensurePolled = function() {
    this.pollingFast && this.poll() && (this.pollingFast = !1);
  };
  TextareaInput.prototype.onKeyPress = function() {
    ie && 9 <= ie_version && (this.hasSelection = null);
    this.fastPoll();
  };
  TextareaInput.prototype.onContextMenu = function(e) {
    function prepareSelectAllHack() {
      if (null != te.selectionStart) {
        var selected = cm.somethingSelected(), extval = "\u200b" + (selected ? te.value : "");
        te.value = "\u21da";
        te.value = extval;
        input.prevInput = selected ? "" : "\u200b";
        te.selectionStart = 1;
        te.selectionEnd = extval.length;
        display.selForContextMenu = cm.doc.sel;
      }
    }
    function rehide() {
      if (input.contextMenuPending == rehide && (input.contextMenuPending = !1, input.wrapper.style.cssText = oldWrapperCSS, te.style.cssText = oldCSS, ie && 9 > ie_version && display.scrollbars.setScrollTop(display.scroller.scrollTop = scrollPos), null != te.selectionStart)) {
        (!ie || ie && 9 > ie_version) && prepareSelectAllHack();
        var i = 0, poll = function() {
          display.selForContextMenu == cm.doc.sel && 0 == te.selectionStart && 0 < te.selectionEnd && "\u200b" == input.prevInput ? operation(cm, selectAll)(cm) : 10 > i++ ? display.detectingSelectAll = setTimeout(poll, 500) : (display.selForContextMenu = null, display.input.reset());
        };
        display.detectingSelectAll = setTimeout(poll, 200);
      }
    }
    var input = this, cm = input.cm, display = cm.display, te = input.textarea;
    input.contextMenuPending && input.contextMenuPending();
    var pos = posFromMouse(cm, e), scrollPos = display.scroller.scrollTop;
    if (pos && !presto) {
      cm.options.resetSelectionOnContextMenu && -1 == cm.doc.sel.contains(pos) && operation(cm, setSelection)(cm.doc, simpleSelection(pos), sel_dontScroll);
      var oldCSS = te.style.cssText, oldWrapperCSS = input.wrapper.style.cssText;
      pos = input.wrapper.offsetParent.getBoundingClientRect();
      input.wrapper.style.cssText = "position: static";
      te.style.cssText = "position: absolute; width: 30px; height: 30px;\n      top: " + (e.clientY - pos.top - 5) + "px; left: " + (e.clientX - pos.left - 5) + "px;\n      z-index: 1000; background: " + (ie ? "rgba(255, 255, 255, .05)" : "transparent") + ";\n      outline: none; border-width: 0; outline: none; overflow: hidden; opacity: .05; filter: alpha(opacity=5);";
      if (webkit) {
        var oldScrollY = window.scrollY;
      }
      display.input.focus();
      webkit && window.scrollTo(null, oldScrollY);
      display.input.reset();
      cm.somethingSelected() || (te.value = input.prevInput = " ");
      input.contextMenuPending = rehide;
      display.selForContextMenu = cm.doc.sel;
      clearTimeout(display.detectingSelectAll);
      ie && 9 <= ie_version && prepareSelectAllHack();
      if (captureRightClick) {
        e_stop(e);
        var mouseup = function() {
          off(window, "mouseup", mouseup);
          setTimeout(rehide, 20);
        };
        on(window, "mouseup", mouseup);
      } else {
        setTimeout(rehide, 50);
      }
    }
  };
  TextareaInput.prototype.readOnlyChanged = function(val) {
    val || this.reset();
    this.textarea.disabled = "nocursor" == val;
    this.textarea.readOnly = !!val;
  };
  TextareaInput.prototype.setUneditable = function() {
  };
  TextareaInput.prototype.needsContentAttribute = !1;
  (function(CodeMirror) {
    function option(name, deflt, handle, notOnInit) {
      CodeMirror.defaults[name] = deflt;
      handle && (optionHandlers[name] = notOnInit ? function(cm, val, old) {
        old != Init && handle(cm, val, old);
      } : handle);
    }
    var optionHandlers = CodeMirror.optionHandlers;
    CodeMirror.defineOption = option;
    CodeMirror.Init = Init;
    option("value", "", function(cm, val) {
      return cm.setValue(val);
    }, !0);
    option("mode", null, function(cm, val) {
      cm.doc.modeOption = val;
      loadMode(cm);
    }, !0);
    option("indentUnit", 2, loadMode, !0);
    option("indentWithTabs", !1);
    option("smartIndent", !0);
    option("tabSize", 4, function(cm) {
      resetModeState(cm);
      clearCaches(cm);
      regChange(cm);
    }, !0);
    option("lineSeparator", null, function(cm, val) {
      if (cm.doc.lineSep = val) {
        var newBreaks = [], lineNo = cm.doc.first;
        cm.doc.iter(function(line) {
          for (var pos = 0;;) {
            var found = line.text.indexOf(val, pos);
            if (-1 == found) {
              break;
            }
            pos = found + val.length;
            newBreaks.push(Pos(lineNo, found));
          }
          lineNo++;
        });
        for (var i = newBreaks.length - 1; 0 <= i; i--) {
          replaceRange(cm.doc, val, newBreaks[i], Pos(newBreaks[i].line, newBreaks[i].ch + val.length));
        }
      }
    });
    option("specialChars", /[\u0000-\u001f\u007f-\u009f\u00ad\u061c\u200b\u200e\u200f\u2028\u2029\ufeff\ufff9-\ufffc]/g, function(cm, val, old) {
      cm.state.specialChars = new RegExp(val.source + (val.test("\t") ? "" : "|\t"), "g");
      old != Init && cm.refresh();
    });
    option("specialCharPlaceholder", defaultSpecialCharPlaceholder, function(cm) {
      return cm.refresh();
    }, !0);
    option("electricChars", !0);
    option("inputStyle", mobile ? "contenteditable" : "textarea", function() {
      throw Error("inputStyle can not (yet) be changed in a running editor");
    }, !0);
    option("spellcheck", !1, function(cm, val) {
      return cm.getInputField().spellcheck = val;
    }, !0);
    option("autocorrect", !1, function(cm, val) {
      return cm.getInputField().autocorrect = val;
    }, !0);
    option("autocapitalize", !1, function(cm, val) {
      return cm.getInputField().autocapitalize = val;
    }, !0);
    option("rtlMoveVisually", !windows);
    option("wholeLineUpdateBefore", !0);
    option("theme", "default", function(cm) {
      themeChanged(cm);
      updateGutters(cm);
    }, !0);
    option("keyMap", "default", function(cm, next$jscomp$4_val, old$jscomp$8_prev) {
      next$jscomp$4_val = getKeyMap(next$jscomp$4_val);
      (old$jscomp$8_prev = old$jscomp$8_prev != Init && getKeyMap(old$jscomp$8_prev)) && old$jscomp$8_prev.detach && old$jscomp$8_prev.detach(cm, next$jscomp$4_val);
      next$jscomp$4_val.attach && next$jscomp$4_val.attach(cm, old$jscomp$8_prev || null);
    });
    option("extraKeys", null);
    option("configureMouse", null);
    option("lineWrapping", !1, wrappingChanged, !0);
    option("gutters", [], function(cm, val) {
      cm.display.gutterSpecs = getGutters(val, cm.options.lineNumbers);
      updateGutters(cm);
    }, !0);
    option("fixedGutter", !0, function(cm, val) {
      cm.display.gutters.style.left = val ? compensateForHScroll(cm.display) + "px" : "0";
      cm.refresh();
    }, !0);
    option("coverGutterNextToScrollbar", !1, function(cm) {
      return updateScrollbars(cm);
    }, !0);
    option("scrollbarStyle", "native", function(cm) {
      initScrollbars(cm);
      updateScrollbars(cm);
      cm.display.scrollbars.setScrollTop(cm.doc.scrollTop);
      cm.display.scrollbars.setScrollLeft(cm.doc.scrollLeft);
    }, !0);
    option("lineNumbers", !1, function(cm, val) {
      cm.display.gutterSpecs = getGutters(cm.options.gutters, val);
      updateGutters(cm);
    }, !0);
    option("firstLineNumber", 1, updateGutters, !0);
    option("lineNumberFormatter", function(integer) {
      return integer;
    }, updateGutters, !0);
    option("showCursorWhenSelecting", !1, updateSelection, !0);
    option("resetSelectionOnContextMenu", !0);
    option("lineWiseCopyCut", !0);
    option("pasteLinesPerSelection", !0);
    option("selectionsMayTouch", !1);
    option("readOnly", !1, function(cm, val) {
      "nocursor" == val && (onBlur(cm), cm.display.input.blur());
      cm.display.input.readOnlyChanged(val);
    });
    option("screenReaderLabel", null, function(cm, val) {
      cm.display.input.screenReaderLabelChanged("" === val ? null : val);
    });
    option("disableInput", !1, function(cm, val) {
      val || cm.display.input.reset();
    }, !0);
    option("dragDrop", !0, dragDropChanged);
    option("allowDropFileTypes", null);
    option("cursorBlinkRate", 530);
    option("cursorScrollMargin", 0);
    option("cursorHeight", 1, updateSelection, !0);
    option("singleCursorHeightPerLine", !0, updateSelection, !0);
    option("workTime", 100);
    option("workDelay", 100);
    option("flattenSpans", !0, resetModeState, !0);
    option("addModeClass", !1, resetModeState, !0);
    option("pollInterval", 100);
    option("undoDepth", 200, function(cm, val) {
      return cm.doc.history.undoDepth = val;
    });
    option("historyEventDelay", 1250);
    option("viewportMargin", 10, function(cm) {
      return cm.refresh();
    }, !0);
    option("maxHighlightLength", 1E4, resetModeState, !0);
    option("moveInputWithCursor", !0, function(cm, val) {
      val || cm.display.input.resetPosition();
    });
    option("tabindex", null, function(cm, val) {
      return cm.display.input.getField().tabIndex = val || "";
    });
    option("autofocus", null);
    option("direction", "ltr", function(cm, val) {
      return cm.doc.setDirection(val);
    }, !0);
    option("phrases", null);
  })(CodeMirror$jscomp$0);
  (function(CodeMirror) {
    var optionHandlers = CodeMirror.optionHandlers, helpers = CodeMirror.helpers = {};
    CodeMirror.prototype = {constructor:CodeMirror, focus:function() {
      window.focus();
      this.display.input.focus();
    }, setOption:function(option, value) {
      var options = this.options, old = options[option];
      if (options[option] != value || "mode" == option) {
        options[option] = value, optionHandlers.hasOwnProperty(option) && operation(this, optionHandlers[option])(this, value, old), signal(this, "optionChange", this, option);
      }
    }, getOption:function(option) {
      return this.options[option];
    }, getDoc:function() {
      return this.doc;
    }, addKeyMap:function(map, bottom) {
      this.state.keyMaps[bottom ? "push" : "unshift"](getKeyMap(map));
    }, removeKeyMap:function(map) {
      for (var maps = this.state.keyMaps, i = 0; i < maps.length; ++i) {
        if (maps[i] == map || maps[i].name == map) {
          return maps.splice(i, 1), !0;
        }
      }
    }, addOverlay:methodOp(function(spec, options) {
      var mode = spec.token ? spec : CodeMirror.getMode(this.options, spec);
      if (mode.startState) {
        throw Error("Overlays may not be stateful.");
      }
      insertSorted(this.state.overlays, {mode, modeSpec:spec, opaque:options && options.opaque, priority:options && options.priority || 0}, function(overlay) {
        return overlay.priority;
      });
      this.state.modeGen++;
      regChange(this);
    }), removeOverlay:methodOp(function(spec) {
      for (var overlays = this.state.overlays, i = 0; i < overlays.length; ++i) {
        var cur = overlays[i].modeSpec;
        if (cur == spec || "string" == typeof spec && cur.name == spec) {
          overlays.splice(i, 1);
          this.state.modeGen++;
          regChange(this);
          break;
        }
      }
    }), indentLine:methodOp(function(n, dir, aggressive) {
      "string" != typeof dir && "number" != typeof dir && (dir = null == dir ? this.options.smartIndent ? "smart" : "prev" : dir ? "add" : "subtract");
      isLine(this.doc, n) && indentLine(this, n, dir, aggressive);
    }), indentSelection:methodOp(function(how) {
      for (var ranges = this.doc.sel.ranges, end = -1, i = 0; i < ranges.length; i++) {
        var j$jscomp$20_newRanges_range$jscomp$40_to = ranges[i];
        if (j$jscomp$20_newRanges_range$jscomp$40_to.empty()) {
          j$jscomp$20_newRanges_range$jscomp$40_to.head.line > end && (indentLine(this, j$jscomp$20_newRanges_range$jscomp$40_to.head.line, how, !0), end = j$jscomp$20_newRanges_range$jscomp$40_to.head.line, i == this.doc.sel.primIndex && ensureCursorVisible(this));
        } else {
          var from = j$jscomp$20_newRanges_range$jscomp$40_to.from();
          j$jscomp$20_newRanges_range$jscomp$40_to = j$jscomp$20_newRanges_range$jscomp$40_to.to();
          var start = Math.max(end, from.line);
          end = Math.min(this.lastLine(), j$jscomp$20_newRanges_range$jscomp$40_to.line - (j$jscomp$20_newRanges_range$jscomp$40_to.ch ? 0 : 1)) + 1;
          for (j$jscomp$20_newRanges_range$jscomp$40_to = start; j$jscomp$20_newRanges_range$jscomp$40_to < end; ++j$jscomp$20_newRanges_range$jscomp$40_to) {
            indentLine(this, j$jscomp$20_newRanges_range$jscomp$40_to, how);
          }
          j$jscomp$20_newRanges_range$jscomp$40_to = this.doc.sel.ranges;
          0 == from.ch && ranges.length == j$jscomp$20_newRanges_range$jscomp$40_to.length && 0 < j$jscomp$20_newRanges_range$jscomp$40_to[i].from().ch && replaceOneSelection(this.doc, i, new Range(from, j$jscomp$20_newRanges_range$jscomp$40_to[i].to()), sel_dontScroll);
        }
      }
    }), getTokenAt:function(pos, precise) {
      return takeToken(this, pos, precise);
    }, getLineTokens:function(line, precise) {
      return takeToken(this, Pos(line), precise, !0);
    }, getTokenTypeAt:function(ch$jscomp$48_pos) {
      ch$jscomp$48_pos = clipPos(this.doc, ch$jscomp$48_pos);
      var styles$jscomp$6_type = getLineStyles(this, getLine(this.doc, ch$jscomp$48_pos.line)), before$jscomp$4_cut = 0, after = (styles$jscomp$6_type.length - 1) / 2;
      ch$jscomp$48_pos = ch$jscomp$48_pos.ch;
      if (0 == ch$jscomp$48_pos) {
        styles$jscomp$6_type = styles$jscomp$6_type[2];
      } else {
        for (;;) {
          var mid = before$jscomp$4_cut + after >> 1;
          if ((mid ? styles$jscomp$6_type[2 * mid - 1] : 0) >= ch$jscomp$48_pos) {
            after = mid;
          } else if (styles$jscomp$6_type[2 * mid + 1] < ch$jscomp$48_pos) {
            before$jscomp$4_cut = mid + 1;
          } else {
            styles$jscomp$6_type = styles$jscomp$6_type[2 * mid + 2];
            break;
          }
        }
      }
      before$jscomp$4_cut = styles$jscomp$6_type ? styles$jscomp$6_type.indexOf("overlay ") : -1;
      return 0 > before$jscomp$4_cut ? styles$jscomp$6_type : 0 == before$jscomp$4_cut ? null : styles$jscomp$6_type.slice(0, before$jscomp$4_cut - 1);
    }, getModeAt:function(pos) {
      var mode = this.doc.mode;
      return mode.innerMode ? CodeMirror.innerMode(mode, this.getTokenAt(pos).state).mode : mode;
    }, getHelper:function(pos, type) {
      return this.getHelpers(pos, type)[0];
    }, getHelpers:function(mode$jscomp$38_pos, i$1$jscomp$19_type) {
      var found = [];
      if (!helpers.hasOwnProperty(i$1$jscomp$19_type)) {
        return found;
      }
      var help = helpers[i$1$jscomp$19_type];
      mode$jscomp$38_pos = this.getModeAt(mode$jscomp$38_pos);
      if ("string" == typeof mode$jscomp$38_pos[i$1$jscomp$19_type]) {
        help[mode$jscomp$38_pos[i$1$jscomp$19_type]] && found.push(help[mode$jscomp$38_pos[i$1$jscomp$19_type]]);
      } else if (mode$jscomp$38_pos[i$1$jscomp$19_type]) {
        for (var cur$jscomp$20_i = 0; cur$jscomp$20_i < mode$jscomp$38_pos[i$1$jscomp$19_type].length; cur$jscomp$20_i++) {
          var val = help[mode$jscomp$38_pos[i$1$jscomp$19_type][cur$jscomp$20_i]];
          val && found.push(val);
        }
      } else {
        mode$jscomp$38_pos.helperType && help[mode$jscomp$38_pos.helperType] ? found.push(help[mode$jscomp$38_pos.helperType]) : help[mode$jscomp$38_pos.name] && found.push(help[mode$jscomp$38_pos.name]);
      }
      for (i$1$jscomp$19_type = 0; i$1$jscomp$19_type < help._global.length; i$1$jscomp$19_type++) {
        cur$jscomp$20_i = help._global[i$1$jscomp$19_type], cur$jscomp$20_i.pred(mode$jscomp$38_pos, this) && -1 == indexOf(found, cur$jscomp$20_i.val) && found.push(cur$jscomp$20_i.val);
      }
      return found;
    }, getStateAfter:function(line, precise) {
      var doc = this.doc;
      line = Math.max(doc.first, Math.min(null == line ? doc.first + doc.size - 1 : line, doc.first + doc.size - 1));
      return getContextBefore(this, line + 1, precise).state;
    }, cursorCoords:function(pos$jscomp$71_start, mode) {
      var range = this.doc.sel.primary();
      pos$jscomp$71_start = null == pos$jscomp$71_start ? range.head : "object" == typeof pos$jscomp$71_start ? clipPos(this.doc, pos$jscomp$71_start) : pos$jscomp$71_start ? range.from() : range.to();
      return cursorCoords(this, pos$jscomp$71_start, mode || "page");
    }, charCoords:function(pos, mode) {
      return charCoords(this, clipPos(this.doc, pos), mode || "page");
    }, coordsChar:function(coords, mode) {
      coords = fromCoordSystem(this, coords, mode || "page");
      return coordsChar(this, coords.left, coords.top);
    }, lineAtHeight:function(height, mode) {
      height = fromCoordSystem(this, {top:height, left:0}, mode || "page").top;
      return lineAtHeight(this.doc, height + this.display.viewOffset);
    }, heightAtLine:function(line$jscomp$124_lineObj, mode, includeWidgets) {
      var end = !1;
      if ("number" == typeof line$jscomp$124_lineObj) {
        var last = this.doc.first + this.doc.size - 1;
        line$jscomp$124_lineObj < this.doc.first ? line$jscomp$124_lineObj = this.doc.first : line$jscomp$124_lineObj > last && (line$jscomp$124_lineObj = last, end = !0);
        line$jscomp$124_lineObj = getLine(this.doc, line$jscomp$124_lineObj);
      }
      return intoCoordSystem(this, line$jscomp$124_lineObj, {top:0, left:0}, mode || "page", includeWidgets || end).top + (end ? this.doc.height - heightAtLine(line$jscomp$124_lineObj) : 0);
    }, defaultTextHeight:function() {
      return textHeight(this.display);
    }, defaultCharWidth:function() {
      return charWidth(this.display);
    }, getViewport:function() {
      return {from:this.display.viewFrom, to:this.display.viewTo};
    }, addWidget:function(pos$jscomp$73_scrollPos, node, scroll, vert, horiz) {
      var display = this.display;
      pos$jscomp$73_scrollPos = cursorCoords(this, clipPos(this.doc, pos$jscomp$73_scrollPos));
      var top = pos$jscomp$73_scrollPos.bottom, left = pos$jscomp$73_scrollPos.left;
      node.style.position = "absolute";
      node.setAttribute("cm-ignore-events", "true");
      this.display.input.setUneditable(node);
      display.sizer.appendChild(node);
      if ("over" == vert) {
        top = pos$jscomp$73_scrollPos.top;
      } else if ("above" == vert || "near" == vert) {
        var vspace = Math.max(display.wrapper.clientHeight, this.doc.height), hspace = Math.max(display.sizer.clientWidth, display.lineSpace.clientWidth);
        ("above" == vert || pos$jscomp$73_scrollPos.bottom + node.offsetHeight > vspace) && pos$jscomp$73_scrollPos.top > node.offsetHeight ? top = pos$jscomp$73_scrollPos.top - node.offsetHeight : pos$jscomp$73_scrollPos.bottom + node.offsetHeight <= vspace && (top = pos$jscomp$73_scrollPos.bottom);
        left + node.offsetWidth > hspace && (left = hspace - node.offsetWidth);
      }
      node.style.top = top + "px";
      node.style.left = node.style.right = "";
      "right" == horiz ? (left = display.sizer.clientWidth - node.offsetWidth, node.style.right = "0px") : ("left" == horiz ? left = 0 : "middle" == horiz && (left = (display.sizer.clientWidth - node.offsetWidth) / 2), node.style.left = left + "px");
      scroll && (pos$jscomp$73_scrollPos = calculateScrollPos(this, {left, top, right:left + node.offsetWidth, bottom:top + node.offsetHeight}), null != pos$jscomp$73_scrollPos.scrollTop && updateScrollTop(this, pos$jscomp$73_scrollPos.scrollTop), null != pos$jscomp$73_scrollPos.scrollLeft && setScrollLeft(this, pos$jscomp$73_scrollPos.scrollLeft));
    }, triggerOnKeyDown:methodOp(onKeyDown), triggerOnKeyPress:methodOp(onKeyPress), triggerOnMouseDown:methodOp(onMouseDown), execCommand:function(cmd) {
      if (commands.hasOwnProperty(cmd)) {
        return commands[cmd].call(null, this);
      }
    }, triggerElectric:methodOp(function(text) {
      triggerElectric(this, text);
    }), findPosH:function(cur$jscomp$21_from, amount, unit, visually) {
      var dir = 1;
      0 > amount && (dir = -1, amount = -amount);
      cur$jscomp$21_from = clipPos(this.doc, cur$jscomp$21_from);
      for (var i = 0; i < amount && (cur$jscomp$21_from = findPosH(this.doc, cur$jscomp$21_from, dir, unit, visually), !cur$jscomp$21_from.hitSide); ++i) {
      }
      return cur$jscomp$21_from;
    }, moveH:methodOp(function(dir, unit) {
      var this$1 = this;
      this.extendSelectionsBy(function(range) {
        return this$1.display.shift || this$1.doc.extend || range.empty() ? findPosH(this$1.doc, range.head, dir, unit, this$1.options.rtlMoveVisually) : 0 > dir ? range.from() : range.to();
      }, sel_move);
    }), deleteH:methodOp(function(dir, unit) {
      var doc = this.doc;
      this.doc.sel.somethingSelected() ? doc.replaceSelection("", null, "+delete") : deleteNearSelection(this, function(range) {
        var other = findPosH(doc, range.head, dir, unit, !1);
        return 0 > dir ? {from:other, to:range.head} : {from:range.head, to:other};
      });
    }), findPosV:function(from$jscomp$46_i, amount, unit, goalColumn_x) {
      var dir = 1;
      0 > amount && (dir = -1, amount = -amount);
      var coords$jscomp$7_cur = clipPos(this.doc, from$jscomp$46_i);
      for (from$jscomp$46_i = 0; from$jscomp$46_i < amount && (coords$jscomp$7_cur = cursorCoords(this, coords$jscomp$7_cur, "div"), null == goalColumn_x ? goalColumn_x = coords$jscomp$7_cur.left : coords$jscomp$7_cur.left = goalColumn_x, coords$jscomp$7_cur = findPosV(this, coords$jscomp$7_cur, dir, unit), !coords$jscomp$7_cur.hitSide); ++from$jscomp$46_i) {
      }
      return coords$jscomp$7_cur;
    }, moveV:methodOp(function(dir, unit) {
      var this$1 = this, doc = this.doc, goals = [], collapse = !this.display.shift && !doc.extend && doc.sel.somethingSelected();
      doc.extendSelectionsBy(function(range) {
        if (collapse) {
          return 0 > dir ? range.from() : range.to();
        }
        var headPos = cursorCoords(this$1, range.head, "div");
        null != range.goalColumn && (headPos.left = range.goalColumn);
        goals.push(headPos.left);
        var pos = findPosV(this$1, headPos, dir, unit);
        "page" == unit && range == doc.sel.primary() && addToScrollTop(this$1, charCoords(this$1, pos, "div").top - headPos.top);
        return pos;
      }, sel_move);
      if (goals.length) {
        for (var i = 0; i < doc.sel.ranges.length; i++) {
          doc.sel.ranges[i].goalColumn = goals[i];
        }
      }
    }), findWordAt:function(pos) {
      var line = getLine(this.doc, pos.line).text, start = pos.ch, end = pos.ch;
      if (line) {
        var helper = this.getHelper(pos, "wordChars");
        "before" != pos.sticky && end != line.length || !start ? ++end : --start;
        var check_startChar = line.charAt(start);
        for (check_startChar = isWordChar(check_startChar, helper) ? function(ch) {
          return isWordChar(ch, helper);
        } : /\s/.test(check_startChar) ? function(ch) {
          return /\s/.test(ch);
        } : function(ch) {
          return !/\s/.test(ch) && !isWordChar(ch);
        }; 0 < start && check_startChar(line.charAt(start - 1));) {
          --start;
        }
        for (; end < line.length && check_startChar(line.charAt(end));) {
          ++end;
        }
      }
      return new Range(Pos(pos.line, start), Pos(pos.line, end));
    }, toggleOverwrite:function(value) {
      if (null == value || value != this.state.overwrite) {
        (this.state.overwrite = !this.state.overwrite) ? addClass(this.display.cursorDiv, "CodeMirror-overwrite") : rmClass(this.display.cursorDiv, "CodeMirror-overwrite"), signal(this, "overwriteToggle", this, this.state.overwrite);
      }
    }, hasFocus:function() {
      return this.display.input.getField() == activeElt();
    }, isReadOnly:function() {
      return !(!this.options.readOnly && !this.doc.cantEdit);
    }, scrollTo:methodOp(function(x, y) {
      scrollToCoords(this, x, y);
    }), getScrollInfo:function() {
      var scroller = this.display.scroller;
      return {left:scroller.scrollLeft, top:scroller.scrollTop, height:scroller.scrollHeight - scrollGap(this) - this.display.barHeight, width:scroller.scrollWidth - scrollGap(this) - this.display.barWidth, clientHeight:displayHeight(this), clientWidth:displayWidth(this)};
    }, scrollIntoView:methodOp(function(range$jscomp$45_range, margin) {
      null == range$jscomp$45_range ? (range$jscomp$45_range = {from:this.doc.sel.primary().head, to:null}, null == margin && (margin = this.options.cursorScrollMargin)) : "number" == typeof range$jscomp$45_range ? range$jscomp$45_range = {from:Pos(range$jscomp$45_range, 0), to:null} : null == range$jscomp$45_range.from && (range$jscomp$45_range = {from:range$jscomp$45_range, to:null});
      range$jscomp$45_range.to || (range$jscomp$45_range.to = range$jscomp$45_range.from);
      range$jscomp$45_range.margin = margin || 0;
      null != range$jscomp$45_range.from.line ? (resolveScrollToPos(this), this.curOp.scrollToPos = range$jscomp$45_range) : scrollToCoordsRange(this, range$jscomp$45_range.from, range$jscomp$45_range.to, range$jscomp$45_range.margin);
    }), setSize:methodOp(function(width, height) {
      function interpret(val) {
        return "number" == typeof val || /^\d+$/.test(String(val)) ? val + "px" : val;
      }
      var this$1 = this;
      null != width && (this.display.wrapper.style.width = interpret(width));
      null != height && (this.display.wrapper.style.height = interpret(height));
      this.options.lineWrapping && clearLineMeasurementCache(this);
      var lineNo = this.display.viewFrom;
      this.doc.iter(lineNo, this.display.viewTo, function(line) {
        if (line.widgets) {
          for (var i = 0; i < line.widgets.length; i++) {
            if (line.widgets[i].noHScroll) {
              regLineChange(this$1, lineNo, "widget");
              break;
            }
          }
        }
        ++lineNo;
      });
      this.curOp.forceUpdate = !0;
      signal(this, "refresh", this);
    }), operation:function(f) {
      return runInOp(this, f);
    }, startOperation:function() {
      return startOperation(this);
    }, endOperation:function() {
      return endOperation(this);
    }, refresh:methodOp(function() {
      var oldHeight = this.display.cachedTextHeight;
      regChange(this);
      this.curOp.forceUpdate = !0;
      clearCaches(this);
      scrollToCoords(this, this.doc.scrollLeft, this.doc.scrollTop);
      updateGutterSpace(this.display);
      (null == oldHeight || .5 < Math.abs(oldHeight - textHeight(this.display)) || this.options.lineWrapping) && estimateLineHeights(this);
      signal(this, "refresh", this);
    }), swapDoc:methodOp(function(doc) {
      var old = this.doc;
      old.cm = null;
      this.state.selectingText && this.state.selectingText();
      attachDoc(this, doc);
      clearCaches(this);
      this.display.input.reset();
      scrollToCoords(this, doc.scrollLeft, doc.scrollTop);
      this.curOp.forceScroll = !0;
      signalLater(this, "swapDoc", this, old);
      return old;
    }), getInputField:function() {
      return this.display.input.getField();
    }, getWrapperElement:function() {
      return this.display.wrapper;
    }, getScrollerElement:function() {
      return this.display.scroller;
    }, getGutterElement:function() {
      return this.display.gutters;
    }};
    eventMixin(CodeMirror);
    CodeMirror.registerHelper = function(type, name, value) {
      helpers.hasOwnProperty(type) || (helpers[type] = CodeMirror[type] = {_global:[]});
      helpers[type][name] = value;
    };
    CodeMirror.registerGlobalHelper = function(type, name, predicate, value) {
      CodeMirror.registerHelper(type, name, value);
      helpers[type]._global.push({pred:predicate, val:value});
    };
  })(CodeMirror$jscomp$0);
  var dontDelegate = "iter insert remove copy getEditor constructor".split(" "), prop$jscomp$0;
  for (prop$jscomp$0 in Doc.prototype) {
    Doc.prototype.hasOwnProperty(prop$jscomp$0) && 0 > indexOf(dontDelegate, prop$jscomp$0) && (CodeMirror$jscomp$0.prototype[prop$jscomp$0] = function(method) {
      return function() {
        return method.apply(this.doc, arguments);
      };
    }(Doc.prototype[prop$jscomp$0]));
  }
  eventMixin(Doc);
  CodeMirror$jscomp$0.inputStyles = {textarea:TextareaInput, contenteditable:ContentEditableInput};
  CodeMirror$jscomp$0.defineMode = function(name) {
    CodeMirror$jscomp$0.defaults.mode || "null" == name || (CodeMirror$jscomp$0.defaults.mode = name);
    defineMode.apply(this, arguments);
  };
  CodeMirror$jscomp$0.defineMIME = function(mime, spec) {
    mimeModes[mime] = spec;
  };
  CodeMirror$jscomp$0.defineMode("null", function() {
    return {token:function(stream) {
      return stream.skipToEnd();
    }};
  });
  CodeMirror$jscomp$0.defineMIME("text/plain", "null");
  CodeMirror$jscomp$0.defineExtension = function(name, func) {
    CodeMirror$jscomp$0.prototype[name] = func;
  };
  CodeMirror$jscomp$0.defineDocExtension = function(name, func) {
    Doc.prototype[name] = func;
  };
  CodeMirror$jscomp$0.fromTextArea = function(textarea, options) {
    function save() {
      textarea.value = cm$jscomp$0.getValue();
    }
    options = options ? copyObj(options) : {};
    options.value = textarea.value;
    !options.tabindex && textarea.tabIndex && (options.tabindex = textarea.tabIndex);
    !options.placeholder && textarea.placeholder && (options.placeholder = textarea.placeholder);
    if (null == options.autofocus) {
      var hasFocus = activeElt();
      options.autofocus = hasFocus == textarea || null != textarea.getAttribute("autofocus") && hasFocus == document.body;
    }
    if (textarea.form && (on(textarea.form, "submit", save), !options.leaveSubmitMethodAlone)) {
      var form = textarea.form;
      var realSubmit = form.submit;
      try {
        var wrappedSubmit = form.submit = function() {
          save();
          form.submit = realSubmit;
          form.submit();
          form.submit = wrappedSubmit;
        };
      } catch (e) {
      }
    }
    options.finishInit = function(cm) {
      cm.save = save;
      cm.getTextArea = function() {
        return textarea;
      };
      cm.toTextArea = function() {
        cm.toTextArea = isNaN;
        save();
        textarea.parentNode.removeChild(cm.getWrapperElement());
        textarea.style.display = "";
        textarea.form && (off(textarea.form, "submit", save), options.leaveSubmitMethodAlone || "function" != typeof textarea.form.submit || (textarea.form.submit = realSubmit));
      };
    };
    textarea.style.display = "none";
    var cm$jscomp$0 = CodeMirror$jscomp$0(function(node) {
      return textarea.parentNode.insertBefore(node, textarea.nextSibling);
    }, options);
    return cm$jscomp$0;
  };
  (function(CodeMirror) {
    CodeMirror.off = off;
    CodeMirror.on = on;
    CodeMirror.wheelEventPixels = wheelEventPixels;
    CodeMirror.Doc = Doc;
    CodeMirror.splitLines = splitLinesAuto;
    CodeMirror.countColumn = countColumn;
    CodeMirror.findColumn = findColumn;
    CodeMirror.isWordChar = isWordCharBasic;
    CodeMirror.Pass = Pass;
    CodeMirror.signal = signal;
    CodeMirror.Line = Line;
    CodeMirror.changeEnd = changeEnd;
    CodeMirror.scrollbarModel = scrollbarModel;
    CodeMirror.Pos = Pos;
    CodeMirror.cmpPos = cmp;
    CodeMirror.modes = modes;
    CodeMirror.mimeModes = mimeModes;
    CodeMirror.resolveMode = resolveMode;
    CodeMirror.getMode = getMode;
    CodeMirror.modeExtensions = modeExtensions;
    CodeMirror.extendMode = extendMode;
    CodeMirror.copyState = copyState;
    CodeMirror.startState = startState;
    CodeMirror.innerMode = innerMode;
    CodeMirror.commands = commands;
    CodeMirror.keyMap = keyMap;
    CodeMirror.keyName = keyName;
    CodeMirror.isModifierKey = isModifierKey;
    CodeMirror.lookupKey = lookupKey;
    CodeMirror.normalizeKeyMap = normalizeKeyMap;
    CodeMirror.StringStream = StringStream;
    CodeMirror.SharedTextMarker = SharedTextMarker;
    CodeMirror.TextMarker = TextMarker;
    CodeMirror.LineWidget = LineWidget;
    CodeMirror.e_preventDefault = e_preventDefault;
    CodeMirror.e_stopPropagation = e_stopPropagation;
    CodeMirror.e_stop = e_stop;
    CodeMirror.addClass = addClass;
    CodeMirror.contains = contains;
    CodeMirror.rmClass = rmClass;
    CodeMirror.keyNames = keyNames;
  })(CodeMirror$jscomp$0);
  CodeMirror$jscomp$0.version = "5.65.6";
  return CodeMirror$jscomp$0;
}
"object" === typeof exports && "undefined" !== typeof module ? module.exports = factory$jscomp$inline_519() : "function" === typeof define && define.amd ? define(factory$jscomp$inline_519) : (global$jscomp$inline_518 = global$jscomp$inline_518 || self, global$jscomp$inline_518.CodeMirror = factory$jscomp$inline_519());
//[third_party/javascript/codemirror4/addon/edit/closebrackets.js]
function mod$jscomp$inline_524(CodeMirror) {
  function getOption(conf, name) {
    return "pairs" == name && "string" == typeof conf ? conf : "object" == typeof conf && null != conf[name] ? conf[name] : defaults[name];
  }
  function ensureBound(chars) {
    for (var i = 0; i < chars.length; i++) {
      var ch = chars.charAt(i), key = "'" + ch + "'";
      keyMap[key] || (keyMap[key] = handler(ch));
    }
  }
  function handler(ch) {
    return function(cm) {
      return handleChar(cm, ch);
    };
  }
  function getConfig(cm) {
    var deflt = cm.state.closeBrackets;
    return !deflt || deflt.override ? deflt : cm.getModeAt(cm.getCursor()).closeBrackets || deflt;
  }
  function moveSel(cm, dir) {
    for (var newRanges = [], ranges = cm.listSelections(), primary = 0, i = 0; i < ranges.length; i++) {
      var pos$jscomp$81_range = ranges[i];
      pos$jscomp$81_range.head == cm.getCursor() && (primary = i);
      pos$jscomp$81_range = pos$jscomp$81_range.head.ch || 0 < dir ? {line:pos$jscomp$81_range.head.line, ch:pos$jscomp$81_range.head.ch + dir} : {line:pos$jscomp$81_range.head.line - 1};
      newRanges.push({anchor:pos$jscomp$81_range, head:pos$jscomp$81_range});
    }
    cm.setSelections(newRanges, primary);
  }
  function handleChar(cm, ch) {
    var conf = getConfig(cm);
    if (!conf || cm.getOption("disableInput")) {
      return CodeMirror.Pass;
    }
    var pairs = getOption(conf, "pairs"), pos = pairs.indexOf(ch);
    if (-1 == pos) {
      return CodeMirror.Pass;
    }
    var closeBefore = getOption(conf, "closeBefore");
    conf = getOption(conf, "triples");
    for (var identical = pairs.charAt(pos + 1) == ch, ranges = cm.listSelections(), opening = 0 == pos % 2, type, i$jscomp$0 = 0; i$jscomp$0 < ranges.length; i$jscomp$0++) {
      var prev$jscomp$7_range = ranges[i$jscomp$0], cur = prev$jscomp$7_range.head;
      var curType_next = cm.getRange(cur, Pos(cur.line, cur.ch + 1));
      if (opening && !prev$jscomp$7_range.empty()) {
        curType_next = "surround";
      } else if (!identical && opening || curType_next != ch) {
        if (identical && 1 < cur.ch && 0 <= conf.indexOf(ch) && cm.getRange(Pos(cur.line, cur.ch - 2), cur) == ch + ch) {
          if (2 < cur.ch && /\bstring/.test(cm.getTokenTypeAt(Pos(cur.line, cur.ch - 2)))) {
            return CodeMirror.Pass;
          }
          curType_next = "addFour";
        } else if (identical) {
          prev$jscomp$7_range = 0 == cur.ch ? " " : cm.getRange(Pos(cur.line, cur.ch - 1), cur);
          if (CodeMirror.isWordChar(curType_next) || prev$jscomp$7_range == ch || CodeMirror.isWordChar(prev$jscomp$7_range)) {
            return CodeMirror.Pass;
          }
          curType_next = "both";
        } else if (opening && (0 === curType_next.length || /\s/.test(curType_next) || -1 < closeBefore.indexOf(curType_next))) {
          curType_next = "both";
        } else {
          return CodeMirror.Pass;
        }
      } else {
        curType_next = identical && stringStartsAfter(cm, cur) ? "both" : 0 <= conf.indexOf(ch) && cm.getRange(cur, Pos(cur.line, cur.ch + 3)) == ch + ch + ch ? "skipThree" : "skip";
      }
      if (!type) {
        type = curType_next;
      } else if (type != curType_next) {
        return CodeMirror.Pass;
      }
    }
    var left = pos % 2 ? pairs.charAt(pos - 1) : ch, right = pos % 2 ? ch : pairs.charAt(pos + 1);
    cm.operation(function() {
      if ("skip" == type) {
        moveSel(cm, 1);
      } else if ("skipThree" == type) {
        moveSel(cm, 3);
      } else if ("surround" == type) {
        for (var sels = cm.getSelections(), i = 0; i < sels.length; i++) {
          sels[i] = left + sels[i] + right;
        }
        cm.replaceSelections(sels, "around");
        sels = cm.listSelections().slice();
        for (i = 0; i < sels.length; i++) {
          var JSCompiler_temp_const = sels, JSCompiler_temp_const$jscomp$0 = i;
          var JSCompiler_inline_result$jscomp$65_sel = sels[i];
          var inverted = 0 < CodeMirror.cmpPos(JSCompiler_inline_result$jscomp$65_sel.anchor, JSCompiler_inline_result$jscomp$65_sel.head);
          JSCompiler_inline_result$jscomp$65_sel = {anchor:new Pos(JSCompiler_inline_result$jscomp$65_sel.anchor.line, JSCompiler_inline_result$jscomp$65_sel.anchor.ch + (inverted ? -1 : 1)), head:new Pos(JSCompiler_inline_result$jscomp$65_sel.head.line, JSCompiler_inline_result$jscomp$65_sel.head.ch + (inverted ? 1 : -1))};
          JSCompiler_temp_const[JSCompiler_temp_const$jscomp$0] = JSCompiler_inline_result$jscomp$65_sel;
        }
        cm.setSelections(sels);
      } else {
        "both" == type ? (cm.replaceSelection(left + right, null), cm.triggerElectric(left + right), moveSel(cm, -1)) : "addFour" == type && (cm.replaceSelection(left + left + left + left, "before"), moveSel(cm, 1));
      }
    });
  }
  function charsAround(cm$jscomp$263_str, pos) {
    cm$jscomp$263_str = cm$jscomp$263_str.getRange(Pos(pos.line, pos.ch - 1), Pos(pos.line, pos.ch + 1));
    return 2 == cm$jscomp$263_str.length ? cm$jscomp$263_str : null;
  }
  function stringStartsAfter(cm, pos) {
    var token = cm.getTokenAt(Pos(pos.line, pos.ch + 1));
    return /\bstring/.test(token.type) && token.start == pos.ch && (0 == pos.ch || !/\bstring/.test(cm.getTokenTypeAt(pos)));
  }
  var defaults = {pairs:"()[]{}''\"\"", closeBefore:")]}'\":;>", triples:"", explode:"[]{}"}, Pos = CodeMirror.Pos;
  CodeMirror.defineOption("autoCloseBrackets", !1, function(cm, val, old) {
    old && old != CodeMirror.Init && (cm.removeKeyMap(keyMap), cm.state.closeBrackets = null);
    val && (ensureBound(getOption(val, "pairs")), cm.state.closeBrackets = val, cm.addKeyMap(keyMap));
  });
  var keyMap = {Backspace:function(cm) {
    var conf$jscomp$1_ranges = getConfig(cm);
    if (!conf$jscomp$1_ranges || cm.getOption("disableInput")) {
      return CodeMirror.Pass;
    }
    var cur = getOption(conf$jscomp$1_ranges, "pairs");
    conf$jscomp$1_ranges = cm.listSelections();
    for (var i = 0; i < conf$jscomp$1_ranges.length; i++) {
      if (!conf$jscomp$1_ranges[i].empty()) {
        return CodeMirror.Pass;
      }
      var around = charsAround(cm, conf$jscomp$1_ranges[i].head);
      if (!around || 0 != cur.indexOf(around) % 2) {
        return CodeMirror.Pass;
      }
    }
    for (i = conf$jscomp$1_ranges.length - 1; 0 <= i; i--) {
      cur = conf$jscomp$1_ranges[i].head, cm.replaceRange("", Pos(cur.line, cur.ch - 1), Pos(cur.line, cur.ch + 1), "+delete");
    }
  }, Enter:function(cm) {
    var conf = getConfig(cm);
    conf = conf && getOption(conf, "explode");
    if (!conf || cm.getOption("disableInput")) {
      return CodeMirror.Pass;
    }
    for (var ranges = cm.listSelections(), i$jscomp$0 = 0; i$jscomp$0 < ranges.length; i$jscomp$0++) {
      if (!ranges[i$jscomp$0].empty()) {
        return CodeMirror.Pass;
      }
      var around = charsAround(cm, ranges[i$jscomp$0].head);
      if (!around || 0 != conf.indexOf(around) % 2) {
        return CodeMirror.Pass;
      }
    }
    cm.operation(function() {
      var i = cm.lineSeparator() || "\n";
      cm.replaceSelection(i + i, null);
      moveSel(cm, -1);
      ranges = cm.listSelections();
      for (i = 0; i < ranges.length; i++) {
        var line = ranges[i].head.line;
        cm.indentLine(line, null, !0);
        cm.indentLine(line + 1, null, !0);
      }
    });
  }};
  ensureBound(defaults.pairs + "`");
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_524(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_524) : mod$jscomp$inline_524(CodeMirror);
//[third_party/javascript/codemirror4/addon/edit/closetag.js]
function mod$jscomp$inline_528(CodeMirror) {
  function autoCloseGT(cm) {
    if (cm.getOption("disableInput")) {
      return CodeMirror.Pass;
    }
    for (var ranges = cm.listSelections(), replacements = [], dontIndentOnAutoClose_opt = cm.getOption("autoCloseTags"), i = 0; i < ranges.length; i++) {
      if (!ranges[i].empty()) {
        return CodeMirror.Pass;
      }
      var info$jscomp$6_pos = ranges[i].head, emptyTags_indent_tok = cm.getTokenAt(info$jscomp$6_pos), inner = CodeMirror.innerMode(cm.getMode(), emptyTags_indent_tok.state), state = inner.state, tagInfo = inner.mode.xmlCurrentTag && inner.mode.xmlCurrentTag(state), sel$jscomp$25_tagName = tagInfo && tagInfo.name;
      if (!sel$jscomp$25_tagName) {
        return CodeMirror.Pass;
      }
      var html = "html" == inner.mode.configuration, dontCloseTags = "object" == typeof dontIndentOnAutoClose_opt && dontIndentOnAutoClose_opt.dontCloseTags || html && htmlDontClose;
      html = "object" == typeof dontIndentOnAutoClose_opt && dontIndentOnAutoClose_opt.indentTags || html && htmlIndent;
      emptyTags_indent_tok.end > info$jscomp$6_pos.ch && (sel$jscomp$25_tagName = sel$jscomp$25_tagName.slice(0, sel$jscomp$25_tagName.length - emptyTags_indent_tok.end + info$jscomp$6_pos.ch));
      var lowerTagName = sel$jscomp$25_tagName.toLowerCase();
      if (!sel$jscomp$25_tagName || "string" == emptyTags_indent_tok.type && (emptyTags_indent_tok.end != info$jscomp$6_pos.ch || !/["']/.test(emptyTags_indent_tok.string.charAt(emptyTags_indent_tok.string.length - 1)) || 1 == emptyTags_indent_tok.string.length) || "tag" == emptyTags_indent_tok.type && tagInfo.close || emptyTags_indent_tok.string.indexOf("/") == info$jscomp$6_pos.ch - emptyTags_indent_tok.start - 1 || dontCloseTags && -1 < indexOf(dontCloseTags, lowerTagName) || closingTagExists(cm, 
      inner.mode.xmlCurrentContext && inner.mode.xmlCurrentContext(state) || [], sel$jscomp$25_tagName, info$jscomp$6_pos, !0)) {
        return CodeMirror.Pass;
      }
      (emptyTags_indent_tok = "object" == typeof dontIndentOnAutoClose_opt && dontIndentOnAutoClose_opt.emptyTags) && -1 < indexOf(emptyTags_indent_tok, sel$jscomp$25_tagName) ? replacements[i] = {text:"/>", newPos:CodeMirror.Pos(info$jscomp$6_pos.line, info$jscomp$6_pos.ch + 2)} : (emptyTags_indent_tok = html && -1 < indexOf(html, lowerTagName), replacements[i] = {indent:emptyTags_indent_tok, text:">" + (emptyTags_indent_tok ? "\n\n" : "") + "</" + sel$jscomp$25_tagName + ">", newPos:emptyTags_indent_tok ? 
      CodeMirror.Pos(info$jscomp$6_pos.line + 1, 0) : CodeMirror.Pos(info$jscomp$6_pos.line, info$jscomp$6_pos.ch + 1)});
    }
    dontIndentOnAutoClose_opt = "object" == typeof dontIndentOnAutoClose_opt && dontIndentOnAutoClose_opt.dontIndentOnAutoClose;
    for (i = ranges.length - 1; 0 <= i; i--) {
      info$jscomp$6_pos = replacements[i], cm.replaceRange(info$jscomp$6_pos.text, ranges[i].head, ranges[i].anchor, "+insert"), sel$jscomp$25_tagName = cm.listSelections().slice(0), sel$jscomp$25_tagName[i] = {head:info$jscomp$6_pos.newPos, anchor:info$jscomp$6_pos.newPos}, cm.setSelections(sel$jscomp$25_tagName), !dontIndentOnAutoClose_opt && info$jscomp$6_pos.indent && (cm.indentLine(info$jscomp$6_pos.newPos.line, null, !0), cm.indentLine(info$jscomp$6_pos.newPos.line + 1, null, !0));
    }
  }
  function autoCloseCurrent(cm, typingSlash) {
    var ranges = cm.listSelections(), replacements = [], head = typingSlash ? "/" : "</", dontIndentOnAutoClose$jscomp$1_opt = cm.getOption("autoCloseTags");
    dontIndentOnAutoClose$jscomp$1_opt = "object" == typeof dontIndentOnAutoClose$jscomp$1_opt && dontIndentOnAutoClose$jscomp$1_opt.dontIndentOnSlash;
    for (var i = 0; i < ranges.length; i++) {
      if (!ranges[i].empty()) {
        return CodeMirror.Pass;
      }
      var pos = ranges[i].head, tok = cm.getTokenAt(pos), context$jscomp$17_inner$jscomp$7_replacement = CodeMirror.innerMode(cm.getMode(), tok.state), state$jscomp$15_top = context$jscomp$17_inner$jscomp$7_replacement.state;
      if (typingSlash && ("string" == tok.type || "<" != tok.string.charAt(0) || tok.start != pos.ch - 1)) {
        return CodeMirror.Pass;
      }
      var mixed = "xml" != context$jscomp$17_inner$jscomp$7_replacement.mode.name && "htmlmixed" == cm.getMode().name;
      if (mixed && "javascript" == context$jscomp$17_inner$jscomp$7_replacement.mode.name) {
        context$jscomp$17_inner$jscomp$7_replacement = head + "script";
      } else if (mixed && "css" == context$jscomp$17_inner$jscomp$7_replacement.mode.name) {
        context$jscomp$17_inner$jscomp$7_replacement = head + "style";
      } else {
        context$jscomp$17_inner$jscomp$7_replacement = context$jscomp$17_inner$jscomp$7_replacement.mode.xmlCurrentContext && context$jscomp$17_inner$jscomp$7_replacement.mode.xmlCurrentContext(state$jscomp$15_top);
        state$jscomp$15_top = context$jscomp$17_inner$jscomp$7_replacement.length ? context$jscomp$17_inner$jscomp$7_replacement[context$jscomp$17_inner$jscomp$7_replacement.length - 1] : "";
        if (!context$jscomp$17_inner$jscomp$7_replacement || context$jscomp$17_inner$jscomp$7_replacement.length && closingTagExists(cm, context$jscomp$17_inner$jscomp$7_replacement, state$jscomp$15_top, pos)) {
          return CodeMirror.Pass;
        }
        context$jscomp$17_inner$jscomp$7_replacement = head + state$jscomp$15_top;
      }
      ">" != cm.getLine(pos.line).charAt(tok.end) && (context$jscomp$17_inner$jscomp$7_replacement += ">");
      replacements[i] = context$jscomp$17_inner$jscomp$7_replacement;
    }
    cm.replaceSelections(replacements);
    ranges = cm.listSelections();
    if (!dontIndentOnAutoClose$jscomp$1_opt) {
      for (i = 0; i < ranges.length; i++) {
        (i == ranges.length - 1 || ranges[i].head.line < ranges[i + 1].head.line) && cm.indentLine(ranges[i].head.line);
      }
    }
  }
  function indexOf(collection, elt) {
    if (collection.indexOf) {
      return collection.indexOf(elt);
    }
    for (var i = 0, e = collection.length; i < e; ++i) {
      if (collection[i] == elt) {
        return i;
      }
    }
    return -1;
  }
  function closingTagExists(cm, context$jscomp$18_next, tagName, nextClose_pos, newTag_onCx) {
    if (!CodeMirror.scanForClosingTag) {
      return !1;
    }
    var end = Math.min(cm.lastLine() + 1, nextClose_pos.line + 500);
    nextClose_pos = CodeMirror.scanForClosingTag(cm, nextClose_pos, null, end);
    if (!nextClose_pos || nextClose_pos.tag != tagName) {
      return !1;
    }
    newTag_onCx = newTag_onCx ? 1 : 0;
    for (var i = context$jscomp$18_next.length - 1; 0 <= i; i--) {
      if (context$jscomp$18_next[i] == tagName) {
        ++newTag_onCx;
      } else {
        break;
      }
    }
    nextClose_pos = nextClose_pos.to;
    for (i = 1; i < newTag_onCx; i++) {
      context$jscomp$18_next = CodeMirror.scanForClosingTag(cm, nextClose_pos, null, end);
      if (!context$jscomp$18_next || context$jscomp$18_next.tag != tagName) {
        return !1;
      }
      nextClose_pos = context$jscomp$18_next.to;
    }
    return !0;
  }
  CodeMirror.defineOption("autoCloseTags", !1, function(cm$jscomp$0, val, map$jscomp$14_old) {
    map$jscomp$14_old != CodeMirror.Init && map$jscomp$14_old && cm$jscomp$0.removeKeyMap("autoCloseTags");
    if (val) {
      map$jscomp$14_old = {name:"autoCloseTags"};
      if ("object" != typeof val || !1 !== val.whenClosing) {
        map$jscomp$14_old["'/'"] = function(cm) {
          return cm.getOption("disableInput") ? CodeMirror.Pass : autoCloseCurrent(cm, !0);
        };
      }
      if ("object" != typeof val || !1 !== val.whenOpening) {
        map$jscomp$14_old["'>'"] = function(cm) {
          return autoCloseGT(cm);
        };
      }
      cm$jscomp$0.addKeyMap(map$jscomp$14_old);
    }
  });
  var htmlDontClose = "area base br col command embed hr img input keygen link meta param source track wbr".split(" "), htmlIndent = "applet blockquote body button div dl fieldset form frameset h1 h2 h3 h4 h5 h6 head html iframe layer legend object ol p select table ul".split(" ");
  CodeMirror.commands.closeTag = function(cm) {
    return autoCloseCurrent(cm);
  };
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_528(require("../../lib/codemirror"), require("../fold/xml-fold")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror", "../fold/xml-fold"], mod$jscomp$inline_528) : mod$jscomp$inline_528(CodeMirror);
//[third_party/javascript/codemirror4/addon/edit/continuelist.js]
function mod$jscomp$inline_545(CodeMirror) {
  var listRE = /^(\s*)(>[> ]*|[*+-] \[[x ]\]\s|[*+-]\s|(\d+)([.)]))(\s*)/, emptyListRE = /^(\s*)(>[> ]*|[*+-] \[[x ]\]|[*+-]|(\d+)[.)])(\s*)$/, unorderedListRE = /[*+-]\s/;
  CodeMirror.commands.newlineAndIndentContinueMarkdownList = function(cm) {
    if (cm.getOption("disableInput")) {
      return CodeMirror.Pass;
    }
    for (var ranges = cm.listSelections(), replacements = [], i = 0; i < ranges.length; i++) {
      var pos$jscomp$88_startLine = ranges[i].head, cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match = cm.getStateAfter(pos$jscomp$88_startLine.line);
      cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match = CodeMirror.innerMode(cm.getMode(), cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match);
      if ("markdown" !== cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match.mode.name && "markdown" !== cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match.mode.helperType) {
        cm.execCommand("newlineAndIndent");
        return;
      }
      cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match = cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match.state;
      var inList_indent$jscomp$1_lookAhead = !1 !== cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match.list, after$jscomp$7_inQuote_skipCount = 0 !== cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match.quote, line$jscomp$131_numbered_startItem = cm.getLine(pos$jscomp$88_startLine.line);
      cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match = listRE.exec(line$jscomp$131_numbered_startItem);
      var cursorBeforeBullet_startIndent = /^\s*$/.test(line$jscomp$131_numbered_startItem.slice(0, pos$jscomp$88_startLine.ch));
      if (!ranges[i].empty() || !inList_indent$jscomp$1_lookAhead && !after$jscomp$7_inQuote_skipCount || !cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match || cursorBeforeBullet_startIndent) {
        cm.execCommand("newlineAndIndent");
        return;
      }
      if (emptyListRE.test(line$jscomp$131_numbered_startItem)) {
        cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match = !/>\s*$/.test(line$jscomp$131_numbered_startItem), (after$jscomp$7_inQuote_skipCount && />\s*$/.test(line$jscomp$131_numbered_startItem) || cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match) && cm.replaceRange("", {line:pos$jscomp$88_startLine.line, ch:0}, {line:pos$jscomp$88_startLine.line, ch:pos$jscomp$88_startLine.ch + 1}), replacements[i] = "\n";
      } else {
        if (inList_indent$jscomp$1_lookAhead = cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match[1], after$jscomp$7_inQuote_skipCount = cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match[5], line$jscomp$131_numbered_startItem = !(unorderedListRE.test(cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match[2]) || 0 <= cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match[2].indexOf(">")), replacements[i] = "\n" + inList_indent$jscomp$1_lookAhead + (line$jscomp$131_numbered_startItem ? 
        parseInt(cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match[3], 10) + 1 + cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match[4] : cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match[2].replace("x", " ")) + after$jscomp$7_inQuote_skipCount, line$jscomp$131_numbered_startItem) {
          cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match = cm;
          pos$jscomp$88_startLine = pos$jscomp$88_startLine.line;
          after$jscomp$7_inQuote_skipCount = inList_indent$jscomp$1_lookAhead = 0;
          line$jscomp$131_numbered_startItem = listRE.exec(cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match.getLine(pos$jscomp$88_startLine));
          cursorBeforeBullet_startIndent = line$jscomp$131_numbered_startItem[1];
          do {
            inList_indent$jscomp$1_lookAhead += 1;
            var nextLineNumber = pos$jscomp$88_startLine + inList_indent$jscomp$1_lookAhead, nextLine = cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match.getLine(nextLineNumber), nextItem = listRE.exec(nextLine);
            if (nextItem) {
              var nextIndent = nextItem[1], newNumber = parseInt(line$jscomp$131_numbered_startItem[3], 10) + inList_indent$jscomp$1_lookAhead - after$jscomp$7_inQuote_skipCount, nextNumber = parseInt(nextItem[3], 10), itemNumber = nextNumber;
              if (cursorBeforeBullet_startIndent !== nextIndent || isNaN(nextNumber)) {
                if (cursorBeforeBullet_startIndent.length > nextIndent.length) {
                  break;
                }
                if (cursorBeforeBullet_startIndent.length < nextIndent.length && 1 === inList_indent$jscomp$1_lookAhead) {
                  break;
                }
                after$jscomp$7_inQuote_skipCount += 1;
              } else {
                newNumber === nextNumber && (itemNumber = nextNumber + 1), newNumber > nextNumber && (itemNumber = newNumber + 1), cm$jscomp$inline_530_endOfList_eolState_inner$jscomp$8_match.replaceRange(nextLine.replace(listRE, nextIndent + itemNumber + nextItem[4] + nextItem[5]), {line:nextLineNumber, ch:0}, {line:nextLineNumber, ch:nextLine.length});
              }
            }
          } while (nextItem);
        }
      }
    }
    cm.replaceSelections(replacements);
  };
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_545(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_545) : mod$jscomp$inline_545(CodeMirror);
//[third_party/javascript/codemirror4/addon/edit/matchbrackets.js]
function mod$jscomp$inline_549(CodeMirror) {
  function findMatchingBracket(cm$jscomp$275_found, where, config) {
    var line$jscomp$132_match = cm$jscomp$275_found.getLineHandle(where.line), pos = where.ch - 1, afterCursor_dir = config && config.afterCursor;
    null == afterCursor_dir && (afterCursor_dir = /(^| )cm-fat-cursor($| )/.test(cm$jscomp$275_found.getWrapperElement().className));
    var re$jscomp$2_style = config && config.bracketRegex || /[(){}[\]]/;
    line$jscomp$132_match = !afterCursor_dir && 0 <= pos && re$jscomp$2_style.test(line$jscomp$132_match.text.charAt(pos)) && matching[line$jscomp$132_match.text.charAt(pos)] || re$jscomp$2_style.test(line$jscomp$132_match.text.charAt(pos + 1)) && matching[line$jscomp$132_match.text.charAt(++pos)];
    if (!line$jscomp$132_match) {
      return null;
    }
    afterCursor_dir = ">" == line$jscomp$132_match.charAt(1) ? 1 : -1;
    if (config && config.strict && 0 < afterCursor_dir != (pos == where.ch)) {
      return null;
    }
    re$jscomp$2_style = cm$jscomp$275_found.getTokenTypeAt(Pos(where.line, pos + 1));
    cm$jscomp$275_found = scanForBracket(cm$jscomp$275_found, Pos(where.line, pos + (0 < afterCursor_dir ? 1 : 0)), afterCursor_dir, re$jscomp$2_style, config);
    return null == cm$jscomp$275_found ? null : {from:Pos(where.line, pos), to:cm$jscomp$275_found && cm$jscomp$275_found.pos, match:cm$jscomp$275_found && cm$jscomp$275_found.ch == line$jscomp$132_match.charAt(0), forward:0 < afterCursor_dir};
  }
  function scanForBracket(cm, where, dir, style, config$jscomp$8_re) {
    var maxScanLen = config$jscomp$8_re && config$jscomp$8_re.maxScanLineLength || 1E4, lineEnd = config$jscomp$8_re && config$jscomp$8_re.maxScanLines || 1E3, stack = [];
    config$jscomp$8_re = config$jscomp$8_re && config$jscomp$8_re.bracketRegex || /[(){}[\]]/;
    lineEnd = 0 < dir ? Math.min(where.line + lineEnd, cm.lastLine() + 1) : Math.max(cm.firstLine() - 1, where.line - lineEnd);
    for (var lineNo = where.line; lineNo != lineEnd; lineNo += dir) {
      var line = cm.getLine(lineNo);
      if (line) {
        var pos = 0 < dir ? 0 : line.length - 1, end = 0 < dir ? line.length : -1;
        if (!(line.length > maxScanLen)) {
          for (lineNo == where.line && (pos = where.ch - (0 > dir ? 1 : 0)); pos != end; pos += dir) {
            var ch = line.charAt(pos);
            if (config$jscomp$8_re.test(ch) && (void 0 === style || (cm.getTokenTypeAt(Pos(lineNo, pos + 1)) || "") == (style || ""))) {
              var match = matching[ch];
              if (match && ">" == match.charAt(1) == 0 < dir) {
                stack.push(ch);
              } else if (stack.length) {
                stack.pop();
              } else {
                return {pos:Pos(lineNo, pos), ch};
              }
            }
          }
        }
      }
    }
    return lineNo - dir == (0 < dir ? cm.lastLine() : cm.firstLine()) ? !1 : null;
  }
  function matchBrackets(cm, autoclear, clear_config) {
    for (var maxHighlightLen = cm.state.matchBrackets.maxHighlightLineLength || 1E3, highlightNonMatching = clear_config && clear_config.highlightNonMatching, marks = [], ranges = cm.listSelections(), i$jscomp$0 = 0; i$jscomp$0 < ranges.length; i$jscomp$0++) {
      var match = ranges[i$jscomp$0].empty() && findMatchingBracket(cm, ranges[i$jscomp$0].head, clear_config);
      if (match && (match.match || !1 !== highlightNonMatching) && cm.getLine(match.from.line).length <= maxHighlightLen) {
        var style = match.match ? "CodeMirror-matchingbracket" : "CodeMirror-nonmatchingbracket";
        marks.push(cm.markText(match.from, Pos(match.from.line, match.from.ch + 1), {className:style}));
        match.to && cm.getLine(match.to.line).length <= maxHighlightLen && marks.push(cm.markText(match.to, Pos(match.to.line, match.to.ch + 1), {className:style}));
      }
    }
    if (marks.length) {
      if (ie_lt8 && cm.state.focused && cm.focus(), clear_config = function() {
        cm.operation(function() {
          for (var i = 0; i < marks.length; i++) {
            marks[i].clear();
          }
        });
      }, autoclear) {
        setTimeout(clear_config, 800);
      } else {
        return clear_config;
      }
    }
  }
  function doMatchBrackets(cm) {
    cm.operation(function() {
      cm.state.matchBrackets.currentlyHighlighted && (cm.state.matchBrackets.currentlyHighlighted(), cm.state.matchBrackets.currentlyHighlighted = null);
      cm.state.matchBrackets.currentlyHighlighted = matchBrackets(cm, !1, cm.state.matchBrackets);
    });
  }
  var ie_lt8 = /MSIE \d/.test(navigator.userAgent) && (null == document.documentMode || 8 > document.documentMode), Pos = CodeMirror.Pos, matching = {"(":")>", ")":"(<", "[":"]>", "]":"[<", "{":"}>", "}":"{<", "<":">>", ">":"<<"};
  CodeMirror.defineOption("matchBrackets", !1, function(cm, val, old) {
    old && old != CodeMirror.Init && (cm.off("cursorActivity", doMatchBrackets), cm.state.matchBrackets && cm.state.matchBrackets.currentlyHighlighted && (cm.state.matchBrackets.currentlyHighlighted(), cm.state.matchBrackets.currentlyHighlighted = null));
    val && (cm.state.matchBrackets = "object" == typeof val ? val : {}, cm.on("cursorActivity", doMatchBrackets));
  });
  CodeMirror.defineExtension("matchBrackets", function() {
    matchBrackets(this, !0);
  });
  CodeMirror.defineExtension("findMatchingBracket", function(pos, config, oldConfig) {
    if (oldConfig || "boolean" == typeof config) {
      oldConfig ? (oldConfig.strict = config, config = oldConfig) : config = config ? {strict:!0} : null;
    }
    return findMatchingBracket(this, pos, config);
  });
  CodeMirror.defineExtension("scanForBracket", function(pos, dir, style, config) {
    return scanForBracket(this, pos, dir, style, config);
  });
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_549(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_549) : mod$jscomp$inline_549(CodeMirror);
//[third_party/javascript/codemirror4/addon/edit/matchtags.js]
function mod$jscomp$inline_551(CodeMirror) {
  function clear(cm) {
    cm.state.tagHit && cm.state.tagHit.clear();
    cm.state.tagOther && cm.state.tagOther.clear();
    cm.state.tagHit = cm.state.tagOther = null;
  }
  function doMatchTags(cm) {
    cm.state.failedTagMatch = !1;
    cm.operation(function() {
      clear(cm);
      if (!cm.somethingSelected()) {
        var cur$jscomp$26_match$jscomp$22_other = cm.getCursor(), hit_range = cm.getViewport();
        hit_range.from = Math.min(hit_range.from, cur$jscomp$26_match$jscomp$22_other.line);
        hit_range.to = Math.max(cur$jscomp$26_match$jscomp$22_other.line + 1, hit_range.to);
        if (cur$jscomp$26_match$jscomp$22_other = CodeMirror.findMatchingTag(cm, cur$jscomp$26_match$jscomp$22_other, hit_range)) {
          cm.state.matchBothTags && (hit_range = "open" == cur$jscomp$26_match$jscomp$22_other.at ? cur$jscomp$26_match$jscomp$22_other.open : cur$jscomp$26_match$jscomp$22_other.close) && (cm.state.tagHit = cm.markText(hit_range.from, hit_range.to, {className:"CodeMirror-matchingtag"})), (cur$jscomp$26_match$jscomp$22_other = "close" == cur$jscomp$26_match$jscomp$22_other.at ? cur$jscomp$26_match$jscomp$22_other.open : cur$jscomp$26_match$jscomp$22_other.close) ? cm.state.tagOther = cm.markText(cur$jscomp$26_match$jscomp$22_other.from, 
          cur$jscomp$26_match$jscomp$22_other.to, {className:"CodeMirror-matchingtag"}) : cm.state.failedTagMatch = !0;
        }
      }
    });
  }
  function maybeUpdateMatch(cm) {
    cm.state.failedTagMatch && doMatchTags(cm);
  }
  CodeMirror.defineOption("matchTags", !1, function(cm, val, old) {
    old && old != CodeMirror.Init && (cm.off("cursorActivity", doMatchTags), cm.off("viewportChange", maybeUpdateMatch), clear(cm));
    val && (cm.state.matchBothTags = "object" == typeof val && val.bothTags, cm.on("cursorActivity", doMatchTags), cm.on("viewportChange", maybeUpdateMatch), doMatchTags(cm));
  });
  CodeMirror.commands.toMatchingTag = function(cm) {
    var found$jscomp$24_other = CodeMirror.findMatchingTag(cm, cm.getCursor());
    found$jscomp$24_other && (found$jscomp$24_other = "close" == found$jscomp$24_other.at ? found$jscomp$24_other.open : found$jscomp$24_other.close) && cm.extendSelection(found$jscomp$24_other.to, found$jscomp$24_other.from);
  };
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_551(require("../../lib/codemirror"), require("../fold/xml-fold")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror", "../fold/xml-fold"], mod$jscomp$inline_551) : mod$jscomp$inline_551(CodeMirror);
//[third_party/javascript/codemirror4/addon/edit/trailingspace.js]
function mod$jscomp$inline_553(CodeMirror) {
  CodeMirror.defineOption("showTrailingSpace", !1, function(cm, val, prev) {
    prev == CodeMirror.Init && (prev = !1);
    prev && !val ? cm.removeOverlay("trailingspace") : !prev && val && cm.addOverlay({token:function(stream) {
      for (var l = stream.string.length, i = l; i && /\s/.test(stream.string.charAt(i - 1)); --i) {
      }
      if (i > stream.pos) {
        return stream.pos = i, null;
      }
      stream.pos = l;
      return "trailingspace";
    }, name:"trailingspace"});
  });
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_553(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_553) : mod$jscomp$inline_553(CodeMirror);
//[third_party/javascript/codemirror4/addon/display/autorefresh.js]
function mod$jscomp$inline_555(CodeMirror) {
  function startListening(cm, state) {
    function check() {
      cm.display.wrapper.offsetHeight ? (stopListening(cm, state), cm.display.lastWrapHeight != cm.display.wrapper.clientHeight && cm.refresh()) : state.timeout = setTimeout(check, state.delay);
    }
    state.timeout = setTimeout(check, state.delay);
    state.hurry = function() {
      clearTimeout(state.timeout);
      state.timeout = setTimeout(check, 50);
    };
    CodeMirror.on(window, "mouseup", state.hurry);
    CodeMirror.on(window, "keyup", state.hurry);
  }
  function stopListening(_cm, state) {
    clearTimeout(state.timeout);
    CodeMirror.off(window, "mouseup", state.hurry);
    CodeMirror.off(window, "keyup", state.hurry);
  }
  CodeMirror.defineOption("autoRefresh", !1, function(cm, val) {
    cm.state.autoRefresh && (stopListening(cm, cm.state.autoRefresh), cm.state.autoRefresh = null);
    val && 0 == cm.display.wrapper.offsetHeight && startListening(cm, cm.state.autoRefresh = {delay:val.delay || 250});
  });
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_555(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_555) : mod$jscomp$inline_555(CodeMirror);
//[third_party/javascript/codemirror4/addon/display/fullscreen.js]
function mod$jscomp$inline_564(CodeMirror) {
  CodeMirror.defineOption("fullScreen", !1, function(cm, val$jscomp$57_wrap$jscomp$inline_558_wrap, info$jscomp$inline_562_old) {
    info$jscomp$inline_562_old == CodeMirror.Init && (info$jscomp$inline_562_old = !1);
    !info$jscomp$inline_562_old != !val$jscomp$57_wrap$jscomp$inline_558_wrap && (val$jscomp$57_wrap$jscomp$inline_558_wrap ? (val$jscomp$57_wrap$jscomp$inline_558_wrap = cm.getWrapperElement(), cm.state.fullScreenRestore = {scrollTop:window.pageYOffset, scrollLeft:window.pageXOffset, width:val$jscomp$57_wrap$jscomp$inline_558_wrap.style.width, height:val$jscomp$57_wrap$jscomp$inline_558_wrap.style.height}, val$jscomp$57_wrap$jscomp$inline_558_wrap.style.width = "", val$jscomp$57_wrap$jscomp$inline_558_wrap.style.height = 
    "auto", val$jscomp$57_wrap$jscomp$inline_558_wrap.className += " CodeMirror-fullscreen", document.documentElement.style.overflow = "hidden") : (val$jscomp$57_wrap$jscomp$inline_558_wrap = cm.getWrapperElement(), val$jscomp$57_wrap$jscomp$inline_558_wrap.className = val$jscomp$57_wrap$jscomp$inline_558_wrap.className.replace(/\s*CodeMirror-fullscreen\b/, ""), document.documentElement.style.overflow = "", info$jscomp$inline_562_old = cm.state.fullScreenRestore, val$jscomp$57_wrap$jscomp$inline_558_wrap.style.width = 
    info$jscomp$inline_562_old.width, val$jscomp$57_wrap$jscomp$inline_558_wrap.style.height = info$jscomp$inline_562_old.height, window.scrollTo(info$jscomp$inline_562_old.scrollLeft, info$jscomp$inline_562_old.scrollTop)), cm.refresh());
  });
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_564(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_564) : mod$jscomp$inline_564(CodeMirror);
//[third_party/javascript/codemirror4/addon/display/panel.js]
function mod$jscomp$inline_572(CodeMirror) {
  function Panel(cm, node, options, height) {
    this.cm = cm;
    this.node = node;
    this.options = options;
    this.height = height;
    this.cleared = !1;
  }
  function initPanels(cm) {
    var wrap = cm.getWrapperElement(), hasFocus$jscomp$1_style = window.getComputedStyle ? window.getComputedStyle(wrap) : wrap.currentStyle, height = parseInt(hasFocus$jscomp$1_style.height), info = cm.state.panels = {setHeight:wrap.style.height, panels:[], wrapper:document.createElement("div")};
    hasFocus$jscomp$1_style = cm.hasFocus();
    var scrollPos = cm.getScrollInfo();
    wrap.parentNode.insertBefore(info.wrapper, wrap);
    info.wrapper.appendChild(wrap);
    cm.scrollTo(scrollPos.left, scrollPos.top);
    hasFocus$jscomp$1_style && cm.focus();
    cm._setSize = cm.setSize;
    null != height && (cm.setSize = function(width, newHeight) {
      newHeight || (newHeight = info.wrapper.offsetHeight);
      info.setHeight = newHeight;
      if ("number" != typeof newHeight) {
        var editorheight_px = /^(\d+\.?\d*)px$/.exec(newHeight);
        editorheight_px ? newHeight = Number(editorheight_px[1]) : (info.wrapper.style.height = newHeight, newHeight = info.wrapper.offsetHeight);
      }
      editorheight_px = newHeight - info.panels.map(function(p) {
        return p.node.getBoundingClientRect().height;
      }).reduce(function(a, b) {
        return a + b;
      }, 0);
      cm._setSize(width, editorheight_px);
      height = newHeight;
    });
  }
  function isAtTop(cm, dom_sibling) {
    for (dom_sibling = dom_sibling.nextSibling; dom_sibling; dom_sibling = dom_sibling.nextSibling) {
      if (dom_sibling == cm.getWrapperElement()) {
        return !0;
      }
    }
    return !1;
  }
  CodeMirror.defineExtension("addPanel", function(node, options) {
    options = options || {};
    this.state.panels || initPanels(this);
    var info = this.state.panels, height$jscomp$40_wrapper = info.wrapper, cmWrapper_panel = this.getWrapperElement(), replace = options.replace instanceof Panel && !options.replace.cleared;
    options.after instanceof Panel && !options.after.cleared ? height$jscomp$40_wrapper.insertBefore(node, options.before.node.nextSibling) : options.before instanceof Panel && !options.before.cleared ? height$jscomp$40_wrapper.insertBefore(node, options.before.node) : replace ? (height$jscomp$40_wrapper.insertBefore(node, options.replace.node), options.replace.clear(!0)) : "bottom" == options.position ? height$jscomp$40_wrapper.appendChild(node) : "before-bottom" == options.position ? height$jscomp$40_wrapper.insertBefore(node, 
    cmWrapper_panel.nextSibling) : "after-top" == options.position ? height$jscomp$40_wrapper.insertBefore(node, cmWrapper_panel) : height$jscomp$40_wrapper.insertBefore(node, height$jscomp$40_wrapper.firstChild);
    height$jscomp$40_wrapper = options && options.height || node.offsetHeight;
    cmWrapper_panel = new Panel(this, node, options, height$jscomp$40_wrapper);
    info.panels.push(cmWrapper_panel);
    this.setSize();
    options.stable && isAtTop(this, node) && this.scrollTo(null, this.getScrollInfo().top + height$jscomp$40_wrapper);
    return cmWrapper_panel;
  });
  Panel.prototype.clear = function(cm) {
    if (!this.cleared) {
      this.cleared = !0;
      var info$jscomp$9_info = this.cm.state.panels;
      info$jscomp$9_info.panels.splice(info$jscomp$9_info.panels.indexOf(this), 1);
      this.cm.setSize();
      this.options.stable && isAtTop(this.cm, this.node) && this.cm.scrollTo(null, this.cm.getScrollInfo().top - this.height);
      info$jscomp$9_info.wrapper.removeChild(this.node);
      if (0 == info$jscomp$9_info.panels.length && !cm) {
        cm = this.cm;
        info$jscomp$9_info = cm.state.panels;
        cm.state.panels = null;
        var wrap = cm.getWrapperElement(), hasFocus = cm.hasFocus(), scrollPos = cm.getScrollInfo();
        info$jscomp$9_info.wrapper.parentNode.replaceChild(wrap, info$jscomp$9_info.wrapper);
        cm.scrollTo(scrollPos.left, scrollPos.top);
        hasFocus && cm.focus();
        wrap.style.height = info$jscomp$9_info.setHeight;
        cm.setSize = cm._setSize;
        cm.setSize();
      }
    }
  };
  Panel.prototype.changed = function() {
    this.height = this.node.getBoundingClientRect().height;
    this.cm.setSize();
  };
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_572(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_572) : mod$jscomp$inline_572(CodeMirror);
//[third_party/javascript/codemirror4/addon/display/placeholder.js]
function mod$jscomp$inline_574(CodeMirror) {
  function clearPlaceholder(cm) {
    cm.state.placeholder && (cm.state.placeholder.parentNode.removeChild(cm.state.placeholder), cm.state.placeholder = null);
  }
  function setPlaceholder(cm) {
    clearPlaceholder(cm);
    var elt = cm.state.placeholder = document.createElement("pre");
    elt.style.cssText = "height: 0; overflow: visible";
    elt.style.direction = cm.getOption("direction");
    elt.className = "CodeMirror-placeholder CodeMirror-line-like";
    var placeHolder = cm.getOption("placeholder");
    "string" == typeof placeHolder && (placeHolder = document.createTextNode(placeHolder));
    elt.appendChild(placeHolder);
    cm.display.lineSpace.insertBefore(elt, cm.display.lineSpace.firstChild);
  }
  function onComposition(cm) {
    setTimeout(function() {
      var empty_input = !1;
      1 == cm.lineCount() && (empty_input = cm.getInputField(), empty_input = "TEXTAREA" == empty_input.nodeName ? !cm.getLine(0).length : !/[^\u200b]/.test(empty_input.querySelector(".CodeMirror-line").textContent));
      empty_input ? setPlaceholder(cm) : clearPlaceholder(cm);
    }, 20);
  }
  function onBlur(cm) {
    isEmpty(cm) && setPlaceholder(cm);
  }
  function onChange(cm) {
    var wrapper = cm.getWrapperElement(), empty = isEmpty(cm);
    wrapper.className = wrapper.className.replace(" CodeMirror-empty", "") + (empty ? " CodeMirror-empty" : "");
    empty ? setPlaceholder(cm) : clearPlaceholder(cm);
  }
  function isEmpty(cm) {
    return 1 === cm.lineCount() && "" === cm.getLine(0);
  }
  CodeMirror.defineOption("placeholder", "", function(cm, val, old$jscomp$18_prev$jscomp$9_wrapper) {
    old$jscomp$18_prev$jscomp$9_wrapper = old$jscomp$18_prev$jscomp$9_wrapper && old$jscomp$18_prev$jscomp$9_wrapper != CodeMirror.Init;
    val && !old$jscomp$18_prev$jscomp$9_wrapper ? (cm.on("blur", onBlur), cm.on("change", onChange), cm.on("swapDoc", onChange), CodeMirror.on(cm.getInputField(), "compositionupdate", cm.state.placeholderCompose = function() {
      onComposition(cm);
    }), onChange(cm)) : !val && old$jscomp$18_prev$jscomp$9_wrapper && (cm.off("blur", onBlur), cm.off("change", onChange), cm.off("swapDoc", onChange), CodeMirror.off(cm.getInputField(), "compositionupdate", cm.state.placeholderCompose), clearPlaceholder(cm), old$jscomp$18_prev$jscomp$9_wrapper = cm.getWrapperElement(), old$jscomp$18_prev$jscomp$9_wrapper.className = old$jscomp$18_prev$jscomp$9_wrapper.className.replace(" CodeMirror-empty", ""));
    val && !cm.hasFocus() && onBlur(cm);
  });
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_574(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_574) : mod$jscomp$inline_574(CodeMirror);
//[third_party/javascript/codemirror4/addon/display/rulers.js]
function mod$jscomp$inline_576(CodeMirror) {
  function drawRulers(cm) {
    cm.state.rulerDiv.textContent = "";
    var val = cm.getOption("rulers"), cw = cm.defaultCharWidth(), left = cm.charCoords(CodeMirror.Pos(cm.firstLine(), 0), "div").left;
    cm.state.rulerDiv.style.minHeight = cm.display.scroller.offsetHeight + 30 + "px";
    for (var i = 0; i < val.length; i++) {
      var elt = document.createElement("div");
      elt.className = "CodeMirror-ruler";
      var conf = val[i];
      if ("number" == typeof conf) {
        var col = conf;
      } else {
        col = conf.column, conf.className && (elt.className += " " + conf.className), conf.color && (elt.style.borderColor = conf.color), conf.lineStyle && (elt.style.borderLeftStyle = conf.lineStyle), conf.width && (elt.style.borderLeftWidth = conf.width);
      }
      elt.style.left = left + col * cw + "px";
      cm.state.rulerDiv.appendChild(elt);
    }
  }
  CodeMirror.defineOption("rulers", !1, function(cm, val) {
    cm.state.rulerDiv && (cm.state.rulerDiv.parentElement.removeChild(cm.state.rulerDiv), cm.state.rulerDiv = null, cm.off("refresh", drawRulers));
    val && val.length && (cm.state.rulerDiv = cm.display.lineSpace.parentElement.insertBefore(document.createElement("div"), cm.display.lineSpace), cm.state.rulerDiv.className = "CodeMirror-rulers", drawRulers(cm), cm.on("refresh", drawRulers));
  });
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_576(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_576) : mod$jscomp$inline_576(CodeMirror);
//[third_party/javascript/codemirror4/addon/mode/multiplex.js]
function mod$jscomp$inline_578(CodeMirror) {
  CodeMirror.multiplexingMode = function(outer) {
    function indexOf(string, m$jscomp$14_pattern, found$jscomp$25_from, returnEnd) {
      return "string" == typeof m$jscomp$14_pattern ? (found$jscomp$25_from = string.indexOf(m$jscomp$14_pattern, found$jscomp$25_from), returnEnd && -1 < found$jscomp$25_from ? found$jscomp$25_from + m$jscomp$14_pattern.length : found$jscomp$25_from) : (m$jscomp$14_pattern = m$jscomp$14_pattern.exec(found$jscomp$25_from ? string.slice(found$jscomp$25_from) : string)) ? m$jscomp$14_pattern.index + found$jscomp$25_from + (returnEnd ? m$jscomp$14_pattern[0].length : 0) : -1;
    }
    var others = Array.prototype.slice.call(arguments, 1);
    return {startState:function() {
      return {outer:CodeMirror.startState(outer), innerActive:null, inner:null, startingInner:!1};
    }, copyState:function(state) {
      return {outer:CodeMirror.copyState(outer, state.outer), innerActive:state.innerActive, inner:state.innerActive && CodeMirror.copyState(state.innerActive.mode, state.inner), startingInner:state.startingInner};
    }, token:function(outerIndent_stream, outerToken_state) {
      if (outerToken_state.innerActive) {
        var curInner_other = outerToken_state.innerActive;
        oldContent_possibleOuterIndent = outerIndent_stream.string;
        if (!curInner_other.close && outerIndent_stream.sol()) {
          return outerToken_state.innerActive = outerToken_state.inner = null, this.token(outerIndent_stream, outerToken_state);
        }
        found = curInner_other.close && !outerToken_state.startingInner ? indexOf(oldContent_possibleOuterIndent, curInner_other.close, outerIndent_stream.pos, curInner_other.parseDelimiters) : -1;
        if (found == outerIndent_stream.pos && !curInner_other.parseDelimiters) {
          return outerIndent_stream.match(curInner_other.close), outerToken_state.innerActive = outerToken_state.inner = null, curInner_other.delimStyle && curInner_other.delimStyle + " " + curInner_other.delimStyle + "-close";
        }
        -1 < found && (outerIndent_stream.string = oldContent_possibleOuterIndent.slice(0, found));
        var cutOff_innerToken = curInner_other.mode.token(outerIndent_stream, outerToken_state.inner);
        -1 < found ? outerIndent_stream.string = oldContent_possibleOuterIndent : outerIndent_stream.pos > outerIndent_stream.start && (outerToken_state.startingInner = !1);
        found == outerIndent_stream.pos && curInner_other.parseDelimiters && (outerToken_state.innerActive = outerToken_state.inner = null);
        curInner_other.innerStyle && (cutOff_innerToken ? cutOff_innerToken = cutOff_innerToken + " " + curInner_other.innerStyle : cutOff_innerToken = curInner_other.innerStyle);
        return cutOff_innerToken;
      }
      cutOff_innerToken = Infinity;
      for (var oldContent_possibleOuterIndent = outerIndent_stream.string, i = 0; i < others.length; ++i) {
        curInner_other = others[i];
        var found = indexOf(oldContent_possibleOuterIndent, curInner_other.open, outerIndent_stream.pos);
        if (found == outerIndent_stream.pos) {
          return curInner_other.parseDelimiters || outerIndent_stream.match(curInner_other.open), outerToken_state.startingInner = !!curInner_other.parseDelimiters, outerToken_state.innerActive = curInner_other, outerIndent_stream = 0, outer.indent && (oldContent_possibleOuterIndent = outer.indent(outerToken_state.outer, "", ""), oldContent_possibleOuterIndent !== CodeMirror.Pass && (outerIndent_stream = oldContent_possibleOuterIndent)), outerToken_state.inner = CodeMirror.startState(curInner_other.mode, 
          outerIndent_stream), curInner_other.delimStyle && curInner_other.delimStyle + " " + curInner_other.delimStyle + "-open";
        }
        -1 != found && found < cutOff_innerToken && (cutOff_innerToken = found);
      }
      Infinity != cutOff_innerToken && (outerIndent_stream.string = oldContent_possibleOuterIndent.slice(0, cutOff_innerToken));
      outerToken_state = outer.token(outerIndent_stream, outerToken_state.outer);
      Infinity != cutOff_innerToken && (outerIndent_stream.string = oldContent_possibleOuterIndent);
      return outerToken_state;
    }, indent:function(state, textAfter, line) {
      var mode = state.innerActive ? state.innerActive.mode : outer;
      return mode.indent ? mode.indent(state.innerActive ? state.inner : state.outer, textAfter, line) : CodeMirror.Pass;
    }, blankLine:function(state) {
      var mode = state.innerActive ? state.innerActive.mode : outer;
      mode.blankLine && mode.blankLine(state.innerActive ? state.inner : state.outer);
      if (state.innerActive) {
        "\n" === state.innerActive.close && (state.innerActive = state.inner = null);
      } else {
        for (var i = 0; i < others.length; ++i) {
          var other = others[i];
          "\n" === other.open && (state.innerActive = other, state.inner = CodeMirror.startState(other.mode, mode.indent ? mode.indent(state.outer, "", "") : 0));
        }
      }
    }, electricChars:outer.electricChars, innerMode:function(state) {
      return state.inner ? {state:state.inner, mode:state.innerActive.mode} : {state:state.outer, mode:outer};
    }};
  };
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_578(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_578) : mod$jscomp$inline_578(CodeMirror);
//[third_party/javascript/codemirror4/mode/htmlembedded/htmlembedded.js]
function mod$jscomp$inline_580(CodeMirror) {
  CodeMirror.defineMode("htmlembedded", function(config, parserConfig) {
    var closeComment = parserConfig.closeComment || "--%>";
    return CodeMirror.multiplexingMode(CodeMirror.getMode(config, "htmlmixed"), {open:parserConfig.openComment || "<%--", close:closeComment, delimStyle:"comment", mode:{token:function(stream) {
      stream.skipTo(closeComment) || stream.skipToEnd();
      return "comment";
    }}}, {open:parserConfig.open || parserConfig.scriptStartRegex || "<%", close:parserConfig.close || parserConfig.scriptEndRegex || "%>", mode:CodeMirror.getMode(config, parserConfig.scriptingModeSpec)});
  }, "htmlmixed");
  CodeMirror.defineMIME("application/x-ejs", {name:"htmlembedded", scriptingModeSpec:"javascript"});
  CodeMirror.defineMIME("application/x-aspx", {name:"htmlembedded", scriptingModeSpec:"text/x-csharp"});
  CodeMirror.defineMIME("application/x-jsp", {name:"htmlembedded", scriptingModeSpec:"text/x-java"});
  CodeMirror.defineMIME("application/x-erb", {name:"htmlembedded", scriptingModeSpec:"ruby"});
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_580(require("../../lib/codemirror"), require("../htmlmixed/htmlmixed"), require("../../addon/mode/multiplex")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror", "../htmlmixed/htmlmixed", "../../addon/mode/multiplex"], mod$jscomp$inline_580) : mod$jscomp$inline_580(CodeMirror);
//[third_party/javascript/codemirror4/mode/htmlmixed/htmlmixed.js]
function mod$jscomp$inline_592(CodeMirror) {
  function addTags(from, to) {
    for (var tag in from) {
      for (var dest = to[tag] || (to[tag] = []), source = from[tag], i = source.length - 1; 0 <= i; i--) {
        dest.unshift(source[i]);
      }
    }
  }
  function findMatchingMode(tagInfo, tagText) {
    for (var i = 0; i < tagInfo.length; i++) {
      var spec = tagInfo[i], JSCompiler_temp$jscomp$72_JSCompiler_temp_const;
      if (!(JSCompiler_temp$jscomp$72_JSCompiler_temp_const = !spec[0])) {
        JSCompiler_temp$jscomp$72_JSCompiler_temp_const = spec[1];
        var JSCompiler_temp_const = JSCompiler_temp$jscomp$72_JSCompiler_temp_const.test, JSCompiler_temp_const$jscomp$640_match = tagText, JSCompiler_temp_const$jscomp$0 = JSCompiler_temp_const$jscomp$640_match.match;
        var JSCompiler_inline_result$jscomp$641_attr = spec[0];
        var regexp = attrRegexpCache[JSCompiler_inline_result$jscomp$641_attr];
        JSCompiler_inline_result$jscomp$641_attr = regexp ? regexp : attrRegexpCache[JSCompiler_inline_result$jscomp$641_attr] = new RegExp("\\s+" + JSCompiler_inline_result$jscomp$641_attr + "\\s*=\\s*('|\")?([^'\"]+)('|\")?\\s*");
        JSCompiler_temp_const$jscomp$640_match = JSCompiler_temp_const$jscomp$0.call(JSCompiler_temp_const$jscomp$640_match, JSCompiler_inline_result$jscomp$641_attr);
        JSCompiler_temp$jscomp$72_JSCompiler_temp_const = JSCompiler_temp_const.call(JSCompiler_temp$jscomp$72_JSCompiler_temp_const, JSCompiler_temp_const$jscomp$640_match ? /^\s*(.*?)\s*$/.exec(JSCompiler_temp_const$jscomp$640_match[2])[1] : "");
      }
      if (JSCompiler_temp$jscomp$72_JSCompiler_temp_const) {
        return spec[2];
      }
    }
  }
  var defaultTags = {script:[["lang", /(javascript|babel)/i, "javascript"], ["type", /^(?:text|application)\/(?:x-)?(?:java|ecma)script$|^module$|^$/i, "javascript"], ["type", /./, "text/plain"], [null, null, "javascript"]], style:[["lang", /^css$/i, "css"], ["type", /^(text\/)?(x-)?(stylesheet|css)$/i, "css"], ["type", /./, "text/plain"], [null, null, "css"]]}, attrRegexpCache = {};
  CodeMirror.defineMode("htmlmixed", function(config, configScript_parserConfig) {
    function html(mode$jscomp$47_modeSpec_stream, state) {
      var style = htmlMode.token(mode$jscomp$47_modeSpec_stream, state.htmlState), inTag_tag = /\btag\b/.test(style), tagName;
      if (inTag_tag && !/[<>\s\/]/.test(mode$jscomp$47_modeSpec_stream.current()) && (tagName = state.htmlState.tagName && state.htmlState.tagName.toLowerCase()) && tags.hasOwnProperty(tagName)) {
        state.inTag = tagName + " ";
      } else if (state.inTag && inTag_tag && />$/.test(mode$jscomp$47_modeSpec_stream.current())) {
        inTag_tag = /^([\S]+) (.*)/.exec(state.inTag);
        state.inTag = null;
        mode$jscomp$47_modeSpec_stream = ">" == mode$jscomp$47_modeSpec_stream.current() && findMatchingMode(tags[inTag_tag[1]], inTag_tag[2]);
        mode$jscomp$47_modeSpec_stream = CodeMirror.getMode(config, mode$jscomp$47_modeSpec_stream);
        var endTagA = new RegExp("^</\\s*" + inTag_tag[1] + "\\s*>", "i"), endTag = new RegExp("</\\s*" + inTag_tag[1] + "\\s*>", "i");
        state.token = function(stream, state$jscomp$24_style) {
          if (stream.match(endTagA, !1)) {
            return state$jscomp$24_style.token = html, state$jscomp$24_style.localState = state$jscomp$24_style.localMode = null;
          }
          state$jscomp$24_style = state$jscomp$24_style.localMode.token(stream, state$jscomp$24_style.localState);
          var cur = stream.current(), close = cur.search(endTag);
          -1 < close ? stream.backUp(cur.length - close) : cur.match(/<\/?$/) && (stream.backUp(cur.length), stream.match(endTag, !1) || stream.match(cur));
          return state$jscomp$24_style;
        };
        state.localMode = mode$jscomp$47_modeSpec_stream;
        state.localState = CodeMirror.startState(mode$jscomp$47_modeSpec_stream, htmlMode.indent(state.htmlState, "", ""));
      } else {
        state.inTag && (state.inTag += mode$jscomp$47_modeSpec_stream.current(), mode$jscomp$47_modeSpec_stream.eol() && (state.inTag += " "));
      }
      return style;
    }
    var htmlMode = CodeMirror.getMode(config, {name:"xml", htmlMode:!0, multilineTagIndentFactor:configScript_parserConfig.multilineTagIndentFactor, multilineTagIndentPastTag:configScript_parserConfig.multilineTagIndentPastTag, allowMissingTagName:configScript_parserConfig.allowMissingTagName,}), tags = {}, configTags_i = configScript_parserConfig && configScript_parserConfig.tags;
    configScript_parserConfig = configScript_parserConfig && configScript_parserConfig.scriptTypes;
    addTags(defaultTags, tags);
    configTags_i && addTags(configTags_i, tags);
    if (configScript_parserConfig) {
      for (configTags_i = configScript_parserConfig.length - 1; 0 <= configTags_i; configTags_i--) {
        tags.script.unshift(["type", configScript_parserConfig[configTags_i].matches, configScript_parserConfig[configTags_i].mode]);
      }
    }
    return {startState:function() {
      var state = CodeMirror.startState(htmlMode);
      return {token:html, inTag:null, localMode:null, localState:null, htmlState:state};
    }, copyState:function(state) {
      var local;
      state.localState && (local = CodeMirror.copyState(state.localMode, state.localState));
      return {token:state.token, inTag:state.inTag, localMode:state.localMode, localState:local, htmlState:CodeMirror.copyState(htmlMode, state.htmlState)};
    }, token:function(stream, state) {
      return state.token(stream, state);
    }, indent:function(state, textAfter, line) {
      return !state.localMode || /^\s*<\//.test(textAfter) ? htmlMode.indent(state.htmlState, textAfter, line) : state.localMode.indent ? state.localMode.indent(state.localState, textAfter, line) : CodeMirror.Pass;
    }, innerMode:function(state) {
      return {state:state.localState || state.htmlState, mode:state.localMode || htmlMode};
    }};
  }, "xml", "javascript", "css");
  CodeMirror.defineMIME("text/html", "htmlmixed");
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_592(require("../../lib/codemirror"), require("../xml/xml"), require("../javascript/javascript"), require("../css/css")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror", "../xml/xml", "../javascript/javascript", "../css/css"], mod$jscomp$inline_592) : mod$jscomp$inline_592(CodeMirror);
//[third_party/javascript/codemirror4/mode/javascript/javascript.js]
function mod$jscomp$inline_607(CodeMirror) {
  CodeMirror.defineMode("javascript", function(config, parserConfig) {
    var JSCompiler_object_inline_state_0, JSCompiler_object_inline_stream_1, JSCompiler_object_inline_marked_2;
    function ret(tp, style, cont) {
      type$jscomp$0 = tp;
      content$jscomp$0 = cont;
      return style;
    }
    function tokenBase(kw$jscomp$1_stream, escaped$jscomp$inline_595_state) {
      var ch$jscomp$58_next$jscomp$inline_596_word = kw$jscomp$1_stream.next();
      if ('"' == ch$jscomp$58_next$jscomp$inline_596_word || "'" == ch$jscomp$58_next$jscomp$inline_596_word) {
        return escaped$jscomp$inline_595_state.tokenize = tokenString(ch$jscomp$58_next$jscomp$inline_596_word), escaped$jscomp$inline_595_state.tokenize(kw$jscomp$1_stream, escaped$jscomp$inline_595_state);
      }
      if ("." == ch$jscomp$58_next$jscomp$inline_596_word && kw$jscomp$1_stream.match(/^\d[\d_]*(?:[eE][+\-]?[\d_]+)?/)) {
        return ret("number", "number");
      }
      if ("." == ch$jscomp$58_next$jscomp$inline_596_word && kw$jscomp$1_stream.match("..")) {
        return ret("spread", "meta");
      }
      if (/[\[\]{}\(\),;:\.]/.test(ch$jscomp$58_next$jscomp$inline_596_word)) {
        return ret(ch$jscomp$58_next$jscomp$inline_596_word);
      }
      if ("=" == ch$jscomp$58_next$jscomp$inline_596_word && kw$jscomp$1_stream.eat(">")) {
        return ret("=>", "operator");
      }
      if ("0" == ch$jscomp$58_next$jscomp$inline_596_word && kw$jscomp$1_stream.match(/^(?:x[\dA-Fa-f_]+|o[0-7_]+|b[01_]+)n?/)) {
        return ret("number", "number");
      }
      if (/\d/.test(ch$jscomp$58_next$jscomp$inline_596_word)) {
        return kw$jscomp$1_stream.match(/^[\d_]*(?:n|(?:\.[\d_]*)?(?:[eE][+\-]?[\d_]+)?)?/), ret("number", "number");
      }
      if ("/" == ch$jscomp$58_next$jscomp$inline_596_word) {
        if (kw$jscomp$1_stream.eat("*")) {
          return escaped$jscomp$inline_595_state.tokenize = tokenComment, tokenComment(kw$jscomp$1_stream, escaped$jscomp$inline_595_state);
        }
        if (kw$jscomp$1_stream.eat("/")) {
          return kw$jscomp$1_stream.skipToEnd(), ret("comment", "comment");
        }
        if (expressionAllowed(kw$jscomp$1_stream, escaped$jscomp$inline_595_state, 1)) {
          for (var inSet = escaped$jscomp$inline_595_state = !1; null != (ch$jscomp$58_next$jscomp$inline_596_word = kw$jscomp$1_stream.next());) {
            if (!escaped$jscomp$inline_595_state) {
              if ("/" == ch$jscomp$58_next$jscomp$inline_596_word && !inSet) {
                break;
              }
              "[" == ch$jscomp$58_next$jscomp$inline_596_word ? inSet = !0 : inSet && "]" == ch$jscomp$58_next$jscomp$inline_596_word && (inSet = !1);
            }
            escaped$jscomp$inline_595_state = !escaped$jscomp$inline_595_state && "\\" == ch$jscomp$58_next$jscomp$inline_596_word;
          }
          kw$jscomp$1_stream.match(/^\b(([gimyus])(?![gimyus]*\2))+\b/);
          return ret("regexp", "string-2");
        }
        kw$jscomp$1_stream.eat("=");
        return ret("operator", "operator", kw$jscomp$1_stream.current());
      }
      if ("`" == ch$jscomp$58_next$jscomp$inline_596_word) {
        return escaped$jscomp$inline_595_state.tokenize = tokenQuasi, tokenQuasi(kw$jscomp$1_stream, escaped$jscomp$inline_595_state);
      }
      if ("#" == ch$jscomp$58_next$jscomp$inline_596_word && "!" == kw$jscomp$1_stream.peek()) {
        return kw$jscomp$1_stream.skipToEnd(), ret("meta", "meta");
      }
      if ("#" == ch$jscomp$58_next$jscomp$inline_596_word && kw$jscomp$1_stream.eatWhile(wordRE)) {
        return ret("variable", "property");
      }
      if ("<" == ch$jscomp$58_next$jscomp$inline_596_word && kw$jscomp$1_stream.match("!--") || "-" == ch$jscomp$58_next$jscomp$inline_596_word && kw$jscomp$1_stream.match("->") && !/\S/.test(kw$jscomp$1_stream.string.slice(0, kw$jscomp$1_stream.start))) {
        return kw$jscomp$1_stream.skipToEnd(), ret("comment", "comment");
      }
      if (isOperatorChar.test(ch$jscomp$58_next$jscomp$inline_596_word)) {
        return ">" == ch$jscomp$58_next$jscomp$inline_596_word && escaped$jscomp$inline_595_state.lexical && ">" == escaped$jscomp$inline_595_state.lexical.type || (kw$jscomp$1_stream.eat("=") ? "!" != ch$jscomp$58_next$jscomp$inline_596_word && "=" != ch$jscomp$58_next$jscomp$inline_596_word || kw$jscomp$1_stream.eat("=") : /[<>*+\-|&?]/.test(ch$jscomp$58_next$jscomp$inline_596_word) && (kw$jscomp$1_stream.eat(ch$jscomp$58_next$jscomp$inline_596_word), ">" == ch$jscomp$58_next$jscomp$inline_596_word && 
        kw$jscomp$1_stream.eat(ch$jscomp$58_next$jscomp$inline_596_word))), "?" == ch$jscomp$58_next$jscomp$inline_596_word && kw$jscomp$1_stream.eat(".") ? ret(".") : ret("operator", "operator", kw$jscomp$1_stream.current());
      }
      if (wordRE.test(ch$jscomp$58_next$jscomp$inline_596_word)) {
        kw$jscomp$1_stream.eatWhile(wordRE);
        ch$jscomp$58_next$jscomp$inline_596_word = kw$jscomp$1_stream.current();
        if ("." != escaped$jscomp$inline_595_state.lastType) {
          if (keywords.propertyIsEnumerable(ch$jscomp$58_next$jscomp$inline_596_word)) {
            return kw$jscomp$1_stream = keywords[ch$jscomp$58_next$jscomp$inline_596_word], ret(kw$jscomp$1_stream.type, kw$jscomp$1_stream.style, ch$jscomp$58_next$jscomp$inline_596_word);
          }
          if ("async" == ch$jscomp$58_next$jscomp$inline_596_word && kw$jscomp$1_stream.match(/^(\s|\/\*([^*]|\*(?!\/))*?\*\/)*[\[\(\w]/, !1)) {
            return ret("async", "keyword", ch$jscomp$58_next$jscomp$inline_596_word);
          }
        }
        return ret("variable", "variable", ch$jscomp$58_next$jscomp$inline_596_word);
      }
    }
    function tokenString(quote) {
      return function(stream, state) {
        var escaped = !1, next;
        if (jsonldMode && "@" == stream.peek() && stream.match(isJsonldKeyword)) {
          return state.tokenize = tokenBase, ret("jsonld-keyword", "meta");
        }
        for (; null != (next = stream.next()) && (next != quote || escaped);) {
          escaped = !escaped && "\\" == next;
        }
        escaped || (state.tokenize = tokenBase);
        return ret("string", "string");
      };
    }
    function tokenComment(stream, state) {
      for (var maybeEnd = !1, ch; ch = stream.next();) {
        if ("/" == ch && maybeEnd) {
          state.tokenize = tokenBase;
          break;
        }
        maybeEnd = "*" == ch;
      }
      return ret("comment", "comment");
    }
    function tokenQuasi(stream, state) {
      for (var escaped = !1, next; null != (next = stream.next());) {
        if (!escaped && ("`" == next || "$" == next && stream.eat("{"))) {
          state.tokenize = tokenBase;
          break;
        }
        escaped = !escaped && "\\" == next;
      }
      return ret("quasi", "string-2", stream.current());
    }
    function findFatArrow(stream, state) {
      state.fatArrowAt && (state.fatArrowAt = null);
      var arrow_pos = stream.string.indexOf("=>", stream.start);
      if (!(0 > arrow_pos)) {
        if (isTS) {
          var depth$jscomp$10_m = /:\s*(?:\w+(?:<[^>]*>|\[\])?|\{[^}]*\})\s*$/.exec(stream.string.slice(stream.start, arrow_pos));
          depth$jscomp$10_m && (arrow_pos = depth$jscomp$10_m.index);
        }
        depth$jscomp$10_m = 0;
        var sawSomething = !1;
        for (--arrow_pos; 0 <= arrow_pos; --arrow_pos) {
          var ch = stream.string.charAt(arrow_pos), bracket = "([{}])".indexOf(ch);
          if (0 <= bracket && 3 > bracket) {
            if (!depth$jscomp$10_m) {
              ++arrow_pos;
              break;
            }
            if (0 == --depth$jscomp$10_m) {
              "(" == ch && (sawSomething = !0);
              break;
            }
          } else if (3 <= bracket && 6 > bracket) {
            ++depth$jscomp$10_m;
          } else if (wordRE.test(ch)) {
            sawSomething = !0;
          } else if (/["'\/`]/.test(ch)) {
            for (;; --arrow_pos) {
              if (0 == arrow_pos) {
                return;
              }
              if (stream.string.charAt(arrow_pos - 1) == ch && "\\" != stream.string.charAt(arrow_pos - 2)) {
                arrow_pos--;
                break;
              }
            }
          } else if (sawSomething && !depth$jscomp$10_m) {
            ++arrow_pos;
            break;
          }
        }
        sawSomething && !depth$jscomp$10_m && (state.fatArrowAt = arrow_pos);
      }
    }
    function JSLexical(indented, column, type, align, prev, info) {
      this.indented = indented;
      this.column = column;
      this.type = type;
      this.prev = prev;
      this.info = info;
      null != align && (this.align = align);
    }
    function parseJS(cx$jscomp$inline_602_state, JSCompiler_temp$jscomp$76_style, JSCompiler_temp$jscomp$77_type$jscomp$210_v, content, stream) {
      var cc = cx$jscomp$inline_602_state.cc;
      JSCompiler_object_inline_state_0 = cx$jscomp$inline_602_state;
      JSCompiler_object_inline_stream_1 = stream;
      JSCompiler_object_inline_marked_2 = null;
      JSCompiler_object_inline_cc_3 = cc;
      JSCompiler_object_inline_style_4 = JSCompiler_temp$jscomp$76_style;
      cx$jscomp$inline_602_state.lexical.hasOwnProperty("align") || (cx$jscomp$inline_602_state.lexical.align = !0);
      for (;;) {
        if ((cc.length ? cc.pop() : jsonMode ? expression : statement)(JSCompiler_temp$jscomp$77_type$jscomp$210_v, content)) {
          for (; cc.length && cc[cc.length - 1].lex;) {
            cc.pop()();
          }
          if (JSCompiler_object_inline_marked_2) {
            JSCompiler_temp$jscomp$76_style = JSCompiler_object_inline_marked_2;
          } else {
            if (JSCompiler_temp$jscomp$77_type$jscomp$210_v = "variable" == JSCompiler_temp$jscomp$77_type$jscomp$210_v) {
              a: {
                if (trackScope) {
                  for (JSCompiler_temp$jscomp$77_type$jscomp$210_v = cx$jscomp$inline_602_state.localVars; JSCompiler_temp$jscomp$77_type$jscomp$210_v; JSCompiler_temp$jscomp$77_type$jscomp$210_v = JSCompiler_temp$jscomp$77_type$jscomp$210_v.next) {
                    if (JSCompiler_temp$jscomp$77_type$jscomp$210_v.name == content) {
                      JSCompiler_temp$jscomp$77_type$jscomp$210_v = !0;
                      break a;
                    }
                  }
                  for (cx$jscomp$inline_602_state = cx$jscomp$inline_602_state.context; cx$jscomp$inline_602_state; cx$jscomp$inline_602_state = cx$jscomp$inline_602_state.prev) {
                    for (JSCompiler_temp$jscomp$77_type$jscomp$210_v = cx$jscomp$inline_602_state.vars; JSCompiler_temp$jscomp$77_type$jscomp$210_v; JSCompiler_temp$jscomp$77_type$jscomp$210_v = JSCompiler_temp$jscomp$77_type$jscomp$210_v.next) {
                      if (JSCompiler_temp$jscomp$77_type$jscomp$210_v.name == content) {
                        JSCompiler_temp$jscomp$77_type$jscomp$210_v = !0;
                        break a;
                      }
                    }
                  }
                  JSCompiler_temp$jscomp$77_type$jscomp$210_v = void 0;
                } else {
                  JSCompiler_temp$jscomp$77_type$jscomp$210_v = !1;
                }
              }
            }
            JSCompiler_temp$jscomp$76_style = JSCompiler_temp$jscomp$77_type$jscomp$210_v ? "variable-2" : JSCompiler_temp$jscomp$76_style;
          }
          return JSCompiler_temp$jscomp$76_style;
        }
      }
    }
    function pass() {
      for (var i = arguments.length - 1; 0 <= i; i--) {
        JSCompiler_object_inline_cc_3.push(arguments[i]);
      }
    }
    function cont() {
      pass.apply(null, arguments);
      return !0;
    }
    function inList(name, list$jscomp$1_v) {
      for (; list$jscomp$1_v; list$jscomp$1_v = list$jscomp$1_v.next) {
        if (list$jscomp$1_v.name == name) {
          return !0;
        }
      }
      return !1;
    }
    function register(varname) {
      var state = JSCompiler_object_inline_state_0;
      JSCompiler_object_inline_marked_2 = "def";
      if (trackScope) {
        if (state.context) {
          if ("var" == state.lexical.info && state.context && state.context.block) {
            var newContext = registerVarScoped(varname, state.context);
            if (null != newContext) {
              state.context = newContext;
              return;
            }
          } else if (!inList(varname, state.localVars)) {
            state.localVars = new Var(varname, state.localVars);
            return;
          }
        }
        parserConfig.globalVars && !inList(varname, state.globalVars) && (state.globalVars = new Var(varname, state.globalVars));
      }
    }
    function registerVarScoped(inner$jscomp$9_varname, context) {
      return context ? context.block ? (inner$jscomp$9_varname = registerVarScoped(inner$jscomp$9_varname, context.prev)) ? inner$jscomp$9_varname == context.prev ? context : new Context(inner$jscomp$9_varname, context.vars, !0) : null : inList(inner$jscomp$9_varname, context.vars) ? context : new Context(context.prev, new Var(inner$jscomp$9_varname, context.vars), !1) : null;
    }
    function isModifier(name) {
      return "public" == name || "private" == name || "protected" == name || "abstract" == name || "readonly" == name;
    }
    function Context(prev, vars, block) {
      this.prev = prev;
      this.vars = vars;
      this.block = block;
    }
    function Var(name, next) {
      this.name = name;
      this.next = next;
    }
    function pushcontext() {
      JSCompiler_object_inline_state_0.context = new Context(JSCompiler_object_inline_state_0.context, JSCompiler_object_inline_state_0.localVars, !1);
      JSCompiler_object_inline_state_0.localVars = defaultVars;
    }
    function pushblockcontext() {
      JSCompiler_object_inline_state_0.context = new Context(JSCompiler_object_inline_state_0.context, JSCompiler_object_inline_state_0.localVars, !0);
      JSCompiler_object_inline_state_0.localVars = null;
    }
    function popcontext() {
      JSCompiler_object_inline_state_0.localVars = JSCompiler_object_inline_state_0.context.vars;
      JSCompiler_object_inline_state_0.context = JSCompiler_object_inline_state_0.context.prev;
    }
    function pushlex(type, info) {
      function result() {
        var state = JSCompiler_object_inline_state_0, indent = state.indented;
        if ("stat" == state.lexical.type) {
          indent = state.lexical.indented;
        } else {
          for (var outer = state.lexical; outer && ")" == outer.type && outer.align; outer = outer.prev) {
            indent = outer.indented;
          }
        }
        state.lexical = new JSLexical(indent, JSCompiler_object_inline_stream_1.column(), type, null, state.lexical, info);
      }
      result.lex = !0;
      return result;
    }
    function poplex() {
      var state = JSCompiler_object_inline_state_0;
      state.lexical.prev && (")" == state.lexical.type && (state.indented = state.lexical.indented), state.lexical = state.lexical.prev);
    }
    function expect(wanted) {
      function exp(type) {
        return type == wanted ? cont() : ";" == wanted || "}" == type || ")" == type || "]" == type ? pass() : cont(exp);
      }
      return exp;
    }
    function statement(type, value) {
      return "var" == type ? cont(pushlex("vardef", value), vardef, expect(";"), poplex) : "keyword a" == type ? cont(pushlex("form"), parenExpr, statement, poplex) : "keyword b" == type ? cont(pushlex("form"), statement, poplex) : "keyword d" == type ? JSCompiler_object_inline_stream_1.match(/^\s*$/, !1) ? cont() : cont(pushlex("stat"), maybeexpression, expect(";"), poplex) : "debugger" == type ? cont(expect(";")) : "{" == type ? cont(pushlex("}"), pushblockcontext, block, poplex, popcontext) : 
      ";" == type ? cont() : "if" == type ? ("else" == JSCompiler_object_inline_state_0.lexical.info && JSCompiler_object_inline_state_0.cc[JSCompiler_object_inline_state_0.cc.length - 1] == poplex && JSCompiler_object_inline_state_0.cc.pop()(), cont(pushlex("form"), parenExpr, statement, poplex, maybeelse)) : "function" == type ? cont(functiondef) : "for" == type ? cont(pushlex("form"), pushblockcontext, forspec, statement, popcontext, poplex) : "class" == type || isTS && "interface" == value ? 
      (JSCompiler_object_inline_marked_2 = "keyword", cont(pushlex("form", "class" == type ? type : value), className, poplex)) : "variable" == type ? isTS && "declare" == value ? (JSCompiler_object_inline_marked_2 = "keyword", cont(statement)) : isTS && ("module" == value || "enum" == value || "type" == value) && JSCompiler_object_inline_stream_1.match(/^\s*\w/, !1) ? (JSCompiler_object_inline_marked_2 = "keyword", "enum" == value ? cont(enumdef) : "type" == value ? cont(typename, expect("operator"), 
      typeexpr, expect(";")) : cont(pushlex("form"), pattern, expect("{"), pushlex("}"), block, poplex, poplex)) : isTS && "namespace" == value ? (JSCompiler_object_inline_marked_2 = "keyword", cont(pushlex("form"), expression, statement, poplex)) : isTS && "abstract" == value ? (JSCompiler_object_inline_marked_2 = "keyword", cont(statement)) : cont(pushlex("stat"), maybelabel) : "switch" == type ? cont(pushlex("form"), parenExpr, expect("{"), pushlex("}", "switch"), pushblockcontext, block, poplex, 
      poplex, popcontext) : "case" == type ? cont(expression, expect(":")) : "default" == type ? cont(expect(":")) : "catch" == type ? cont(pushlex("form"), pushcontext, maybeCatchBinding, statement, poplex, popcontext) : "export" == type ? cont(pushlex("stat"), afterExport, poplex) : "import" == type ? cont(pushlex("stat"), afterImport, poplex) : "async" == type ? cont(statement) : "@" == value ? cont(expression, statement) : pass(pushlex("stat"), expression, expect(";"), poplex);
    }
    function maybeCatchBinding(type) {
      if ("(" == type) {
        return cont(funarg, expect(")"));
      }
    }
    function expression(type, value) {
      return expressionInner(type, value, !1);
    }
    function expressionNoComma(type, value) {
      return expressionInner(type, value, !0);
    }
    function parenExpr(type) {
      return "(" != type ? pass() : cont(pushlex(")"), maybeexpression, expect(")"), poplex);
    }
    function expressionInner(type, value, noComma) {
      if (JSCompiler_object_inline_state_0.fatArrowAt == JSCompiler_object_inline_stream_1.start) {
        var body = noComma ? arrowBodyNoComma : arrowBody;
        if ("(" == type) {
          return cont(pushcontext, pushlex(")"), commasep(funarg, ")"), poplex, expect("=>"), body, popcontext);
        }
        if ("variable" == type) {
          return pass(pushcontext, pattern, expect("=>"), body, popcontext);
        }
      }
      body = noComma ? maybeoperatorNoComma : maybeoperatorComma;
      return atomicTypes.hasOwnProperty(type) ? cont(body) : "function" == type ? cont(functiondef, body) : "class" == type || isTS && "interface" == value ? (JSCompiler_object_inline_marked_2 = "keyword", cont(pushlex("form"), classExpression, poplex)) : "keyword c" == type || "async" == type ? cont(noComma ? expressionNoComma : expression) : "(" == type ? cont(pushlex(")"), maybeexpression, expect(")"), poplex, body) : "operator" == type || "spread" == type ? cont(noComma ? expressionNoComma : 
      expression) : "[" == type ? cont(pushlex("]"), arrayLiteral, poplex, body) : "{" == type ? contCommasep(objprop, "}", null, body) : "quasi" == type ? pass(quasi, body) : "new" == type ? cont(maybeTarget(noComma)) : cont();
    }
    function maybeexpression(type) {
      return type.match(/[;\}\)\],]/) ? pass() : pass(expression);
    }
    function maybeoperatorComma(type, value) {
      return "," == type ? cont(maybeexpression) : maybeoperatorNoComma(type, value, !1);
    }
    function maybeoperatorNoComma(type, value, noComma) {
      var me = 0 == noComma ? maybeoperatorComma : maybeoperatorNoComma, expr = 0 == noComma ? expression : expressionNoComma;
      if ("=>" == type) {
        return cont(pushcontext, noComma ? arrowBodyNoComma : arrowBody, popcontext);
      }
      if ("operator" == type) {
        return /\+\+|--/.test(value) || isTS && "!" == value ? cont(me) : isTS && "<" == value && JSCompiler_object_inline_stream_1.match(/^([^<>]|<[^<>]*>)*>\s*\(/, !1) ? cont(pushlex(">"), commasep(typeexpr, ">"), poplex, me) : "?" == value ? cont(expression, expect(":"), expr) : cont(expr);
      }
      if ("quasi" == type) {
        return pass(quasi, me);
      }
      if (";" != type) {
        if ("(" == type) {
          return contCommasep(expressionNoComma, ")", "call", me);
        }
        if ("." == type) {
          return cont(property, me);
        }
        if ("[" == type) {
          return cont(pushlex("]"), maybeexpression, expect("]"), poplex, me);
        }
        if (isTS && "as" == value) {
          return JSCompiler_object_inline_marked_2 = "keyword", cont(typeexpr, me);
        }
        if ("regexp" == type) {
          return JSCompiler_object_inline_state_0.lastType = JSCompiler_object_inline_marked_2 = "operator", JSCompiler_object_inline_stream_1.backUp(JSCompiler_object_inline_stream_1.pos - JSCompiler_object_inline_stream_1.start - 1), cont(expr);
        }
      }
    }
    function quasi(type, value) {
      return "quasi" != type ? pass() : "${" != value.slice(value.length - 2) ? cont(quasi) : cont(maybeexpression, continueQuasi);
    }
    function continueQuasi(type) {
      if ("}" == type) {
        return JSCompiler_object_inline_marked_2 = "string-2", JSCompiler_object_inline_state_0.tokenize = tokenQuasi, cont(quasi);
      }
    }
    function arrowBody(type) {
      findFatArrow(JSCompiler_object_inline_stream_1, JSCompiler_object_inline_state_0);
      return pass("{" == type ? statement : expression);
    }
    function arrowBodyNoComma(type) {
      findFatArrow(JSCompiler_object_inline_stream_1, JSCompiler_object_inline_state_0);
      return pass("{" == type ? statement : expressionNoComma);
    }
    function maybeTarget(noComma) {
      return function(type) {
        return "." == type ? cont(noComma ? targetNoComma : target) : "variable" == type && isTS ? cont(maybeTypeArgs, noComma ? maybeoperatorNoComma : maybeoperatorComma) : pass(noComma ? expressionNoComma : expression);
      };
    }
    function target(_, value) {
      if ("target" == value) {
        return JSCompiler_object_inline_marked_2 = "keyword", cont(maybeoperatorComma);
      }
    }
    function targetNoComma(_, value) {
      if ("target" == value) {
        return JSCompiler_object_inline_marked_2 = "keyword", cont(maybeoperatorNoComma);
      }
    }
    function maybelabel(type) {
      return ":" == type ? cont(poplex, statement) : pass(maybeoperatorComma, expect(";"), poplex);
    }
    function property(type) {
      if ("variable" == type) {
        return JSCompiler_object_inline_marked_2 = "property", cont();
      }
    }
    function objprop(type, value) {
      if ("async" == type) {
        return JSCompiler_object_inline_marked_2 = "property", cont(objprop);
      }
      if ("variable" == type || "keyword" == JSCompiler_object_inline_style_4) {
        JSCompiler_object_inline_marked_2 = "property";
        if ("get" == value || "set" == value) {
          return cont(getterSetter);
        }
        var m;
        isTS && JSCompiler_object_inline_state_0.fatArrowAt == JSCompiler_object_inline_stream_1.start && (m = JSCompiler_object_inline_stream_1.match(/^\s*:\s*/, !1)) && (JSCompiler_object_inline_state_0.fatArrowAt = JSCompiler_object_inline_stream_1.pos + m[0].length);
        return cont(afterprop);
      }
      if ("number" == type || "string" == type) {
        return JSCompiler_object_inline_marked_2 = jsonldMode ? "property" : JSCompiler_object_inline_style_4 + " property", cont(afterprop);
      }
      if ("jsonld-keyword" == type) {
        return cont(afterprop);
      }
      if (isTS && isModifier(value)) {
        return JSCompiler_object_inline_marked_2 = "keyword", cont(objprop);
      }
      if ("[" == type) {
        return cont(expression, maybetype, expect("]"), afterprop);
      }
      if ("spread" == type) {
        return cont(expressionNoComma, afterprop);
      }
      if ("*" == value) {
        return JSCompiler_object_inline_marked_2 = "keyword", cont(objprop);
      }
      if (":" == type) {
        return pass(afterprop);
      }
    }
    function getterSetter(type) {
      if ("variable" != type) {
        return pass(afterprop);
      }
      JSCompiler_object_inline_marked_2 = "property";
      return cont(functiondef);
    }
    function afterprop(type) {
      if (":" == type) {
        return cont(expressionNoComma);
      }
      if ("(" == type) {
        return pass(functiondef);
      }
    }
    function commasep(what, end, sep) {
      function proceed(lex_type, value$jscomp$0) {
        return (sep ? -1 < sep.indexOf(lex_type) : "," == lex_type) ? (lex_type = JSCompiler_object_inline_state_0.lexical, "call" == lex_type.info && (lex_type.pos = (lex_type.pos || 0) + 1), cont(function(type, value) {
          return type == end || value == end ? pass() : pass(what);
        }, proceed)) : lex_type == end || value$jscomp$0 == end ? cont() : sep && -1 < sep.indexOf(";") ? pass(what) : cont(expect(end));
      }
      return function(type, value) {
        return type == end || value == end ? cont() : pass(what, proceed);
      };
    }
    function contCommasep(what, end, info) {
      for (var i = 3; i < arguments.length; i++) {
        JSCompiler_object_inline_cc_3.push(arguments[i]);
      }
      return cont(pushlex(end, info), commasep(what, end), poplex);
    }
    function block(type) {
      return "}" == type ? cont() : pass(statement, block);
    }
    function maybetype(type, value) {
      if (isTS) {
        if (":" == type) {
          return cont(typeexpr);
        }
        if ("?" == value) {
          return cont(maybetype);
        }
      }
    }
    function maybetypeOrIn(type, value) {
      if (isTS && (":" == type || "in" == value)) {
        return cont(typeexpr);
      }
    }
    function mayberettype(type) {
      if (isTS && ":" == type) {
        return JSCompiler_object_inline_stream_1.match(/^\s*\w+\s+is\b/, !1) ? cont(expression, isKW, typeexpr) : cont(typeexpr);
      }
    }
    function isKW(_, value) {
      if ("is" == value) {
        return JSCompiler_object_inline_marked_2 = "keyword", cont();
      }
    }
    function typeexpr(type, value) {
      if ("keyof" == value || "typeof" == value || "infer" == value || "readonly" == value) {
        return JSCompiler_object_inline_marked_2 = "keyword", cont("typeof" == value ? expressionNoComma : typeexpr);
      }
      if ("variable" == type || "void" == value) {
        return JSCompiler_object_inline_marked_2 = "type", cont(afterType);
      }
      if ("|" == value || "&" == value) {
        return cont(typeexpr);
      }
      if ("string" == type || "number" == type || "atom" == type) {
        return cont(afterType);
      }
      if ("[" == type) {
        return cont(pushlex("]"), commasep(typeexpr, "]", ","), poplex, afterType);
      }
      if ("{" == type) {
        return cont(pushlex("}"), typeprops, poplex, afterType);
      }
      if ("(" == type) {
        return cont(commasep(typearg, ")"), maybeReturnType, afterType);
      }
      if ("<" == type) {
        return cont(commasep(typeexpr, ">"), typeexpr);
      }
      if ("quasi" == type) {
        return pass(quasiType, afterType);
      }
    }
    function maybeReturnType(type) {
      if ("=>" == type) {
        return cont(typeexpr);
      }
    }
    function typeprops(type) {
      return type.match(/[\}\)\]]/) ? cont() : "," == type || ";" == type ? cont(typeprops) : pass(typeprop, typeprops);
    }
    function typeprop(type, value) {
      if ("variable" == type || "keyword" == JSCompiler_object_inline_style_4) {
        return JSCompiler_object_inline_marked_2 = "property", cont(typeprop);
      }
      if ("?" == value || "number" == type || "string" == type) {
        return cont(typeprop);
      }
      if (":" == type) {
        return cont(typeexpr);
      }
      if ("[" == type) {
        return cont(expect("variable"), maybetypeOrIn, expect("]"), typeprop);
      }
      if ("(" == type) {
        return pass(functiondecl, typeprop);
      }
      if (!type.match(/[;\}\)\],]/)) {
        return cont();
      }
    }
    function quasiType(type, value) {
      return "quasi" != type ? pass() : "${" != value.slice(value.length - 2) ? cont(quasiType) : cont(typeexpr, continueQuasiType);
    }
    function continueQuasiType(type) {
      if ("}" == type) {
        return JSCompiler_object_inline_marked_2 = "string-2", JSCompiler_object_inline_state_0.tokenize = tokenQuasi, cont(quasiType);
      }
    }
    function typearg(type, value) {
      return "variable" == type && JSCompiler_object_inline_stream_1.match(/^\s*[?:]/, !1) || "?" == value ? cont(typearg) : ":" == type ? cont(typeexpr) : "spread" == type ? cont(typearg) : pass(typeexpr);
    }
    function afterType(type, value) {
      if ("<" == value) {
        return cont(pushlex(">"), commasep(typeexpr, ">"), poplex, afterType);
      }
      if ("|" == value || "." == type || "&" == value) {
        return cont(typeexpr);
      }
      if ("[" == type) {
        return cont(typeexpr, expect("]"), afterType);
      }
      if ("extends" == value || "implements" == value) {
        return JSCompiler_object_inline_marked_2 = "keyword", cont(typeexpr);
      }
      if ("?" == value) {
        return cont(typeexpr, expect(":"), typeexpr);
      }
    }
    function maybeTypeArgs(_, value) {
      if ("<" == value) {
        return cont(pushlex(">"), commasep(typeexpr, ">"), poplex, afterType);
      }
    }
    function typeparam() {
      return pass(typeexpr, maybeTypeDefault);
    }
    function maybeTypeDefault(_, value) {
      if ("=" == value) {
        return cont(typeexpr);
      }
    }
    function vardef(_, value) {
      return "enum" == value ? (JSCompiler_object_inline_marked_2 = "keyword", cont(enumdef)) : pass(pattern, maybetype, maybeAssign, vardefCont);
    }
    function pattern(type, value) {
      if (isTS && isModifier(value)) {
        return JSCompiler_object_inline_marked_2 = "keyword", cont(pattern);
      }
      if ("variable" == type) {
        return register(value), cont();
      }
      if ("spread" == type) {
        return cont(pattern);
      }
      if ("[" == type) {
        return contCommasep(eltpattern, "]");
      }
      if ("{" == type) {
        return contCommasep(proppattern, "}");
      }
    }
    function proppattern(type, value) {
      if ("variable" == type && !JSCompiler_object_inline_stream_1.match(/^\s*:/, !1)) {
        return register(value), cont(maybeAssign);
      }
      "variable" == type && (JSCompiler_object_inline_marked_2 = "property");
      return "spread" == type ? cont(pattern) : "}" == type ? pass() : "[" == type ? cont(expression, expect("]"), expect(":"), proppattern) : cont(expect(":"), pattern, maybeAssign);
    }
    function eltpattern() {
      return pass(pattern, maybeAssign);
    }
    function maybeAssign(_type, value) {
      if ("=" == value) {
        return cont(expressionNoComma);
      }
    }
    function vardefCont(type) {
      if ("," == type) {
        return cont(vardef);
      }
    }
    function maybeelse(type, value) {
      if ("keyword b" == type && "else" == value) {
        return cont(pushlex("form", "else"), statement, poplex);
      }
    }
    function forspec(type, value) {
      if ("await" == value) {
        return cont(forspec);
      }
      if ("(" == type) {
        return cont(pushlex(")"), forspec1, poplex);
      }
    }
    function forspec1(type) {
      return "var" == type ? cont(vardef, forspec2) : "variable" == type ? cont(forspec2) : pass(forspec2);
    }
    function forspec2(type, value) {
      return ")" == type ? cont() : ";" == type ? cont(forspec2) : "in" == value || "of" == value ? (JSCompiler_object_inline_marked_2 = "keyword", cont(expression, forspec2)) : pass(expression, forspec2);
    }
    function functiondef(type, value) {
      if ("*" == value) {
        return JSCompiler_object_inline_marked_2 = "keyword", cont(functiondef);
      }
      if ("variable" == type) {
        return register(value), cont(functiondef);
      }
      if ("(" == type) {
        return cont(pushcontext, pushlex(")"), commasep(funarg, ")"), poplex, mayberettype, statement, popcontext);
      }
      if (isTS && "<" == value) {
        return cont(pushlex(">"), commasep(typeparam, ">"), poplex, functiondef);
      }
    }
    function functiondecl(type, value) {
      if ("*" == value) {
        return JSCompiler_object_inline_marked_2 = "keyword", cont(functiondecl);
      }
      if ("variable" == type) {
        return register(value), cont(functiondecl);
      }
      if ("(" == type) {
        return cont(pushcontext, pushlex(")"), commasep(funarg, ")"), poplex, mayberettype, popcontext);
      }
      if (isTS && "<" == value) {
        return cont(pushlex(">"), commasep(typeparam, ">"), poplex, functiondecl);
      }
    }
    function typename(type, value) {
      if ("keyword" == type || "variable" == type) {
        return JSCompiler_object_inline_marked_2 = "type", cont(typename);
      }
      if ("<" == value) {
        return cont(pushlex(">"), commasep(typeparam, ">"), poplex);
      }
    }
    function funarg(type, value) {
      "@" == value && cont(expression, funarg);
      return "spread" == type ? cont(funarg) : isTS && isModifier(value) ? (JSCompiler_object_inline_marked_2 = "keyword", cont(funarg)) : isTS && "this" == type ? cont(maybetype, maybeAssign) : pass(pattern, maybetype, maybeAssign);
    }
    function classExpression(type, value) {
      return "variable" == type ? className(type, value) : classNameAfter(type, value);
    }
    function className(type, value) {
      if ("variable" == type) {
        return register(value), cont(classNameAfter);
      }
    }
    function classNameAfter(type, value) {
      if ("<" == value) {
        return cont(pushlex(">"), commasep(typeparam, ">"), poplex, classNameAfter);
      }
      if ("extends" == value || "implements" == value || isTS && "," == type) {
        return "implements" == value && (JSCompiler_object_inline_marked_2 = "keyword"), cont(isTS ? typeexpr : expression, classNameAfter);
      }
      if ("{" == type) {
        return cont(pushlex("}"), classBody, poplex);
      }
    }
    function classBody(type, value) {
      if ("async" == type || "variable" == type && ("static" == value || "get" == value || "set" == value || isTS && isModifier(value)) && JSCompiler_object_inline_stream_1.match(/^\s+[\w$\xa1-\uffff]/, !1)) {
        return JSCompiler_object_inline_marked_2 = "keyword", cont(classBody);
      }
      if ("variable" == type || "keyword" == JSCompiler_object_inline_style_4) {
        return JSCompiler_object_inline_marked_2 = "property", cont(classfield, classBody);
      }
      if ("number" == type || "string" == type) {
        return cont(classfield, classBody);
      }
      if ("[" == type) {
        return cont(expression, maybetype, expect("]"), classfield, classBody);
      }
      if ("*" == value) {
        return JSCompiler_object_inline_marked_2 = "keyword", cont(classBody);
      }
      if (isTS && "(" == type) {
        return pass(functiondecl, classBody);
      }
      if (";" == type || "," == type) {
        return cont(classBody);
      }
      if ("}" == type) {
        return cont();
      }
      if ("@" == value) {
        return cont(expression, classBody);
      }
    }
    function classfield(context$jscomp$20_type, value) {
      if ("!" == value || "?" == value) {
        return cont(classfield);
      }
      if (":" == context$jscomp$20_type) {
        return cont(typeexpr, maybeAssign);
      }
      if ("=" == value) {
        return cont(expressionNoComma);
      }
      context$jscomp$20_type = JSCompiler_object_inline_state_0.lexical.prev;
      return pass(context$jscomp$20_type && "interface" == context$jscomp$20_type.info ? functiondecl : functiondef);
    }
    function afterExport(type, value) {
      return "*" == value ? (JSCompiler_object_inline_marked_2 = "keyword", cont(maybeFrom, expect(";"))) : "default" == value ? (JSCompiler_object_inline_marked_2 = "keyword", cont(expression, expect(";"))) : "{" == type ? cont(commasep(exportField, "}"), maybeFrom, expect(";")) : pass(statement);
    }
    function exportField(type, value) {
      if ("as" == value) {
        return JSCompiler_object_inline_marked_2 = "keyword", cont(expect("variable"));
      }
      if ("variable" == type) {
        return pass(expressionNoComma, exportField);
      }
    }
    function afterImport(type) {
      return "string" == type ? cont() : "(" == type ? pass(expression) : "." == type ? pass(maybeoperatorComma) : pass(importSpec, maybeMoreImports, maybeFrom);
    }
    function importSpec(type, value) {
      if ("{" == type) {
        return contCommasep(importSpec, "}");
      }
      "variable" == type && register(value);
      "*" == value && (JSCompiler_object_inline_marked_2 = "keyword");
      return cont(maybeAs);
    }
    function maybeMoreImports(type) {
      if ("," == type) {
        return cont(importSpec, maybeMoreImports);
      }
    }
    function maybeAs(_type, value) {
      if ("as" == value) {
        return JSCompiler_object_inline_marked_2 = "keyword", cont(importSpec);
      }
    }
    function maybeFrom(_type, value) {
      if ("from" == value) {
        return JSCompiler_object_inline_marked_2 = "keyword", cont(expression);
      }
    }
    function arrayLiteral(type) {
      return "]" == type ? cont() : pass(commasep(expressionNoComma, "]"));
    }
    function enumdef() {
      return pass(pushlex("form"), pattern, expect("{"), pushlex("}"), commasep(enummember, "}"), poplex, poplex);
    }
    function enummember() {
      return pass(pattern, maybeAssign);
    }
    function expressionAllowed(stream, state, backUp) {
      return state.tokenize == tokenBase && /^(?:operator|sof|keyword [bcd]|case|new|export|default|spread|[\[{}\(,;:]|=>)$/.test(state.lastType) || "quasi" == state.lastType && /\{\s*$/.test(stream.string.slice(0, stream.pos - (backUp || 0)));
    }
    var indentUnit = config.indentUnit, statementIndent = parserConfig.statementIndent, jsonldMode = parserConfig.jsonld, jsonMode = parserConfig.json || jsonldMode, trackScope = !1 !== parserConfig.trackScope, isTS = parserConfig.typescript, wordRE = parserConfig.wordCharacters || /[\w$\xa1-\uffff]/, keywords = function() {
      function kw(type) {
        return {type, style:"keyword"};
      }
      var A = kw("keyword a"), B = kw("keyword b"), C = kw("keyword c"), D = kw("keyword d"), operator = kw("operator"), atom = {type:"atom", style:"atom"};
      return {"if":kw("if"), "while":A, "with":A, "else":B, "do":B, "try":B, "finally":B, "return":D, "break":D, "continue":D, "new":kw("new"), "delete":C, "void":C, "throw":C, "debugger":kw("debugger"), "var":kw("var"), "const":kw("var"), let:kw("var"), "function":kw("function"), "catch":kw("catch"), "for":kw("for"), "switch":kw("switch"), "case":kw("case"), "default":kw("default"), "in":operator, "typeof":operator, "instanceof":operator, "true":atom, "false":atom, "null":atom, undefined:atom, NaN:atom, 
      Infinity:atom, "this":kw("this"), "class":kw("class"), "super":kw("atom"), yield:C, "export":kw("export"), "import":kw("import"), "extends":C, await:C};
    }(), isOperatorChar = /[+\-*&%=<>!?|~^@]/, isJsonldKeyword = /^@(context|id|value|language|type|container|list|set|reverse|index|base|vocab|graph)"/, type$jscomp$0, content$jscomp$0, atomicTypes = {atom:!0, number:!0, variable:!0, string:!0, regexp:!0, "this":!0, "import":!0, "jsonld-keyword":!0};
    var JSCompiler_object_inline_cc_3 = JSCompiler_object_inline_marked_2 = JSCompiler_object_inline_state_0 = null;
    var JSCompiler_object_inline_style_4 = JSCompiler_object_inline_stream_1 = void 0;
    var defaultVars = new Var("this", new Var("arguments", null));
    pushcontext.lex = pushblockcontext.lex = !0;
    popcontext.lex = !0;
    poplex.lex = !0;
    return {startState:function(basecolumn_state) {
      basecolumn_state = {tokenize:tokenBase, lastType:"sof", cc:[], lexical:new JSLexical((basecolumn_state || 0) - indentUnit, 0, "block", !1), localVars:parserConfig.localVars, context:parserConfig.localVars && new Context(null, null, !1), indented:basecolumn_state || 0};
      parserConfig.globalVars && "object" == typeof parserConfig.globalVars && (basecolumn_state.globalVars = parserConfig.globalVars);
      return basecolumn_state;
    }, token:function(stream, state) {
      stream.sol() && (state.lexical.hasOwnProperty("align") || (state.lexical.align = !1), state.indented = stream.indentation(), findFatArrow(stream, state));
      if (state.tokenize != tokenComment && stream.eatSpace()) {
        return null;
      }
      var style = state.tokenize(stream, state);
      if ("comment" == type$jscomp$0) {
        return style;
      }
      state.lastType = "operator" != type$jscomp$0 || "++" != content$jscomp$0 && "--" != content$jscomp$0 ? type$jscomp$0 : "incdec";
      return parseJS(state, style, type$jscomp$0, content$jscomp$0, stream);
    }, indent:function(JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state, textAfter) {
      if (JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state.tokenize == tokenComment || JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state.tokenize == tokenQuasi) {
        return CodeMirror.Pass;
      }
      if (JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state.tokenize != tokenBase) {
        return 0;
      }
      var JSCompiler_temp_const = textAfter && textAfter.charAt(0), lexical = JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state.lexical, top$jscomp$19_type;
      if (!/^\s*else\b/.test(textAfter)) {
        for (var closing$jscomp$1_i = JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state.cc.length - 1; 0 <= closing$jscomp$1_i; --closing$jscomp$1_i) {
          var c = JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state.cc[closing$jscomp$1_i];
          if (c == poplex) {
            lexical = lexical.prev;
          } else if (c != maybeelse && c != popcontext) {
            break;
          }
        }
      }
      for (; !("stat" != lexical.type && "form" != lexical.type || "}" != JSCompiler_temp_const && (!(top$jscomp$19_type = JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state.cc[JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state.cc.length - 1]) || top$jscomp$19_type != maybeoperatorComma && top$jscomp$19_type != 
      maybeoperatorNoComma || /^[,\.=+\-*:?[\(]/.test(textAfter)));) {
        lexical = lexical.prev;
      }
      statementIndent && ")" == lexical.type && "stat" == lexical.prev.type && (lexical = lexical.prev);
      top$jscomp$19_type = lexical.type;
      closing$jscomp$1_i = JSCompiler_temp_const == top$jscomp$19_type;
      "vardef" == top$jscomp$19_type ? JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state = lexical.indented + ("operator" == JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state.lastType || "," == JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state.lastType ? 
      lexical.info.length + 1 : 0) : "form" == top$jscomp$19_type && "{" == JSCompiler_temp_const ? JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state = lexical.indented : "form" == top$jscomp$19_type ? JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state = lexical.indented + indentUnit : "stat" == top$jscomp$19_type ? 
      (JSCompiler_temp_const = lexical.indented, JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state = "operator" == JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state.lastType || "," == JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state.lastType || 
      isOperatorChar.test(textAfter.charAt(0)) || /[,.]/.test(textAfter.charAt(0)), JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state = JSCompiler_temp_const + (JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state ? statementIndent || indentUnit : 0)) : JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state = 
      "switch" != lexical.info || closing$jscomp$1_i || 0 == parserConfig.doubleIndentSwitch ? lexical.align ? lexical.column + (closing$jscomp$1_i ? 0 : 1) : lexical.indented + (closing$jscomp$1_i ? 0 : indentUnit) : lexical.indented + (/^(?:case|default)\b/.test(textAfter) ? indentUnit : 2 * indentUnit);
      return JSCompiler_inline_result$jscomp$83_JSCompiler_temp$jscomp$78_JSCompiler_temp$jscomp$79_JSCompiler_temp$jscomp$80_JSCompiler_temp$jscomp$81_state;
    }, electricInput:/^\s*(?:case .*?:|default:|\{|\})$/, blockCommentStart:jsonMode ? null : "/*", blockCommentEnd:jsonMode ? null : "*/", blockCommentContinue:jsonMode ? null : " * ", lineComment:jsonMode ? null : "//", fold:"brace", closeBrackets:"()[]{}''\"\"``", helperType:jsonMode ? "json" : "javascript", jsonldMode, jsonMode, expressionAllowed, skipExpression:function(state) {
      parseJS(state, "atom", "atom", "true", new CodeMirror.StringStream("", 2, null));
    }};
  });
  CodeMirror.registerHelper("wordChars", "javascript", /[\w$]/);
  CodeMirror.defineMIME("text/javascript", "javascript");
  CodeMirror.defineMIME("text/ecmascript", "javascript");
  CodeMirror.defineMIME("application/javascript", "javascript");
  CodeMirror.defineMIME("application/x-javascript", "javascript");
  CodeMirror.defineMIME("application/ecmascript", "javascript");
  CodeMirror.defineMIME("application/json", {name:"javascript", json:!0});
  CodeMirror.defineMIME("application/x-json", {name:"javascript", json:!0});
  CodeMirror.defineMIME("application/manifest+json", {name:"javascript", json:!0});
  CodeMirror.defineMIME("application/ld+json", {name:"javascript", jsonld:!0});
  CodeMirror.defineMIME("text/typescript", {name:"javascript", typescript:!0});
  CodeMirror.defineMIME("application/typescript", {name:"javascript", typescript:!0});
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_607(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_607) : mod$jscomp$inline_607(CodeMirror);
//[third_party/javascript/codemirror4/mode/clike/clike.js]
function mod$jscomp$inline_609(CodeMirror) {
  function Context(indented, column, type, info, align, prev) {
    this.indented = indented;
    this.column = column;
    this.type = type;
    this.info = info;
    this.align = align;
    this.prev = prev;
  }
  function pushContext(state, col, type, info) {
    var indent = state.indented;
    state.context && "statement" == state.context.type && "statement" != type && (indent = state.context.indented);
    return state.context = new Context(indent, col, type, info, null, state.context);
  }
  function popContext(state) {
    var t = state.context.type;
    if (")" == t || "]" == t || "}" == t) {
      state.indented = state.context.indented;
    }
    return state.context = state.context.prev;
  }
  function typeBefore(stream, state, pos) {
    if ("variable" == state.prevToken || "type" == state.prevToken || /\S(?:[^- ]>|[*\]])\s*$|\*$/.test(stream.string.slice(0, pos)) || state.typeAtEndOfLine && stream.column() == stream.indentation()) {
      return !0;
    }
  }
  function isTopScope(context) {
    for (;;) {
      if (!context || "top" == context.type) {
        return !0;
      }
      if ("}" == context.type && "namespace" != context.prev.info) {
        return !1;
      }
      context = context.prev;
    }
  }
  function words(str$jscomp$74_words) {
    var obj = {};
    str$jscomp$74_words = str$jscomp$74_words.split(" ");
    for (var i = 0; i < str$jscomp$74_words.length; ++i) {
      obj[str$jscomp$74_words[i]] = !0;
    }
    return obj;
  }
  function contains(words, word) {
    return "function" === typeof words ? words(word) : words.propertyIsEnumerable(word);
  }
  function cTypes(identifier) {
    return contains(basicCTypes, identifier) || /.+_t$/.test(identifier);
  }
  function objCTypes(identifier) {
    return cTypes(identifier) || contains(basicObjCTypes, identifier);
  }
  function cppHook(stream, state) {
    if (!state.startOfLine) {
      return !1;
    }
    for (var ch, next = null; ch = stream.peek();) {
      if ("\\" == ch && stream.match(/^.$/)) {
        next = cppHook;
        break;
      } else if ("/" == ch && stream.match(/^\/[\/\*]/, !1)) {
        break;
      }
      stream.next();
    }
    state.tokenize = next;
    return "meta";
  }
  function pointerHook(_stream, state) {
    return "type" == state.prevToken ? "type" : !1;
  }
  function cIsReservedIdentifier(token) {
    return !token || 2 > token.length || "_" != token[0] ? !1 : "_" == token[1] || token[1] !== token[1].toLowerCase();
  }
  function cpp14Literal(stream) {
    stream.eatWhile(/[\w\.']/);
    return "number";
  }
  function cpp11StringHook(stream, state) {
    stream.backUp(1);
    if (stream.match(/^(?:R|u8R|uR|UR|LR)/)) {
      var match = stream.match(/^"([^\s\\()]{0,16})\(/);
      if (!match) {
        return !1;
      }
      state.cpp11RawStringDelim = match[1];
      state.tokenize = tokenRawString;
      return tokenRawString(stream, state);
    }
    if (stream.match(/^(?:u8|u|U|L)/)) {
      return stream.match(/^["']/, !1) ? "string" : !1;
    }
    stream.next();
    return !1;
  }
  function cppLooksLikeConstructor(lastTwo_word) {
    return (lastTwo_word = /(\w+)::~?(\w+)$/.exec(lastTwo_word)) && lastTwo_word[1] == lastTwo_word[2];
  }
  function tokenAtString(stream, state) {
    for (var next; null != (next = stream.next());) {
      if ('"' == next && !stream.eat('"')) {
        state.tokenize = null;
        break;
      }
    }
    return "string";
  }
  function tokenRawString(stream, state) {
    var delim = state.cpp11RawStringDelim.replace(/[^\w\s]/g, "\\$&");
    stream.match(new RegExp(".*?\\)" + delim + '"')) ? state.tokenize = null : stream.skipToEnd();
    return "string";
  }
  function def(mimes, mode) {
    function add(obj) {
      if (obj) {
        for (var prop in obj) {
          obj.hasOwnProperty(prop) && words.push(prop);
        }
      }
    }
    "string" == typeof mimes && (mimes = [mimes]);
    var words = [];
    add(mode.keywords);
    add(mode.types);
    add(mode.builtin);
    add(mode.atoms);
    words.length && (mode.helperType = mimes[0], CodeMirror.registerHelper("hintWords", mimes[0], words));
    for (var i = 0; i < mimes.length; ++i) {
      CodeMirror.defineMIME(mimes[i], mode);
    }
  }
  function tokenTripleString(stream, state) {
    for (var escaped = !1; !stream.eol();) {
      if (!escaped && stream.match('"""')) {
        state.tokenize = null;
        break;
      }
      escaped = "\\" == stream.next() && !escaped;
    }
    return "string";
  }
  function tokenNestedComment(depth) {
    return function(stream, state) {
      for (var ch; ch = stream.next();) {
        if ("*" == ch && stream.eat("/")) {
          if (1 == depth) {
            state.tokenize = null;
            break;
          } else {
            return state.tokenize = tokenNestedComment(depth - 1), state.tokenize(stream, state);
          }
        } else if ("/" == ch && stream.eat("*")) {
          return state.tokenize = tokenNestedComment(depth + 1), state.tokenize(stream, state);
        }
      }
      return "comment";
    };
  }
  function tokenKotlinString(tripleString) {
    return function(stream, state) {
      for (var escaped = !1, next, end = !1; !stream.eol();) {
        if (!tripleString && !escaped && stream.match('"')) {
          end = !0;
          break;
        }
        if (tripleString && stream.match('"""')) {
          end = !0;
          break;
        }
        next = stream.next();
        !escaped && "$" == next && stream.match("{") && stream.skipTo("}");
        escaped = !escaped && "\\" == next && !tripleString;
      }
      if (end || !tripleString) {
        state.tokenize = null;
      }
      return "string";
    };
  }
  function tokenCeylonString(type) {
    return function(stream, state) {
      for (var escaped = !1, next, end = !1; !stream.eol();) {
        if (!escaped && stream.match('"') && ("single" == type || stream.match('""'))) {
          end = !0;
          break;
        }
        if (!escaped && stream.match("``")) {
          stringTokenizer = tokenCeylonString(type);
          end = !0;
          break;
        }
        next = stream.next();
        escaped = "single" == type && !escaped && "\\" == next;
      }
      end && (state.tokenize = null);
      return "string";
    };
  }
  CodeMirror.defineMode("clike", function(config, parserConfig) {
    function tokenBase(cur$jscomp$28_stream, state) {
      var ch = cur$jscomp$28_stream.next();
      if (hooks[ch]) {
        var result = hooks[ch](cur$jscomp$28_stream, state);
        if (!1 !== result) {
          return result;
        }
      }
      if ('"' == ch || "'" == ch) {
        return state.tokenize = tokenString(ch), state.tokenize(cur$jscomp$28_stream, state);
      }
      if (numberStart.test(ch)) {
        cur$jscomp$28_stream.backUp(1);
        if (cur$jscomp$28_stream.match(number)) {
          return "number";
        }
        cur$jscomp$28_stream.next();
      }
      if (isPunctuationChar.test(ch)) {
        return curPunc = ch, null;
      }
      if ("/" == ch) {
        if (cur$jscomp$28_stream.eat("*")) {
          return state.tokenize = tokenComment, tokenComment(cur$jscomp$28_stream, state);
        }
        if (cur$jscomp$28_stream.eat("/")) {
          return cur$jscomp$28_stream.skipToEnd(), "comment";
        }
      }
      if (isOperatorChar.test(ch)) {
        for (; !cur$jscomp$28_stream.match(/^\/[\/*]/, !1) && cur$jscomp$28_stream.eat(isOperatorChar);) {
        }
        return "operator";
      }
      cur$jscomp$28_stream.eatWhile(isIdentifierChar);
      if (namespaceSeparator) {
        for (; cur$jscomp$28_stream.match(namespaceSeparator);) {
          cur$jscomp$28_stream.eatWhile(isIdentifierChar);
        }
      }
      cur$jscomp$28_stream = cur$jscomp$28_stream.current();
      return contains(keywords, cur$jscomp$28_stream) ? (contains(blockKeywords, cur$jscomp$28_stream) && (curPunc = "newstatement"), contains(defKeywords, cur$jscomp$28_stream) && (isDefKeyword = !0), "keyword") : contains(types, cur$jscomp$28_stream) ? "type" : contains(builtin, cur$jscomp$28_stream) || isReservedIdentifier && isReservedIdentifier(cur$jscomp$28_stream) ? (contains(blockKeywords, cur$jscomp$28_stream) && (curPunc = "newstatement"), "builtin") : contains(atoms, cur$jscomp$28_stream) ? 
      "atom" : "variable";
    }
    function tokenString(quote) {
      return function(stream, state) {
        for (var escaped = !1, next, end = !1; null != (next = stream.next());) {
          if (next == quote && !escaped) {
            end = !0;
            break;
          }
          escaped = !escaped && "\\" == next;
        }
        if (end || !escaped && !multiLineStrings) {
          state.tokenize = null;
        }
        return "string";
      };
    }
    function tokenComment(stream, state) {
      for (var maybeEnd = !1, ch; ch = stream.next();) {
        if ("/" == ch && maybeEnd) {
          state.tokenize = null;
          break;
        }
        maybeEnd = "*" == ch;
      }
      return "comment";
    }
    function maybeEOL(stream, state) {
      parserConfig.typeFirstDefinitions && stream.eol() && isTopScope(state.context) && (state.typeAtEndOfLine = typeBefore(stream, state, stream.pos));
    }
    var indentUnit = config.indentUnit, statementIndentUnit = parserConfig.statementIndentUnit || indentUnit, dontAlignCalls = parserConfig.dontAlignCalls, keywords = parserConfig.keywords || {}, types = parserConfig.types || {}, builtin = parserConfig.builtin || {}, blockKeywords = parserConfig.blockKeywords || {}, defKeywords = parserConfig.defKeywords || {}, atoms = parserConfig.atoms || {}, hooks = parserConfig.hooks || {}, multiLineStrings = parserConfig.multiLineStrings, indentStatements = 
    !1 !== parserConfig.indentStatements, namespaceSeparator = parserConfig.namespaceSeparator, isPunctuationChar = parserConfig.isPunctuationChar || /[\[\]{}\(\),;:\.]/, numberStart = parserConfig.numberStart || /[\d\.]/, number = parserConfig.number || /^(?:0x[a-f\d]+|0b[01]+|(?:\d+\.?\d*|\.\d+)(?:e[-+]?\d+)?)(u|ll?|l|f)?/i, isOperatorChar = parserConfig.isOperatorChar || /[+\-*&%=<>!?|\/]/, isIdentifierChar = parserConfig.isIdentifierChar || /[\w\$_\xa1-\uffff]/, isReservedIdentifier = parserConfig.isReservedIdentifier || 
    !1, curPunc, isDefKeyword;
    return {startState:function(basecolumn) {
      return {tokenize:null, context:new Context((basecolumn || 0) - indentUnit, 0, "top", null, !1), indented:0, startOfLine:!0, prevToken:null};
    }, token:function(stream, state) {
      var ctx_result = state.context;
      stream.sol() && (null == ctx_result.align && (ctx_result.align = !1), state.indented = stream.indentation(), state.startOfLine = !0);
      if (stream.eatSpace()) {
        return maybeEOL(stream, state), null;
      }
      curPunc = isDefKeyword = null;
      var style = (state.tokenize || tokenBase)(stream, state);
      if ("comment" == style || "meta" == style) {
        return style;
      }
      null == ctx_result.align && (ctx_result.align = !0);
      if (";" == curPunc || ":" == curPunc || "," == curPunc && stream.match(/^\s*(?:\/\/.*)?$/, !1)) {
        for (; "statement" == state.context.type;) {
          popContext(state);
        }
      } else if ("{" == curPunc) {
        pushContext(state, stream.column(), "}");
      } else if ("[" == curPunc) {
        pushContext(state, stream.column(), "]");
      } else if ("(" == curPunc) {
        pushContext(state, stream.column(), ")");
      } else if ("}" == curPunc) {
        for (; "statement" == ctx_result.type;) {
          ctx_result = popContext(state);
        }
        for ("}" == ctx_result.type && (ctx_result = popContext(state)); "statement" == ctx_result.type;) {
          ctx_result = popContext(state);
        }
      } else {
        curPunc == ctx_result.type ? popContext(state) : indentStatements && (("}" == ctx_result.type || "top" == ctx_result.type) && ";" != curPunc || "statement" == ctx_result.type && "newstatement" == curPunc) && pushContext(state, stream.column(), "statement", stream.current());
      }
      "variable" == style && ("def" == state.prevToken || parserConfig.typeFirstDefinitions && typeBefore(stream, state, stream.start) && isTopScope(state.context) && stream.match(/^\s*\(/, !1)) && (style = "def");
      hooks.token && (ctx_result = hooks.token(stream, state, style), void 0 !== ctx_result && (style = ctx_result));
      "def" == style && !1 === parserConfig.styleDefs && (style = "variable");
      state.startOfLine = !1;
      state.prevToken = isDefKeyword ? "def" : style || curPunc;
      maybeEOL(stream, state);
      return style;
    }, indent:function(hook_state, textAfter) {
      if (hook_state.tokenize != tokenBase && null != hook_state.tokenize || hook_state.typeAtEndOfLine) {
        return CodeMirror.Pass;
      }
      var ctx = hook_state.context, firstChar = textAfter && textAfter.charAt(0), closing = firstChar == ctx.type;
      "statement" == ctx.type && "}" == firstChar && (ctx = ctx.prev);
      if (parserConfig.dontIndentStatements) {
        for (; "statement" == ctx.type && parserConfig.dontIndentStatements.test(ctx.info);) {
          ctx = ctx.prev;
        }
      }
      if (hooks.indent && (hook_state = hooks.indent(hook_state, ctx, textAfter, indentUnit), "number" == typeof hook_state)) {
        return hook_state;
      }
      hook_state = ctx.prev && "switch" == ctx.prev.info;
      if (parserConfig.allmanIndentation && /[{(]/.test(firstChar)) {
        for (; "top" != ctx.type && "}" != ctx.type;) {
          ctx = ctx.prev;
        }
        return ctx.indented;
      }
      return "statement" == ctx.type ? ctx.indented + ("{" == firstChar ? 0 : statementIndentUnit) : !ctx.align || dontAlignCalls && ")" == ctx.type ? ")" != ctx.type || closing ? ctx.indented + (closing ? 0 : indentUnit) + (closing || !hook_state || /^(?:case|default)\b/.test(textAfter) ? 0 : indentUnit) : ctx.indented + statementIndentUnit : ctx.column + (closing ? 0 : 1);
    }, electricInput:!1 !== parserConfig.indentSwitch ? /^\s*(?:case .*?:|default:|\{\}?|\})$/ : /^\s*[{}]$/, blockCommentStart:"/*", blockCommentEnd:"*/", blockCommentContinue:" * ", lineComment:"//", fold:"brace"};
  });
  var basicCTypes = words("int long char short double float unsigned signed void bool"), basicObjCTypes = words("SEL instancetype id Class Protocol BOOL");
  def(["text/x-csrc", "text/x-c", "text/x-chdr"], {name:"clike", keywords:words("auto if break case register continue return default do sizeof static else struct switch extern typedef union for goto while enum const volatile inline restrict asm fortran"), types:cTypes, blockKeywords:words("case do else for if switch while struct enum union"), defKeywords:words("struct enum union"), typeFirstDefinitions:!0, atoms:words("NULL true false"), isReservedIdentifier:cIsReservedIdentifier, hooks:{"#":cppHook, 
  "*":pointerHook,}, modeProps:{fold:["brace", "include"]}});
  def(["text/x-c++src", "text/x-c++hdr"], {name:"clike", keywords:words("auto if break case register continue return default do sizeof static else struct switch extern typedef union for goto while enum const volatile inline restrict asm fortran alignas alignof and and_eq audit axiom bitand bitor catch class compl concept constexpr const_cast decltype delete dynamic_cast explicit export final friend import module mutable namespace new noexcept not not_eq operator or or_eq override private protected public reinterpret_cast requires static_assert static_cast template this thread_local throw try typeid typename using virtual xor xor_eq"), 
  types:cTypes, blockKeywords:words("case do else for if switch while struct enum union class try catch"), defKeywords:words("struct enum union class namespace"), typeFirstDefinitions:!0, atoms:words("true false NULL nullptr"), dontIndentStatements:/^template$/, isIdentifierChar:/[\w\$_~\xa1-\uffff]/, isReservedIdentifier:cIsReservedIdentifier, hooks:{"#":cppHook, "*":pointerHook, u:cpp11StringHook, U:cpp11StringHook, L:cpp11StringHook, R:cpp11StringHook, 0:cpp14Literal, 1:cpp14Literal, 2:cpp14Literal, 
  3:cpp14Literal, 4:cpp14Literal, 5:cpp14Literal, 6:cpp14Literal, 7:cpp14Literal, 8:cpp14Literal, 9:cpp14Literal, token:function(stream, state, style) {
    if ("variable" == style && "(" == stream.peek() && (";" == state.prevToken || null == state.prevToken || "}" == state.prevToken) && cppLooksLikeConstructor(stream.current())) {
      return "def";
    }
  }}, namespaceSeparator:"::", modeProps:{fold:["brace", "include"]}});
  def("text/x-java", {name:"clike", keywords:words("abstract assert break case catch class const continue default do else enum extends final finally for goto if implements import instanceof interface native new package private protected public return static strictfp super switch synchronized this throw throws transient try volatile while @interface"), types:words("var byte short int long float double boolean char void Boolean Byte Character Double Float Integer Long Number Object Short String StringBuffer StringBuilder Void"), 
  blockKeywords:words("catch class do else finally for if switch try while"), defKeywords:words("class interface enum @interface"), typeFirstDefinitions:!0, atoms:words("true false null"), number:/^(?:0x[a-f\d_]+|0b[01_]+|(?:[\d_]+\.?\d*|\.\d+)(?:e[-+]?[\d_]+)?)(u|ll?|l|f)?/i, hooks:{"@":function(stream) {
    if (stream.match("interface", !1)) {
      return !1;
    }
    stream.eatWhile(/[\w\$_]/);
    return "meta";
  }, '"':function(stream, state) {
    if (!stream.match(/""$/)) {
      return !1;
    }
    state.tokenize = tokenTripleString;
    return state.tokenize(stream, state);
  }}, modeProps:{fold:["brace", "import"]}});
  def("text/x-csharp", {name:"clike", keywords:words("abstract as async await base break case catch checked class const continue default delegate do else enum event explicit extern finally fixed for foreach goto if implicit in interface internal is lock namespace new operator out override params private protected public readonly ref return sealed sizeof stackalloc static struct switch this throw try typeof unchecked unsafe using virtual void volatile while add alias ascending descending dynamic from get global group into join let orderby partial remove select set value var yield"), 
  types:words("Action Boolean Byte Char DateTime DateTimeOffset Decimal Double Func Guid Int16 Int32 Int64 Object SByte Single String Task TimeSpan UInt16 UInt32 UInt64 bool byte char decimal double short int long object sbyte float string ushort uint ulong"), blockKeywords:words("catch class do else finally for foreach if struct switch try while"), defKeywords:words("class interface namespace struct var"), typeFirstDefinitions:!0, atoms:words("true false null"), hooks:{"@":function(stream, state) {
    if (stream.eat('"')) {
      return state.tokenize = tokenAtString, tokenAtString(stream, state);
    }
    stream.eatWhile(/[\w\$_]/);
    return "meta";
  }}});
  def("text/x-scala", {name:"clike", keywords:words("abstract case catch class def do else extends final finally for forSome if implicit import lazy match new null object override package private protected return sealed super this throw trait try type val var while with yield _ assert assume require print println printf readLine readBoolean readByte readShort readChar readInt readLong readFloat readDouble"), types:words("AnyVal App Application Array BufferedIterator BigDecimal BigInt Char Console Either Enumeration Equiv Error Exception Fractional Function IndexedSeq Int Integral Iterable Iterator List Map Numeric Nil NotNull Option Ordered Ordering PartialFunction PartialOrdering Product Proxy Range Responder Seq Serializable Set Specializable Stream StringBuilder StringContext Symbol Throwable Traversable TraversableOnce Tuple Unit Vector Boolean Byte Character CharSequence Class ClassLoader Cloneable Comparable Compiler Double Exception Float Integer Long Math Number Object Package Pair Process Runtime Runnable SecurityManager Short StackTraceElement StrictMath String StringBuffer System Thread ThreadGroup ThreadLocal Throwable Triple Void"), 
  multiLineStrings:!0, blockKeywords:words("catch class enum do else finally for forSome if match switch try while"), defKeywords:words("class enum def object package trait type val var"), atoms:words("true false null"), indentStatements:!1, indentSwitch:!1, isOperatorChar:/[+\-*&%=<>!?|\/#:@]/, hooks:{"@":function(stream) {
    stream.eatWhile(/[\w\$_]/);
    return "meta";
  }, '"':function(stream, state) {
    if (!stream.match('""')) {
      return !1;
    }
    state.tokenize = tokenTripleString;
    return state.tokenize(stream, state);
  }, "'":function(stream) {
    stream.eatWhile(/[\w\$_\xa1-\uffff]/);
    return "atom";
  }, "=":function(stream, state) {
    var cx = state.context;
    return "}" == cx.type && cx.align && stream.eat(">") ? (state.context = new Context(cx.indented, cx.column, cx.type, cx.info, null, cx.prev), "operator") : !1;
  }, "/":function(stream, state) {
    if (!stream.eat("*")) {
      return !1;
    }
    state.tokenize = tokenNestedComment(1);
    return state.tokenize(stream, state);
  }}, modeProps:{closeBrackets:{pairs:'()[]{}""', triples:'"'}}});
  def("text/x-kotlin", {name:"clike", keywords:words("package as typealias class interface this super val operator var fun for is in This throw return annotation break continue object if else while do try when !in !is as? file import where by get set abstract enum open inner override private public internal protected catch finally out final vararg reified dynamic companion constructor init sealed field property receiver param sparam lateinit data inline noinline tailrec external annotation crossinline const operator infix suspend actual expect setparam value"), 
  types:words("Boolean Byte Character CharSequence Class ClassLoader Cloneable Comparable Compiler Double Exception Float Integer Long Math Number Object Package Pair Process Runtime Runnable SecurityManager Short StackTraceElement StrictMath String StringBuffer System Thread ThreadGroup ThreadLocal Throwable Triple Void Annotation Any BooleanArray ByteArray Char CharArray DeprecationLevel DoubleArray Enum FloatArray Function Int IntArray Lazy LazyThreadSafetyMode LongArray Nothing ShortArray Unit"), 
  intendSwitch:!1, indentStatements:!1, multiLineStrings:!0, number:/^(?:0x[a-f\d_]+|0b[01_]+|(?:[\d_]+(\.\d+)?|\.\d+)(?:e[-+]?[\d_]+)?)(u|ll?|l|f)?/i, blockKeywords:words("catch class do else finally for if where try while enum"), defKeywords:words("class val var object interface fun"), atoms:words("true false null this"), hooks:{"@":function(stream) {
    stream.eatWhile(/[\w\$_]/);
    return "meta";
  }, "*":function(_stream, state) {
    return "." == state.prevToken ? "variable" : "operator";
  }, '"':function(stream, state) {
    state.tokenize = tokenKotlinString(stream.match('""'));
    return state.tokenize(stream, state);
  }, "/":function(stream, state) {
    if (!stream.eat("*")) {
      return !1;
    }
    state.tokenize = tokenNestedComment(1);
    return state.tokenize(stream, state);
  }, indent:function(state, ctx, textAfter, indentUnit) {
    var firstChar = textAfter && textAfter.charAt(0);
    if (("}" == state.prevToken || ")" == state.prevToken) && "" == textAfter) {
      return state.indented;
    }
    if ("operator" == state.prevToken && "}" != textAfter && "}" != state.context.type || "variable" == state.prevToken && "." == firstChar || ("}" == state.prevToken || ")" == state.prevToken) && "." == firstChar) {
      return 2 * indentUnit + ctx.indented;
    }
    if (ctx.align && "}" == ctx.type) {
      return ctx.indented + (state.context.type == (textAfter || "").charAt(0) ? 0 : indentUnit);
    }
  }}, modeProps:{closeBrackets:{triples:'"'}}});
  def(["x-shader/x-vertex", "x-shader/x-fragment"], {name:"clike", keywords:words("sampler1D sampler2D sampler3D samplerCube sampler1DShadow sampler2DShadow const attribute uniform varying break continue discard return for while do if else struct in out inout"), types:words("float int bool void vec2 vec3 vec4 ivec2 ivec3 ivec4 bvec2 bvec3 bvec4 mat2 mat3 mat4"), blockKeywords:words("for while do if else struct"), builtin:words("radians degrees sin cos tan asin acos atan pow exp log exp2 sqrt inversesqrt abs sign floor ceil fract mod min max clamp mix step smoothstep length distance dot cross normalize ftransform faceforward reflect refract matrixCompMult lessThan lessThanEqual greaterThan greaterThanEqual equal notEqual any all not texture1D texture1DProj texture1DLod texture1DProjLod texture2D texture2DProj texture2DLod texture2DProjLod texture3D texture3DProj texture3DLod texture3DProjLod textureCube textureCubeLod shadow1D shadow2D shadow1DProj shadow2DProj shadow1DLod shadow2DLod shadow1DProjLod shadow2DProjLod dFdx dFdy fwidth noise1 noise2 noise3 noise4"), 
  atoms:words("true false gl_FragColor gl_SecondaryColor gl_Normal gl_Vertex gl_MultiTexCoord0 gl_MultiTexCoord1 gl_MultiTexCoord2 gl_MultiTexCoord3 gl_MultiTexCoord4 gl_MultiTexCoord5 gl_MultiTexCoord6 gl_MultiTexCoord7 gl_FogCoord gl_PointCoord gl_Position gl_PointSize gl_ClipVertex gl_FrontColor gl_BackColor gl_FrontSecondaryColor gl_BackSecondaryColor gl_TexCoord gl_FogFragCoord gl_FragCoord gl_FrontFacing gl_FragData gl_FragDepth gl_ModelViewMatrix gl_ProjectionMatrix gl_ModelViewProjectionMatrix gl_TextureMatrix gl_NormalMatrix gl_ModelViewMatrixInverse gl_ProjectionMatrixInverse gl_ModelViewProjectionMatrixInverse gl_TextureMatrixTranspose gl_ModelViewMatrixInverseTranspose gl_ProjectionMatrixInverseTranspose gl_ModelViewProjectionMatrixInverseTranspose gl_TextureMatrixInverseTranspose gl_NormalScale gl_DepthRange gl_ClipPlane gl_Point gl_FrontMaterial gl_BackMaterial gl_LightSource gl_LightModel gl_FrontLightModelProduct gl_BackLightModelProduct gl_TextureColor gl_EyePlaneS gl_EyePlaneT gl_EyePlaneR gl_EyePlaneQ gl_FogParameters gl_MaxLights gl_MaxClipPlanes gl_MaxTextureUnits gl_MaxTextureCoords gl_MaxVertexAttribs gl_MaxVertexUniformComponents gl_MaxVaryingFloats gl_MaxVertexTextureImageUnits gl_MaxTextureImageUnits gl_MaxFragmentUniformComponents gl_MaxCombineTextureImageUnits gl_MaxDrawBuffers"), 
  indentSwitch:!1, hooks:{"#":cppHook}, modeProps:{fold:["brace", "include"]}});
  def("text/x-nesc", {name:"clike", keywords:words("auto if break case register continue return default do sizeof static else struct switch extern typedef union for goto while enum const volatile inline restrict asm fortran as atomic async call command component components configuration event generic implementation includes interface module new norace nx_struct nx_union post provides signal task uses abstract extends"), types:cTypes, blockKeywords:words("case do else for if switch while struct enum union"), 
  atoms:words("null true false"), hooks:{"#":cppHook}, modeProps:{fold:["brace", "include"]}});
  def("text/x-objectivec", {name:"clike", keywords:words("auto if break case register continue return default do sizeof static else struct switch extern typedef union for goto while enum const volatile inline restrict asm fortran bycopy byref in inout oneway out self super atomic nonatomic retain copy readwrite readonly strong weak assign typeof nullable nonnull null_resettable _cmd @interface @implementation @end @protocol @encode @property @synthesize @dynamic @class @public @package @private @protected @required @optional @try @catch @finally @import @selector @encode @defs @synchronized @autoreleasepool @compatibility_alias @available"), 
  types:objCTypes, builtin:words("FOUNDATION_EXPORT FOUNDATION_EXTERN NS_INLINE NS_FORMAT_FUNCTION  NS_RETURNS_RETAINEDNS_ERROR_ENUM NS_RETURNS_NOT_RETAINED NS_RETURNS_INNER_POINTER NS_DESIGNATED_INITIALIZER NS_ENUM NS_OPTIONS NS_REQUIRES_NIL_TERMINATION NS_ASSUME_NONNULL_BEGIN NS_ASSUME_NONNULL_END NS_SWIFT_NAME NS_REFINED_FOR_SWIFT"), blockKeywords:words("case do else for if switch while struct enum union @synthesize @try @catch @finally @autoreleasepool @synchronized"), defKeywords:words("struct enum union @interface @implementation @protocol @class"), 
  dontIndentStatements:/^@.*$/, typeFirstDefinitions:!0, atoms:words("YES NO NULL Nil nil true false nullptr"), isReservedIdentifier:cIsReservedIdentifier, hooks:{"#":cppHook, "*":pointerHook,}, modeProps:{fold:["brace", "include"]}});
  def("text/x-objectivec++", {name:"clike", keywords:words("auto if break case register continue return default do sizeof static else struct switch extern typedef union for goto while enum const volatile inline restrict asm fortran bycopy byref in inout oneway out self super atomic nonatomic retain copy readwrite readonly strong weak assign typeof nullable nonnull null_resettable _cmd @interface @implementation @end @protocol @encode @property @synthesize @dynamic @class @public @package @private @protected @required @optional @try @catch @finally @import @selector @encode @defs @synchronized @autoreleasepool @compatibility_alias @available alignas alignof and and_eq audit axiom bitand bitor catch class compl concept constexpr const_cast decltype delete dynamic_cast explicit export final friend import module mutable namespace new noexcept not not_eq operator or or_eq override private protected public reinterpret_cast requires static_assert static_cast template this thread_local throw try typeid typename using virtual xor xor_eq"), 
  types:objCTypes, builtin:words("FOUNDATION_EXPORT FOUNDATION_EXTERN NS_INLINE NS_FORMAT_FUNCTION  NS_RETURNS_RETAINEDNS_ERROR_ENUM NS_RETURNS_NOT_RETAINED NS_RETURNS_INNER_POINTER NS_DESIGNATED_INITIALIZER NS_ENUM NS_OPTIONS NS_REQUIRES_NIL_TERMINATION NS_ASSUME_NONNULL_BEGIN NS_ASSUME_NONNULL_END NS_SWIFT_NAME NS_REFINED_FOR_SWIFT"), blockKeywords:words("case do else for if switch while struct enum union @synthesize @try @catch @finally @autoreleasepool @synchronized class try catch"), defKeywords:words("struct enum union @interface @implementation @protocol @class class namespace"), 
  dontIndentStatements:/^@.*$|^template$/, typeFirstDefinitions:!0, atoms:words("YES NO NULL Nil nil true false nullptr"), isReservedIdentifier:cIsReservedIdentifier, hooks:{"#":cppHook, "*":pointerHook, u:cpp11StringHook, U:cpp11StringHook, L:cpp11StringHook, R:cpp11StringHook, 0:cpp14Literal, 1:cpp14Literal, 2:cpp14Literal, 3:cpp14Literal, 4:cpp14Literal, 5:cpp14Literal, 6:cpp14Literal, 7:cpp14Literal, 8:cpp14Literal, 9:cpp14Literal, token:function(stream, state, style) {
    if ("variable" == style && "(" == stream.peek() && (";" == state.prevToken || null == state.prevToken || "}" == state.prevToken) && cppLooksLikeConstructor(stream.current())) {
      return "def";
    }
  }}, namespaceSeparator:"::", modeProps:{fold:["brace", "include"]}});
  def("text/x-squirrel", {name:"clike", keywords:words("base break clone continue const default delete enum extends function in class foreach local resume return this throw typeof yield constructor instanceof static"), types:cTypes, blockKeywords:words("case catch class else for foreach if switch try while"), defKeywords:words("function local class"), typeFirstDefinitions:!0, atoms:words("true false null"), hooks:{"#":cppHook}, modeProps:{fold:["brace", "include"]}});
  var stringTokenizer = null;
  def("text/x-ceylon", {name:"clike", keywords:words("abstracts alias assembly assert assign break case catch class continue dynamic else exists extends finally for function given if import in interface is let module new nonempty object of out outer package return satisfies super switch then this throw try value void while"), types:function(first$jscomp$9_word) {
    first$jscomp$9_word = first$jscomp$9_word.charAt(0);
    return first$jscomp$9_word === first$jscomp$9_word.toUpperCase() && first$jscomp$9_word !== first$jscomp$9_word.toLowerCase();
  }, blockKeywords:words("case catch class dynamic else finally for function if interface module new object switch try while"), defKeywords:words("class dynamic function interface module object package value"), builtin:words("abstract actual aliased annotation by default deprecated doc final formal late license native optional sealed see serializable shared suppressWarnings tagged throws variable"), isPunctuationChar:/[\[\]{}\(\),;:\.`]/, isOperatorChar:/[+\-*&%=<>!?|^~:\/]/, numberStart:/[\d#$]/, 
  number:/^(?:#[\da-fA-F_]+|\$[01_]+|[\d_]+[kMGTPmunpf]?|[\d_]+\.[\d_]+(?:[eE][-+]?\d+|[kMGTPmunpf]|)|)/i, multiLineStrings:!0, typeFirstDefinitions:!0, atoms:words("true false null larger smaller equal empty finished"), indentSwitch:!1, styleDefs:!1, hooks:{"@":function(stream) {
    stream.eatWhile(/[\w\$_]/);
    return "meta";
  }, '"':function(stream, state) {
    state.tokenize = tokenCeylonString(stream.match('""') ? "triple" : "single");
    return state.tokenize(stream, state);
  }, "`":function(stream, state) {
    if (!stringTokenizer || !stream.match("`")) {
      return !1;
    }
    state.tokenize = stringTokenizer;
    stringTokenizer = null;
    return state.tokenize(stream, state);
  }, "'":function(stream) {
    stream.eatWhile(/[\w\$_\xa1-\uffff]/);
    return "atom";
  }, token:function(_stream, state, style) {
    if (("variable" == style || "type" == style) && "." == state.prevToken) {
      return "variable-2";
    }
  }}, modeProps:{fold:["brace", "import"], closeBrackets:{triples:'"'}}});
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_609(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_609) : mod$jscomp$inline_609(CodeMirror);
//[third_party/javascript/codemirror4/mode/css/css.js]
function mod$jscomp$inline_611(CodeMirror) {
  function keySet(array) {
    for (var keys = {}, i = 0; i < array.length; ++i) {
      keys[array[i].toLowerCase()] = !0;
    }
    return keys;
  }
  function tokenCComment(stream, state) {
    for (var maybeEnd = !1, ch; null != (ch = stream.next());) {
      if (maybeEnd && "/" == ch) {
        state.tokenize = null;
        break;
      }
      maybeEnd = "*" == ch;
    }
    return ["comment", "comment"];
  }
  CodeMirror.defineMode("css", function(config, parserConfig) {
    function ret(style, tp) {
      type$jscomp$0 = tp;
      return style;
    }
    function tokenBase(stream, state) {
      var ch = stream.next();
      if (tokenHooks[ch]) {
        var result = tokenHooks[ch](stream, state);
        if (!1 !== result) {
          return result;
        }
      }
      if ("@" == ch) {
        return stream.eatWhile(/[\w\\\-]/), ret("def", stream.current());
      }
      if ("=" == ch || ("~" == ch || "|" == ch) && stream.eat("=")) {
        return ret(null, "compare");
      }
      if ('"' == ch || "'" == ch) {
        return state.tokenize = tokenString(ch), state.tokenize(stream, state);
      }
      if ("#" == ch) {
        return stream.eatWhile(/[\w\\\-]/), ret("atom", "hash");
      }
      if ("!" == ch) {
        return stream.match(/^\s*\w*/), ret("keyword", "important");
      }
      if (/\d/.test(ch) || "." == ch && stream.eat(/\d/)) {
        return stream.eatWhile(/[\w.%]/), ret("number", "unit");
      }
      if ("-" === ch) {
        if (/[\d.]/.test(stream.peek())) {
          return stream.eatWhile(/[\w.%]/), ret("number", "unit");
        }
        if (stream.match(/^-[\w\\\-]*/)) {
          return stream.eatWhile(/[\w\\\-]/), stream.match(/^\s*:/, !1) ? ret("variable-2", "variable-definition") : ret("variable-2", "variable");
        }
        if (stream.match(/^\w+-/)) {
          return ret("meta", "meta");
        }
      } else {
        return /[,+>*\/]/.test(ch) ? ret(null, "select-op") : "." == ch && stream.match(/^-?[_a-z][_a-z0-9-]*/i) ? ret("qualifier", "qualifier") : /[:;{}\[\]\(\)]/.test(ch) ? ret(null, ch) : stream.match(/^[\w-.]+(?=\()/) ? (/^(url(-prefix)?|domain|regexp)$/i.test(stream.current()) && (state.tokenize = tokenParenthesized), ret("variable callee", "variable")) : /[\w\\\-]/.test(ch) ? (stream.eatWhile(/[\w\\\-]/), ret("property", "word")) : ret(null, null);
      }
    }
    function tokenString(quote) {
      return function(stream, state) {
        for (var escaped = !1, ch; null != (ch = stream.next());) {
          if (ch == quote && !escaped) {
            ")" == quote && stream.backUp(1);
            break;
          }
          escaped = !escaped && "\\" == ch;
        }
        if (ch == quote || !escaped && ")" != quote) {
          state.tokenize = null;
        }
        return ret("string", "string");
      };
    }
    function tokenParenthesized(stream, state) {
      stream.next();
      stream.match(/^\s*["')]/, !1) ? state.tokenize = null : state.tokenize = tokenString(")");
      return ret(null, "(");
    }
    function Context(type, indent, prev) {
      this.type = type;
      this.indent = indent;
      this.prev = prev;
    }
    function pushContext(state, stream, type, indent) {
      state.context = new Context(type, stream.indentation() + (!1 === indent ? 0 : indentUnit), state.context);
      return type;
    }
    function popContext(state) {
      state.context.prev && (state.context = state.context.prev);
      return state.context.type;
    }
    function popAndPass(type, stream, state, i$jscomp$307_n) {
      for (i$jscomp$307_n = i$jscomp$307_n || 1; 0 < i$jscomp$307_n; i$jscomp$307_n--) {
        state.context = state.context.prev;
      }
      return states[state.context.type](type, stream, state);
    }
    function wordAsValue(stream$jscomp$64_word) {
      stream$jscomp$64_word = stream$jscomp$64_word.current().toLowerCase();
      override = valueKeywords.hasOwnProperty(stream$jscomp$64_word) ? "atom" : colorKeywords.hasOwnProperty(stream$jscomp$64_word) ? "keyword" : "variable";
    }
    var inline = parserConfig.inline;
    parserConfig.propertyKeywords || (parserConfig = CodeMirror.resolveMode("text/css"));
    var indentUnit = config.indentUnit, tokenHooks = parserConfig.tokenHooks, documentTypes = parserConfig.documentTypes || {}, mediaTypes = parserConfig.mediaTypes || {}, mediaFeatures = parserConfig.mediaFeatures || {}, mediaValueKeywords = parserConfig.mediaValueKeywords || {}, propertyKeywords = parserConfig.propertyKeywords || {}, nonStandardPropertyKeywords = parserConfig.nonStandardPropertyKeywords || {}, fontProperties = parserConfig.fontProperties || {}, counterDescriptors = parserConfig.counterDescriptors || 
    {}, colorKeywords = parserConfig.colorKeywords || {}, valueKeywords = parserConfig.valueKeywords || {}, allowNested = parserConfig.allowNested, supportsAtComponent = !0 === parserConfig.supportsAtComponent, highlightNonStandardPropertyKeywords = !1 !== config.highlightNonStandardPropertyKeywords, type$jscomp$0, override, states = {top:function(type, stream, state) {
      if ("{" == type) {
        return pushContext(state, stream, "block");
      }
      if ("}" == type && state.context.prev) {
        return popContext(state);
      }
      if (supportsAtComponent && /@component/i.test(type)) {
        return pushContext(state, stream, "atComponentBlock");
      }
      if (/^@(-moz-)?document$/i.test(type)) {
        return pushContext(state, stream, "documentTypes");
      }
      if (/^@(media|supports|(-moz-)?document|import)$/i.test(type)) {
        return pushContext(state, stream, "atBlock");
      }
      if (/^@(font-face|counter-style)/i.test(type)) {
        return state.stateArg = type, "restricted_atBlock_before";
      }
      if (/^@(-(moz|ms|o|webkit)-)?keyframes$/i.test(type)) {
        return "keyframes";
      }
      if (type && "@" == type.charAt(0)) {
        return pushContext(state, stream, "at");
      }
      if ("hash" == type) {
        override = "builtin";
      } else if ("word" == type) {
        override = "tag";
      } else {
        if ("variable-definition" == type) {
          return "maybeprop";
        }
        if ("interpolation" == type) {
          return pushContext(state, stream, "interpolation");
        }
        if (":" == type) {
          return "pseudo";
        }
        if (allowNested && "(" == type) {
          return pushContext(state, stream, "parens");
        }
      }
      return state.context.type;
    }, block:function(type$jscomp$279_word, stream, state) {
      if ("word" == type$jscomp$279_word) {
        type$jscomp$279_word = stream.current().toLowerCase();
        if (propertyKeywords.hasOwnProperty(type$jscomp$279_word)) {
          return override = "property", "maybeprop";
        }
        if (nonStandardPropertyKeywords.hasOwnProperty(type$jscomp$279_word)) {
          return override = highlightNonStandardPropertyKeywords ? "string-2" : "property", "maybeprop";
        }
        if (allowNested) {
          return override = stream.match(/^\s*:(?:\s|$)/, !1) ? "property" : "tag", "block";
        }
        override += " error";
        return "maybeprop";
      }
      if ("meta" == type$jscomp$279_word) {
        return "block";
      }
      if (allowNested || "hash" != type$jscomp$279_word && "qualifier" != type$jscomp$279_word) {
        return states.top(type$jscomp$279_word, stream, state);
      }
      override = "error";
      return "block";
    }, maybeprop:function(type, stream, state) {
      return ":" == type ? pushContext(state, stream, "prop") : states[state.context.type](type, stream, state);
    }, prop:function(type, stream, state) {
      if (";" == type) {
        return popContext(state);
      }
      if ("{" == type && allowNested) {
        return pushContext(state, stream, "propBlock");
      }
      if ("}" == type || "{" == type) {
        return popAndPass(type, stream, state);
      }
      if ("(" == type) {
        return pushContext(state, stream, "parens");
      }
      if ("hash" == type && !/^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/.test(stream.current())) {
        override += " error";
      } else if ("word" == type) {
        wordAsValue(stream);
      } else if ("interpolation" == type) {
        return pushContext(state, stream, "interpolation");
      }
      return "prop";
    }, propBlock:function(type, _stream, state) {
      return "}" == type ? popContext(state) : "word" == type ? (override = "property", "maybeprop") : state.context.type;
    }, parens:function(type, stream, state) {
      if ("{" == type || "}" == type) {
        return popAndPass(type, stream, state);
      }
      if (")" == type) {
        return popContext(state);
      }
      if ("(" == type) {
        return pushContext(state, stream, "parens");
      }
      if ("interpolation" == type) {
        return pushContext(state, stream, "interpolation");
      }
      "word" == type && wordAsValue(stream);
      return "parens";
    }, pseudo:function(type, stream, state) {
      return "meta" == type ? "pseudo" : "word" == type ? (override = "variable-3", state.context.type) : states[state.context.type](type, stream, state);
    }, documentTypes:function(type, stream, state) {
      return "word" == type && documentTypes.hasOwnProperty(stream.current()) ? (override = "tag", state.context.type) : states.atBlock(type, stream, state);
    }, atBlock:function(type$jscomp$286_word, stream, state) {
      if ("(" == type$jscomp$286_word) {
        return pushContext(state, stream, "atBlock_parens");
      }
      if ("}" == type$jscomp$286_word || ";" == type$jscomp$286_word) {
        return popAndPass(type$jscomp$286_word, stream, state);
      }
      if ("{" == type$jscomp$286_word) {
        return popContext(state) && pushContext(state, stream, allowNested ? "block" : "top");
      }
      if ("interpolation" == type$jscomp$286_word) {
        return pushContext(state, stream, "interpolation");
      }
      "word" == type$jscomp$286_word && (type$jscomp$286_word = stream.current().toLowerCase(), override = "only" == type$jscomp$286_word || "not" == type$jscomp$286_word || "and" == type$jscomp$286_word || "or" == type$jscomp$286_word ? "keyword" : mediaTypes.hasOwnProperty(type$jscomp$286_word) ? "attribute" : mediaFeatures.hasOwnProperty(type$jscomp$286_word) ? "property" : mediaValueKeywords.hasOwnProperty(type$jscomp$286_word) ? "keyword" : propertyKeywords.hasOwnProperty(type$jscomp$286_word) ? 
      "property" : nonStandardPropertyKeywords.hasOwnProperty(type$jscomp$286_word) ? highlightNonStandardPropertyKeywords ? "string-2" : "property" : valueKeywords.hasOwnProperty(type$jscomp$286_word) ? "atom" : colorKeywords.hasOwnProperty(type$jscomp$286_word) ? "keyword" : "error");
      return state.context.type;
    }, atComponentBlock:function(type, stream, state) {
      if ("}" == type) {
        return popAndPass(type, stream, state);
      }
      if ("{" == type) {
        return popContext(state) && pushContext(state, stream, allowNested ? "block" : "top", !1);
      }
      "word" == type && (override = "error");
      return state.context.type;
    }, atBlock_parens:function(type, stream, state) {
      return ")" == type ? popContext(state) : "{" == type || "}" == type ? popAndPass(type, stream, state, 2) : states.atBlock(type, stream, state);
    }, restricted_atBlock_before:function(type, stream, state) {
      return "{" == type ? pushContext(state, stream, "restricted_atBlock") : "word" == type && "@counter-style" == state.stateArg ? (override = "variable", "restricted_atBlock_before") : states[state.context.type](type, stream, state);
    }, restricted_atBlock:function(type, stream, state) {
      return "}" == type ? (state.stateArg = null, popContext(state)) : "word" == type ? (override = "@font-face" == state.stateArg && !fontProperties.hasOwnProperty(stream.current().toLowerCase()) || "@counter-style" == state.stateArg && !counterDescriptors.hasOwnProperty(stream.current().toLowerCase()) ? "error" : "property", "maybeprop") : "restricted_atBlock";
    }, keyframes:function(type, stream, state) {
      return "word" == type ? (override = "variable", "keyframes") : "{" == type ? pushContext(state, stream, "top") : states[state.context.type](type, stream, state);
    }, at:function(type, stream, state) {
      if (";" == type) {
        return popContext(state);
      }
      if ("{" == type || "}" == type) {
        return popAndPass(type, stream, state);
      }
      "word" == type ? override = "tag" : "hash" == type && (override = "builtin");
      return "at";
    }, interpolation:function(type, stream, state) {
      if ("}" == type) {
        return popContext(state);
      }
      if ("{" == type || ";" == type) {
        return popAndPass(type, stream, state);
      }
      "word" == type ? override = "variable" : "variable" != type && "(" != type && ")" != type && (override = "error");
      return "interpolation";
    }};
    return {startState:function(base) {
      return {tokenize:null, state:inline ? "block" : "top", stateArg:null, context:new Context(inline ? "block" : "top", base || 0, null)};
    }, token:function(stream, state) {
      if (!state.tokenize && stream.eatSpace()) {
        return null;
      }
      var style = (state.tokenize || tokenBase)(stream, state);
      style && "object" == typeof style && (type$jscomp$0 = style[1], style = style[0]);
      override = style;
      "comment" != type$jscomp$0 && (state.state = states[state.state](type$jscomp$0, stream, state));
      return override;
    }, indent:function(cx$jscomp$4_state, ch$jscomp$67_textAfter) {
      cx$jscomp$4_state = cx$jscomp$4_state.context;
      ch$jscomp$67_textAfter = ch$jscomp$67_textAfter && ch$jscomp$67_textAfter.charAt(0);
      var indent = cx$jscomp$4_state.indent;
      "prop" != cx$jscomp$4_state.type || "}" != ch$jscomp$67_textAfter && ")" != ch$jscomp$67_textAfter || (cx$jscomp$4_state = cx$jscomp$4_state.prev);
      if (cx$jscomp$4_state.prev) {
        if ("}" == ch$jscomp$67_textAfter && ("block" == cx$jscomp$4_state.type || "top" == cx$jscomp$4_state.type || "interpolation" == cx$jscomp$4_state.type || "restricted_atBlock" == cx$jscomp$4_state.type)) {
          cx$jscomp$4_state = cx$jscomp$4_state.prev, indent = cx$jscomp$4_state.indent;
        } else if (")" == ch$jscomp$67_textAfter && ("parens" == cx$jscomp$4_state.type || "atBlock_parens" == cx$jscomp$4_state.type) || "{" == ch$jscomp$67_textAfter && ("at" == cx$jscomp$4_state.type || "atBlock" == cx$jscomp$4_state.type)) {
          indent = Math.max(0, cx$jscomp$4_state.indent - indentUnit);
        }
      }
      return indent;
    }, electricChars:"}", blockCommentStart:"/*", blockCommentEnd:"*/", blockCommentContinue:" * ", lineComment:parserConfig.lineComment, fold:"brace"};
  });
  var documentTypes_ = ["domain", "regexp", "url", "url-prefix"], documentTypes = keySet(documentTypes_), mediaTypes_ = "all aural braille handheld print projection screen tty tv embossed".split(" "), mediaTypes = keySet(mediaTypes_), mediaFeatures_ = "width min-width max-width height min-height max-height device-width min-device-width max-device-width device-height min-device-height max-device-height aspect-ratio min-aspect-ratio max-aspect-ratio device-aspect-ratio min-device-aspect-ratio max-device-aspect-ratio color min-color max-color color-index min-color-index max-color-index monochrome min-monochrome max-monochrome resolution min-resolution max-resolution scan grid orientation device-pixel-ratio min-device-pixel-ratio max-device-pixel-ratio pointer any-pointer hover any-hover prefers-color-scheme dynamic-range video-dynamic-range".split(" "), 
  mediaFeatures = keySet(mediaFeatures_), mediaValueKeywords_ = "landscape portrait none coarse fine on-demand hover interlace progressive dark light standard high".split(" "), mediaValueKeywords = keySet(mediaValueKeywords_), propertyKeywords_ = "align-content align-items align-self alignment-adjust alignment-baseline all anchor-point animation animation-delay animation-direction animation-duration animation-fill-mode animation-iteration-count animation-name animation-play-state animation-timing-function appearance azimuth backdrop-filter backface-visibility background background-attachment background-blend-mode background-clip background-color background-image background-origin background-position background-position-x background-position-y background-repeat background-size baseline-shift binding bleed block-size bookmark-label bookmark-level bookmark-state bookmark-target border border-bottom border-bottom-color border-bottom-left-radius border-bottom-right-radius border-bottom-style border-bottom-width border-collapse border-color border-image border-image-outset border-image-repeat border-image-slice border-image-source border-image-width border-left border-left-color border-left-style border-left-width border-radius border-right border-right-color border-right-style border-right-width border-spacing border-style border-top border-top-color border-top-left-radius border-top-right-radius border-top-style border-top-width border-width bottom box-decoration-break box-shadow box-sizing break-after break-before break-inside caption-side caret-color clear clip color color-profile column-count column-fill column-gap column-rule column-rule-color column-rule-style column-rule-width column-span column-width columns contain content counter-increment counter-reset crop cue cue-after cue-before cursor direction display dominant-baseline drop-initial-after-adjust drop-initial-after-align drop-initial-before-adjust drop-initial-before-align drop-initial-size drop-initial-value elevation empty-cells fit fit-content fit-position flex flex-basis flex-direction flex-flow flex-grow flex-shrink flex-wrap float float-offset flow-from flow-into font font-family font-feature-settings font-kerning font-language-override font-optical-sizing font-size font-size-adjust font-stretch font-style font-synthesis font-variant font-variant-alternates font-variant-caps font-variant-east-asian font-variant-ligatures font-variant-numeric font-variant-position font-variation-settings font-weight gap grid grid-area grid-auto-columns grid-auto-flow grid-auto-rows grid-column grid-column-end grid-column-gap grid-column-start grid-gap grid-row grid-row-end grid-row-gap grid-row-start grid-template grid-template-areas grid-template-columns grid-template-rows hanging-punctuation height hyphens icon image-orientation image-rendering image-resolution inline-box-align inset inset-block inset-block-end inset-block-start inset-inline inset-inline-end inset-inline-start isolation justify-content justify-items justify-self left letter-spacing line-break line-height line-height-step line-stacking line-stacking-ruby line-stacking-shift line-stacking-strategy list-style list-style-image list-style-position list-style-type margin margin-bottom margin-left margin-right margin-top marks marquee-direction marquee-loop marquee-play-count marquee-speed marquee-style mask-clip mask-composite mask-image mask-mode mask-origin mask-position mask-repeat mask-size mask-type max-block-size max-height max-inline-size max-width min-block-size min-height min-inline-size min-width mix-blend-mode move-to nav-down nav-index nav-left nav-right nav-up object-fit object-position offset offset-anchor offset-distance offset-path offset-position offset-rotate opacity order orphans outline outline-color outline-offset outline-style outline-width overflow overflow-style overflow-wrap overflow-x overflow-y padding padding-bottom padding-left padding-right padding-top page page-break-after page-break-before page-break-inside page-policy pause pause-after pause-before perspective perspective-origin pitch pitch-range place-content place-items place-self play-during position presentation-level punctuation-trim quotes region-break-after region-break-before region-break-inside region-fragment rendering-intent resize rest rest-after rest-before richness right rotate rotation rotation-point row-gap ruby-align ruby-overhang ruby-position ruby-span scale scroll-behavior scroll-margin scroll-margin-block scroll-margin-block-end scroll-margin-block-start scroll-margin-bottom scroll-margin-inline scroll-margin-inline-end scroll-margin-inline-start scroll-margin-left scroll-margin-right scroll-margin-top scroll-padding scroll-padding-block scroll-padding-block-end scroll-padding-block-start scroll-padding-bottom scroll-padding-inline scroll-padding-inline-end scroll-padding-inline-start scroll-padding-left scroll-padding-right scroll-padding-top scroll-snap-align scroll-snap-type shape-image-threshold shape-inside shape-margin shape-outside size speak speak-as speak-header speak-numeral speak-punctuation speech-rate stress string-set tab-size table-layout target target-name target-new target-position text-align text-align-last text-combine-upright text-decoration text-decoration-color text-decoration-line text-decoration-skip text-decoration-skip-ink text-decoration-style text-emphasis text-emphasis-color text-emphasis-position text-emphasis-style text-height text-indent text-justify text-orientation text-outline text-overflow text-rendering text-shadow text-size-adjust text-space-collapse text-transform text-underline-position text-wrap top touch-action transform transform-origin transform-style transition transition-delay transition-duration transition-property transition-timing-function translate unicode-bidi user-select vertical-align visibility voice-balance voice-duration voice-family voice-pitch voice-range voice-rate voice-stress voice-volume volume white-space widows width will-change word-break word-spacing word-wrap writing-mode z-index clip-path clip-rule mask enable-background filter flood-color flood-opacity lighting-color stop-color stop-opacity pointer-events color-interpolation color-interpolation-filters color-rendering fill fill-opacity fill-rule image-rendering marker marker-end marker-mid marker-start paint-order shape-rendering stroke stroke-dasharray stroke-dashoffset stroke-linecap stroke-linejoin stroke-miterlimit stroke-opacity stroke-width text-rendering baseline-shift dominant-baseline glyph-orientation-horizontal glyph-orientation-vertical text-anchor writing-mode".split(" "), 
  propertyKeywords = keySet(propertyKeywords_), nonStandardPropertyKeywords_ = "accent-color aspect-ratio border-block border-block-color border-block-end border-block-end-color border-block-end-style border-block-end-width border-block-start border-block-start-color border-block-start-style border-block-start-width border-block-style border-block-width border-inline border-inline-color border-inline-end border-inline-end-color border-inline-end-style border-inline-end-width border-inline-start border-inline-start-color border-inline-start-style border-inline-start-width border-inline-style border-inline-width content-visibility margin-block margin-block-end margin-block-start margin-inline margin-inline-end margin-inline-start overflow-anchor overscroll-behavior padding-block padding-block-end padding-block-start padding-inline padding-inline-end padding-inline-start scroll-snap-stop scrollbar-3d-light-color scrollbar-arrow-color scrollbar-base-color scrollbar-dark-shadow-color scrollbar-face-color scrollbar-highlight-color scrollbar-shadow-color scrollbar-track-color searchfield-cancel-button searchfield-decoration searchfield-results-button searchfield-results-decoration shape-inside zoom".split(" "), 
  nonStandardPropertyKeywords = keySet(nonStandardPropertyKeywords_), fontProperties = keySet("font-display font-family src unicode-range font-variant font-feature-settings font-stretch font-weight font-style".split(" ")), counterDescriptors = keySet("additive-symbols fallback negative pad prefix range speak-as suffix symbols system".split(" ")), colorKeywords_ = "aliceblue antiquewhite aqua aquamarine azure beige bisque black blanchedalmond blue blueviolet brown burlywood cadetblue chartreuse chocolate coral cornflowerblue cornsilk crimson cyan darkblue darkcyan darkgoldenrod darkgray darkgreen darkgrey darkkhaki darkmagenta darkolivegreen darkorange darkorchid darkred darksalmon darkseagreen darkslateblue darkslategray darkslategrey darkturquoise darkviolet deeppink deepskyblue dimgray dimgrey dodgerblue firebrick floralwhite forestgreen fuchsia gainsboro ghostwhite gold goldenrod gray grey green greenyellow honeydew hotpink indianred indigo ivory khaki lavender lavenderblush lawngreen lemonchiffon lightblue lightcoral lightcyan lightgoldenrodyellow lightgray lightgreen lightgrey lightpink lightsalmon lightseagreen lightskyblue lightslategray lightslategrey lightsteelblue lightyellow lime limegreen linen magenta maroon mediumaquamarine mediumblue mediumorchid mediumpurple mediumseagreen mediumslateblue mediumspringgreen mediumturquoise mediumvioletred midnightblue mintcream mistyrose moccasin navajowhite navy oldlace olive olivedrab orange orangered orchid palegoldenrod palegreen paleturquoise palevioletred papayawhip peachpuff peru pink plum powderblue purple rebeccapurple red rosybrown royalblue saddlebrown salmon sandybrown seagreen seashell sienna silver skyblue slateblue slategray slategrey snow springgreen steelblue tan teal thistle tomato turquoise violet wheat white whitesmoke yellow yellowgreen".split(" "), 
  colorKeywords = keySet(colorKeywords_), valueKeywords_ = "above absolute activeborder additive activecaption afar after-white-space ahead alias all all-scroll alphabetic alternate always amharic amharic-abegede antialiased appworkspace arabic-indic armenian asterisks attr auto auto-flow avoid avoid-column avoid-page avoid-region axis-pan background backwards baseline below bidi-override binary bengali blink block block-axis blur bold bolder border border-box both bottom break break-all break-word brightness bullets button buttonface buttonhighlight buttonshadow buttontext calc cambodian capitalize caps-lock-indicator caption captiontext caret cell center checkbox circle cjk-decimal cjk-earthly-branch cjk-heavenly-stem cjk-ideographic clear clip close-quote col-resize collapse color color-burn color-dodge column column-reverse compact condensed conic-gradient contain content contents content-box context-menu continuous contrast copy counter counters cover crop cross crosshair cubic-bezier currentcolor cursive cyclic darken dashed decimal decimal-leading-zero default default-button dense destination-atop destination-in destination-out destination-over devanagari difference disc discard disclosure-closed disclosure-open document dot-dash dot-dot-dash dotted double down drop-shadow e-resize ease ease-in ease-in-out ease-out element ellipse ellipsis embed end ethiopic ethiopic-abegede ethiopic-abegede-am-et ethiopic-abegede-gez ethiopic-abegede-ti-er ethiopic-abegede-ti-et ethiopic-halehame-aa-er ethiopic-halehame-aa-et ethiopic-halehame-am-et ethiopic-halehame-gez ethiopic-halehame-om-et ethiopic-halehame-sid-et ethiopic-halehame-so-et ethiopic-halehame-ti-er ethiopic-halehame-ti-et ethiopic-halehame-tig ethiopic-numeric ew-resize exclusion expanded extends extra-condensed extra-expanded fantasy fast fill fill-box fixed flat flex flex-end flex-start footnotes forwards from geometricPrecision georgian grayscale graytext grid groove gujarati gurmukhi hand hangul hangul-consonant hard-light hebrew help hidden hide higher highlight highlighttext hiragana hiragana-iroha horizontal hsl hsla hue hue-rotate icon ignore inactiveborder inactivecaption inactivecaptiontext infinite infobackground infotext inherit initial inline inline-axis inline-block inline-flex inline-grid inline-table inset inside intrinsic invert italic japanese-formal japanese-informal justify kannada katakana katakana-iroha keep-all khmer korean-hangul-formal korean-hanja-formal korean-hanja-informal landscape lao large larger left level lighter lighten line-through linear linear-gradient lines list-item listbox listitem local logical loud lower lower-alpha lower-armenian lower-greek lower-hexadecimal lower-latin lower-norwegian lower-roman lowercase ltr luminosity malayalam manipulation match matrix matrix3d media-play-button media-slider media-sliderthumb media-volume-slider media-volume-sliderthumb medium menu menulist menulist-button menutext message-box middle min-intrinsic mix mongolian monospace move multiple multiple_mask_images multiply myanmar n-resize narrower ne-resize nesw-resize no-close-quote no-drop no-open-quote no-repeat none normal not-allowed nowrap ns-resize numbers numeric nw-resize nwse-resize oblique octal opacity open-quote optimizeLegibility optimizeSpeed oriya oromo outset outside outside-shape overlay overline padding padding-box painted page paused persian perspective pinch-zoom plus-darker plus-lighter pointer polygon portrait pre pre-line pre-wrap preserve-3d progress push-button radial-gradient radio read-only read-write read-write-plaintext-only rectangle region relative repeat repeating-linear-gradient repeating-radial-gradient repeating-conic-gradient repeat-x repeat-y reset reverse rgb rgba ridge right rotate rotate3d rotateX rotateY rotateZ round row row-resize row-reverse rtl run-in running s-resize sans-serif saturate saturation scale scale3d scaleX scaleY scaleZ screen scroll scrollbar scroll-position se-resize searchfield searchfield-cancel-button searchfield-decoration searchfield-results-button searchfield-results-decoration self-start self-end semi-condensed semi-expanded separate sepia serif show sidama simp-chinese-formal simp-chinese-informal single skew skewX skewY skip-white-space slide slider-horizontal slider-vertical sliderthumb-horizontal sliderthumb-vertical slow small small-caps small-caption smaller soft-light solid somali source-atop source-in source-out source-over space space-around space-between space-evenly spell-out square square-button start static status-bar stretch stroke stroke-box sub subpixel-antialiased svg_masks super sw-resize symbolic symbols system-ui table table-caption table-cell table-column table-column-group table-footer-group table-header-group table-row table-row-group tamil telugu text text-bottom text-top textarea textfield thai thick thin threeddarkshadow threedface threedhighlight threedlightshadow threedshadow tibetan tigre tigrinya-er tigrinya-er-abegede tigrinya-et tigrinya-et-abegede to top trad-chinese-formal trad-chinese-informal transform translate translate3d translateX translateY translateZ transparent ultra-condensed ultra-expanded underline unidirectional-pan unset up upper-alpha upper-armenian upper-greek upper-hexadecimal upper-latin upper-norwegian upper-roman uppercase urdu url var vertical vertical-text view-box visible visibleFill visiblePainted visibleStroke visual w-resize wait wave wider window windowframe windowtext words wrap wrap-reverse x-large x-small xor xx-large xx-small".split(" "), 
  valueKeywords = keySet(valueKeywords_);
  CodeMirror.registerHelper("hintWords", "css", documentTypes_.concat(mediaTypes_).concat(mediaFeatures_).concat(mediaValueKeywords_).concat(propertyKeywords_).concat(nonStandardPropertyKeywords_).concat(colorKeywords_).concat(valueKeywords_));
  CodeMirror.defineMIME("text/css", {documentTypes, mediaTypes, mediaFeatures, mediaValueKeywords, propertyKeywords, nonStandardPropertyKeywords, fontProperties, counterDescriptors, colorKeywords, valueKeywords, tokenHooks:{"/":function(stream, state) {
    if (!stream.eat("*")) {
      return !1;
    }
    state.tokenize = tokenCComment;
    return tokenCComment(stream, state);
  }}, name:"css"});
  CodeMirror.defineMIME("text/x-scss", {mediaTypes, mediaFeatures, mediaValueKeywords, propertyKeywords, nonStandardPropertyKeywords, colorKeywords, valueKeywords, fontProperties, allowNested:!0, lineComment:"//", tokenHooks:{"/":function(stream, state) {
    return stream.eat("/") ? (stream.skipToEnd(), ["comment", "comment"]) : stream.eat("*") ? (state.tokenize = tokenCComment, tokenCComment(stream, state)) : ["operator", "operator"];
  }, ":":function(stream) {
    return stream.match(/^\s*\{/, !1) ? [null, null] : !1;
  }, $:function(stream) {
    stream.match(/^[\w-]+/);
    return stream.match(/^\s*:/, !1) ? ["variable-2", "variable-definition"] : ["variable-2", "variable"];
  }, "#":function(stream) {
    return stream.eat("{") ? [null, "interpolation"] : !1;
  }}, name:"css", helperType:"scss"});
  CodeMirror.defineMIME("text/x-less", {mediaTypes, mediaFeatures, mediaValueKeywords, propertyKeywords, nonStandardPropertyKeywords, colorKeywords, valueKeywords, fontProperties, allowNested:!0, lineComment:"//", tokenHooks:{"/":function(stream, state) {
    return stream.eat("/") ? (stream.skipToEnd(), ["comment", "comment"]) : stream.eat("*") ? (state.tokenize = tokenCComment, tokenCComment(stream, state)) : ["operator", "operator"];
  }, "@":function(stream) {
    if (stream.eat("{")) {
      return [null, "interpolation"];
    }
    if (stream.match(/^(charset|document|font-face|import|(-(moz|ms|o|webkit)-)?keyframes|media|namespace|page|supports)\b/i, !1)) {
      return !1;
    }
    stream.eatWhile(/[\w\\\-]/);
    return stream.match(/^\s*:/, !1) ? ["variable-2", "variable-definition"] : ["variable-2", "variable"];
  }, "&":function() {
    return ["atom", "atom"];
  }}, name:"css", helperType:"less"});
  CodeMirror.defineMIME("text/x-gss", {documentTypes, mediaTypes, mediaFeatures, propertyKeywords, nonStandardPropertyKeywords, fontProperties, counterDescriptors, colorKeywords, valueKeywords, supportsAtComponent:!0, tokenHooks:{"/":function(stream, state) {
    if (!stream.eat("*")) {
      return !1;
    }
    state.tokenize = tokenCComment;
    return tokenCComment(stream, state);
  }}, name:"css", helperType:"gss"});
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_611(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_611) : mod$jscomp$inline_611(CodeMirror);
//[third_party/javascript/codemirror4/mode/go/go.js]
function mod$jscomp$inline_613(CodeMirror) {
  CodeMirror.defineMode("go", function(config) {
    function tokenBase(cur$jscomp$29_stream, state) {
      var ch = cur$jscomp$29_stream.next();
      if ('"' == ch || "'" == ch || "`" == ch) {
        return state.tokenize = tokenString(ch), state.tokenize(cur$jscomp$29_stream, state);
      }
      if (/[\d\.]/.test(ch)) {
        return "." == ch ? cur$jscomp$29_stream.match(/^[0-9]+([eE][\-+]?[0-9]+)?/) : "0" == ch ? cur$jscomp$29_stream.match(/^[xX][0-9a-fA-F]+/) || cur$jscomp$29_stream.match(/^0[0-7]+/) : cur$jscomp$29_stream.match(/^[0-9]*\.?[0-9]*([eE][\-+]?[0-9]+)?/), "number";
      }
      if (/[\[\]{}\(\),;:\.]/.test(ch)) {
        return curPunc = ch, null;
      }
      if ("/" == ch) {
        if (cur$jscomp$29_stream.eat("*")) {
          return state.tokenize = tokenComment, tokenComment(cur$jscomp$29_stream, state);
        }
        if (cur$jscomp$29_stream.eat("/")) {
          return cur$jscomp$29_stream.skipToEnd(), "comment";
        }
      }
      if (isOperatorChar.test(ch)) {
        return cur$jscomp$29_stream.eatWhile(isOperatorChar), "operator";
      }
      cur$jscomp$29_stream.eatWhile(/[\w\$_\xa1-\uffff]/);
      cur$jscomp$29_stream = cur$jscomp$29_stream.current();
      if (keywords.propertyIsEnumerable(cur$jscomp$29_stream)) {
        if ("case" == cur$jscomp$29_stream || "default" == cur$jscomp$29_stream) {
          curPunc = "case";
        }
        return "keyword";
      }
      return atoms.propertyIsEnumerable(cur$jscomp$29_stream) ? "atom" : "variable";
    }
    function tokenString(quote) {
      return function(stream, state) {
        for (var escaped = !1, next, end = !1; null != (next = stream.next());) {
          if (next == quote && !escaped) {
            end = !0;
            break;
          }
          escaped = !escaped && "`" != quote && "\\" == next;
        }
        if (end || !escaped && "`" != quote) {
          state.tokenize = tokenBase;
        }
        return "string";
      };
    }
    function tokenComment(stream, state) {
      for (var maybeEnd = !1, ch; ch = stream.next();) {
        if ("/" == ch && maybeEnd) {
          state.tokenize = tokenBase;
          break;
        }
        maybeEnd = "*" == ch;
      }
      return "comment";
    }
    function Context(indented, column, type, align, prev) {
      this.indented = indented;
      this.column = column;
      this.type = type;
      this.align = align;
      this.prev = prev;
    }
    function pushContext(state, col, type) {
      return state.context = new Context(state.indented, col, type, null, state.context);
    }
    function popContext(state) {
      if (state.context.prev) {
        var t = state.context.type;
        if (")" == t || "]" == t || "}" == t) {
          state.indented = state.context.indented;
        }
        return state.context = state.context.prev;
      }
    }
    var indentUnit = config.indentUnit, keywords = {"break":!0, "case":!0, chan:!0, "const":!0, "continue":!0, "default":!0, defer:!0, "else":!0, fallthrough:!0, "for":!0, func:!0, go:!0, "goto":!0, "if":!0, "import":!0, "interface":!0, map:!0, "package":!0, range:!0, "return":!0, select:!0, struct:!0, "switch":!0, type:!0, "var":!0, bool:!0, "byte":!0, complex64:!0, complex128:!0, float32:!0, float64:!0, int8:!0, int16:!0, int32:!0, int64:!0, string:!0, uint8:!0, uint16:!0, uint32:!0, uint64:!0, 
    "int":!0, uint:!0, uintptr:!0, error:!0, rune:!0, any:!0, comparable:!0}, atoms = {"true":!0, "false":!0, iota:!0, nil:!0, append:!0, cap:!0, close:!0, complex:!0, copy:!0, "delete":!0, imag:!0, len:!0, make:!0, "new":!0, panic:!0, print:!0, println:!0, real:!0, recover:!0}, isOperatorChar = /[+\-*&^%:=<>!|\/]/, curPunc;
    return {startState:function(basecolumn) {
      return {tokenize:null, context:new Context((basecolumn || 0) - indentUnit, 0, "top", !1), indented:0, startOfLine:!0};
    }, token:function(stream, state) {
      var ctx = state.context;
      stream.sol() && (null == ctx.align && (ctx.align = !1), state.indented = stream.indentation(), state.startOfLine = !0, "case" == ctx.type && (ctx.type = "}"));
      if (stream.eatSpace()) {
        return null;
      }
      curPunc = null;
      var style = (state.tokenize || tokenBase)(stream, state);
      if ("comment" == style) {
        return style;
      }
      null == ctx.align && (ctx.align = !0);
      "{" == curPunc ? pushContext(state, stream.column(), "}") : "[" == curPunc ? pushContext(state, stream.column(), "]") : "(" == curPunc ? pushContext(state, stream.column(), ")") : "case" == curPunc ? ctx.type = "case" : "}" == curPunc && "}" == ctx.type ? popContext(state) : curPunc == ctx.type && popContext(state);
      state.startOfLine = !1;
      return style;
    }, indent:function(closing$jscomp$3_state, textAfter) {
      if (closing$jscomp$3_state.tokenize != tokenBase && null != closing$jscomp$3_state.tokenize) {
        return CodeMirror.Pass;
      }
      var ctx = closing$jscomp$3_state.context, firstChar = textAfter && textAfter.charAt(0);
      if ("case" == ctx.type && /^(?:case|default)\b/.test(textAfter)) {
        return closing$jscomp$3_state.context.type = "}", ctx.indented;
      }
      closing$jscomp$3_state = firstChar == ctx.type;
      return ctx.align ? ctx.column + (closing$jscomp$3_state ? 0 : 1) : ctx.indented + (closing$jscomp$3_state ? 0 : indentUnit);
    }, electricChars:"{}):", closeBrackets:"()[]{}''\"\"``", fold:"brace", blockCommentStart:"/*", blockCommentEnd:"*/", lineComment:"//"};
  });
  CodeMirror.defineMIME("text/x-go", "go");
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_613(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_613) : mod$jscomp$inline_613(CodeMirror);
//[third_party/javascript/codemirror4/mode/python/python.js]
function mod$jscomp$inline_621(CodeMirror) {
  function wordRegexp(words) {
    return new RegExp("^((" + words.join(")|(") + "))\\b");
  }
  function top(state) {
    return state.scopes[state.scopes.length - 1];
  }
  var wordOperators = wordRegexp(["and", "or", "not", "is"]), commonKeywords = "as assert break class continue def del elif else except finally for from global if import lambda pass raise return try while with yield in".split(" "), commonBuiltins = "abs all any bin bool bytearray callable chr classmethod compile complex delattr dict dir divmod enumerate eval filter float format frozenset getattr globals hasattr hash help hex id input int isinstance issubclass iter len list locals map max memoryview min next object oct open ord pow property range repr reversed round set setattr slice sorted staticmethod str sum super tuple type vars zip __import__ NotImplemented Ellipsis __debug__".split(" ");
  CodeMirror.registerHelper("hintWords", "python", commonKeywords.concat(commonBuiltins));
  CodeMirror.defineMode("python", function(conf, parserConf) {
    function tokenBase(stream, state) {
      var scopeOffset_sol = stream.sol() && "\\" != state.lastToken;
      scopeOffset_sol && (state.indent = stream.indentation());
      if (scopeOffset_sol && "py" == top(state).type) {
        scopeOffset_sol = top(state).offset;
        if (stream.eatSpace()) {
          var lineOffset_style = stream.indentation();
          lineOffset_style > scopeOffset_sol ? pushPyScope(state) : lineOffset_style < scopeOffset_sol && dedent(stream, state) && "#" != stream.peek() && (state.errorToken = !0);
          return null;
        }
        lineOffset_style = tokenBaseInner(stream, state);
        0 < scopeOffset_sol && dedent(stream, state) && (lineOffset_style += " error");
        return lineOffset_style;
      }
      return tokenBaseInner(stream, state);
    }
    function tokenBaseInner(stream, state, inFormat) {
      if (stream.eatSpace()) {
        return null;
      }
      if (!inFormat && stream.match(/^#.*/)) {
        return "comment";
      }
      if (stream.match(/^[0-9\.]/, !1)) {
        var floatLiteral_i = !1;
        stream.match(/^[\d_]*\.\d+(e[\+\-]?\d+)?/i) && (floatLiteral_i = !0);
        stream.match(/^[\d_]+\.\d*/) && (floatLiteral_i = !0);
        stream.match(/^\.\d+/) && (floatLiteral_i = !0);
        if (floatLiteral_i) {
          return stream.eat(/J/i), "number";
        }
        floatLiteral_i = !1;
        stream.match(/^0x[0-9a-f_]+/i) && (floatLiteral_i = !0);
        stream.match(/^0b[01_]+/i) && (floatLiteral_i = !0);
        stream.match(/^0o[0-7_]+/i) && (floatLiteral_i = !0);
        stream.match(/^[1-9][\d_]*(e[\+\-]?[\d_]+)?/) && (stream.eat(/J/i), floatLiteral_i = !0);
        stream.match(/^0(?![\dx])/i) && (floatLiteral_i = !0);
        if (floatLiteral_i) {
          return stream.eat(/L/i), "number";
        }
      }
      if (stream.match(stringPrefixes)) {
        return -1 === stream.current().toLowerCase().indexOf("f") ? state.tokenize = tokenStringFactory(stream.current(), state.tokenize) : state.tokenize = formatStringFactory(stream.current(), state.tokenize), state.tokenize(stream, state);
      }
      for (floatLiteral_i = 0; floatLiteral_i < operators.length; floatLiteral_i++) {
        if (stream.match(operators[floatLiteral_i])) {
          return "operator";
        }
      }
      if (stream.match(delimiters)) {
        return "punctuation";
      }
      if ("." == state.lastToken && stream.match(identifiers)) {
        return "property";
      }
      if (stream.match(keywords) || stream.match(wordOperators)) {
        return "keyword";
      }
      if (stream.match(builtins)) {
        return "builtin";
      }
      if (stream.match(/^(self|cls)\b/)) {
        return "variable-2";
      }
      if (stream.match(identifiers)) {
        return "def" == state.lastToken || "class" == state.lastToken ? "def" : "variable";
      }
      stream.next();
      return inFormat ? null : "error";
    }
    function formatStringFactory(delimiter, tokenOuter) {
      function tokenNestedExpr(depth) {
        return function(stream, state) {
          var inner = tokenBaseInner(stream, state, !0);
          "punctuation" == inner && ("{" == stream.current() ? state.tokenize = tokenNestedExpr(depth + 1) : "}" == stream.current() && (1 < depth ? state.tokenize = tokenNestedExpr(depth - 1) : state.tokenize = tokenString));
          return inner;
        };
      }
      function tokenString(stream, state) {
        for (; !stream.eol();) {
          if (stream.eatWhile(/[^'"\{\}\\]/), stream.eat("\\")) {
            if (stream.next(), singleline && stream.eol()) {
              return "string";
            }
          } else {
            if (stream.match(delimiter)) {
              return state.tokenize = tokenOuter, "string";
            }
            if (stream.match("{{")) {
              return "string";
            }
            if (stream.match("{", !1)) {
              return state.tokenize = tokenNestedExpr(0), stream.current() ? "string" : state.tokenize(stream, state);
            }
            if (stream.match("}}")) {
              return "string";
            }
            if (stream.match("}")) {
              return "error";
            }
            stream.eat(/['"]/);
          }
        }
        if (singleline) {
          if (parserConf.singleLineStringErrors) {
            return "error";
          }
          state.tokenize = tokenOuter;
        }
        return "string";
      }
      for (; 0 <= "rubf".indexOf(delimiter.charAt(0).toLowerCase());) {
        delimiter = delimiter.substr(1);
      }
      var singleline = 1 == delimiter.length;
      tokenString.isString = !0;
      return tokenString;
    }
    function tokenStringFactory(delimiter, tokenOuter) {
      function tokenString(stream, state) {
        for (; !stream.eol();) {
          if (stream.eatWhile(/[^'"\\]/), stream.eat("\\")) {
            if (stream.next(), singleline && stream.eol()) {
              return "string";
            }
          } else {
            if (stream.match(delimiter)) {
              return state.tokenize = tokenOuter, "string";
            }
            stream.eat(/['"]/);
          }
        }
        if (singleline) {
          if (parserConf.singleLineStringErrors) {
            return "error";
          }
          state.tokenize = tokenOuter;
        }
        return "string";
      }
      for (; 0 <= "rubf".indexOf(delimiter.charAt(0).toLowerCase());) {
        delimiter = delimiter.substr(1);
      }
      var singleline = 1 == delimiter.length;
      tokenString.isString = !0;
      return tokenString;
    }
    function pushPyScope(state) {
      for (; "py" != top(state).type;) {
        state.scopes.pop();
      }
      state.scopes.push({offset:top(state).offset + conf.indentUnit, type:"py", align:null});
    }
    function dedent(indented$jscomp$5_stream, state) {
      for (indented$jscomp$5_stream = indented$jscomp$5_stream.indentation(); 1 < state.scopes.length && top(state).offset > indented$jscomp$5_stream;) {
        if ("py" != top(state).type) {
          return !0;
        }
        state.scopes.pop();
      }
      return top(state).offset != indented$jscomp$5_stream;
    }
    for (var delimiters = parserConf.delimiters || parserConf.singleDelimiters || /^[\(\)\[\]\{\}@,:`=;\.\\]/, operators = [parserConf.singleOperators, parserConf.doubleOperators, parserConf.doubleDelimiters, parserConf.tripleDelimiters, parserConf.operators || /^([-+*/%\/&|^]=?|[<>=]+|\/\/=?|\*\*=?|!=|[~!@]|\.\.\.)/], i = 0; i < operators.length; i++) {
      operators[i] || operators.splice(i--, 1);
    }
    var hangingIndent = parserConf.hangingIndent || conf.indentUnit;
    i = commonKeywords;
    var myBuiltins = commonBuiltins;
    void 0 != parserConf.extra_keywords && (i = i.concat(parserConf.extra_keywords));
    void 0 != parserConf.extra_builtins && (myBuiltins = myBuiltins.concat(parserConf.extra_builtins));
    var py3 = !(parserConf.version && 3 > Number(parserConf.version));
    if (py3) {
      var identifiers = parserConf.identifiers || /^[_A-Za-z\u00A1-\uFFFF][_A-Za-z0-9\u00A1-\uFFFF]*/;
      i = i.concat("nonlocal False True None async await".split(" "));
      myBuiltins = myBuiltins.concat(["ascii", "bytes", "exec", "print"]);
      var stringPrefixes = RegExp("^(([rbuf]|(br)|(rb)|(fr)|(rf))?('{3}|\"{3}|['\"]))", "i");
    } else {
      identifiers = parserConf.identifiers || /^[_A-Za-z][_A-Za-z0-9]*/, i = i.concat(["exec", "print"]), myBuiltins = myBuiltins.concat("apply basestring buffer cmp coerce execfile file intern long raw_input reduce reload unichr unicode xrange False True None".split(" ")), stringPrefixes = RegExp("^(([rubf]|(ur)|(br))?('{3}|\"{3}|['\"]))", "i");
    }
    var keywords = wordRegexp(i), builtins = wordRegexp(myBuiltins);
    return {startState:function(basecolumn) {
      return {tokenize:tokenBase, scopes:[{offset:basecolumn || 0, type:"py", align:null}], indent:basecolumn || 0, lastToken:null, lambda:!1, dedent:0};
    }, token:function(stream, state) {
      var addErr = state.errorToken;
      addErr && (state.errorToken = !1);
      a: {
        stream.sol() && (state.beginningOfLine = !0, state.dedent = !1);
        var style$jscomp$47_style = state.tokenize(stream, state);
        var current = stream.current();
        if (state.beginningOfLine && "@" == current) {
          style$jscomp$47_style = stream.match(identifiers, !1) ? "meta" : py3 ? "operator" : "error";
        } else {
          /\S/.test(current) && (state.beginningOfLine = !1);
          "variable" != style$jscomp$47_style && "builtin" != style$jscomp$47_style || "meta" != state.lastToken || (style$jscomp$47_style = "meta");
          if ("pass" == current || "return" == current) {
            state.dedent = !0;
          }
          "lambda" == current && (state.lambda = !0);
          ":" == current && !state.lambda && "py" == top(state).type && stream.match(/^\s*(?:#|$)/, !1) && pushPyScope(state);
          if (1 == current.length && !/string|comment/.test(style$jscomp$47_style)) {
            var delimiter_index$jscomp$inline_619_type = "[({".indexOf(current);
            if (-1 != delimiter_index$jscomp$inline_619_type) {
              delimiter_index$jscomp$inline_619_type = "])}".slice(delimiter_index$jscomp$inline_619_type, delimiter_index$jscomp$inline_619_type + 1);
              var align = stream.match(/^[\s\[\{\(]*(?:#|$)/, !1) ? null : stream.column() + 1;
              state.scopes.push({offset:state.indent + hangingIndent, type:delimiter_index$jscomp$inline_619_type, align});
            }
            delimiter_index$jscomp$inline_619_type = "])}".indexOf(current);
            if (-1 != delimiter_index$jscomp$inline_619_type) {
              if (top(state).type == current) {
                state.indent = state.scopes.pop().offset - hangingIndent;
              } else {
                style$jscomp$47_style = "error";
                break a;
              }
            }
          }
          state.dedent && stream.eol() && "py" == top(state).type && 1 < state.scopes.length && state.scopes.pop();
        }
      }
      style$jscomp$47_style && "comment" != style$jscomp$47_style && (state.lastToken = "keyword" == style$jscomp$47_style || "punctuation" == style$jscomp$47_style ? stream.current() : style$jscomp$47_style);
      "punctuation" == style$jscomp$47_style && (style$jscomp$47_style = null);
      stream.eol() && state.lambda && (state.lambda = !1);
      return addErr ? style$jscomp$47_style + " error" : style$jscomp$47_style;
    }, indent:function(closing$jscomp$4_state, textAfter) {
      if (closing$jscomp$4_state.tokenize != tokenBase) {
        return closing$jscomp$4_state.tokenize.isString ? CodeMirror.Pass : 0;
      }
      var scope = top(closing$jscomp$4_state);
      closing$jscomp$4_state = scope.type == textAfter.charAt(0) || "py" == scope.type && !closing$jscomp$4_state.dedent && /^(else:|elif |except |finally:)/.test(textAfter);
      return null != scope.align ? scope.align - (closing$jscomp$4_state ? 1 : 0) : scope.offset - (closing$jscomp$4_state ? hangingIndent : 0);
    }, electricInput:/^\s*([\}\]\)]|else:|elif |except |finally:)$/, closeBrackets:{triples:"'\""}, lineComment:"#", fold:"indent"};
  });
  CodeMirror.defineMIME("text/x-python", "python");
  CodeMirror.defineMIME("text/x-cython", {name:"python", extra_keywords:"by cdef cimport cpdef ctypedef enum except extern gil include nogil property public readonly struct union DEF IF ELIF ELSE".split(" ")});
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_621(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_621) : mod$jscomp$inline_621(CodeMirror);
//[third_party/javascript/codemirror4/mode/shell/shell.js]
function mod$jscomp$inline_623(CodeMirror) {
  CodeMirror.defineMode("shell", function() {
    function tokenDollar(stream, state) {
      1 < state.tokens.length && stream.eat("$");
      var ch = stream.next();
      if (/['"({]/.test(ch)) {
        return state.tokens[0] = tokenString(ch, "(" == ch ? "quote" : "{" == ch ? "def" : "string"), tokenize(stream, state);
      }
      /\d/.test(ch) || stream.eatWhile(/\w/);
      state.tokens.shift();
      return "def";
    }
    function define(style, dict) {
      for (var i = 0; i < dict.length; i++) {
        words[dict[i]] = style;
      }
    }
    function tokenBase(stream, cur$jscomp$30_state) {
      if (stream.eatSpace()) {
        return null;
      }
      var heredoc_sol = stream.sol(), ch = stream.next();
      if ("\\" === ch) {
        return stream.next(), null;
      }
      if ("'" === ch || '"' === ch || "`" === ch) {
        return cur$jscomp$30_state.tokens.unshift(tokenString(ch, "`" === ch ? "quote" : "string")), tokenize(stream, cur$jscomp$30_state);
      }
      if ("#" === ch) {
        if (heredoc_sol && stream.eat("!")) {
          return stream.skipToEnd(), "meta";
        }
        stream.skipToEnd();
        return "comment";
      }
      if ("$" === ch) {
        return cur$jscomp$30_state.tokens.unshift(tokenDollar), tokenize(stream, cur$jscomp$30_state);
      }
      if ("+" === ch || "=" === ch) {
        return "operator";
      }
      if ("-" === ch) {
        return stream.eat("-"), stream.eatWhile(/\w/), "attribute";
      }
      if ("<" == ch) {
        if (stream.match("<<")) {
          return "operator";
        }
        if (heredoc_sol = stream.match(/^<-?\s*['"]?([^'"]*)['"]?/)) {
          return cur$jscomp$30_state.tokens.unshift(tokenHeredoc(heredoc_sol[1])), "string-2";
        }
      }
      if (/\d/.test(ch) && (stream.eatWhile(/\d/), stream.eol() || !/\w/.test(stream.peek()))) {
        return "number";
      }
      stream.eatWhile(/[\w-]/);
      cur$jscomp$30_state = stream.current();
      return "=" === stream.peek() && /\w+/.test(cur$jscomp$30_state) ? "def" : words.hasOwnProperty(cur$jscomp$30_state) ? words[cur$jscomp$30_state] : null;
    }
    function tokenString(quote, style) {
      var close = "(" == quote ? ")" : "{" == quote ? "}" : quote;
      return function(stream, state) {
        for (var next, escaped = !1; null != (next = stream.next());) {
          if (next !== close || escaped) {
            if ("$" !== next || escaped || "'" === quote || stream.peek() == close) {
              if (!escaped && quote !== close && next === quote) {
                return state.tokens.unshift(tokenString(quote, style)), tokenize(stream, state);
              }
              if (!escaped && /['"]/.test(next) && !/['"]/.test(quote)) {
                state.tokens.unshift(tokenStringStart(next, "string"));
                stream.backUp(1);
                break;
              }
            } else {
              stream.backUp(1);
              state.tokens.unshift(tokenDollar);
              break;
            }
          } else {
            state.tokens.shift();
            break;
          }
          escaped = !escaped && "\\" === next;
        }
        return style;
      };
    }
    function tokenStringStart(quote, style) {
      return function(stream, state) {
        state.tokens[0] = tokenString(quote, style);
        stream.next();
        return tokenize(stream, state);
      };
    }
    function tokenHeredoc(delim) {
      return function(stream, state) {
        stream.sol() && stream.string == delim && state.tokens.shift();
        stream.skipToEnd();
        return "string-2";
      };
    }
    function tokenize(stream, state) {
      return (state.tokens[0] || tokenBase)(stream, state);
    }
    var words = {}, commonAtoms = ["true", "false"], commonKeywords = "if then do else elif while until for in esac fi fin fil done exit set unset export function".split(" "), commonCommands = "ab awk bash beep cat cc cd chown chmod chroot clear cp curl cut diff echo find gawk gcc get git grep hg kill killall ln ls make mkdir openssl mv nc nl node npm ping ps restart rm rmdir sed service sh shopt shred source sort sleep ssh start stop su sudo svn tee telnet top touch vi vim wall wc wget who write yes zsh".split(" ");
    CodeMirror.registerHelper("hintWords", "shell", commonAtoms.concat(commonKeywords, commonCommands));
    define("atom", commonAtoms);
    define("keyword", commonKeywords);
    define("builtin", commonCommands);
    return {startState:function() {
      return {tokens:[]};
    }, token:function(stream, state) {
      return tokenize(stream, state);
    }, closeBrackets:"()[]{}''\"\"``", lineComment:"#", fold:"brace"};
  });
  CodeMirror.defineMIME("text/x-sh", "shell");
  CodeMirror.defineMIME("application/x-sh", "shell");
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_623(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_623) : mod$jscomp$inline_623(CodeMirror);
//[third_party/javascript/codemirror4/mode/xml/xml.js]
function mod$jscomp$inline_625(CodeMirror) {
  var htmlConfig = {autoSelfClosers:{area:!0, base:!0, br:!0, col:!0, command:!0, embed:!0, frame:!0, hr:!0, img:!0, input:!0, keygen:!0, link:!0, meta:!0, param:!0, source:!0, track:!0, wbr:!0, menuitem:!0}, implicitlyClosed:{dd:!0, li:!0, optgroup:!0, option:!0, p:!0, rp:!0, rt:!0, tbody:!0, td:!0, tfoot:!0, th:!0, tr:!0}, contextGrabbers:{dd:{dd:!0, dt:!0}, dt:{dd:!0, dt:!0}, li:{li:!0}, option:{option:!0, optgroup:!0}, optgroup:{optgroup:!0}, p:{address:!0, article:!0, aside:!0, blockquote:!0, 
  dir:!0, div:!0, dl:!0, fieldset:!0, footer:!0, form:!0, h1:!0, h2:!0, h3:!0, h4:!0, h5:!0, h6:!0, header:!0, hgroup:!0, hr:!0, menu:!0, nav:!0, ol:!0, p:!0, pre:!0, section:!0, table:!0, ul:!0}, rp:{rp:!0, rt:!0}, rt:{rp:!0, rt:!0}, tbody:{tbody:!0, tfoot:!0}, td:{td:!0, th:!0}, tfoot:{tbody:!0}, th:{td:!0, th:!0}, thead:{tbody:!0, tfoot:!0}, tr:{tr:!0}}, doNotIndent:{pre:!0}, allowUnquoted:!0, allowMissing:!0, caseFold:!0}, xmlConfig = {autoSelfClosers:{}, implicitlyClosed:{}, contextGrabbers:{}, 
  doNotIndent:{}, allowUnquoted:!1, allowMissing:!1, allowMissingTagName:!1, caseFold:!1};
  CodeMirror.defineMode("xml", function(defaults, config_) {
    function inText(stream, state) {
      function chain(parser) {
        state.tokenize = parser;
        return parser(stream, state);
      }
      var ch = stream.next();
      if ("<" == ch) {
        if (stream.eat("!")) {
          return stream.eat("[") ? stream.match("CDATA[") ? chain(inBlock("atom", "]]\x3e")) : null : stream.match("--") ? chain(inBlock("comment", "--\x3e")) : stream.match("DOCTYPE", !0, !0) ? (stream.eatWhile(/[\w\._\-]/), chain(doctype(1))) : null;
        }
        if (stream.eat("?")) {
          return stream.eatWhile(/[\w\._\-]/), state.tokenize = inBlock("meta", "?>"), "meta";
        }
        type$jscomp$0 = stream.eat("/") ? "closeTag" : "openTag";
        state.tokenize = inTag;
        return "tag bracket";
      }
      if ("&" == ch) {
        return (stream.eat("#") ? stream.eat("x") ? stream.eatWhile(/[a-fA-F\d]/) && stream.eat(";") : stream.eatWhile(/[\d]/) && stream.eat(";") : stream.eatWhile(/[\w\.\-:]/) && stream.eat(";")) ? "atom" : "error";
      }
      stream.eatWhile(/[^&<]/);
      return null;
    }
    function inTag(next$jscomp$20_stream, state) {
      var ch = next$jscomp$20_stream.next();
      if (">" == ch || "/" == ch && next$jscomp$20_stream.eat(">")) {
        return state.tokenize = inText, type$jscomp$0 = ">" == ch ? "endTag" : "selfcloseTag", "tag bracket";
      }
      if ("=" == ch) {
        return type$jscomp$0 = "equals", null;
      }
      if ("<" == ch) {
        return state.tokenize = inText, state.state = baseState, state.tagName = state.tagStart = null, (next$jscomp$20_stream = state.tokenize(next$jscomp$20_stream, state)) ? next$jscomp$20_stream + " tag error" : "tag error";
      }
      if (/['"]/.test(ch)) {
        return state.tokenize = inAttribute(ch), state.stringStartCol = next$jscomp$20_stream.column(), state.tokenize(next$jscomp$20_stream, state);
      }
      next$jscomp$20_stream.match(/^[^\s\u00a0=<>"']*[^\s\u00a0=<>"'\/]/);
      return "word";
    }
    function inAttribute(quote) {
      function closure(stream, state) {
        for (; !stream.eol();) {
          if (stream.next() == quote) {
            state.tokenize = inTag;
            break;
          }
        }
        return "string";
      }
      closure.isInAttribute = !0;
      return closure;
    }
    function inBlock(style, terminator) {
      return function(stream, state) {
        for (; !stream.eol();) {
          if (stream.match(terminator)) {
            state.tokenize = inText;
            break;
          }
          stream.next();
        }
        return style;
      };
    }
    function doctype(depth) {
      return function(stream, state) {
        for (var ch; null != (ch = stream.next());) {
          if ("<" == ch) {
            return state.tokenize = doctype(depth + 1), state.tokenize(stream, state);
          }
          if (">" == ch) {
            if (1 == depth) {
              state.tokenize = inText;
              break;
            } else {
              return state.tokenize = doctype(depth - 1), state.tokenize(stream, state);
            }
          }
        }
        return "meta";
      };
    }
    function lower(tagName) {
      return tagName && tagName.toLowerCase();
    }
    function Context(state, tagName, startOfLine) {
      this.prev = state.context;
      this.tagName = tagName || "";
      this.indent = state.indented;
      this.startOfLine = startOfLine;
      if (config.doNotIndent.hasOwnProperty(tagName) || state.context && state.context.noIndent) {
        this.noIndent = !0;
      }
    }
    function popContext(state) {
      state.context && (state.context = state.context.prev);
    }
    function maybePopContext(state, nextTagName) {
      for (var parentTagName; state.context;) {
        parentTagName = state.context.tagName;
        if (!config.contextGrabbers.hasOwnProperty(lower(parentTagName)) || !config.contextGrabbers[lower(parentTagName)].hasOwnProperty(lower(nextTagName))) {
          break;
        }
        popContext(state);
      }
    }
    function baseState(type, stream, state) {
      return "openTag" == type ? (state.tagStart = stream.column(), tagNameState) : "closeTag" == type ? closeTagNameState : baseState;
    }
    function tagNameState(type, stream, state) {
      if ("word" == type) {
        return state.tagName = stream.current(), setStyle = "tag", attrState;
      }
      if (config.allowMissingTagName && "endTag" == type) {
        return setStyle = "tag bracket", attrState(type, stream, state);
      }
      setStyle = "error";
      return tagNameState;
    }
    function closeTagNameState(tagName$jscomp$24_type, stream, state) {
      if ("word" == tagName$jscomp$24_type) {
        tagName$jscomp$24_type = stream.current();
        state.context && state.context.tagName != tagName$jscomp$24_type && config.implicitlyClosed.hasOwnProperty(lower(state.context.tagName)) && popContext(state);
        if (state.context && state.context.tagName == tagName$jscomp$24_type || !1 === config.matchClosing) {
          return setStyle = "tag", closeState;
        }
        setStyle = "tag error";
        return closeStateErr;
      }
      if (config.allowMissingTagName && "endTag" == tagName$jscomp$24_type) {
        return setStyle = "tag bracket", closeState(tagName$jscomp$24_type, stream, state);
      }
      setStyle = "error";
      return closeStateErr;
    }
    function closeState(type, _stream, state) {
      if ("endTag" != type) {
        return setStyle = "error", closeState;
      }
      popContext(state);
      return baseState;
    }
    function closeStateErr(type, stream, state) {
      setStyle = "error";
      return closeState(type, stream, state);
    }
    function attrState(type, _stream, state) {
      if ("word" == type) {
        return setStyle = "attribute", attrEqState;
      }
      if ("endTag" == type || "selfcloseTag" == type) {
        var tagName = state.tagName, tagStart = state.tagStart;
        state.tagName = state.tagStart = null;
        "selfcloseTag" == type || config.autoSelfClosers.hasOwnProperty(lower(tagName)) ? maybePopContext(state, tagName) : (maybePopContext(state, tagName), state.context = new Context(state, tagName, tagStart == state.indented));
        return baseState;
      }
      setStyle = "error";
      return attrState;
    }
    function attrEqState(type, stream, state) {
      if ("equals" == type) {
        return attrValueState;
      }
      config.allowMissing || (setStyle = "error");
      return attrState(type, stream, state);
    }
    function attrValueState(type, stream, state) {
      if ("string" == type) {
        return attrContinuedState;
      }
      if ("word" == type && config.allowUnquoted) {
        return setStyle = "string", attrState;
      }
      setStyle = "error";
      return attrState(type, stream, state);
    }
    function attrContinuedState(type, stream, state) {
      return "string" == type ? attrContinuedState : attrState(type, stream, state);
    }
    var indentUnit = defaults.indentUnit, config = {};
    defaults = config_.htmlMode ? htmlConfig : xmlConfig;
    for (var prop in defaults) {
      config[prop] = defaults[prop];
    }
    for (prop in config_) {
      config[prop] = config_[prop];
    }
    var type$jscomp$0, setStyle;
    return {startState:function(baseIndent) {
      var state = {tokenize:inText, state:baseState, indented:baseIndent || 0, tagName:null, tagStart:null, context:null};
      null != baseIndent && (state.baseIndent = baseIndent);
      return state;
    }, token:function(stream, state) {
      !state.tagName && stream.sol() && (state.indented = stream.indentation());
      if (stream.eatSpace()) {
        return null;
      }
      type$jscomp$0 = null;
      var style = state.tokenize(stream, state);
      (style || type$jscomp$0) && "comment" != style && (setStyle = null, state.state = state.state(type$jscomp$0 || style, stream, state), setStyle && (style = "error" == setStyle ? style + " error" : setStyle));
      return style;
    }, indent:function(state, tagAfter_textAfter, fullLine_grabbers) {
      var context = state.context;
      if (state.tokenize.isInAttribute) {
        return state.tagStart == state.indented ? state.stringStartCol + 1 : state.indented + indentUnit;
      }
      if (context && context.noIndent) {
        return CodeMirror.Pass;
      }
      if (state.tokenize != inTag && state.tokenize != inText) {
        return fullLine_grabbers ? fullLine_grabbers.match(/^(\s*)/)[0].length : 0;
      }
      if (state.tagName) {
        return !1 !== config.multilineTagIndentPastTag ? state.tagStart + state.tagName.length + 2 : state.tagStart + indentUnit * (config.multilineTagIndentFactor || 1);
      }
      if (config.alignCDATA && /<!\[CDATA\[/.test(tagAfter_textAfter)) {
        return 0;
      }
      if ((tagAfter_textAfter = tagAfter_textAfter && /^<(\/)?([\w_:\.-]*)/.exec(tagAfter_textAfter)) && tagAfter_textAfter[1]) {
        for (; context;) {
          if (context.tagName == tagAfter_textAfter[2]) {
            context = context.prev;
            break;
          } else if (config.implicitlyClosed.hasOwnProperty(lower(context.tagName))) {
            context = context.prev;
          } else {
            break;
          }
        }
      } else if (tagAfter_textAfter) {
        for (; context;) {
          if ((fullLine_grabbers = config.contextGrabbers[lower(context.tagName)]) && fullLine_grabbers.hasOwnProperty(lower(tagAfter_textAfter[2]))) {
            context = context.prev;
          } else {
            break;
          }
        }
      }
      for (; context && context.prev && !context.startOfLine;) {
        context = context.prev;
      }
      return context ? context.indent + indentUnit : state.baseIndent || 0;
    }, electricInput:/<\/[\s\w:]+>$/, blockCommentStart:"\x3c!--", blockCommentEnd:"--\x3e", configuration:config.htmlMode ? "html" : "xml", helperType:config.htmlMode ? "html" : "xml", skipAttribute:function(state) {
      state.state == attrValueState && (state.state = attrState);
    }, xmlCurrentTag:function(state) {
      return state.tagName ? {name:state.tagName, close:"closeTag" == state.type} : null;
    }, xmlCurrentContext:function(cx$jscomp$5_state) {
      var context = [];
      for (cx$jscomp$5_state = cx$jscomp$5_state.context; cx$jscomp$5_state; cx$jscomp$5_state = cx$jscomp$5_state.prev) {
        context.push(cx$jscomp$5_state.tagName);
      }
      return context.reverse();
    }};
  });
  CodeMirror.defineMIME("text/xml", "xml");
  CodeMirror.defineMIME("application/xml", "xml");
  CodeMirror.mimeModes.hasOwnProperty("text/html") || CodeMirror.defineMIME("text/html", {name:"xml", htmlMode:!0});
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_625(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_625) : mod$jscomp$inline_625(CodeMirror);
//[third_party/javascript/codemirror4/mode/yaml/yaml.js]
function mod$jscomp$inline_627(CodeMirror) {
  CodeMirror.defineMode("yaml", function() {
    var keywordRegex = RegExp("\\b((true)|(false)|(on)|(off)|(yes)|(no))$", "i");
    return {token:function(stream, state) {
      var ch = stream.peek(), esc = state.escaped;
      state.escaped = !1;
      if ("#" == ch && (0 == stream.pos || /\s/.test(stream.string.charAt(stream.pos - 1)))) {
        return stream.skipToEnd(), "comment";
      }
      if (stream.match(/^('([^']|\\.)*'?|"([^"]|\\.)*"?)/)) {
        return "string";
      }
      if (state.literal && stream.indentation() > state.keyCol) {
        return stream.skipToEnd(), "string";
      }
      state.literal && (state.literal = !1);
      if (stream.sol()) {
        state.keyCol = 0;
        state.pair = !1;
        state.pairStart = !1;
        if (stream.match("---") || stream.match("...")) {
          return "def";
        }
        if (stream.match(/\s*-\s+/)) {
          return "meta";
        }
      }
      if (stream.match(/^(\{|\}|\[|\])/)) {
        return "{" == ch ? state.inlinePairs++ : "}" == ch ? state.inlinePairs-- : "[" == ch ? state.inlineList++ : state.inlineList--, "meta";
      }
      if (0 < state.inlineList && !esc && "," == ch) {
        return stream.next(), "meta";
      }
      if (0 < state.inlinePairs && !esc && "," == ch) {
        return state.keyCol = 0, state.pair = !1, state.pairStart = !1, stream.next(), "meta";
      }
      if (state.pairStart) {
        if (stream.match(/^\s*(\||>)\s*/)) {
          return state.literal = !0, "meta";
        }
        if (stream.match(/^\s*(&|\*)[a-z0-9\._-]+\b/i)) {
          return "variable-2";
        }
        if (0 == state.inlinePairs && stream.match(/^\s*-?[0-9\.,]+\s?$/) || 0 < state.inlinePairs && stream.match(/^\s*-?[0-9\.,]+\s?(?=(,|}))/)) {
          return "number";
        }
        if (stream.match(keywordRegex)) {
          return "keyword";
        }
      }
      if (!state.pair && stream.match(/^\s*(?:[,\[\]{}&*!|>'"%@`][^\s'":]|[^,\[\]{}#&*!|>'"%@`])[^#]*?(?=\s*:($|\s))/)) {
        return state.pair = !0, state.keyCol = stream.indentation(), "atom";
      }
      if (state.pair && stream.match(/^:\s*/)) {
        return state.pairStart = !0, "meta";
      }
      state.pairStart = !1;
      state.escaped = "\\" == ch;
      stream.next();
      return null;
    }, startState:function() {
      return {pair:!1, pairStart:!1, keyCol:0, inlinePairs:0, inlineList:0, literal:!1, escaped:!1};
    }, lineComment:"#", fold:"indent"};
  });
  CodeMirror.defineMIME("text/x-yaml", "yaml");
  CodeMirror.defineMIME("text/yaml", "yaml");
}
"object" == typeof exports && "object" == typeof module ? mod$jscomp$inline_627(require("../../lib/codemirror")) : "function" == typeof define && define.amd ? define(["../../lib/codemirror"], mod$jscomp$inline_627) : mod$jscomp$inline_627(CodeMirror);
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/lit-html/src/directive.closure.js]
//[blaze-out/k8-fastbuild/bin/third_party/javascript/lit/packages/reactive-element/src/reactive-controller.closure.js]
//[third_party/javascript/typings/codemirror/shim.js]
}).call(this);
