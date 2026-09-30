import { UnprocessableEntityException, ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';

import { ERROR_INVALID_INPUT } from './auth/auth.constants';
import { AppModule } from './app.module';
import { AppLogger } from './common/logging/app-logger.service';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  app.useLogger(app.get(AppLogger));

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      exceptionFactory: () =>
        new UnprocessableEntityException(ERROR_INVALID_INPUT),
    }),
  );

  const config = new DocumentBuilder()
    .setTitle('Auth service')
    .setVersion('1.0.0')
    .addBearerAuth(
      {
        type: 'http',
        scheme: 'bearer',
        bearerFormat: 'JWT',
      },
      'bearerAuth',
    )
    .build();

  const document = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup('docs', app, document, {
    jsonDocumentUrl: '/docs/json',
  });

  await app.listen(process.env.PORT ?? 3000);
}
void bootstrap();
