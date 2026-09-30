const REDACTED = '[REDACTED]';

const SENSITIVE_KEY_NAMES = new Set([
  'password',
  'passwordhash',
  'token',
  'accesstoken',
  'refreshtoken',
  'authorization',
  'secret',
  'apikey',
  'jwtsecret',
]);

function normalizeKeyName(key: string): string {
  return key.toLowerCase().replace(/[^a-z0-9]/g, '');
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Object.prototype.toString.call(value) === '[object Object]';
}

function redactInternal(value: unknown, seen: WeakSet<object>): unknown {
  if (value === null || value === undefined) {
    return value;
  }

  if (Array.isArray(value)) {
    return value.map((item) => redactInternal(item, seen));
  }

  if (value instanceof Date || value instanceof Error) {
    return value;
  }

  if (typeof value !== 'object') {
    return value;
  }

  if (seen.has(value)) {
    return '[Circular]';
  }

  if (!isPlainObject(value)) {
    return value;
  }

  seen.add(value);
  const copy: Record<string, unknown> = {};

  for (const [key, nested] of Object.entries(value)) {
    if (SENSITIVE_KEY_NAMES.has(normalizeKeyName(key))) {
      copy[key] = REDACTED;
      continue;
    }

    copy[key] = redactInternal(nested, seen);
  }

  return copy;
}

export function redactSensitiveData(value: unknown): unknown {
  return redactInternal(value, new WeakSet<object>());
}
