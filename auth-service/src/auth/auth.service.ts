import { createHash, randomBytes } from 'node:crypto';

import {
  ConflictException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import * as bcrypt from 'bcryptjs';

import {
  DUMMY_PASSWORD_HASH,
  ERROR_REGISTRATION_FAILED,
  ERROR_UNAUTHORISED,
} from './auth.constants';
import { AuthRepository } from './auth.repository';
import { AppLogger } from '../common/logging/app-logger.service';
import type {
  AccessTokenClaims,
  AuthUserRecord,
  Role,
} from './types/auth.types';
import { TokenService } from './token.service';
import type { LoginRequestDto } from './dto/login-request.dto';
import type { RefreshRequestDto } from './dto/refresh-request.dto';
import type { RegisterRequestDto } from './dto/register-request.dto';
import type { TokenResponseDto } from './dto/token-response.dto';
import type { UserResponseDto } from './dto/user-response.dto';

@Injectable()
export class AuthService {
  constructor(
    private readonly authRepository: AuthRepository,
    private readonly tokenService: TokenService,
    private readonly logger: AppLogger,
  ) {}

  async register(request: RegisterRequestDto): Promise<UserResponseDto> {
    const existing = await this.authRepository.findUserByUsername(request.username);
    if (existing) {
      this.logger.warn('Registration rejected: username already exists', {
        event: 'auth.register.rejected.username_exists',
        username: request.username,
      });
      throw new ConflictException(ERROR_REGISTRATION_FAILED);
    }

    const existingAccountId = await this.authRepository.findUserByAccountId(request.accountId);
    if (existingAccountId) {
      this.logger.warn('Registration rejected: account already linked', {
        event: 'auth.register.rejected.account_exists',
        accountId: request.accountId,
      });
      throw new ConflictException(ERROR_REGISTRATION_FAILED);
    }

    const user = await this.authRepository.createUser({
      username: request.username,
      passwordHash: await bcrypt.hash(request.password, 12),
      accountId: request.accountId,
      roles: ['CUSTOMER'],
    });

    this.logger.log('Registration completed', {
      event: 'auth.register.success',
      userId: user.id,
      username: user.username,
      accountId: user.accountId,
    });

    return this.toUserResponse(user);
  }

  async login(request: LoginRequestDto): Promise<TokenResponseDto> {
    const user = await this.authRepository.findUserByUsername(request.username);
    const passwordHash = user?.passwordHash ?? DUMMY_PASSWORD_HASH;
    const isValidPassword = await bcrypt.compare(request.password, passwordHash);

    if (!user || !isValidPassword) {
      this.logger.warn('Login failed', {
        event: 'auth.login.failed',
        username: request.username,
      });
      throw new UnauthorizedException(ERROR_UNAUTHORISED);
    }

    this.logger.log('Login accepted', {
      event: 'auth.login.success',
      userId: user.id,
      username: user.username,
    });

    return this.issueTokenPair(user);
  }

  async refresh(request: RefreshRequestDto): Promise<TokenResponseDto> {
    const now = new Date();
    const tokenHash = this.hashRefreshToken(request.refreshToken);
    const refreshToken = await this.authRepository.findRefreshTokenByHash(tokenHash);

    if (!refreshToken) {
      this.logger.warn('Refresh rejected: token not found', {
        event: 'auth.refresh.failed.token_not_found',
      });
      this.throwUnauthorised();
    }

    if (
      refreshToken.consumedOn ||
      refreshToken.revokedOn ||
      refreshToken.expiresOn.getTime() <= now.getTime()
    ) {
      this.logger.warn('Refresh rejected: token invalid state', {
        event: 'auth.refresh.failed.invalid_state',
        userId: refreshToken.userId,
      });
      await this.authRepository.revokeAllRefreshTokensForUser(refreshToken.userId, now);
      this.throwUnauthorised();
    }

    const consumed = await this.authRepository.consumeRefreshToken(refreshToken.id, now);
    if (!consumed) {
      this.logger.warn('Refresh rejected: consume race', {
        event: 'auth.refresh.failed.consume_race',
        userId: refreshToken.userId,
      });
      await this.authRepository.revokeAllRefreshTokensForUser(refreshToken.userId, now);
      this.throwUnauthorised();
    }

    const user = await this.authRepository.findUserById(refreshToken.userId);
    if (!user) {
      this.logger.warn('Refresh rejected: user not found', {
        event: 'auth.refresh.failed.user_not_found',
        userId: refreshToken.userId,
      });
      this.throwUnauthorised();
    }

    this.logger.log('Refresh accepted', {
      event: 'auth.refresh.success',
      userId: user.id,
      username: user.username,
    });

    return this.issueTokenPair(user);
  }

  async me(claims: AccessTokenClaims): Promise<UserResponseDto> {
    const user = await this.authRepository.findUserByAccountId(claims.sub);
    if (!user) {
      this.logger.warn('Profile lookup failed', {
        event: 'auth.me.failed.user_not_found',
        subject: claims.sub,
      });
      throw new UnauthorizedException(ERROR_UNAUTHORISED);
    }

    return this.toUserResponse(user);
  }

  private async issueTokenPair(user: AuthUserRecord): Promise<TokenResponseDto> {
    const refreshToken = randomBytes(32).toString('hex');

    await this.authRepository.storeRefreshToken({
      userId: user.id,
      tokenHash: this.hashRefreshToken(refreshToken),
      expiresOn: new Date(Date.now() + this.tokenService.refreshTtlSeconds * 1000),
    });

    return {
      accessToken: this.tokenService.signAccessToken(user.accountId, user.roles),
      refreshToken,
      tokenType: 'Bearer',
      expiresIn: this.tokenService.accessTtlSeconds,
    };
  }

  private hashRefreshToken(token: string): string {
    return createHash('sha256').update(token).digest('hex');
  }

  private toUserResponse(user: AuthUserRecord): UserResponseDto {
    return {
      id: user.id,
      username: user.username,
      accountId: user.accountId,
      roles: user.roles,
      createdOn: user.createdOn.toISOString(),
    };
  }

  private throwUnauthorised(): never {
    throw new UnauthorizedException(ERROR_UNAUTHORISED);
  }
}
