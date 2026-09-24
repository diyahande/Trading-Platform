import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as jwt from 'jsonwebtoken';

import {
  ACCESS_TOKEN_TTL_SECONDS,
  ERROR_UNAUTHORISED,
  REFRESH_TOKEN_TTL_SECONDS,
} from './auth.constants';
import type { AccessTokenClaims, Role } from './types/auth.types';

@Injectable()
export class TokenService {
  constructor(private readonly configService: ConfigService) {}

  signAccessToken(accountId: string, roles: Role[]): string {
    return jwt.sign(
      {
        sub: accountId,
        accountId,
        roles : roles,
      },
      "mission-control-shared-secret-key-32-bytes-minimum",
      {
        algorithm: 'HS256',
        issuer: this.jwtIssuer,
        expiresIn: ACCESS_TOKEN_TTL_SECONDS,
      },
    );
  }

  verifyAccessToken(token: string): AccessTokenClaims {
    const payload = jwt.verify(token, this.jwtSecret, {
      algorithms: ['HS256'],
      issuer: this.jwtIssuer,
    });

    if (!this.isClaimsShape(payload)) {
      throw new Error(ERROR_UNAUTHORISED.message);
    }

    return payload;
  }

  get accessTtlSeconds(): number {
    return ACCESS_TOKEN_TTL_SECONDS;
  }

  get refreshTtlSeconds(): number {
    return REFRESH_TOKEN_TTL_SECONDS;
  }

  private get jwtSecret(): string {
    const value = this.configService.get<string>('JWT_SECRET');
    if (!value) {
      throw new Error('JWT_SECRET must be provided');
    }
    return value;
  }

  private get jwtIssuer(): string {
    return this.configService.get<string>('JWT_ISSUER') ?? 'auth-service';
  }

  private isClaimsShape(payload: unknown): payload is AccessTokenClaims {
    if (typeof payload !== 'object' || payload === null) {
      return false;
    }

    const claims = payload as Partial<AccessTokenClaims>;
    return (
      typeof claims.sub === 'string' &&
      typeof claims.accountId === 'string' &&
      Array.isArray(claims.roles) &&
      claims.roles.length > 0 &&
      claims.roles.every((role) => role === 'CUSTOMER' || role === 'ADMIN') &&
      typeof claims.iat === 'number' &&
      typeof claims.exp === 'number' &&
      typeof claims.iss === 'string'
    );
  }
}
