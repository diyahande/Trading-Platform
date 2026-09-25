import { UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as jwt from 'jsonwebtoken';

import { JwtAuthGuard } from './jwt-auth.guard';
import { TokenService } from '../token.service';

describe('JwtAuthGuard', () => {
  const secret = 'test-secret-key-for-jwt-signing-min-32-bytes';
  let guard: JwtAuthGuard;

  beforeEach(() => {
    const configService = {
      get: (key: string) => {
        if (key === 'JWT_SECRET') {
          return secret;
        }
        if (key === 'JWT_ISSUER') {
          return 'auth-service';
        }
        return undefined;
      },
    } as ConfigService;

    guard = new JwtAuthGuard(new TokenService(configService));
  });

  const makeContext = (authorization?: string) => {
    const request = { headers: { authorization } };

    return {
      request,
      context: {
        switchToHttp: () => ({
          getRequest: () => request,
        }),
      },
    };
  };

  it('rejects an expired token that was otherwise valid and signed correctly', () => {
    const now = Math.floor(Date.now() / 1000);
    const token = jwt.sign(
      {
        sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        accountId: '11111111-1111-4111-8111-111111111111',
        roles: ['CUSTOMER'],
        iat: now - 60,
        exp: now - 1,
      },
      secret,
      {
        algorithm: 'HS256',
        issuer: 'auth-service',
      },
    );

    const { context } = makeContext(`Bearer ${token}`);
    expect(() => guard.canActivate(context as never)).toThrow(UnauthorizedException);
  });

  it('rejects a well-formed unexpired token signed with the wrong key', () => {
    const now = Math.floor(Date.now() / 1000);
    const token = jwt.sign(
      {
        sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        accountId: '11111111-1111-4111-8111-111111111111',
        roles: ['CUSTOMER'],
        iat: now,
        exp: now + 900,
      },
      'different-secret-key-which-should-fail',
      {
        algorithm: 'HS256',
        issuer: 'auth-service',
      },
    );

    const { context } = makeContext(`Bearer ${token}`);
    expect(() => guard.canActivate(context as never)).toThrow(UnauthorizedException);
  });

  it('accepts a valid token and writes contract claims onto request.user', () => {
    const token = jwt.sign(
      {
        sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        accountId: '11111111-1111-4111-8111-111111111111',
        roles: ['CUSTOMER'],
      },
      secret,
      {
        algorithm: 'HS256',
        issuer: 'auth-service',
        expiresIn: 900,
      },
    );

    const { context, request } = makeContext(`Bearer ${token}`);
    expect(guard.canActivate(context as never)).toBe(true);
    expect(request).toHaveProperty('user.sub', '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f');
    expect(request).toHaveProperty('user.accountId', '11111111-1111-4111-8111-111111111111');
    expect(request).toHaveProperty('user.roles');
    expect(request).toHaveProperty('user.iat');
    expect(request).toHaveProperty('user.exp');
    expect(request).toHaveProperty('user.iss', 'auth-service');
  });
});
