import { UnauthorizedException } from '@nestjs/common';

import { AuthService } from './auth.service';
import { AuthRepository } from './auth.repository';
import { TokenService } from './token.service';

describe('AuthService', () => {
  let authService: AuthService;
  let authRepository: jest.Mocked<AuthRepository>;
  let tokenService: jest.Mocked<TokenService>;

  beforeEach(() => {
    authRepository = {
      findUserByUsername: jest.fn(),
      findUserById: jest.fn(),
      findUserByAccountId: jest.fn(),
      createUser: jest.fn(),
      storeRefreshToken: jest.fn(),
      findRefreshTokenByHash: jest.fn(),
      consumeRefreshToken: jest.fn(),
      revokeAllRefreshTokensForUser: jest.fn(),
      onModuleDestroy: jest.fn(),
    } as unknown as jest.Mocked<AuthRepository>;

    tokenService = {
      signAccessToken: jest.fn(),
      verifyAccessToken: jest.fn(),
      accessTtlSeconds: 900,
      refreshTtlSeconds: 604800,
    } as unknown as jest.Mocked<TokenService>;

    authService = new AuthService(authRepository, tokenService);
  });

  it('issues new access and refresh tokens on successful refresh', async () => {
    authRepository.findRefreshTokenByHash.mockResolvedValue({
      id: 'refresh-id',
      userId: 'user-id',
      tokenHash: 'hash',
      expiresOn: new Date(Date.now() + 10_000),
      consumedOn: null,
      revokedOn: null,
    });
    authRepository.consumeRefreshToken.mockResolvedValue(true);
    authRepository.findUserById.mockResolvedValue({
      id: 'user-id',
      username: 'priya.menon',
      passwordHash: 'hash',
      accountId: '11111111-1111-4111-8111-111111111111',
      roles: ['CUSTOMER'],
      createdOn: new Date('2026-01-01T00:00:00Z'),
    });
    tokenService.signAccessToken.mockReturnValue('access-token');

    const result = await authService.refresh({ refreshToken: 'opaque-token' });

    expect(result.accessToken).toBe('access-token');
    expect(result.tokenType).toBe('Bearer');
    expect(result.expiresIn).toBe(900);
    expect(result.refreshToken).toHaveLength(64);
    expect(authRepository.storeRefreshToken).toHaveBeenCalledTimes(1);
  });

  it('revokes all refresh tokens and rejects when a consumed token is presented again', async () => {
    authRepository.findRefreshTokenByHash.mockResolvedValue({
      id: 'refresh-id',
      userId: 'user-id',
      tokenHash: 'hash',
      expiresOn: new Date(Date.now() + 10_000),
      consumedOn: new Date(),
      revokedOn: null,
    });

    await expect(
      authService.refresh({ refreshToken: 'already-consumed-token' }),
    ).rejects.toBeInstanceOf(UnauthorizedException);

    expect(authRepository.revokeAllRefreshTokensForUser).toHaveBeenCalledWith(
      'user-id',
      expect.any(Date),
    );
  });
});
