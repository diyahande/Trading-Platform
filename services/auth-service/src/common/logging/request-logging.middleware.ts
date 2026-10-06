import type { NextFunction, Request, Response } from 'express';
import { Injectable, NestMiddleware } from '@nestjs/common';

import { AppLogger } from './app-logger.service';

@Injectable()
export class RequestLoggingMiddleware implements NestMiddleware {
  constructor(private readonly logger: AppLogger) {}

  use(request: Request, response: Response, next: NextFunction): void {
    const startedAt = Date.now();

    response.on('finish', () => {
      const durationMs = Date.now() - startedAt;
      const requestId = this.firstHeaderValue(request.headers['x-request-id']);

      // We only log selected safe request fields and never pass request.body as a whole object.
      const username =
        request.body && typeof request.body.username === 'string'
          ? request.body.username
          : undefined;
      const accountId =
        request.body && typeof request.body.accountId === 'string'
          ? request.body.accountId
          : undefined;

      this.logger.log('HTTP request completed', {
        event: 'http.request.completed',
        method: request.method,
        path: request.originalUrl,
        statusCode: response.statusCode,
        durationMs,
        requestId,
        username,
        accountId,
      });
    });

    next();
  }

  private firstHeaderValue(value: string | string[] | undefined): string | undefined {
    if (Array.isArray(value)) {
      return value[0];
    }

    return value;
  }
}
