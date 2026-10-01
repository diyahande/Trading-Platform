import { Body, Controller, Get, HttpCode, Param, Patch, Post, Query, UseGuards } from '@nestjs/common';
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
import { ApproveSignupRequestDto } from './dto/approve-signup-request.dto';
import { ApproveSignupRequestResponseDto } from './dto/approve-signup-request-response.dto';
import { CreateSignupRequestDto } from './dto/create-signup-request.dto';
import { ErrorResponseDto } from './dto/error-response.dto';
import { ListSignupRequestsQueryDto } from './dto/list-signup-requests-query.dto';
import { LoginRequestDto } from './dto/login-request.dto';
import { RejectSignupRequestDto } from './dto/reject-signup-request.dto';
import { RejectSignupRequestResponseDto } from './dto/reject-signup-request-response.dto';
import { RefreshRequestDto } from './dto/refresh-request.dto';
import { RegisterRequestDto } from './dto/register-request.dto';
import { SignupRequestResponseDto } from './dto/signup-request-response.dto';
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

  @Post('signup-request')
  @ApiOperation({ summary: 'Submit user signup request' })
  @ApiBody({ type: CreateSignupRequestDto })
  @ApiCreatedResponse({ type: SignupRequestResponseDto })
  @ApiResponse({ status: 409, type: ErrorResponseDto })
  @ApiResponse({ status: 422, type: ErrorResponseDto })
  signupRequest(@Body() request: CreateSignupRequestDto): Promise<SignupRequestResponseDto> {
    return this.authService.submitSignupRequest(request);
  }

  @Get('admin/signup-requests')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth('bearerAuth')
  @ApiOperation({ summary: 'List signup requests for admin review' })
  @ApiOkResponse({ type: SignupRequestResponseDto, isArray: true })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 403, type: ErrorResponseDto })
  listSignupRequests(
    @AuthUser() user: AccessTokenClaims,
    @Query() query: ListSignupRequestsQueryDto,
  ): Promise<SignupRequestResponseDto[]> {
    return this.authService.listSignupRequests(user, query.status);
  }

  @Patch('admin/signup-requests/:id/approve-and-register')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth('bearerAuth')
  @ApiOperation({ summary: 'Approve request and register user with admin provided account id' })
  @ApiBody({ type: ApproveSignupRequestDto })
  @ApiOkResponse({ type: ApproveSignupRequestResponseDto })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 403, type: ErrorResponseDto })
  @ApiResponse({ status: 404, type: ErrorResponseDto })
  @ApiResponse({ status: 409, type: ErrorResponseDto })
  approveAndRegisterSignupRequest(
    @AuthUser() user: AccessTokenClaims,
    @Param('id') id: string,
    @Body() request: ApproveSignupRequestDto,
  ): Promise<ApproveSignupRequestResponseDto> {
    return this.authService.approveAndRegisterSignupRequest(user, id, request);
  }

  @Patch('admin/signup-requests/:id/reject')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth('bearerAuth')
  @ApiOperation({ summary: 'Reject a signup request and notify the applicant' })
  @ApiBody({ type: RejectSignupRequestDto })
  @ApiOkResponse({ type: RejectSignupRequestResponseDto })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 403, type: ErrorResponseDto })
  @ApiResponse({ status: 404, type: ErrorResponseDto })
  @ApiResponse({ status: 409, type: ErrorResponseDto })
  rejectSignupRequest(
    @AuthUser() user: AccessTokenClaims,
    @Param('id') id: string,
    @Body() request: RejectSignupRequestDto,
  ): Promise<RejectSignupRequestResponseDto> {
    return this.authService.rejectSignupRequest(user, id, request);
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
