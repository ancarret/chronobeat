import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { errorInterceptor } from './error.interceptor';
import { ProfileService } from './profile.service';
import { profileInterceptor } from './profile.interceptor';

const API = 'http://localhost:8080';
const STORAGE_KEY = 'chronobeat.profile';

describe('ProfileService', () => {
  let http: HttpTestingController;

  function setup(stored?: object | string) {
    localStorage.clear();
    if (stored !== undefined) {
      localStorage.setItem(STORAGE_KEY, typeof stored === 'string' ? stored : JSON.stringify(stored));
    }
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([profileInterceptor, errorInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(ProfileService);
  }

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('starts as a guest when nothing is stored', () => {
    const service = setup();
    expect(service.profile()).toBeNull();
    expect(service.token()).toBeNull();
  });

  it('loads a stored profile', () => {
    const service = setup({ id: 'p1', nickname: 'Ana', token: 'secret' });
    expect(service.profile()?.nickname).toBe('Ana');
    expect(service.token()).toBe('secret');
  });

  it('ignores corrupt or incomplete stored data instead of crashing', () => {
    expect(setup('{not json').profile()).toBeNull();
    TestBed.resetTestingModule();
    expect(setup({ id: 'p1' }).profile()).toBeNull();
  });

  it('creates a profile once, persists it, and reuses it afterwards', () => {
    const service = setup();

    let created: unknown;
    service.ensureProfile('  Ana ').subscribe((p) => (created = p));
    const req = http.expectOne(`${API}/api/profiles`);
    expect(req.request.body).toEqual({ nickname: 'Ana' });
    req.flush({ id: 'p1', nickname: 'Ana', token: 'secret' });

    expect(created).toEqual({ id: 'p1', nickname: 'Ana', token: 'secret' });
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual(created);

    let again: unknown;
    service.ensureProfile('Someone else').subscribe((p) => (again = p));
    http.expectNone(`${API}/api/profiles`);
    expect(again).toEqual(created);
  });

  it('falls back to a default nickname when the name is blank', () => {
    const service = setup();
    service.ensureProfile('   ').subscribe();
    const req = http.expectOne(`${API}/api/profiles`);
    expect(req.request.body).toEqual({ nickname: 'Player' });
    req.flush({ id: 'p1', nickname: 'Player', token: 't' });
  });

  it('keeps working in memory when localStorage is unavailable', () => {
    const service = setup();
    const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota');
    });

    service.ensureProfile('Ana').subscribe();
    http.expectOne(`${API}/api/profiles`).flush({ id: 'p1', nickname: 'Ana', token: 'secret' });

    expect(service.profile()?.token).toBe('secret');
    spy.mockRestore();
  });

  it('restores a profile from a recovery code and stores it', () => {
    const service = setup();

    service.restore('  the-code ').subscribe();
    const req = http.expectOne(`${API}/api/profiles/me`);
    expect(req.request.headers.get('X-Profile-Token')).toBe('the-code');
    req.flush({ id: 'p9', nickname: 'Back', createdAt: '2026-01-01T00:00:00Z' });

    expect(service.profile()).toEqual({ id: 'p9', nickname: 'Back', token: 'the-code' });
  });

  it('forgets a profile the server no longer recognises (401)', () => {
    const service = setup({ id: 'p1', nickname: 'Ana', token: 'stale' });

    let error: { status: number } | undefined;
    service.stats().subscribe({ error: (e) => (error = e) });
    http.expectOne(`${API}/api/profiles/me/stats`).flush(
      { message: 'Missing or invalid X-Profile-Token header', status: 401 },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(error?.status).toBe(401);
    expect(service.profile()).toBeNull();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('keeps the profile on other errors', () => {
    const service = setup({ id: 'p1', nickname: 'Ana', token: 'good' });
    service.stats().subscribe({ error: () => undefined });
    http.expectOne(`${API}/api/profiles/me/stats`).flush('boom', { status: 500, statusText: 'Server Error' });
    expect(service.profile()).not.toBeNull();
  });
});

describe('profileInterceptor', () => {
  let http: HttpTestingController;
  let client: HttpClient;

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ id: 'p1', nickname: 'Ana', token: 'secret' }));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([profileInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('sends the token to our own API', () => {
    client.get(`${API}/api/games/x`).subscribe();
    expect(http.expectOne(`${API}/api/games/x`).request.headers.get('X-Profile-Token')).toBe('secret');
  });

  it('never sends the token to third-party URLs', () => {
    client.get('https://itunes.apple.com/search').subscribe();
    expect(http.expectOne('https://itunes.apple.com/search').request.headers.has('X-Profile-Token')).toBe(false);
  });

  it('does not overwrite an explicitly provided token', () => {
    client.get(`${API}/api/profiles/me`, { headers: { 'X-Profile-Token': 'explicit' } }).subscribe();
    expect(http.expectOne(`${API}/api/profiles/me`).request.headers.get('X-Profile-Token')).toBe('explicit');
  });
});
