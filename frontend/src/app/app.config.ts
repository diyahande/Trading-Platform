import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideApi } from './generated/api-client/provide-api';
import { AUTH_HEADER_POLICY, bearerTokenInterceptor } from './core/interceptors/bearer-token.interceptor';
import { basePathInterceptor } from './core/interceptors/base-path.interceptor';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptors([basePathInterceptor, bearerTokenInterceptor])),
    provideRouter(routes),
    {
      provide: AUTH_HEADER_POLICY,
      useValue: {
        allowedOrigins: ['http://localhost:4200', 'http://localhost:3000', 'http://localhost:8082', 'http://localhost:8083'],
        unauthenticatedAuthPaths: [
          '/auth/login',
          '/auth/register',
          '/auth/refresh',
          '/auth/signup-request',
        ],
      },
    },
    // Use relative paths; basePathInterceptor will route to correct service
    provideApi({ basePath: '' })
  ]
};
