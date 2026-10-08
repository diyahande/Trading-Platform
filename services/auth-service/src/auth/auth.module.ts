import { Module } from '@nestjs/common';

import { AuthController } from './auth.controller';
import { AuthRepository } from './auth.repository';
import { AuthService } from './auth.service';
import { AppLogger } from '../common/logging/app-logger.service';
import { JwtAuthGuard } from './guards/jwt-auth.guard';
import { LoginThrottleGuard } from './guards/login-throttle.guard';
import { TokenService } from './token.service';
import { EmailModule } from '../email/email.module';

@Module({
  imports: [
    EmailModule,
  ],
  controllers: [AuthController],
  providers: [
    AuthService,
    AuthRepository,
    TokenService,
    AppLogger,
    JwtAuthGuard,
    LoginThrottleGuard,
  ],
  exports: [TokenService, JwtAuthGuard, AppLogger],
})
export class AuthModule {}
