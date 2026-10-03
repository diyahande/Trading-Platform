import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideApi } from './generated/api-client/provide-api';
import { AUTH_HEADER_POLICY, bearerTokenInterceptor } from './core/interceptors/bearer-token.interceptor';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptors([bearerTokenInterceptor])),
    provideRouter(routes),
    {
      provide: AUTH_HEADER_POLICY,
      useValue: {
        allowedOrigins: ['http://localhost:4200', 'http://localhost:3000', 'http://localhost:8083'],
        unauthenticatedAuthPaths: [
          '/auth/login',
          '/auth/register',
          '/auth/refresh',
          '/auth/signup-request',
        ],
      },
    },
    // Use relative basePath so Angular dev proxy can split auth and trade API calls.
    provideApi({ basePath: '' })
  ]
};
