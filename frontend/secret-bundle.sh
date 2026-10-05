#!/usr/bin/env node
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = resolve(uiRoot, '..');
const distDir = resolve(uiRoot, process.argv[2] ?? 'dist');

const PATTERNS = [
  { rule: 'market-data key reference', regex: /x-api-key|api_key|api-key|fauxnance/i },
  { rule: 'market-data host', regex: /execute-api\.[a-z0-9-]+\.amazonaws\.com/i },
  { rule: 'signing secret name', regex: /jwt_secret/i },
  { rule: 'secret assigned a long literal', regex: /secret["']?\s*[:=]\s*["'`][^"'`\s]{16,}["'`]/i },
  { rule: 'three-part JWT', regex: /eyJ[A-Za-z0-9_-]{8,}\.eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}/ },
  { rule: 'source map reference', regex: /sourceMappingURL=/ },
];

// Literal values shorter than this are too likely to collide with ordinary minified text to be meaningful.
const MIN_LITERAL_LENGTH = 8;

function readDotEnv(path) {
  if (!existsSync(path)) return {};
  const values = {};
  for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$/);
    if (!match) continue;
    values[match[1]] = match[2].replace(/^(['"])(.*)\1$/, '$2');
  }
  return values;
}

function hostOf(url) {
  try {
    return new URL(url).host;
  } catch {
    return undefined;
  }
}

function secretLiterals() {
  const dotEnv = readDotEnv(join(repoRoot, '.env'));
  const value = (name) => process.env[name] || dotEnv[name] || undefined;
  const baseUrl = value('BASE_URL');
  return [
    { name: 'API_KEY', value: value('API_KEY') },
    { name: 'JWT_SECRET', value: value('JWT_SECRET') },
    { name: 'BASE_URL host', value: baseUrl ? hostOf(baseUrl) : undefined },
  ];
}

function listFiles(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry);
    return statSync(path).isDirectory() ? listFiles(path) : [path];
  });
}

function lineOf(text, index) {
  return text.slice(0, index).split('\n').length;
}

if (!existsSync(distDir)) {
  console.error(`[bundle-check] ${relative(uiRoot, distDir)} does not exist. Run "npm run build" first.`);
  process.exit(2);
}

const files = listFiles(distDir);
const findings = [];

const literals = secretLiterals();
const missing = literals.filter((l) => !l.value).map((l) => l.name);
const usable = literals.filter((l) => l.value && l.value.length >= MIN_LITERAL_LENGTH);
const tooShort = literals.filter((l) => l.value && l.value.length < MIN_LITERAL_LENGTH).map((l) => l.name);

for (const file of files) {
  const shown = relative(uiRoot, file).replaceAll('\\', '/');
  if (file.endsWith('.map')) {
    findings.push(`${shown}: source map file (the source back again)`);
  }
  const text = readFileSync(file, 'latin1');
  for (const { rule, regex } of PATTERNS) {
    const match = regex.exec(text);
    if (match) findings.push(`${shown}:${lineOf(text, match.index)}: ${rule} ("${match[0].slice(0, 40)}")`);
  }
  for (const { name, value } of usable) {
    const index = text.indexOf(value);
    // The value itself is never printed; naming which secret leaked is enough to act on.
    if (index !== -1) findings.push(`${shown}:${lineOf(text, index)}: literal value of ${name}`);
  }
}

console.log(`[bundle-check] Scanned ${files.length} file(s) under ${relative(uiRoot, distDir) || '.'}`);
console.log(`[bundle-check] Literal values checked: ${usable.map((l) => l.name).join(', ') || '(none)'}`);
if (tooShort.length) console.warn(`[bundle-check] WARNING: too short to search reliably: ${tooShort.join(', ')}`);

let failed = findings.length > 0;

if (missing.length) {
  const message = `literal value(s) not available: ${missing.join(', ')} (set them in the repo-root .env or the environment)`;
  if (process.env.ALLOW_MISSING_SECRET_VALUES === '1') {
    console.warn(`[bundle-check] WARNING: ${message}; literal-value check skipped for them.`);
  } else {
    console.error(`[bundle-check] ERROR: ${message}.`);
    failed = true;
  }
}

if (findings.length) {
  console.error('[bundle-check] FAILED: the production bundle contains material that must stay server-side:');
  for (const finding of findings) console.error(`  - ${finding}`);
  console.error('[bundle-check] A key that reached a bundle is published. Revoke and rotate it; do not just delete the line.');
}

if (failed) process.exit(1);
console.log('[bundle-check] PASSED: no API key, signing secret, market-data host or source map in the bundle.');
