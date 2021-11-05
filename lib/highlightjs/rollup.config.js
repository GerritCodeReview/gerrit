const cjsPlugin = require('@rollup/plugin-commonjs');
const jsonPlugin = require('@rollup/plugin-json');
const { nodeResolve } = require('@rollup/plugin-node-resolve');

export default {
  plugins: [
    cjsPlugin(),
    jsonPlugin(),
    nodeResolve()
  ],
  output: {
    format: "cjs",
    strict: false,
    exports: "auto",
    footer: ""
  }
};
