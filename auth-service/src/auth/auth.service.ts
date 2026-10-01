import { createHash, randomBytes } from 'node:crypto';

import {
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
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
import { EmailService } from '../email/email.service';
import type {
  AccessTokenClaims,
  AuthUserRecord,
  Role,
  SignupRequestRecord,
  SignupRequestStatus,
} from './types/auth.types';
import { TokenService } from './token.service';
import type { ApproveSignupRequestDto } from './dto/approve-signup-request.dto';
import type { ApproveSignupRequestResponseDto } from './dto/approve-signup-request-response.dto';
import type { CreateSignupRequestDto } from './dto/create-signup-request.dto';
import type { LoginRequestDto } from './dto/login-request.dto';
import type { RejectSignupRequestDto } from './dto/reject-signup-request.dto';
import type { RejectSignupRequestResponseDto } from './dto/reject-signup-request-response.dto';
import type { RefreshRequestDto } from './dto/refresh-request.dto';
import type { RegisterRequestDto } from './dto/register-request.dto';
import type { SignupRequestResponseDto } from './dto/signup-request-response.dto';
import type { TokenResponseDto } from './dto/token-response.dto';
import type { UserResponseDto } from './dto/user-response.dto';

@Injectable()
export class AuthService {
  constructor(
    private readonly authRepository: AuthRepository,
    private readonly tokenService: TokenService,
    private readonly emailService: EmailService,
    private readonly logger: AppLogger,
  ) {}

  async submitSignupRequest(request: CreateSignupRequestDto): Promise<SignupRequestResponseDto> {
    const normalizedUsername = request.username.trim().toLowerCase();

    const existingUser = await this.authRepository.findUserByUsername(normalizedUsername);
    if (existingUser) {
      this.logger.warn('Signup request rejected: username already exists as user', {
        event: 'auth.signup_request.rejected.username_exists',
        username: normalizedUsername,
      });
      throw new ConflictException(ERROR_REGISTRATION_FAILED);
    }

    const activeRequest = await this.authRepository.findActiveSignupRequestByUsername(normalizedUsername);
    if (activeRequest) {
      this.logger.warn('Signup request rejected: active request exists', {
        event: 'auth.signup_request.rejected.active_exists',
        username: normalizedUsername,
        signupRequestId: activeRequest.id,
      });
      throw new ConflictException({
        errorCode: 'AUTH-409',
        message: 'Signup request already exists',
      });
    }

    const created = await this.authRepository.createSignupRequest({
      firstName: request.firstName.trim(),
      lastName: request.lastName.trim(),
      username: normalizedUsername,
      phone: request.phone.trim(),
      dob: new Date(request.dob),
    });

    this.logger.log('Signup request created', {
      event: 'auth.signup_request.created',
      signupRequestId: created.id,
      username: created.username,
    });

    return this.toSignupRequestResponse(created);
  }

  async listSignupRequests(
    claims: AccessTokenClaims,
    status?: SignupRequestStatus,
  ): Promise<SignupRequestResponseDto[]> {
    this.assertAdmin(claims);
    const requests = await this.authRepository.listSignupRequests(status);
    return requests.map((item) => this.toSignupRequestResponse(item));
  }

  async approveAndRegisterSignupRequest(
    claims: AccessTokenClaims,
    requestId: string,
    request: ApproveSignupRequestDto,
  ): Promise<ApproveSignupRequestResponseDto> {
    this.assertAdmin(claims);

    const signupRequest = await this.authRepository.findSignupRequestById(requestId);
    if (!signupRequest) {
      throw new NotFoundException({
        errorCode: 'AUTH-404',
        message: 'Signup request not found',
      });
    }

    if (signupRequest.status !== 'NEW') {
      throw new ConflictException({
        errorCode: 'AUTH-409',
        message: `Signup request is already ${signupRequest.status.toLowerCase()}`,
      });
    }

    const temporaryPassword = this.generateTemporaryPassword();
    const createdUser = await this.register({
      username: signupRequest.username,
      password: temporaryPassword,
      accountId: request.accountId,
      roles: ['CUSTOMER'],
    });

    const reviewedOn = new Date();
    const updated = await this.authRepository.markSignupRequestApproved({
      id: signupRequest.id,
      reviewNotes: this.cleanOptionalText(request.reviewNotes),
      approvedAccountId: request.accountId,
      approvedAuthUserId: createdUser.id,
      reviewedBy: claims.accountId,
      reviewedOn,
    });

    if (!updated) {
      throw new ConflictException({
        errorCode: 'AUTH-409',
        message: 'Signup request can no longer be approved',
      });
    }

    let emailSent = true;
    try {
      await this.emailService.sendTemporaryPassword(
        signupRequest.username,
        signupRequest.username,
        temporaryPassword,
      );
    } catch (error) {
      emailSent = false;
      this.logger.error('Approval email failed', {
        event: 'auth.signup_request.approved.email_failed',
        signupRequestId: signupRequest.id,
        username: signupRequest.username,
        error,
      });
    }

    this.logger.log('Signup request approved and registered', {
      event: 'auth.signup_request.approved',
      signupRequestId: signupRequest.id,
      userId: createdUser.id,
      accountId: request.accountId,
      emailSent,
    });

    return {
      requestId: signupRequest.id,
      authUserId: createdUser.id,
      accountId: request.accountId,
      emailSent,
    };
  }

  async rejectSignupRequest(
    claims: AccessTokenClaims,
    requestId: string,
    request: RejectSignupRequestDto,
  ): Promise<RejectSignupRequestResponseDto> {
    this.assertAdmin(claims);

    const signupRequest = await this.authRepository.findSignupRequestById(requestId);
    if (!signupRequest) {
      throw new NotFoundException({
        errorCode: 'AUTH-404',
        message: 'Signup request not found',
      });
    }

    if (signupRequest.status !== 'NEW') {
      throw new ConflictException({
        errorCode: 'AUTH-409',
        message: `Signup request is already ${signupRequest.status.toLowerCase()}`,
      });
    }

    const updated = await this.authRepository.markSignupRequestRejected({
      id: signupRequest.id,
      rejectionReason: request.rejectionReason.trim(),
      reviewNotes: this.cleanOptionalText(request.reviewNotes),
      reviewedBy: claims.accountId,
      reviewedOn: new Date(),
    });

    if (!updated) {
      throw new ConflictException({
        errorCode: 'AUTH-409',
        message: 'Signup request can no longer be rejected',
      });
    }

    let emailSent = true;
    try {
      await this.emailService.sendRejectionEmail(
        signupRequest.username,
        signupRequest.firstName,
        request.rejectionReason.trim(),
      );
    } catch (error) {
      emailSent = false;
      this.logger.error('Rejection email failed', {
        event: 'auth.signup_request.rejected.email_failed',
        signupRequestId: signupRequest.id,
        username: signupRequest.username,
        error,
      });
    }

    this.logger.log('Signup request rejected', {
      event: 'auth.signup_request.rejected',
      signupRequestId: signupRequest.id,
      reviewedBy: claims.accountId,
      emailSent,
    });

    return {
      requestId: signupRequest.id,
      emailSent,
    };
  }

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

  private generateTemporaryPassword(): string {
    return randomBytes(18).toString('base64url').slice(0, 16);
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

  private toSignupRequestResponse(request: SignupRequestRecord): SignupRequestResponseDto {
    return {
      id: request.id,
      firstName: request.firstName,
      lastName: request.lastName,
      username: request.username,
      phone: request.phone,
      dob: request.dob.toISOString().slice(0, 10),
      status: request.status,
      reviewNotes: request.reviewNotes,
      rejectionReason: request.rejectionReason,
      approvedAccountId: request.approvedAccountId,
      approvedAuthUserId: request.approvedAuthUserId,
      reviewedBy: request.reviewedBy,
      reviewedOn: request.reviewedOn ? request.reviewedOn.toISOString() : null,
      createdOn: request.createdOn.toISOString(),
      updatedOn: request.updatedOn.toISOString(),
    };
  }

  private cleanOptionalText(value?: string | null): string | null {
    if (!value) {
      return null;
    }
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
  }

  private assertAdmin(claims: AccessTokenClaims): void {
    if (!claims.roles.includes('ADMIN')) {
      throw new ForbiddenException({
        errorCode: 'AUTH-403',
        message: 'Admin access required',
      });
    }
  }

  private throwUnauthorised(): never {
    throw new UnauthorizedException(ERROR_UNAUTHORISED);
  }
}
