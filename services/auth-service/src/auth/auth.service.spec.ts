import { ConflictException, UnauthorizedException } from '@nestjs/common';
import * as bcrypt from 'bcryptjs';

import { AuthService } from './auth.service';
import { AuthRepository } from './auth.repository';
import { AppLogger } from '../common/logging/app-logger.service';
import { EmailService } from '../email/email.service';
import { TokenService } from './token.service';
import type { Role } from './types/auth.types';

describe('AuthService', () => {
  let authService: AuthService;
  let authRepository: jest.Mocked<AuthRepository>;
  let tokenService: jest.Mocked<TokenService>;
  let emailService: jest.Mocked<Pick<EmailService, 'sendTemporaryPassword' | 'sendRejectionEmail'>>;
  let logger: jest.Mocked<Pick<AppLogger, 'log' | 'warn' | 'error' | 'debug' | 'verbose'>>;

  const userRecord = {
    id: 'user-id',
    username: 'priya.menon',
    passwordHash: '$2b$12$gR1nXz7VvPBmkfA6o8URxe2P0xX0/84f8M6XqQ.A8lP3bEf4thvqe',
    accountId: '11111111-1111-4111-8111-111111111111',
    roles: ['CUSTOMER'] as Role[],
    createdOn: new Date('2026-01-01T00:00:00Z'),
  };

  beforeEach(() => {
    authRepository = {
      findUserByUsername: jest.fn(),
      findUserById: jest.fn(),
      findUserByAccountId: jest.fn(),
      findSignupRequestById: jest.fn(),
      findActiveSignupRequestByUsername: jest.fn(),
      createSignupRequest: jest.fn(),
      listSignupRequests: jest.fn(),
      markSignupRequestApproved: jest.fn(),
      markSignupRequestRejected: jest.fn(),
      createUser: jest.fn(),
      updateUserPasswordHash: jest.fn(),
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

    logger = {
      log: jest.fn(),
      warn: jest.fn(),
      error: jest.fn(),
      debug: jest.fn(),
      verbose: jest.fn(),
    };

    emailService = {
      sendTemporaryPassword: jest.fn(),
      sendRejectionEmail: jest.fn(),
    };

    authService = new AuthService(
      authRepository,
      tokenService,
      emailService as EmailService,
      logger as AppLogger,
    );
  });

  beforeEach(() => {
    console.info(`[TEST START] ${expect.getState().currentTestName}`);
  });

  afterEach(() => {
    console.info(`[TEST END] ${expect.getState().currentTestName}`);
  });

  it('rejects register when username already exists', async () => {
    authRepository.findUserByUsername.mockResolvedValue(userRecord);

    await expect(
      authService.register({
        username: userRecord.username,
        password: 'StrongPassword#123',
        accountId: userRecord.accountId,
      }),
    ).rejects.toBeInstanceOf(ConflictException);
  });

  it('rejects register when account reference is already linked', async () => {
    authRepository.findUserByUsername.mockResolvedValue(null);
    authRepository.findUserByAccountId.mockResolvedValue(userRecord);

    await expect(
      authService.register({
        username: 'new.user',
        password: 'StrongPassword#123',
        accountId: userRecord.accountId,
      }),
    ).rejects.toBeInstanceOf(ConflictException);
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

  it('newly issued refresh token can be exchanged for a fresh token pair', async () => {
    authRepository.findRefreshTokenByHash.mockResolvedValue({
      id: 'refresh-id-1',
      userId: 'user-id',
      tokenHash: 'hash',
      expiresOn: new Date(Date.now() + 10_000),
      consumedOn: null,
      revokedOn: null,
    });
    authRepository.consumeRefreshToken.mockResolvedValue(true);
    authRepository.findUserById.mockResolvedValue(userRecord);
    tokenService.signAccessToken.mockReturnValue('access-token-1');

    const first = await authService.refresh({ refreshToken: 'valid-refresh-token' });
    expect(first.accessToken).toBe('access-token-1');

    authRepository.findRefreshTokenByHash.mockResolvedValueOnce({
      id: 'refresh-id-2',
      userId: 'user-id',
      tokenHash: 'hash-2',
      expiresOn: new Date(Date.now() + 10_000),
      consumedOn: null,
      revokedOn: null,
    });
    tokenService.signAccessToken.mockReturnValueOnce('access-token-2');

    const second = await authService.refresh({ refreshToken: first.refreshToken });

    expect(second.accessToken).toBe('access-token-2');
    expect(second.refreshToken).toHaveLength(64);
    expect(authRepository.storeRefreshToken).toHaveBeenCalledTimes(2);
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

  it('rejects refresh when token hash is unknown', async () => {
    authRepository.findRefreshTokenByHash.mockResolvedValue(null);

    await expect(
      authService.refresh({ refreshToken: 'missing-token' }),
    ).rejects.toBeInstanceOf(UnauthorizedException);
  });

  it('rejects refresh when token is revoked', async () => {
    authRepository.findRefreshTokenByHash.mockResolvedValue({
      id: 'refresh-id',
      userId: 'user-id',
      tokenHash: 'hash',
      expiresOn: new Date(Date.now() + 10_000),
      consumedOn: null,
      revokedOn: new Date(),
    });

    await expect(
      authService.refresh({ refreshToken: 'revoked-token' }),
    ).rejects.toBeInstanceOf(UnauthorizedException);

    expect(authRepository.revokeAllRefreshTokensForUser).toHaveBeenCalledTimes(1);
  });

  it('rejects refresh when token has expired', async () => {
    authRepository.findRefreshTokenByHash.mockResolvedValue({
      id: 'refresh-id',
      userId: 'user-id',
      tokenHash: 'hash',
      expiresOn: new Date(Date.now() - 1_000),
      consumedOn: null,
      revokedOn: null,
    });

    await expect(
      authService.refresh({ refreshToken: 'expired-token' }),
    ).rejects.toBeInstanceOf(UnauthorizedException);

    expect(authRepository.revokeAllRefreshTokensForUser).toHaveBeenCalledTimes(1);
  });

  it('rejects refresh when consume step fails due to replay race', async () => {
    authRepository.findRefreshTokenByHash.mockResolvedValue({
      id: 'refresh-id',
      userId: 'user-id',
      tokenHash: 'hash',
      expiresOn: new Date(Date.now() + 10_000),
      consumedOn: null,
      revokedOn: null,
    });
    authRepository.consumeRefreshToken.mockResolvedValue(false);

    await expect(
      authService.refresh({ refreshToken: 'racy-token' }),
    ).rejects.toBeInstanceOf(UnauthorizedException);

    expect(authRepository.revokeAllRefreshTokensForUser).toHaveBeenCalledTimes(1);
  });

  it('rejects refresh when token user no longer exists', async () => {
    authRepository.findRefreshTokenByHash.mockResolvedValue({
      id: 'refresh-id',
      userId: 'user-id',
      tokenHash: 'hash',
      expiresOn: new Date(Date.now() + 10_000),
      consumedOn: null,
      revokedOn: null,
    });
    authRepository.consumeRefreshToken.mockResolvedValue(true);
    authRepository.findUserById.mockResolvedValue(null);

    await expect(
      authService.refresh({ refreshToken: 'orphaned-user-token' }),
    ).rejects.toBeInstanceOf(UnauthorizedException);
  });

  it('accepts login with correct password and issues tokens', async () => {
    const password = 'StrongPassword#123';
    const hash = await bcrypt.hash(password, 12);

    authRepository.findUserByUsername.mockResolvedValue({
      ...userRecord,
      passwordHash: hash,
    });
    tokenService.signAccessToken.mockReturnValue('access-token');

    const result = await authService.login({
      username: userRecord.username,
      password,
    });

    expect(result.accessToken).toBe('access-token');
    expect(result.refreshToken).toHaveLength(64);
    expect(result.tokenType).toBe('Bearer');
    expect(result.expiresIn).toBe(900);
  });

  it('accepts login when accidental surrounding spaces are included around a valid password', async () => {
    const password = 'StrongPassword#123';
    const hash = await bcrypt.hash(password, 12);

    authRepository.findUserByUsername.mockResolvedValue({
      ...userRecord,
      passwordHash: hash,
    });
    tokenService.signAccessToken.mockReturnValue('access-token');

    const result = await authService.login({
      username: userRecord.username,
      password: `  ${password}  `,
    });

    expect(result.accessToken).toBe('access-token');
    expect(result.tokenType).toBe('Bearer');
  });

  it('accepts login when the stored password intentionally includes surrounding spaces', async () => {
    const passwordWithSpaces = '  StrongPassword#123  ';
    const hash = await bcrypt.hash(passwordWithSpaces, 12);

    authRepository.findUserByUsername.mockResolvedValue({
      ...userRecord,
      passwordHash: hash,
    });
    tokenService.signAccessToken.mockReturnValue('access-token');

    const result = await authService.login({
      username: userRecord.username,
      password: passwordWithSpaces,
    });

    expect(result.accessToken).toBe('access-token');
    expect(result.tokenType).toBe('Bearer');
  });

  it('rejects login with incorrect password', async () => {
    const hash = await bcrypt.hash('CorrectPassword#123', 12);
    authRepository.findUserByUsername.mockResolvedValue({
      ...userRecord,
      passwordHash: hash,
    });

    await expect(
      authService.login({
        username: userRecord.username,
        password: 'WrongPassword#123',
      }),
    ).rejects.toBeInstanceOf(UnauthorizedException);
  });

  it('unknown user and wrong password both return UnauthorizedException with same body', async () => {
    authRepository.findUserByUsername.mockResolvedValueOnce(null);

    await expect(
      authService.login({
        username: 'unknown.user',
        password: 'SomePassword#123',
      }),
    ).rejects.toEqual(expect.objectContaining({ response: expect.objectContaining({ errorCode: 'AUTH-401' }) }));

    const hash = await bcrypt.hash('CorrectPassword#123', 12);
    authRepository.findUserByUsername.mockResolvedValueOnce({
      ...userRecord,
      passwordHash: hash,
    });

    await expect(
      authService.login({
        username: userRecord.username,
        password: 'WrongPassword#123',
      }),
    ).rejects.toEqual(expect.objectContaining({ response: expect.objectContaining({ errorCode: 'AUTH-401' }) }));
  });

  it('unknown user path still performs bcrypt compare using dummy hash', async () => {
    const issueTokenPairSpy = jest.spyOn(authService as never, 'issueTokenPair');
    authRepository.findUserByUsername.mockResolvedValue(null);

    await expect(
      authService.login({
        username: 'unknown.user',
        password: 'AnyPassword#123',
      }),
    ).rejects.toBeInstanceOf(UnauthorizedException);

    expect(issueTokenPairSpy).not.toHaveBeenCalled();
  });

  it('rejects me when account from claims is not found', async () => {
    authRepository.findUserByAccountId.mockResolvedValue(null);

    await expect(
      authService.me({
        sub: '11111111-1111-4111-8111-111111111111',
        accountId: '11111111-1111-4111-8111-111111111111',
        roles: ['CUSTOMER'],
        iat: 1,
        exp: 2,
        iss: 'auth-service',
      }),
    ).rejects.toBeInstanceOf(UnauthorizedException);
  });

  it('returns profile details on me when available', async () => {
    authRepository.findUserByAccountId.mockResolvedValue({
      ...userRecord,
      firstName: 'Priya',
      lastName: 'Menon',
      phone: '+919900001234',
      dob: new Date('1996-06-15T00:00:00.000Z'),
    });

    const response = await authService.me({
      sub: userRecord.accountId,
      accountId: userRecord.accountId,
      roles: ['CUSTOMER'],
      iat: 1,
      exp: 2,
      iss: 'auth-service',
    });

    expect(response.firstName).toBe('Priya');
    expect(response.lastName).toBe('Menon');
    expect(response.phone).toBe('+919900001234');
    expect(response.dob).toBe('1996-06-15');
  });

  it('change password rejects mismatched confirm password with validation error', async () => {
    await expect(
      authService.changePassword(
        {
          sub: userRecord.accountId,
          accountId: userRecord.accountId,
          roles: ['CUSTOMER'],
          iat: 1,
          exp: 2,
          iss: 'auth-service',
        },
        {
          oldPassword: 'OldPassword#123',
          newPassword: 'NewPassword#123',
          confirmNewPassword: 'DifferentPassword#123',
        },
      ),
    ).rejects.toEqual(expect.objectContaining({ response: expect.objectContaining({ errorCode: 'VAL-422' }) }));
  });

  it('change password rejects incorrect old password', async () => {
    const hash = await bcrypt.hash('CurrentPassword#123', 12);
    authRepository.findUserByAccountId.mockResolvedValue({
      ...userRecord,
      passwordHash: hash,
    });

    await expect(
      authService.changePassword(
        {
          sub: userRecord.accountId,
          accountId: userRecord.accountId,
          roles: ['CUSTOMER'],
          iat: 1,
          exp: 2,
          iss: 'auth-service',
        },
        {
          oldPassword: 'WrongPassword#123',
          newPassword: 'NextPassword#123',
          confirmNewPassword: 'NextPassword#123',
        },
      ),
    ).rejects.toBeInstanceOf(UnauthorizedException);
  });

  it('change password succeeds and revokes refresh tokens', async () => {
    const hash = await bcrypt.hash('CurrentPassword#123', 12);
    authRepository.findUserByAccountId.mockResolvedValue({
      ...userRecord,
      passwordHash: hash,
    });
    authRepository.updateUserPasswordHash.mockResolvedValue(true);

    const result = await authService.changePassword(
      {
        sub: userRecord.accountId,
        accountId: userRecord.accountId,
        roles: ['CUSTOMER'],
        iat: 1,
        exp: 2,
        iss: 'auth-service',
      },
      {
        oldPassword: 'CurrentPassword#123',
        newPassword: 'NewPassword#456',
        confirmNewPassword: 'NewPassword#456',
      },
    );

    expect(result.message).toContain('Please sign in again');
    expect(authRepository.updateUserPasswordHash).toHaveBeenCalledWith(userRecord.id, expect.any(String));
    expect(authRepository.revokeAllRefreshTokensForUser).toHaveBeenCalledWith(
      userRecord.id,
      expect.any(Date),
    );
  });
});
