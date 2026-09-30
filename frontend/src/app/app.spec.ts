import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient()],
    }).compileComponents();
  });

  afterEach(() => localStorage.clear());

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the product name in the navbar', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.brand-name')?.textContent).toContain('Chronobeat');
  });

  it('offers the records page to guests', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const link = (fixture.nativeElement as HTMLElement).querySelector('.profile-link');
    expect(link?.textContent).toContain('Records');
    expect(link?.querySelector('.avatar')).toBeNull();
  });

  it('shows the stored profile nickname with an avatar initial', async () => {
    localStorage.setItem('chronobeat.profile', JSON.stringify({ id: 'p1', nickname: 'ana', token: 't' }));
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const link = (fixture.nativeElement as HTMLElement).querySelector('.profile-link');
    expect(link?.querySelector('.avatar')?.textContent?.trim()).toBe('A');
    expect(link?.textContent).toContain('ana');
  });
});
