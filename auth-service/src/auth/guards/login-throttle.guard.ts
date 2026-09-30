import {
  CanActivate,
  ExecutionContext,
  HttpException,
  HttpStatus,
  Injectable,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Request } from 'express';

interface LoginThrottleState {
  count: number;
  windowStartedAtMs: number;
  blockedUntilMs: number;
}

@Injectable()
export class LoginThrottleGuard implements CanActivate {
  private readonly stateByIp = new Map<string, LoginThrottleState>();

  private readonly maxAttempts: number;
  private readonly windowMs: number;
  private readonly blockMs: number;

  constructor(configService: ConfigService) {
    this.maxAttempts = this.parsePositiveInt(
      configService.get<string>('LOGIN_THROTTLE_MAX_ATTEMPTS'),
      5,
    );
    this.windowMs = this.parsePositiveInt(
      configService.get<string>('LOGIN_THROTTLE_WINDOW_MS'),
      60_000,
    );
    this.blockMs = this.parsePositiveInt(
      configService.get<string>('LOGIN_THROTTLE_BLOCK_MS'),
      300_000,
    );
  }

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<Request>();
    const clientIp = this.getClientIp(request);
    const now = Date.now();

    this.cleanupIfNeeded(now);

    const existing = this.stateByIp.get(clientIp);
    if (!existing) {
      this.stateByIp.set(clientIp, {
        count: 1,
        windowStartedAtMs: now,
        blockedUntilMs: 0,
      });
      return true;
    }

    if (existing.blockedUntilMs > now) {
      throw new HttpException(
        'Too many login attempts. Please try again later.',
        HttpStatus.TOO_MANY_REQUESTS,
      );
    }

    if (now - existing.windowStartedAtMs > this.windowMs) {
      existing.count = 1;
      existing.windowStartedAtMs = now;
      existing.blockedUntilMs = 0;
      this.stateByIp.set(clientIp, existing);
      return true;
    }

    existing.count += 1;

    if (existing.count > this.maxAttempts) {
      existing.blockedUntilMs = now + this.blockMs;
      this.stateByIp.set(clientIp, existing);
      throw new HttpException(
        'Too many login attempts. Please try again later.',
        HttpStatus.TOO_MANY_REQUESTS,
      );
    }

    this.stateByIp.set(clientIp, existing);
    return true;
  }

  private getClientIp(request: Request): string {
    const xForwardedFor = request.headers['x-forwarded-for'];
    if (typeof xForwardedFor === 'string' && xForwardedFor.length > 0) {
      return xForwardedFor.split(',')[0].trim();
    }

    if (Array.isArray(xForwardedFor) && xForwardedFor.length > 0) {
      return xForwardedFor[0].trim();
    }

    return request.ip ?? 'unknown';
  }

  private parsePositiveInt(value: string | undefined, fallback: number): number {
    const parsed = Number.parseInt(value ?? '', 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
  }

  // Keep in-memory map bounded by dropping stale entries every few requests.
  private cleanupIfNeeded(now: number): void {
    if (this.stateByIp.size < 200) {
      return;
    }

    for (const [ip, state] of this.stateByIp.entries()) {
      const windowExpired = now - state.windowStartedAtMs > this.windowMs;
      const blockExpired = state.blockedUntilMs > 0 && state.blockedUntilMs <= now;

      if (windowExpired && blockExpired) {
        this.stateByIp.delete(ip);
      }
    }
  }
}