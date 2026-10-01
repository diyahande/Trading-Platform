import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AuthService as ApiAuthService } from '../../generated/api-client/api/auth.service';

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
      ],
    });
    service = TestBed.inject(Auth);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
