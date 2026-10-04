import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AiChatService } from '../../../core/services/ai-chat';

type ChatRole = 'user' | 'assistant';

type ChatMessage = {
  role: ChatRole;
  content: string;
  createdAt: number;
};

type PersistedChatState = {
  messages: ChatMessage[];
};

@Component({
  selector: 'app-ai-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ai-chat.html',
  styleUrls: ['./ai-chat.css'],
})
export class AiChat implements OnInit {
  isOpen = false;
  isExpanded = false;
  isSending = false;
  errorMessage: string | null = null;
  draft = '';
  messages: ChatMessage[] = [];

  private readonly maxMessages = 50;

  constructor(private aiChatService: AiChatService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.messages = this.loadPersistedMessages();
  }

  toggleOpen(): void {
    this.isOpen = !this.isOpen;
    if (!this.isOpen) {
      this.isExpanded = false;
    }
    this.cdr.detectChanges();
  }

  toggleExpanded(): void {
    this.isExpanded = !this.isExpanded;
    this.cdr.detectChanges();
  }

  async send(): Promise<void> {
    const prompt = this.draft.trim();
    if (!prompt || this.isSending) {
      return;
    }

    this.errorMessage = null;
    this.appendMessage('user', prompt);
    this.draft = '';
    this.isSending = true;
    this.cdr.detectChanges();
    const sessionId = this.resolveSessionId();

    try {
      const response = await firstValueFrom(this.aiChatService.sendMessage(prompt, sessionId));
      const reply = response?.reply?.trim() || 'No response received from the AI service.';
      this.appendMessage('assistant', reply);
    } catch (error: any) {
      this.errorMessage = this.resolveErrorMessage(error);
      this.cdr.detectChanges();
    } finally {
      this.isSending = false;
      this.cdr.detectChanges();
    }
  }

  trackByMessage(index: number, message: ChatMessage): string {
    return `${message.role}-${message.createdAt}-${index}`;
  }

  formatMessageContent(content: string): string {
    const escaped = this.escapeHtml(content);
    const lines = escaped.split(/\r?\n/);

    const renderedLines = lines.map((line) => {
      const withBold = line.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');

      if (/^###\s+/.test(withBold)) {
        return `<strong>${withBold.replace(/^###\s+/, '')}</strong>`;
      }

      if (/^[-*]\s+/.test(withBold)) {
        return `&bull; ${withBold.replace(/^[-*]\s+/, '')}`;
      }

      return withBold;
    });

    return renderedLines.join('<br />');
  }

  private appendMessage(role: ChatRole, content: string): void {
    this.messages = [...this.messages, { role, content, createdAt: Date.now() }].slice(-this.maxMessages);
    this.persistMessages();
    this.cdr.detectChanges();
  }

  private escapeHtml(value: string): string {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  private persistMessages(): void {
    const payload: PersistedChatState = {
      messages: this.messages,
    };
    localStorage.setItem(this.storageKey(), JSON.stringify(payload));
  }

  private loadPersistedMessages(): ChatMessage[] {
    const raw = localStorage.getItem(this.storageKey());
    if (!raw) {
      return [];
    }

    try {
      const parsed = JSON.parse(raw) as PersistedChatState;
      if (!Array.isArray(parsed.messages)) {
        return [];
      }

      return parsed.messages.filter((message) => this.isValidMessage(message)).slice(-this.maxMessages);
    } catch {
      return [];
    }
  }

  private isValidMessage(message: unknown): message is ChatMessage {
    if (!message || typeof message !== 'object') {
      return false;
    }

    const candidate = message as ChatMessage;
    return (
      (candidate.role === 'user' || candidate.role === 'assistant') &&
      typeof candidate.content === 'string' &&
      typeof candidate.createdAt === 'number'
    );
  }

  private storageKey(): string {
    const userId = this.resolveUserId();
    return `aiChat:${userId ?? 'anonymous'}`;
  }

  private resolveSessionId(): string {
    const userId = this.resolveUserId();
    if (userId) {
      return `user:${userId}`;
    }

    let anonymousId = localStorage.getItem('aiChatAnonymousSessionId');
    if (!anonymousId) {
      anonymousId = `anon:${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
      localStorage.setItem('aiChatAnonymousSessionId', anonymousId);
    }

    return anonymousId;
  }

  private resolveUserId(): string | null {
    const token = localStorage.getItem('accessToken');
    if (!token) {
      return null;
    }

    try {
      const parts = token.split('.');
      if (parts.length !== 3) {
        return null;
      }

      const payload = JSON.parse(this.base64UrlDecode(parts[1])) as { sub?: string; accountId?: string };
      const id = payload.accountId || payload.sub;
      return typeof id === 'string' && id.trim() ? id : null;
    } catch {
      return null;
    }
  }

  private base64UrlDecode(value: string): string {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    return atob(padded);
  }

  private resolveErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const details =
        typeof error.error?.details === 'string'
          ? error.error.details
          : typeof error.error?.error === 'string'
            ? error.error.error
            : '';

      const detailsLower = details.toLowerCase();

      if (error.status === 0) {
        return 'Could not reach AI backend. Check that it is running on port 8088.';
      }

      if (
        error.status === 502 &&
        (detailsLower.includes('503') ||
          detailsLower.includes('unavailable') ||
          detailsLower.includes('high demand'))
      ) {
        return 'AI is experiencing high traffic right now. Please try again in a few moments.';
      }

      if (error.status === 502 && details) {
        return `AI provider error: ${details}`;
      }

      if (details) {
        return details;
      }
    }

    return 'AI service is unavailable right now. Please try again.';
  }
}
