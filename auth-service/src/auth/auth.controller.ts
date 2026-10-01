import { Body, Controller, Get, HttpCode, Post, UseGuards } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiBody,
  ApiCreatedResponse,
  ApiOkResponse,
  ApiOperation,
  ApiResponse,
  ApiTags,
} from '@nestjs/swagger';

import { EmailService } from '../email/email.service';

import { AuthService } from './auth.service';
import { AuthUser } from './decorators/auth-user.decorator';
import { ErrorResponseDto } from './dto/error-response.dto';
import { LoginRequestDto } from './dto/login-request.dto';
import { RefreshRequestDto } from './dto/refresh-request.dto';
import { RegisterRequestDto } from './dto/register-request.dto';
import { TokenResponseDto } from './dto/token-response.dto';
import { UserResponseDto } from './dto/user-response.dto';
import { JwtAuthGuard } from './guards/jwt-auth.guard';
import { LoginThrottleGuard } from './guards/login-throttle.guard';
import type { AccessTokenClaims } from './types/auth.types';

@ApiTags('Auth')
@Controller('auth')
export class AuthController {
  constructor(
    private readonly authService: AuthService,
    private readonly emailService: EmailService,
  ) {}

  @Post('register')
  @ApiOperation({ summary: 'Register a user' })
  @ApiBody({ type: RegisterRequestDto })
  @ApiCreatedResponse({ type: UserResponseDto })
  @ApiResponse({ status: 409, type: ErrorResponseDto })
  @ApiResponse({ status: 422, type: ErrorResponseDto })
  register(@Body() request: RegisterRequestDto): Promise<UserResponseDto> {
    return this.authService.register(request);
  }

  @Get('send-temporary-password')
  async sendTemporaryPassword(): Promise<void> {
    const temporaryPassword = Math.random().toString(36).slice(-8);
    await this.emailService.sendTemporaryPassword(
      "kaushikvijay9911@gmail.com",
      "kaushikvijay9911@gmail.com",
      temporaryPassword,
    );
  }

  @Post('login')
  @HttpCode(200)
  @UseGuards(LoginThrottleGuard)
  @ApiOperation({ summary: 'Log in and receive tokens' })
  @ApiBody({ type: LoginRequestDto })
  @ApiOkResponse({ type: TokenResponseDto })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 429, type: ErrorResponseDto })
  @ApiResponse({ status: 422, type: ErrorResponseDto })
  login(@Body() request: LoginRequestDto): Promise<TokenResponseDto> {
    return this.authService.login(request);
  }

  @Post('refresh')
  @HttpCode(200)
  @ApiOperation({ summary: 'Exchange a refresh token for a new token pair' })
  @ApiBody({ type: RefreshRequestDto })
  @ApiOkResponse({ type: TokenResponseDto })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 422, type: ErrorResponseDto })
  refresh(@Body() request: RefreshRequestDto): Promise<TokenResponseDto> {
    return this.authService.refresh(request);
  }

  @Get('me')
  @UseGuards(JwtAuthGuard)
  @ApiTags('Profile')
  @ApiBearerAuth('bearerAuth')
  @ApiOperation({ summary: 'Get the authenticated user' })
  @ApiOkResponse({ type: UserResponseDto })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  me(@AuthUser() user: AccessTokenClaims): Promise<UserResponseDto> {
    return this.authService.me(user);
  }
}
