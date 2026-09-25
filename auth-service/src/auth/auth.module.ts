import { Module } from '@nestjs/common';

import { AuthController } from './auth.controller';
import { AuthRepository } from './auth.repository';
import { AuthService } from './auth.service';
import { JwtAuthGuard } from './guards/jwt-auth.guard';
import { LoginThrottleGuard } from './guards/login-throttle.guard';
import { TokenService } from './token.service';

@Module({
  controllers: [AuthController],
  providers: [
    AuthService,
    AuthRepository,
    TokenService,
    JwtAuthGuard,
    LoginThrottleGuard,
  ],
  exports: [TokenService, JwtAuthGuard],
})
export class AuthModule {}
