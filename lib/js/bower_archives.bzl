# DO NOT EDIT
# generated with the following command:
#
#   tools/js/bower2bazel.py -w lib/js/bower_archives.bzl -b lib/js/bower_components.bzl
#

load("//tools/bzl:js.bzl", "bower_archive")

def load_bower_archives():
    bower_archive(
        name = "accessibility-developer-tools",
        package = "accessibility-developer-tools",
        version = "2.12.0",
        sha1 = "88ae82dcdeb6c658f76eff509d0ee425cae14d49",
    )
    bower_archive(
        name = "async",
        package = "async",
        version = "1.5.2",
        sha1 = "1ec975d3b3834646a7e3d4b7e68118b90ed72508",
    )
    bower_archive(
        name = "chai",
        package = "chai",
        version = "3.5.0",
        sha1 = "849ad3ee7c77506548b7b5db603a4e150b9431aa",
    )
    bower_archive(
        name = "font-roboto-local",
        package = "PolymerElements/font-roboto-local",
        version = "1.1.0",
        sha1 = "de651abf9b1b2d0935f7b264d48131677196412f",
    )
    bower_archive(
        name = "iron-a11y-announcer",
        package = "PolymerElements/iron-a11y-announcer",
        version = "2.1.0",
        sha1 = "bda12ed6fe7b98a64bf5f70f3e84384053763190",
    )
    bower_archive(
        name = "iron-a11y-keys-behavior",
        package = "PolymerElements/iron-a11y-keys-behavior",
        version = "2.1.1",
        sha1 = "4c8f303479253301e81c63b8ba7bd4cfb62ddf55",
    )
    bower_archive(
        name = "iron-behaviors",
        package = "PolymerElements/iron-behaviors",
        version = "2.1.1",
        sha1 = "d2418e886c3237dcbc8d74a956eec367a95cd068",
    )
    bower_archive(
        name = "iron-checked-element-behavior",
        package = "PolymerElements/iron-checked-element-behavior",
        version = "2.1.1",
        sha1 = "822b6c73e349cf5174e3a17aa9b3d2cb823c37ac",
    )
    bower_archive(
        name = "iron-fit-behavior",
        package = "PolymerElements/iron-fit-behavior",
        version = "2.2.1",
        sha1 = "7b12bc96bf05f04bbb6ad78a16d6c39758263a14",
    )
    bower_archive(
        name = "iron-flex-layout",
        package = "PolymerElements/iron-flex-layout",
        version = "2.0.3",
        sha1 = "c88e9577cabb005ea6d33f35b97d9c39c68f3d9e",
    )
    bower_archive(
        name = "iron-form-element-behavior",
        package = "PolymerElements/iron-form-element-behavior",
        version = "2.1.3",
        sha1 = "634f01cdedd7a616ae025fdcde85c6c5804f6377",
    )
    bower_archive(
        name = "iron-menu-behavior",
        package = "PolymerElements/iron-menu-behavior",
        version = "2.1.1",
        sha1 = "1504997f6eb9aec490b855dadee473cac064f38c",
    )
    bower_archive(
        name = "iron-meta",
        package = "PolymerElements/iron-meta",
        version = "2.1.1",
        sha1 = "7985a9f18b6c32d62f5d3870d58d73ef66613cb9",
    )
    bower_archive(
        name = "iron-resizable-behavior",
        package = "PolymerElements/iron-resizable-behavior",
        version = "2.1.1",
        sha1 = "31e32da6880a983da32da21ee3f483525b24e458",
    )
    bower_archive(
        name = "iron-validatable-behavior",
        package = "PolymerElements/iron-validatable-behavior",
        version = "2.1.0",
        sha1 = "b5dcf3bf4d95b074b74f8170d7122d34ab417daf",
    )
    bower_archive(
        name = "lodash",
        package = "lodash",
        version = "3.10.1",
        sha1 = "2f207a8293c4c554bf6cf071241f7a00dc513d3a",
    )
    bower_archive(
        name = "mocha",
        package = "mocha",
        version = "3.5.3",
        sha1 = "c14f149821e4e96241b20f85134aa757b73038f1",
    )
    bower_archive(
        name = "neon-animation",
        package = "PolymerElements/neon-animation",
        version = "2.2.1",
        sha1 = "865f4252c6306b91609769fefefb4f641361931f",
    )
    bower_archive(
        name = "paper-behaviors",
        package = "PolymerElements/paper-behaviors",
        version = "2.1.1",
        sha1 = "af59936a9015cda4abcfb235f831090a41faa2c4",
    )
    bower_archive(
        name = "paper-icon-button",
        package = "PolymerElements/paper-icon-button",
        version = "2.2.1",
        sha1 = "68f76af3a9379f256a3900a4b68d871898f1fe57",
    )
    bower_archive(
        name = "paper-ripple",
        package = "PolymerElements/paper-ripple",
        version = "2.1.1",
        sha1 = "d402c8165c6a09d17c12a2b421e69ea54e2fc8ef",
    )
    bower_archive(
        name = "paper-styles",
        package = "PolymerElements/paper-styles",
        # Basically 2.1.0 but with
        # https://github.com/PolymerElements/paper-styles/pull/165 applied
        version = "a6c207e6eee3402fd7a6550e6f9c387ca22ec4c4",
        sha1 = "6bd17410578b5d4017ccef330393a4b41b1c716e",
    )
    bower_archive(
        name = "shadycss",
        package = "webcomponents/shadycss",
        version = "1.9.1",
        sha1 = "3ef3bd54280ea2d7ce90434620354a2022c8e13d",
    )
    bower_archive(
        name = "sinon-chai",
        package = "sinon-chai",
        version = "2.14.0",
        sha1 = "78f0dc184efe47012a2b1b9a16a4289acf8300dc",
    )
    bower_archive(
        name = "sinonjs",
        package = "sinonjs",
        version = "1.17.1",
        sha1 = "a26a6aab7358807de52ba738770f6ac709afd240",
    )
    bower_archive(
        name = "stacky",
        package = "stacky",
        version = "1.3.2",
        sha1 = "d6c07a0112ab2e9677fe085933744466a89232fb",
    )
    bower_archive(
        name = "webcomponentsjs",
        package = "webcomponents/webcomponentsjs",
        version = "1.3.3",
        sha1 = "bbad90bd8301a2f2f5e014e750e0c86351579391",
    )
