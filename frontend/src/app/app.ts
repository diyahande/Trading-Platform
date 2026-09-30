import { Component } from '@angular/core';
import { Login } from './features/auth/login/login';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [Login],
  template: '<app-login></app-login>',
  styleUrl: './app.css'
})
export class App {}
