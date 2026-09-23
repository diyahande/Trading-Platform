import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';

import { AppController } from './app.controller';
import { AppService } from './app.service';
import { JwtService } from './auth/jwt.service';
import { JwtAuthGuard } from './auth/jwt-auth.guard';


@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
    }),
  ],
  controllers: [AppController],
  providers: [AppService, JwtService, JwtAuthGuard],
  exports: [JwtService, JwtAuthGuard],
})
export class AppModule {}