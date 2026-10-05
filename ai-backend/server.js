import { createServer } from 'node:http';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

loadEnvFile();

const PORT = Number(process.env.PORT || 8088);
const ALLOWED_ORIGIN = process.env.ALLOWED_ORIGIN || 'http://localhost:4200';
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || '';
const GEMINI_API_URL =
  process.env.GEMINI_API_URL ||
  'https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent';
const MAX_SESSION_MESSAGES = clampInt(process.env.MAX_SESSION_MESSAGES, 20, 4, 100);
const GEMINI_TIMEOUT_MS = clampInt(process.env.GEMINI_TIMEOUT_MS, 20000, 3000, 120000);

const sessions = new Map();

const server = createServer(async (req, res) => {
  applyCors(res);

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  if (req.url === '/health' && req.method === 'GET') {
    json(res, 200, { status: 'ok', service: 'ai-backend' });
    return;
  }

  if (req.url === '/api/chat' && req.method === 'POST') {
    if (!GEMINI_API_KEY) {
      json(res, 503, { error: 'GEMINI_API_KEY is missing in ai-backend/.env' });
      return;
    }

    try {
      const body = await readJson(req);
      const message = String(body?.message || '').trim();

      if (!message) {
        json(res, 400, { error: 'message is required' });
        return;
      }

      if (message.length > 1000) {
        json(res, 400, { error: 'message must be <= 1000 characters' });
        return;
      }

      const sessionId = resolveSessionId(req, body);
      const history = getSessionHistory(sessionId);
      const reply = await generateGeminiReply(message, history);

      history.push({ role: 'user', text: message });
      history.push({ role: 'assistant', text: reply });
      trimHistory(history);

      json(res, 200, { reply, sessionId });
    } catch (error) {
      json(res, 502, {
        error: 'Failed to get AI response',
        details: error instanceof Error ? error.message : 'Unknown error',
      });
    }

    return;
  }

  json(res, 404, { error: 'Not found' });
});

server.listen(PORT, () => {
  console.log(`ai-backend listening on http://localhost:${PORT}`);
});

function applyCors(res) {
  res.setHeader('Access-Control-Allow-Origin', ALLOWED_ORIGIN);
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-Session-Id');
}

function json(res, statusCode, payload) {
  res.statusCode = statusCode;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.end(JSON.stringify(payload));
}

function clampInt(value, fallback, min, max) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  return Math.max(min, Math.min(max, Math.floor(parsed)));
}

function loadEnvFile() {
  const envPath = resolve(process.cwd(), '.env');
  if (!existsSync(envPath)) {
    return;
  }

  const content = readFileSync(envPath, 'utf8');
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      continue;
    }

    const index = trimmed.indexOf('=');
    if (index <= 0) {
      continue;
    }

    const key = trimmed.slice(0, index).trim();
    let value = trimmed.slice(index + 1).trim();
    value = value.replace(/^['\"]|['\"]$/g, '');

    if (!(key in process.env)) {
      process.env[key] = value;
    }
  }
}

function resolveSessionId(req, body) {
  const fromBody = typeof body?.sessionId === 'string' ? body.sessionId.trim() : '';
  if (fromBody) {
    return fromBody;
  }

  const fromHeader = typeof req.headers['x-session-id'] === 'string' ? req.headers['x-session-id'].trim() : '';
  if (fromHeader) {
    return fromHeader;
  }

  const remote = req.socket?.remoteAddress || 'local';
  return `session:${remote}`;
}

function getSessionHistory(sessionId) {
  if (!sessions.has(sessionId)) {
    sessions.set(sessionId, []);
  }
  return sessions.get(sessionId);
}

function trimHistory(history) {
  const overflow = history.length - MAX_SESSION_MESSAGES;
  if (overflow > 0) {
    history.splice(0, overflow);
  }
}

async function generateGeminiReply(message, history) {
  const result = await callGemini(message);
  if (!result.text) {
    throw new Error('Gemini API returned empty response');
  }
  return result.text;
}

async function callGemini(promptText) {
  const controller = new AbortController();
  const timeoutHandle = setTimeout(() => controller.abort(), GEMINI_TIMEOUT_MS);

  let response;
  try {
    response = await fetch(`${GEMINI_API_URL}?key=${encodeURIComponent(GEMINI_API_KEY)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [
          {
            role: 'user',
            parts: [{ text: promptText }],
          },
        ],
        generationConfig: {
          temperature: 0.4,
        },
      }),
      signal: controller.signal,
    });
  } catch (error) {
    if (error && typeof error === 'object' && error.name === 'AbortError') {
      throw new Error(`Gemini request timed out after ${GEMINI_TIMEOUT_MS}ms`);
    }
    throw error;
  } finally {
    clearTimeout(timeoutHandle);
  }

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Gemini API error ${response.status}: ${errorText}`);
  }

  const payload = await response.json();
  const parts = payload?.candidates?.[0]?.content?.parts;
  const text = Array.isArray(parts)
    ? parts
        .map((part) => (typeof part?.text === 'string' ? part.text : ''))
        .join('')
        .trim()
    : '';

  return { text };
}

function readJson(req) {
  return new Promise((resolvePromise, rejectPromise) => {
    let raw = '';

    req.on('data', (chunk) => {
      raw += chunk;
      if (raw.length > 200_000) {
        rejectPromise(new Error('Request body too large'));
        req.destroy();
      }
    });

    req.on('end', () => {
      if (!raw.trim()) {
        resolvePromise({});
        return;
      }

      try {
        resolvePromise(JSON.parse(raw));
      } catch {
        rejectPromise(new Error('Invalid JSON body'));
      }
    });

    req.on('error', (error) => {
      rejectPromise(error);
    });
  });
}
