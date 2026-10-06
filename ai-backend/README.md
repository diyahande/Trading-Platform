# AI Backend (Standalone)

Minimal zero-dependency Node.js backend for the frontend AI chat widget.

## Features

- `POST /api/chat` for chat responses
- `GET /health` for readiness checks
- Uses Gemini endpoint:
  - `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent`
- Enforces two-line responses
- Keeps in-memory per-session context for follow-up prompts

## Setup

1. Copy env template:

```powershell
Copy-Item .env.example .env
```

2. Set your key in `.env`:

```env
GEMINI_API_KEY=your_key_here
```

3. Run server:

```powershell
npm start
```

Server defaults to `http://localhost:8088`.

## Request

`POST /api/chat`

```json
{
  "message": "What is stop loss?",
  "sessionId": "user-123"
}
```

`sessionId` is optional but recommended so follow-up context is preserved correctly.

## Response

```json
{
  "reply": "Line one...\nLine two...",
  "sessionId": "user-123"
}
```

## Note on context

Context is stored in server memory only. Restarting the server clears all session history.
