import { Component } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Auth } from '../../core/services/auth';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css'],
})
export class Dashboard {
  username = 'Trader';

  constructor(private route: ActivatedRoute, private auth: Auth, private router: Router) {
    const routeUsername = this.route.snapshot.queryParamMap.get('username');
    if (routeUsername && routeUsername.trim()) {
      this.username = routeUsername;
    }
  }

  signOut() {
    this.auth.signOut();
    void this.router.navigate(['/login']);
  }
}
