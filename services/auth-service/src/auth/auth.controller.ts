import { Body, Controller, Get, Headers, HttpCode, Param, Patch, Post, Query, UnauthorizedException, UseGuards } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiBody,
  ApiCreatedResponse,
  ApiOkResponse,
  ApiOperation,
  ApiResponse,
  ApiTags,
} from '@nestjs/swagger';

import { AuthService } from './auth.service';
import { AuthUser } from './decorators/auth-user.decorator';
import { ApproveSignupRequestDto } from './dto/approve-signup-request.dto';
import { ApproveSignupRequestResponseDto } from './dto/approve-signup-request-response.dto';
import { ChangePasswordRequestDto } from './dto/change-password-request.dto';
import { ChangePasswordResponseDto } from './dto/change-password-response.dto';
import { CreateSignupRequestDto } from './dto/create-signup-request.dto';
import { ErrorResponseDto } from './dto/error-response.dto';
import { ListSignupRequestsQueryDto } from './dto/list-signup-requests-query.dto';
import { LoginRequestDto } from './dto/login-request.dto';
import { RejectSignupRequestDto } from './dto/reject-signup-request.dto';
import { RejectSignupRequestResponseDto } from './dto/reject-signup-request-response.dto';
import { RefreshRequestDto } from './dto/refresh-request.dto';
import { RegisterRequestDto } from './dto/register-request.dto';
import { OrderPlacedNotificationRequestDto } from './dto/order-placed-notification-request.dto';
import { OrderPlacedNotificationResponseDto } from './dto/order-placed-notification-response.dto';
import { PriceAlertNotificationRequestDto } from './dto/price-alert-notification-request.dto';
import { PriceAlertNotificationResponseDto } from './dto/price-alert-notification-response.dto';
import { SignupRequestResponseDto } from './dto/signup-request-response.dto';
import { TokenResponseDto } from './dto/token-response.dto';
import { UserResponseDto } from './dto/user-response.dto';
import { JwtAuthGuard } from './guards/jwt-auth.guard';
import { LoginThrottleGuard } from './guards/login-throttle.guard';
import type { AccessTokenClaims } from './types/auth.types';
import { ERROR_UNAUTHORISED } from './auth.constants';

@ApiTags('Auth')
@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

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

  @Post('change-password')
  @HttpCode(200)
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth('bearerAuth')
  @ApiOperation({ summary: 'Change password for the authenticated user' })
  @ApiBody({ type: ChangePasswordRequestDto })
  @ApiOkResponse({ type: ChangePasswordResponseDto })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 422, type: ErrorResponseDto })
  changePassword(
    @AuthUser() user: AccessTokenClaims,
    @Body() request: ChangePasswordRequestDto,
  ): Promise<ChangePasswordResponseDto> {
    return this.authService.changePassword(user, request);
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

  @Post('notifications/order-placed')
  @HttpCode(202)
  @ApiOperation({ summary: 'Send order placed email notification (internal service endpoint)' })
  @ApiBody({ type: OrderPlacedNotificationRequestDto })
  @ApiOkResponse({ type: OrderPlacedNotificationResponseDto })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 404, type: ErrorResponseDto })
  notifyOrderPlaced(
    @Body() request: OrderPlacedNotificationRequestDto,
    @Headers('x-internal-token') internalToken?: string,
  ): Promise<OrderPlacedNotificationResponseDto> {
    this.assertInternalCall(internalToken);
    return this.authService.sendOrderPlacedNotification(request);
  }

  @Post('notifications/price-alert')
  @HttpCode(202)
  @ApiOperation({ summary: 'Send price alert email notification (internal service endpoint)' })
  @ApiBody({ type: PriceAlertNotificationRequestDto })
  @ApiOkResponse({ type: PriceAlertNotificationResponseDto })
  @ApiResponse({ status: 401, type: ErrorResponseDto })
  @ApiResponse({ status: 404, type: ErrorResponseDto })
  notifyPriceAlert(
    @Body() request: PriceAlertNotificationRequestDto,
    @Headers('x-internal-token') internalToken?: string,
  ): Promise<PriceAlertNotificationResponseDto> {
    this.assertInternalCall(internalToken);
    return this.authService.sendPriceAlertNotification(request);
  }

  private assertInternalCall(internalToken?: string): void {
    const configuredToken = process.env.INTERNAL_SERVICE_TOKEN?.trim();
    if (!configuredToken) {
      throw new UnauthorizedException(ERROR_UNAUTHORISED);
    }

    if (!internalToken || internalToken.trim() !== configuredToken) {
      throw new UnauthorizedException(ERROR_UNAUTHORISED);
    }
  }
}
