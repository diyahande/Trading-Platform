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
        allowedOrigins: ['http://localhost:3000', 'http://localhost:8080'],
        unauthenticatedAuthPaths: ['/auth/login', '/auth/register', '/auth/refresh', '/auth/signup-request'],
      },
    },
    // adjust basePath as needed for your running backend (example localhost:3333)
    provideApi({ basePath: 'http://localhost:3000' })
  ]
};
