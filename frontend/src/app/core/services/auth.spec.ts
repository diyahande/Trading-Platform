import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { AuthService as ApiAuthService } from '../../generated/api-client/api/auth.service';
import { Configuration } from '../../generated/api-client/configuration';

import { Auth } from './auth';

describe('Auth', () => {
  let service: Auth;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: ApiAuthService,
          useValue: {
            authControllerLogin: () => of(),
            authControllerRefresh: () => of(),
          },
        },
        {
          provide: HttpClient,
          useValue: {
            post: () => of({ message: 'ok' }),
          },
        },
        {
          provide: Configuration,
          useValue: {
            basePath: 'http://localhost:3000',
          },
        },
      ],
    });
    service = TestBed.inject(Auth);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
