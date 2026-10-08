import { Component, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../../core/services/auth';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {}

@Component({
  selector: 'app-root-redirect',
  standalone: true,
  template: '',
})
export class RootRedirect implements OnInit {
  constructor(
    private readonly auth: Auth,
    private readonly router: Router,
  ) {}

  async ngOnInit(): Promise<void> {
    const token = await this.auth.getValidAccessToken();
    void this.router.navigateByUrl(token ? '/dashboard' : '/home', { replaceUrl: true });
  }
}