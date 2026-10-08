import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, OnInit } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../../core/services/auth';
import { ProfileService } from '../../generated/api-client/api/profile.service';
import { UserResponseDto } from '../../generated/api-client/model/userResponseDto';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './profile.html',
  styleUrl: './profile.css',
})
export class Profile implements OnInit {
  user: UserResponseDto | null = null;
  isLoading = true;
  errorMessage: string | null = null;
  isProfileMenuOpen = false;
  isNavMenuOpen = false;

  constructor(
    private readonly auth: Auth,
    private readonly router: Router,
    private readonly elementRef: ElementRef<HTMLElement>,
    private readonly profileService: ProfileService,
  ) {}

  ngOnInit(): void {
    void this.loadProfile();
  }

  toggleNavMenu(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.isNavMenuOpen = !this.isNavMenuOpen;
  }

  toggleProfileMenu(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.isProfileMenuOpen = !this.isProfileMenuOpen;
  }

  viewProfile(): void {
    this.isProfileMenuOpen = false;
    this.isNavMenuOpen = false;
  }

  viewPreferences(): void {
    this.isProfileMenuOpen = false;
    this.isNavMenuOpen = false;
    void this.router.navigate(['/preferences']);
  }

  viewChangePassword(): void {
    this.isProfileMenuOpen = false;
    this.isNavMenuOpen = false;
    void this.router.navigate(['/change-password']);
  }

  signOut(): void {
    this.isProfileMenuOpen = false;
    this.isNavMenuOpen = false;
    this.auth.signOut();
    void this.router.navigate(['/login']);
  }

  get displayName(): string {
    const firstName = this.user?.firstName?.trim() ?? '';
    const lastName = this.user?.lastName?.trim() ?? '';
    const fullName = `${firstName} ${lastName}`.trim();
    return fullName || this.user?.username || 'Trader';
  }

  get initials(): string {
    const firstNameInitial = this.user?.firstName?.trim().charAt(0) ?? '';
    const lastNameInitial = this.user?.lastName?.trim().charAt(0) ?? '';
    const fromNames = `${firstNameInitial}${lastNameInitial}`.toUpperCase();
    if (fromNames) {
      return fromNames;
    }
    const usernameInitial = this.user?.username?.trim().charAt(0) ?? '';
    return usernameInitial.toUpperCase() || 'U';
  }

  formatValue(value: string | null | undefined): string {
    if (typeof value !== 'string') {
      return 'Not available';
    }
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : 'Not available';
  }

  formatRoles(roles: UserResponseDto.RolesEnum[] | undefined): string {
    if (!Array.isArray(roles) || roles.length === 0) {
      return 'Not available';
    }
    return roles.join(', ');
  }

  formatDate(value: string | null | undefined): string {
    if (!value) {
      return 'Not available';
    }
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return 'Not available';
    }
    return new Intl.DateTimeFormat('en-US', {
      dateStyle: 'medium',
    }).format(parsed);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.isProfileMenuOpen && !this.isNavMenuOpen) {
      return;
    }

    const target = event.target as Node | null;
    if (target && !this.elementRef.nativeElement.contains(target)) {
      this.isProfileMenuOpen = false;
      this.isNavMenuOpen = false;
    }
  }

  private async loadProfile(): Promise<void> {
    this.isLoading = true;
    this.errorMessage = null;

    try {
      this.user = await firstValueFrom(this.profileService.authControllerMe());
    } catch (error: any) {
      const status = typeof error?.status === 'number' ? error.status : null;
      if (status === 401) {
        this.errorMessage = 'Session expired. Please sign in again.';
      } else {
        this.errorMessage = 'Unable to load your profile details right now.';
      }
      this.user = null;
    } finally {
      this.isLoading = false;
    }
  }
}
