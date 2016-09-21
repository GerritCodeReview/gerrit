# Generated. DO NOT EDIT.
load("//tools/bzl:js.bzl", "bower_component")
def define_bower_components():
  bower_component(
    name = "es6-promise",
    license = "polymer",
    seed = True,
  )
  bower_component(
    name = "fetch",
    license = "DO_NOT_DISTRIBUTE",
    seed = True,
  )
  bower_component(
    name = "iron-a11y-announcer",
    license = "polymer",
    deps = [ ":polymer" ],
  )
  bower_component(
    name = "iron-a11y-keys-behavior",
    license = "polymer",
    deps = [ ":polymer" ],
  )
  bower_component(
    name = "iron-autogrow-textarea",
    license = "polymer",
    deps = [
      ":iron-behaviors",
      ":iron-flex-layout",
      ":iron-form-element-behavior",
      ":iron-validatable-behavior",
      ":polymer",
    ],
    seed = True,
  )
  bower_component(
    name = "iron-behaviors",
    license = "polymer",
    deps = [
      ":iron-a11y-keys-behavior",
      ":polymer",
    ],
  )
  bower_component(
    name = "iron-dropdown",
    license = "polymer",
    deps = [
      ":iron-a11y-keys-behavior",
      ":iron-behaviors",
      ":iron-overlay-behavior",
      ":iron-resizable-behavior",
      ":neon-animation",
      ":polymer",
    ],
    seed = True,
  )
  bower_component(
    name = "iron-fit-behavior",
    license = "polymer",
    deps = [ ":polymer" ],
  )
  bower_component(
    name = "iron-flex-layout",
    license = "polymer",
    deps = [ ":polymer" ],
  )
  bower_component(
    name = "iron-form-element-behavior",
    license = "polymer",
    deps = [ ":polymer" ],
  )
  bower_component(
    name = "iron-input",
    license = "polymer",
    deps = [
      ":iron-a11y-announcer",
      ":iron-validatable-behavior",
      ":polymer",
    ],
    seed = True,
  )
  bower_component(
    name = "iron-meta",
    license = "polymer",
    deps = [ ":polymer" ],
  )
  bower_component(
    name = "iron-overlay-behavior",
    license = "polymer",
    deps = [
      ":iron-a11y-keys-behavior",
      ":iron-fit-behavior",
      ":iron-resizable-behavior",
      ":polymer",
    ],
    seed = True,
  )
  bower_component(
    name = "iron-resizable-behavior",
    license = "polymer",
    deps = [ ":polymer" ],
  )
  bower_component(
    name = "iron-selector",
    license = "polymer",
    deps = [ ":polymer" ],
    seed = True,
  )
  bower_component(
    name = "iron-validatable-behavior",
    license = "polymer",
    deps = [
      ":iron-meta",
      ":polymer",
    ],
  )
  bower_component(
    name = "moment",
    license = "DO_NOT_DISTRIBUTE",
    seed = True,
  )
  bower_component(
    name = "neon-animation",
    license = "polymer",
    deps = [
      ":iron-meta",
      ":iron-resizable-behavior",
      ":iron-selector",
      ":polymer",
      ":web-animations-js",
    ],
  )
  bower_component(
    name = "page",
    license = "polymer",
    seed = True,
  )
  bower_component(
    name = "polymer",
    license = "polymer",
    deps = [ ":webcomponentsjs" ],
    seed = True,
  )
  bower_component(
    name = "promise-polyfill",
    license = "polymer",
    deps = [ ":polymer" ],
    seed = True,
  )
  bower_component(
    name = "web-animations-js",
    license = "Apache2.0",
  )
  bower_component(
    name = "webcomponentsjs",
    license = "polymer",
  )
