import { Test } from '@nestjs/testing';
import { JwtAuthGuard } from './jwt-auth.guard';
import { JwtService } from './jwt.service';
import * as jwt from 'jsonwebtoken';

describe('JwtAuthGuard', () => {
  let guard: JwtAuthGuard;
  let jwtService: JwtService;

  beforeEach(async () => {
    const mod = await Test.createTestingModule({
      providers: [JwtService, JwtAuthGuard],
    }).compile();
    jwtService = mod.get(JwtService);
    guard = mod.get(JwtAuthGuard);
  });

  function makeContext(authHeader?: string) {
    return {
      switchToHttp: () => ({ getRequest: () => ({ headers: { authorization: authHeader } }) }),
    } as any;
  }

  it('a valid token is accepted', () => {
    const token = jwtService.sign({ sub: 'uuid', accountId: 1, roles: ['CUSTOMER'], iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 900, iss: 'auth-service' });
    const ctx = makeContext(`Bearer ${token}`);
    expect(guard.canActivate(ctx)).toBe(true);
  });

  it('an expired token is refused', () => {
    const past = Math.floor(Date.now() / 1000) - 3600;
    const token = jwt.sign({ sub: 'uuid', accountId: 1, roles: ['CUSTOMER'], iat: past - 100, exp: past, iss: 'auth-service' }, process.env.JWT_SECRET || 'dev-secret', { algorithm: 'HS256' });
    const ctx = makeContext(`Bearer ${token}`);
    expect(() => guard.canActivate(ctx)).toThrow();
  });

  it('a token with a wrong signature is refused before any claim is read', () => {
    const token = jwt.sign({ sub: 'uuid', accountId: 1, roles: ['CUSTOMER'], iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 900, iss: 'auth-service' }, 'different-secret', { algorithm: 'HS256' });
    const ctx = makeContext(`Bearer ${token}`);
    expect(() => guard.canActivate(ctx)).toThrow();
  });

  it('a malformed header is refused', () => {
    const ctx = makeContext('BadHeader');
    expect(() => guard.canActivate(ctx)).toThrow();
  });
});
