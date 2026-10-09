import { MiddlewareConsumer, Module, NestModule } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { resolve } from 'node:path';

import { AuthModule } from './auth/auth.module';
import { EmailModule } from './email/email.module';
import { RequestLoggingMiddleware } from './common/logging/request-logging.middleware';

// Repo-root .env (src/ and dist/ both sit three levels below the root).
const ROOT_ENV_FILE = resolve(__dirname, '..', '..', '..', '.env');

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ROOT_ENV_FILE,
    }),
    AuthModule,
    EmailModule
  ],
  controllers: [],
  providers: [RequestLoggingMiddleware],
  exports: [],
})
export class AppModule implements NestModule {
  configure(consumer: MiddlewareConsumer): void {
    consumer.apply(RequestLoggingMiddleware).forRoutes('*');
  }
}