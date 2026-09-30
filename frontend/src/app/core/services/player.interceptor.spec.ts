import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { OnlineSessionService } from './online-session.service';
import { playerInterceptor } from './player.interceptor';

const API = 'http://localhost:8080';
const GAME = '3f2b1c4e-1111-2222-3333-444455556666';
const OTHER_GAME = '9a8b7c6d-1111-2222-3333-444455556666';

describe('playerInterceptor', () => {
  let http: HttpTestingController;
  let client: HttpClient;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([playerInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    TestBed.inject(OnlineSessionService).remember({ gameId: GAME, roomCode: 'BCDF', playerId: 'p1', playerToken: 'seat-secret' });
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  const tokenOf = (url: string) => http.expectOne(url).request.headers.get('X-Player-Token');

  it('presents the seat token on calls about the game it belongs to', () => {
    client.get(`${API}/api/games/${GAME}/state`).subscribe();
    expect(tokenOf(`${API}/api/games/${GAME}/state`)).toBe('seat-secret');
  });

  it('also covers the bare game URL and calls with query strings', () => {
    client.get(`${API}/api/games/${GAME}`).subscribe();
    client.post(`${API}/api/games/${GAME}/next-round?after=2`, {}).subscribe();
    expect(tokenOf(`${API}/api/games/${GAME}`)).toBe('seat-secret');
    expect(tokenOf(`${API}/api/games/${GAME}/next-round?after=2`)).toBe('seat-secret');
  });

  it('does not send a token for games this browser has no seat in', () => {
    client.get(`${API}/api/games/${OTHER_GAME}/state`).subscribe();
    expect(tokenOf(`${API}/api/games/${OTHER_GAME}/state`)).toBeNull();
  });

  it('never leaks the token to another origin', () => {
    client.get(`https://evil.example/api/games/${GAME}/state`).subscribe();
    expect(tokenOf(`https://evil.example/api/games/${GAME}/state`)).toBeNull();
  });

  it('leaves requests that are not about a game alone', () => {
    client.get(`${API}/api/rooms/BCDF`).subscribe();
    expect(tokenOf(`${API}/api/rooms/BCDF`)).toBeNull();
  });
});
