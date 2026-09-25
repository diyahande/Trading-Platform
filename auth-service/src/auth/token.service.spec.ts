import { ConfigService } from '@nestjs/config';
import * as jwt from 'jsonwebtoken';

import { TokenService } from './token.service';

describe('TokenService', () => {
  const secret = 'test-secret-key-for-jwt-signing-min-32-bytes';
  let tokenService: TokenService;

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

    tokenService = new TokenService(configService);
  });

  it('issues token with contract claims and expiry', () => {
    const token = tokenService.signAccessToken(
      '11111111-1111-4111-8111-111111111111',
      ['CUSTOMER'],
    );

    const payload = jwt.decode(token) as jwt.JwtPayload;

    expect(payload.sub).toBe('11111111-1111-4111-8111-111111111111');
    expect(payload.accountId).toBe('11111111-1111-4111-8111-111111111111');
    expect(payload.roles).toEqual(['CUSTOMER']);
    expect(payload.iss).toBe('auth-service');
    expect(typeof payload.iat).toBe('number');
    expect(typeof payload.exp).toBe('number');
    expect((payload.exp as number) - (payload.iat as number)).toBe(900);
  });

  it('token signature verifies with service key', () => {
    const token = tokenService.signAccessToken(
      '11111111-1111-4111-8111-111111111111',
      ['CUSTOMER'],
    );

    expect(() =>
      jwt.verify(token, secret, {
        algorithms: ['HS256'],
        issuer: 'auth-service',
      }),
    ).not.toThrow();
  });

  it('does not include claims outside the allowed contract set', () => {
    const token = tokenService.signAccessToken(
      '11111111-1111-4111-8111-111111111111',
      ['CUSTOMER'],
    );

    const payload = jwt.decode(token) as jwt.JwtPayload;
    const keys = Object.keys(payload).sort();

    expect(keys).toEqual(['accountId', 'exp', 'iat', 'iss', 'roles', 'sub']);
  });

  it('does not use MD5 or SHA digest helpers for token signing', () => {
    const createHashSpy = jest.spyOn(require('node:crypto'), 'createHash');

    tokenService.signAccessToken('11111111-1111-4111-8111-111111111111', ['CUSTOMER']);

    expect(createHashSpy).not.toHaveBeenCalled();
    createHashSpy.mockRestore();
  });
});
