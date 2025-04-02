import { generate } from './bom/generate';

generate().catch(() => {
  // Don't continue the workflow if something threw in an async function
  process.exitCode = 1;
});
