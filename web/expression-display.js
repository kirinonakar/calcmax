import {expressionTree} from './expression-tree.js';
import {mathDisplay} from './math-display.js';

// Source previews preserve the expression; result captions can round display only.
export function expressionDisplay(source,{digits=10,roundNumbers=false}={}) {
  return mathDisplay(expressionTree(source),digits,false,{roundNumbers});
}
