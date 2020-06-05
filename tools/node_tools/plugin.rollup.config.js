import {terser} from 'rollup-plugin-terser';

export default {
  treeshake: false,
  output: {
    format: 'iife',
    compact: true,
    plugins: [terser()]
  },
};
