import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, timeout } from 'rxjs';
import { APP_ENV } from '../config/app-env.generated';

export type AiChatRequest = {
  message: string;
  sessionId?: string;
};

export type AiChatResponse = {
  reply: string;
  sessionId?: string;
};

@Injectable({
  providedIn: 'root',
})
export class AiChatService {
  // Keep AI service fully separate from trade/auth APIs.
  private readonly endpoint = APP_ENV.aiChatUrl;
  private readonly requestTimeoutMs = 25_000;

  constructor(private http: HttpClient) {}

  sendMessage(message: string, sessionId?: string): Observable<AiChatResponse> {
    const payload: AiChatRequest = {
      message,
      ...(sessionId ? { sessionId } : {}),
    };
    return this.http.post<AiChatResponse>(this.endpoint, payload).pipe(timeout(this.requestTimeoutMs));
  }
}
