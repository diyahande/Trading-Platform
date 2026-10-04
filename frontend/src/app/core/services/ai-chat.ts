import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, timeout } from 'rxjs';

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
  private readonly endpoint = 'http://localhost:8088/api/chat';
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
